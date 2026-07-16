package com.vervan.chat.ui.models

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.reconcileCapabilities
import com.vervan.chat.model.ImportResult
import com.vervan.chat.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun BackendChoice.toEnginePreference(): LlmEngine.BackendPreference = when (this) {
    BackendChoice.AUTO -> LlmEngine.BackendPreference.AUTO
    BackendChoice.GPU -> LlmEngine.BackendPreference.GPU_ONLY
    BackendChoice.CPU -> LlmEngine.BackendPreference.CPU_ONLY
    BackendChoice.NPU -> LlmEngine.BackendPreference.NPU_ONLY
}

/** App-wide generation defaults (Settings) — a model's own temperature/topP/topK/context/seed
 * fields are null until the user overrides them in Configure, and fall back to these. */
data class ModelDefaults(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxNumImages: Int,
    val contextTokens: Int,
    val seed: Int
)

class ModelManagerViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val importManager = app.container.modelImportManager
    private val settings = app.container.settingsRepository
    private val downloadRepo = app.container.modelDownloadRepository

    /** Catalogue entries not yet installed + anything actively downloading — "Available for
     * Download" and "Active Downloads" both render from this one flow, split by status. Ready
     * installed models are deliberately NOT included here; they stay [models] (a plain
     * [ModelInfo] list) since load/unload/delete for them is already fully implemented below. */
    val downloadStates: StateFlow<List<com.vervan.chat.modeldownload.ModelUiState>> =
        downloadRepo.uiStates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun downloadModel(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.startDownload(modelId, version) }
    }
    fun pauseDownload(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.pauseDownload(modelId, version) }
    }
    fun resumeDownload(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.resumeDownload(modelId, version) }
    }
    fun cancelDownload(modelId: String, version: String, keepPartial: Boolean) {
        viewModelScope.launch { downloadRepo.cancelDownload(modelId, version, keepPartial) }
    }
    fun retryDownload(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.retryDownload(modelId, version) }
    }

    companion object {
        private const val TAG = "ModelManagerVM"
    }

    val models: StateFlow<List<ModelInfo>> = db.modelDao().observeModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaults: StateFlow<ModelDefaults> = combine<Number, ModelDefaults>(
        settings.temperature, settings.topP, settings.topK, settings.maxNumImages, settings.contextTokenLimit, settings.randomSeed
    ) { values ->
        ModelDefaults(
            temperature = values[0].toFloat(),
            topP = values[1].toFloat(),
            topK = values[2].toInt(),
            maxNumImages = values[3].toInt(),
            contextTokens = values[4].toInt(),
            seed = values[5].toInt()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelDefaults(0.8f, 0.95f, 40, 1, 4096, -1))

    init {
        // Single-model-per-role convenience (user ask): with only one generation (or
        // embedding) model installed, it's simply "the" model — no manual "set as default"
        // step needed. Re-evaluated on every models change so this also kicks in right after
        // deleting down to a lone survivor, not just at import time.
        viewModelScope.launch {
            models.collect { list ->
                ModelRole.entries.forEach { role ->
                    val sole = list.filter { it.role == role }.singleOrNull()
                    if (sole != null && !sole.isActive) setActive(sole)
                }
            }
        }
    }

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _busyModelId = MutableStateFlow<String?>(null)
    val busyModelId: StateFlow<String?> = _busyModelId

    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel

    /** Set when a model needs the one-time license acknowledgment (spec §12) before it can
     * be activated — the screen shows a dialog and calls [acknowledgeAndActivate] or dismisses. */
    private val _pendingAcknowledgment = MutableStateFlow<ModelInfo?>(null)
    val pendingAcknowledgment: StateFlow<ModelInfo?> = _pendingAcknowledgment

    /** Set when a freshly-verified import looks like a new version of an already-installed
     * model (spec §11.12) — the screen offers to relink defaults instead of silently
     * replacing the active model. */
    private val _pendingMigration = MutableStateFlow<Pair<ModelInfo, ModelInfo>?>(null)
    val pendingMigration: StateFlow<Pair<ModelInfo, ModelInfo>?> = _pendingMigration

    /** Which model.filePath is actually resident in the (single, shared) generation/embedding
     * engine right now — drives the single Load/Unload toggle per card instead of showing both
     * buttons regardless of real state. Refreshed after every action that can change it; this
     * screen is the only place that mutates engine state via user action, so polling on every
     * recomposition isn't needed. */
    private val _loadedGenerationPath = MutableStateFlow<String?>(app.container.llmEngine.loadedModelPath)
    val loadedGenerationPath: StateFlow<String?> = _loadedGenerationPath
    private val _loadedEmbeddingPath = MutableStateFlow<String?>(app.container.embeddingEngine.loadedModelPath)
    val loadedEmbeddingPath: StateFlow<String?> = _loadedEmbeddingPath

    fun refreshLoadedState() {
        _loadedGenerationPath.value = app.container.llmEngine.loadedModelPath
        _loadedEmbeddingPath.value = app.container.embeddingEngine.loadedModelPath
    }

    fun importModel(uri: Uri, role: ModelRole) {
        Log.i(TAG, "importModel() requested: uri=$uri, role=$role")
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = "Opening model…"
            try {
                when (val result = importManager.import(uri, role) { progress ->
                    _busyLabel.value = progress
                    _status.value = progress
                }) {
                    is ImportResult.Success -> {
                        Log.i(TAG, "importModel() copied file ok: ${result.model.displayName} (${result.model.fileSizeBytes} bytes)")
                        validateAndActivate(result.model)
                    }
                    is ImportResult.Duplicate -> {
                        Log.i(TAG, "importModel() duplicate of ${result.existing.displayName}")
                        _status.value = "Already imported as ${result.existing.displayName}"
                    }
                    is ImportResult.Rejected -> {
                        Log.w(TAG, "importModel() rejected: ${result.reason}")
                        _status.value = "Import failed: ${result.reason}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importModel() threw unexpectedly", t)
                _status.value = "Import failed: ${t.message ?: t::class.simpleName}"
            } finally {
                _importing.value = false
                _busyLabel.value = null
            }
        }
    }

    /** Embedding models always need two files (the model + its tokenizer) — see
     * [com.vervan.chat.model.ModelImportManager.importEmbeddingModel]. */
    fun importEmbeddingPair(fileA: Uri, fileB: Uri) {
        Log.i(TAG, "importEmbeddingPair() requested: $fileA, $fileB")
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = "Opening model…"
            try {
                when (val result = importManager.importEmbeddingModel(fileA, fileB) { progress ->
                    _busyLabel.value = progress
                    _status.value = progress
                }) {
                    is ImportResult.Success -> {
                        Log.i(TAG, "importEmbeddingPair() copied file ok: ${result.model.displayName}")
                        validateAndActivate(result.model)
                    }
                    is ImportResult.Duplicate -> {
                        Log.i(TAG, "importEmbeddingPair() duplicate of ${result.existing.displayName}")
                        _status.value = "Already imported as ${result.existing.displayName}"
                    }
                    is ImportResult.Rejected -> {
                        Log.w(TAG, "importEmbeddingPair() rejected: ${result.reason}")
                        _status.value = "Import failed: ${result.reason}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importEmbeddingPair() threw unexpectedly", t)
                _status.value = "Import failed: ${t.message ?: t::class.simpleName}"
            } finally {
                _importing.value = false
                _busyLabel.value = null
            }
        }
    }

    private suspend fun validateAndActivate(model: ModelInfo) {
        Log.i(TAG, "validateAndActivate() start: ${model.displayName} (role=${model.role})")
        if (!canLoadSafely(model)) {
            Log.w(TAG, "validateAndActivate() rejected pre-flight: ${unsupportedRuntimeMessage(model)}")
            _status.value = unsupportedRuntimeMessage(model)
            return
        }
        _busyModelId.value = model.id
        _busyLabel.value = "Validating ${model.displayName}..."
        _status.value = "Validating ${model.displayName}..."
        // B12: MODEL_VERIFY is another job type the Job Queue screen claimed to track but
        // never actually created a record for.
        val job = com.vervan.chat.data.db.entities.JobRecord(
            type = com.vervan.chat.data.db.entities.JobType.MODEL_VERIFY,
            label = model.displayName,
            state = com.vervan.chat.data.db.entities.JobState.RUNNING
        )
        db.jobDao().upsert(job)
        try {
            val verified = withContext(Dispatchers.Default) {
                    if (model.role == ModelRole.GENERATION) {
                        app.container.withLlm { engine ->
                            val result = engine.load(model.filePath)
                            val output = StringBuilder()
                            engine.generate("Reply with OK.").collect { chunk ->
                                output.append(chunk)
                                if (output.length > 10) return@collect
                            }
                            require(output.isNotBlank()) {
                                "Model initialized but produced no output"
                            }
                        val mtpSupported = engine.detectSpeculativeDecodingSupport(model.filePath)
                        Log.i(TAG, "validateAndActivate() ${model.displayName} mtpSupported=$mtpSupported")
                        val dbBackend = when (result.backend) {
                            LlmEngine.ModelBackend.GPU -> ModelBackend.GPU
                            LlmEngine.ModelBackend.NPU -> ModelBackend.NPU
                            else -> ModelBackend.CPU
                        }
                        val (reconciled, _) = model.copy(mtpSupported = mtpSupported)
                            .reconcileCapabilities(dbBackend, engine.visionEnabled, engine.audioEnabled, mtpAttempted = false, mtpActive = false)
                        reconciled
                    }
                } else {
                    app.container.withEmbedding { engine ->
                        engine.load(model.filePath, model.tokenizerPath)
                        require(engine.embed("model validation")?.isNotEmpty() == true) {
                            "Embedding model returned no vector"
                        }
                        val dbBackend = if (engine.activeBackend == com.vervan.chat.retrieval.EmbeddingBackend.GPU) ModelBackend.GPU else ModelBackend.CPU
                        model.copy(lastWorkingBackend = dbBackend)
                    }
                }
            }
            db.modelDao().upsert(verified)
            Log.i(TAG, "validateAndActivate() SUCCESS: ${verified.displayName} verified on ${verified.lastWorkingBackend}")
            _status.value = "Verified ${verified.displayName}"
            db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
            refreshLoadedState()

            val previousVersion = db.modelDao().getOthersOfRole(verified.role, verified.id)
                .firstOrNull { com.vervan.chat.model.ModelFamily.sameFamily(it.displayName, verified.displayName) }
            if (previousVersion != null) {
                Log.i(TAG, "validateAndActivate() looks like a new version of ${previousVersion.displayName}; asking user")
                _pendingMigration.value = verified to previousVersion
            } else {
                setActive(verified)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "validateAndActivate() FAILED for ${model.displayName}: ${t::class.simpleName}: ${t.message}", t)
            db.modelDao().upsert(model.copy(lastWorkingBackend = ModelBackend.UNVERIFIED))
            db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = t.message ?: ""))
            _status.value = "Model could not be verified: ${t.message ?: t::class.simpleName}. " +
                "The file is kept — try activating it manually, or delete it if it's incompatible."
        } finally {
            _busyModelId.value = null
            _busyLabel.value = null
        }
    }

    /** Requires [ModelInfo.licenseAcknowledged] first — if it isn't set yet, this raises the
     * acknowledgment dialog instead of activating immediately. */
    fun setActive(model: ModelInfo) {
        if (!model.licenseAcknowledged) {
            _pendingAcknowledgment.value = model
            return
        }
        viewModelScope.launch {
            db.modelDao().clearActive(model.role)
            db.modelDao().upsert(model.copy(isActive = true))
        }
    }

    fun acknowledgeAndActivate(model: ModelInfo) {
        _pendingAcknowledgment.value = null
        viewModelScope.launch {
            db.modelDao().clearActive(model.role)
            db.modelDao().upsert(model.copy(isActive = true, licenseAcknowledged = true))
        }
    }

    fun dismissAcknowledgment() { _pendingAcknowledgment.value = null }

    fun benchmark(model: ModelInfo) {
        if (_importing.value) return
        if (!canLoadSafely(model)) {
            _status.value = unsupportedRuntimeMessage(model)
            return
        }
        viewModelScope.launch {
            _importing.value = true
            _busyModelId.value = model.id
            _busyLabel.value = "Benchmarking ${model.displayName}…"
            _status.value = "Benchmarking ${model.displayName}..."
            val job = com.vervan.chat.data.db.entities.JobRecord(
                type = com.vervan.chat.data.db.entities.JobType.BENCHMARK,
                label = model.displayName,
                state = com.vervan.chat.data.db.entities.JobState.RUNNING
            )
            db.jobDao().upsert(job)
            try {
                val result = withContext(Dispatchers.Default) {
                    val started = System.nanoTime()
                    if (model.role == ModelRole.GENERATION) {
                        app.container.withLlm { engine ->
                            engine.load(
                                model.filePath,
                                backendPreference = model.preferredBackend.toEnginePreference(),
                                enableSpeculativeDecoding = model.mtpEnabled
                            )
                            var chars = 0
                            engine.generate("Explain local-first AI in two sentences.").collect { chars += it.length }
                            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                            "${String.format("%.1f", chars / seconds)} characters/sec on ${engine.activeBackend}" +
                                (if (engine.speculativeDecodingActive) " (MTP active)" else "")
                        }
                    } else {
                        app.container.withEmbedding { engine ->
                            engine.load(model.filePath, model.tokenizerPath)
                            val dimension = engine.embed("benchmark")?.size ?: 0
                            val millis = (System.nanoTime() - started) / 1_000_000
                            "$dimension dimensions in ${millis}ms on ${engine.activeBackend}"
                        }
                    }
                }
                Log.i(TAG, "benchmark() SUCCESS for ${model.displayName}: $result")
                _status.value = "Benchmark: $result"
                db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.COMPLETED, updatedAt = System.currentTimeMillis(), detail = result))
            } catch (t: Throwable) {
                Log.e(TAG, "benchmark() FAILED for ${model.displayName}: ${t::class.simpleName}: ${t.message}", t)
                db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = t.message ?: ""))
                _status.value = "Benchmark failed: ${t.message ?: t::class.simpleName}"
            } finally {
                _importing.value = false
                _busyModelId.value = null
                _busyLabel.value = null
                refreshLoadedState()
            }
        }
    }

    fun load(model: ModelInfo) {
        if (_importing.value) return
        Log.i(TAG, "load() requested: ${model.displayName} (preferredBackend=${model.preferredBackend})")
        // Already resident — LlmEngine.load() would no-op internally anyway, but skip the
        // busy state/toast churn of "loading" a model that's already loaded.
        val alreadyLoadedPath = if (model.role == ModelRole.GENERATION) _loadedGenerationPath.value else _loadedEmbeddingPath.value
        if (alreadyLoadedPath == model.filePath) {
            _status.value = "${model.displayName} is already loaded"
            return
        }
        if (!canLoadSafely(model)) {
            Log.w(TAG, "load() rejected pre-flight: ${unsupportedRuntimeMessage(model)}")
            _status.value = unsupportedRuntimeMessage(model)
            return
        }
        viewModelScope.launch {
            _importing.value = true
            _busyModelId.value = model.id
            _busyLabel.value = "Loading ${model.displayName}…"
            _status.value = "Loading ${model.displayName}…"
            // Only one generation (or embedding) model is ever resident — remember what was
            // loaded before this call so the toast below can call out the auto-unload instead
            // of looking like nothing happened to the previous model.
            val previousName = if (model.role == ModelRole.GENERATION) {
                _loadedGenerationPath.value?.takeIf { it != model.filePath }
                    ?.let { path -> models.value.find { it.filePath == path }?.displayName }
            } else {
                _loadedEmbeddingPath.value?.takeIf { it != model.filePath }
                    ?.let { path -> models.value.find { it.filePath == path }?.displayName }
            }
            try {
                var disabledCapabilities: List<String> = emptyList()
                withContext(Dispatchers.Default) {
                    if (model.role == ModelRole.GENERATION) {
                        // Loading a new generation model always frees whatever was loaded before —
                        // the engine is a single shared instance, so only one generation model is
                        // ever resident at a time (LlmEngine.load() closes the previous one first).
                        val (updated, disabled) = app.container.withLlm { engine ->
                            val result = engine.load(
                                model.filePath, model.contextTokens ?: 4096, model.maxNumImages ?: 1,
                                model.preferredBackend.toEnginePreference(),
                                enableSpeculativeDecoding = model.mtpEnabled
                            )
                            val dbBackend = when (result.backend) {
                                LlmEngine.ModelBackend.GPU -> ModelBackend.GPU
                                LlmEngine.ModelBackend.NPU -> ModelBackend.NPU
                                else -> ModelBackend.CPU
                            }
                            model.reconcileCapabilities(dbBackend, engine.visionEnabled, engine.audioEnabled, mtpAttempted = model.mtpEnabled, mtpActive = engine.speculativeDecodingActive)
                        }
                        // Was never persisted here — a manual Load from this screen left the model
                        // shown as "Unverified"/"Not tested yet" forever, even after it demonstrably
                        // worked, because only the chat-triggered load path (ChatViewModel) wrote
                        // lastWorkingBackend back to the DB.
                        db.modelDao().upsert(updated)
                        disabledCapabilities = disabled
                        Log.i(TAG, "load() SUCCESS: ${model.displayName} loaded on ${updated.lastWorkingBackend}, disabled=$disabled")
                    } else {
                        val dbBackend = app.container.withEmbedding { engine ->
                            engine.load(model.filePath, model.tokenizerPath)
                            if (engine.activeBackend == com.vervan.chat.retrieval.EmbeddingBackend.GPU) ModelBackend.GPU else ModelBackend.CPU
                        }
                        db.modelDao().upsert(model.copy(lastWorkingBackend = dbBackend))
                        Log.i(TAG, "load() SUCCESS: embedding model ${model.displayName} loaded on $dbBackend")
                    }
                }
                _status.value = "Loaded ${model.displayName}"
                android.widget.Toast.makeText(
                    app,
                    if (previousName != null) "Unloaded $previousName — loaded ${model.displayName}" else "Loaded ${model.displayName}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (disabledCapabilities.isNotEmpty()) {
                    android.widget.Toast.makeText(
                        app, "${disabledCapabilities.joinToString(", ")} not supported on this model — disabled",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "load() FAILED for ${model.displayName} (preferredBackend=${model.preferredBackend}): ${t::class.simpleName}: ${t.message}", t)
                val backendNote = if (model.preferredBackend != BackendChoice.AUTO) " on ${model.preferredBackend}" else ""
                _status.value = "Load failed$backendNote: ${t.message ?: t::class.simpleName}"
            } finally {
                _importing.value = false
                _busyModelId.value = null
                _busyLabel.value = null
                refreshLoadedState()
            }
        }
    }

    fun unload(model: ModelInfo) {
        Log.i(TAG, "unload() requested: ${model.displayName}")
        viewModelScope.launch {
            if (model.role == ModelRole.GENERATION) {
                app.container.withLlm { if (it.loadedModelPath == model.filePath) it.close() }
            } else {
                app.container.withEmbedding { if (it.loadedModelPath == model.filePath) it.close() }
            }
            _status.value = "Unloaded ${model.displayName}"
            android.widget.Toast.makeText(app, "Unloaded ${model.displayName}", android.widget.Toast.LENGTH_SHORT).show()
            refreshLoadedState()
        }
    }

    fun setMtpEnabled(model: ModelInfo, enabled: Boolean) {
        Log.i(TAG, "setMtpEnabled(): ${model.displayName} -> $enabled")
        viewModelScope.launch { db.modelDao().upsert(model.copy(mtpEnabled = enabled)) }
    }

    fun update(model: ModelInfo) {
        viewModelScope.launch { db.modelDao().upsert(model) }
    }

    /** Relinks every folder default pointing at [previous] to [newModel] and makes [newModel]
     * active — [previous] itself is left installed and untouched (spec §11.12: "keep both").
     * Historical chats already reference [previous] by id directly and are never rewritten. */
    fun relinkToNewVersion(newModel: ModelInfo, previous: ModelInfo) {
        _pendingMigration.value = null
        viewModelScope.launch {
            db.folderDao().relinkDefaultModel(previous.id, newModel.id)
            setActive(newModel)
        }
    }

    /** Keeps the existing active model as-is; the new import just sits in the list, available
     * to activate manually later. */
    fun dismissMigration() { _pendingMigration.value = null }

    fun delete(model: ModelInfo) {
        viewModelScope.launch {
            _busyModelId.value = model.id
            _busyLabel.value = "Deleting ${model.displayName}…"
            _status.value = _busyLabel.value
            try {
                // B14: clear any folder default pointing at this model before deleting it, so a
                // folder can't end up silently referencing a dangling model id.
                db.folderDao().clearDefaultModel(model.id)
                db.chatDao().clearModel(model.id)
                app.container.withLlm { engine -> if (engine.loadedModelPath == model.filePath) engine.close() }
                app.container.withEmbedding { engine -> if (engine.loadedModelPath == model.filePath) engine.close() }
                withContext(Dispatchers.IO) {
                    com.vervan.chat.data.SecureDelete.overwriteAndDelete(java.io.File(model.filePath))
                    model.tokenizerPath?.let { com.vervan.chat.data.SecureDelete.overwriteAndDelete(java.io.File(it)) }
                }
                db.modelDao().delete(model)
                _status.value = "Deleted ${model.displayName}"
                refreshLoadedState()
            } catch (t: Throwable) {
                Log.e(TAG, "delete() failed for ${model.displayName}", t)
                _status.value = "Delete failed: ${t.message ?: t::class.simpleName}"
            } finally {
                _busyModelId.value = null
                _busyLabel.value = null
            }
        }
    }

    private fun canLoadSafely(model: ModelInfo): Boolean =
        model.role != ModelRole.GENERATION || LlmEngine.mediaPipeCompatibilityIssue(model.filePath) == null

    private fun unsupportedRuntimeMessage(model: ModelInfo): String =
        "${model.displayName} was not loaded: ${LlmEngine.mediaPipeCompatibilityIssue(model.filePath) ?: "unsupported model package"}"
}
