package com.vervan.chat.store

import android.util.Log
import com.vervan.chat.data.db.entities.StoreInstallSession
import com.vervan.chat.data.db.entities.StoreInstallState
import com.vervan.chat.store.catalog.CatalogRepository
import com.vervan.chat.store.catalog.SyncResult
import com.vervan.chat.store.eligibility.EligibilityResult
import com.vervan.chat.store.eligibility.VariantEligibilityChecker
import com.vervan.chat.store.install.InstallOutcome
import com.vervan.chat.store.install.InstallProgress
import com.vervan.chat.store.install.VariantInstaller
import com.vervan.chat.store.license.LicenseAcceptanceStore
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.StoreCatalog
import com.vervan.chat.store.model.StoreModel
import com.vervan.chat.store.storage.InstallRecord
import com.vervan.chat.store.storage.InstalledManifestStore
import com.vervan.chat.store.storage.StoreMaintenance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single entry point the UI talks to. Composes the catalogue, eligibility, licence and install
 * layers so no ViewModel has to know how they fit together.
 *
 * Mirrors the concurrency policy of the app's existing
 * [com.vervan.chat.modeldownload.ModelDownloadRepository]: **one install at a time**, serialised by
 * a mutex. Parallel multi-gigabyte downloads on a phone contend for the same bandwidth and the same
 * storage headroom, so they finish no sooner in aggregate and make the disk-space precheck a lie —
 * each install's check would be computed as if it were the only one running.
 */
class ModelStoreRepository(
    private val catalogRepository: CatalogRepository,
    private val installer: VariantInstaller,
    private val manifestStore: InstalledManifestStore,
    private val maintenance: StoreMaintenance,
    private val licenseStore: LicenseAcceptanceStore,
    private val eligibilityProvider: () -> VariantEligibilityChecker,
    private val scope: CoroutineScope,
    /**
     * Invoked once an install has passed every gate and is about to start. Wired to
     * [StoreDownloadService.start] in production; a no-op default keeps this class free of any
     * Android service dependency, which is what lets the installer tests construct it directly.
     */
    private val onInstallStarting: () -> Unit = {}
) {
    private val installMutex = Mutex()
    private var activeJob: Job? = null

    val catalog: StateFlow<StoreCatalog?> = catalogRepository.catalog
    val syncError: StateFlow<String?> = catalogRepository.lastSyncError

    private val _installed = MutableStateFlow<List<InstallRecord>>(emptyList())
    val installed: StateFlow<List<InstallRecord>> = _installed

    private val _activeInstall = MutableStateFlow<ActiveInstall?>(null)
    val activeInstall: StateFlow<ActiveInstall?> = _activeInstall

    data class ActiveInstall(
        val variantId: String,
        val displayName: String,
        val state: StoreInstallState,
        val progress: InstallProgress?,
        val error: String? = null
    )

    suspend fun initialize() {
        catalogRepository.initialize()
        refreshInstalled()
    }

    suspend fun sync(): SyncResult = catalogRepository.sync()

    fun refreshInstalled() {
        _installed.value = manifestStore.readAll()
    }

    fun isInstalled(variantId: String): Boolean = _installed.value.any { it.variantId == variantId }

    fun eligibility(variant: ModelVariant, intendedContextTokens: Int? = null): EligibilityResult =
        eligibilityProvider().check(variant, intendedContextTokens)

    fun needsLicenseAcceptance(model: StoreModel): Boolean = !licenseStore.isAccepted(model)

    fun acceptLicense(model: StoreModel) {
        licenseStore.accept(model, catalog.value?.catalogVersion ?: 0)
    }

    /**
     * Starts an install. Returns false when it was refused up front — the caller should already
     * have prevented these cases in the UI, but this is the enforcement point rather than the
     * presentation one, so a bug in a screen cannot bypass the licence gate or the device check.
     */
    fun install(model: StoreModel, variant: ModelVariant): Boolean {
        if (isInstalled(variant.variantId)) return false
        // One install at a time (see the class doc). Without this, a second tap would overwrite
        // the single _activeInstall slot and the first install's progress would stop being
        // reportable even though it is still running behind the mutex.
        if (activeJob?.isActive == true) {
            Log.w(TAG, "Refusing install of ${variant.variantId}: another install is in progress")
            return false
        }
        // Spec §14: never skip tap-to-accept, in either tier.
        if (needsLicenseAcceptance(model)) {
            Log.w(TAG, "Refusing install of ${variant.variantId}: licence not accepted")
            return false
        }
        if (!eligibility(variant).canInstall) {
            Log.w(TAG, "Refusing install of ${variant.variantId}: device is not compatible")
            return false
        }

        // Started before the coroutine rather than inside it: the service must be up while this
        // call is still traceable to the user's tap, or Android's background-start restrictions
        // reject it on 12+.
        _activeInstall.value = ActiveInstall(
            variant.variantId, model.displayName, StoreInstallState.QUEUED, null
        )
        onInstallStarting()

        activeJob = scope.launch {
            installMutex.withLock {
                val outcome = installer.install(
                    model = model,
                    variant = variant,
                    catalogVersion = catalog.value?.catalogVersion ?: 0,
                    acceptedLicenseHash = model.license.acceptanceHash
                ) { progress ->
                    _activeInstall.value = _activeInstall.value?.copy(
                        state = StoreInstallState.DOWNLOADING,
                        progress = progress
                    )
                }
                _activeInstall.value = when (outcome) {
                    is InstallOutcome.Success -> {
                        refreshInstalled()
                        null
                    }
                    is InstallOutcome.Failed -> _activeInstall.value?.copy(
                        state = if (outcome.permanent) {
                            StoreInstallState.FAILED_PERMANENT
                        } else {
                            StoreInstallState.FAILED_RETRYABLE
                        },
                        error = outcome.reason
                    )
                    InstallOutcome.Cancelled -> null
                }
            }
        }
        return true
    }

    /** Cancels the running install. The coroutine cancellation propagates into
     * [VariantInstaller], which deliberately leaves staged parts on disk so a later retry resumes
     * rather than restarting. */
    fun cancelActiveInstall() {
        activeJob?.cancel()
        activeJob = null
        _activeInstall.value = null
    }

    fun dismissInstallError() {
        _activeInstall.value = null
    }

    /**
     * Removes an installed variant. Only the manifest is deleted here; the blobs it referenced are
     * left for the GC pass, because another installed variant may share them and deciding that
     * here would mean duplicating the reference computation that
     * [StoreMaintenance.reconcileAndCollect] already owns.
     */
    suspend fun uninstall(variantId: String) {
        manifestStore.uninstall(variantId)
        refreshInstalled()
        maintenance.reconcileAndCollect()
    }

    fun usageByVariant(): Map<String, Long> = maintenance.usageByVariant()

    companion object {
        private const val TAG = "ModelStoreRepository"
    }
}
