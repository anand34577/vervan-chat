package com.vervan.chat.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.store.ModelStoreRepository
import com.vervan.chat.store.catalog.SyncResult
import com.vervan.chat.store.eligibility.EligibilityResult
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.StoreModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row in the store list: a model plus everything the UI needs to decide what to render for
 * it, precomputed so the composable stays free of policy. */
data class StoreModelUi(
    val model: StoreModel,
    val variants: List<StoreVariantUi>,
    val licenseAccepted: Boolean
)

data class StoreVariantUi(
    val variant: ModelVariant,
    val eligibility: EligibilityResult,
    val installed: Boolean
)

class ModelStoreViewModel(app: VervanApp) : ViewModel() {
    private val repo: ModelStoreRepository = app.container.modelStoreRepository

    val activeInstall = repo.activeInstall
    val syncError = repo.syncError

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    private val _models = MutableStateFlow<List<StoreModelUi>>(emptyList())
    val models: StateFlow<List<StoreModelUi>> = _models

    /** Pending licence prompt. Non-null means the user tapped install on a model whose licence
     * they have not accepted (or whose licence text changed since they did). */
    private val _pendingLicense = MutableStateFlow<Pair<StoreModel, ModelVariant>?>(null)
    val pendingLicense: StateFlow<Pair<StoreModel, ModelVariant>?> = _pendingLicense

    init {
        viewModelScope.launch {
            repo.initialize()
            rebuild()
        }
        // Rebuild whenever the catalogue or the installed set changes, so eligibility and
        // installed badges never go stale behind a completed install.
        viewModelScope.launch { repo.catalog.collect { rebuild() } }
        viewModelScope.launch { repo.installed.collect { rebuild() } }
    }

    private fun rebuild() {
        val catalog = repo.catalog.value
        _models.value = catalog?.models?.map { model ->
            StoreModelUi(
                model = model,
                variants = model.variants.map { variant ->
                    StoreVariantUi(
                        variant = variant,
                        eligibility = repo.eligibility(variant),
                        installed = repo.isInstalled(variant.variantId)
                    )
                },
                licenseAccepted = !repo.needsLicenseAcceptance(model)
            )
        }.orEmpty()
    }

    fun sync() {
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = when (val result = repo.sync()) {
                is SyncResult.Updated -> "Updated to catalogue v${result.catalogVersion} (${result.modelCount} models)"
                SyncResult.AlreadyCurrent -> "Catalogue is up to date"
                is SyncResult.Failed -> null // surfaced via syncError instead
            }
            _syncing.value = false
            rebuild()
        }
    }

    /**
     * Install entry point. The licence gate lives here rather than in the composable so a screen
     * cannot route around it — if acceptance is missing, this opens the prompt instead of
     * installing, and the actual install only runs after [acceptLicenseAndInstall].
     * [ModelStoreRepository.install] re-checks the same conditions as a backstop.
     */
    fun install(model: StoreModel, variant: ModelVariant) {
        if (repo.needsLicenseAcceptance(model)) {
            _pendingLicense.value = model to variant
            return
        }
        repo.install(model, variant)
    }

    fun acceptLicenseAndInstall() {
        val (model, variant) = _pendingLicense.value ?: return
        repo.acceptLicense(model)
        _pendingLicense.value = null
        repo.install(model, variant)
        rebuild()
    }

    fun dismissLicensePrompt() {
        _pendingLicense.value = null
    }

    fun cancelInstall() = repo.cancelActiveInstall()

    fun dismissInstallError() = repo.dismissInstallError()

    fun dismissSyncMessage() {
        _syncMessage.value = null
    }

    fun uninstall(variantId: String) {
        viewModelScope.launch {
            repo.uninstall(variantId)
            rebuild()
        }
    }
}
