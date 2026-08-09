package com.vervan.chat.ui.models

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.model.ImportResult
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.modelload.LoadTrigger
import com.vervan.chat.modelload.ModelLoadInfo
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide generation defaults (Settings) — a model's own temperature/topP/topK/context/seed/
 * (and the newer minP/repetitionPenalty/maxOutputTokens/llama.cpp load knobs) fields are null
 * until the user overrides them in Configure, and fall back to these. */
data class ModelDefaults(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxNumImages: Int,
    val contextTokens: Int,
    val seed: Int,
    val minP: Float,
    val repetitionPenalty: Float,
    val maxOutputTokens: Int,
    val cpuThreads: Int,
    val nBatch: Int,
    val nUbatch: Int,
    val vulkanDeviceIndex: Int
)

class ModelManagerViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val importManager = app.container.modelImportManager
    private val settings = app.container.settingsRepository
    private val downloadRepo = app.container.modelDownloadRepository
    private val coordinator = app.container.modelLoadCoordinator

    /** Catalogue entries not yet installed + anything actively downloading — "Available for
     * Download" and "Active Downloads" both render from this one flow, split by status. Ready
     * installed models are deliberately NOT included here; they stay [models] (a plain
     * [ModelInfo] list) since load/unload/delete for them is already fully implemented below. */
    val downloadStates: StateFlow<List<com.vervan.chat.modeldownload.ModelUiState>> =
        downloadRepo.uiStates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun downloadModel(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.startDownload(modelId, version) }
    }
    private var recommendedSetupCatalogId: String? = null

    fun setupRecommendedModel(modelId: String, version: String) {
        recommendedSetupCatalogId = modelId
        _status.value = "Setting up the recommended model…"
        downloadModel(modelId, version)
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
    fun deleteDownload(modelId: String, version: String) {
        viewModelScope.launch { downloadRepo.deleteDownload(modelId, version) }
    }

    companion object {
        private const val TAG = "ModelManagerVM"
    }

    // Room's Flow starts this ViewModel's `models` at the stateIn seed (emptyList()) for at
    // least one dispatch before the real query result arrives — indistinguishable from
    // "genuinely no models installed" by list contents alone. The screen's Library/Discover
    // default (My Models vs. Discover tab) needs to tell those two apart, so this flips once
    // and only once the DB has actually answered — see ModelManagerScreen's showingDiscover.
    private val _modelsLoaded = MutableStateFlow(false)
    val modelsLoaded: StateFlow<Boolean> = _modelsLoaded

    val models: StateFlow<List<ModelInfo>> = db.modelDao().observeModels()
        .onEach { _modelsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaults: StateFlow<ModelDefaults> = combine<Number, ModelDefaults>(
        settings.temperature, settings.topP, settings.topK, settings.maxNumImages, settings.contextTokenLimit, settings.randomSeed,
        settings.minP, settings.repetitionPenalty, settings.maxOutputTokens,
        settings.cpuThreads, settings.nBatch, settings.nUbatch, settings.vulkanDeviceIndex
    ) { values ->
        ModelDefaults(
            temperature = values[0].toFloat(),
            topP = values[1].toFloat(),
            topK = values[2].toInt(),
            maxNumImages = values[3].toInt(),
            contextTokens = values[4].toInt(),
            seed = values[5].toInt(),
            minP = values[6].toFloat(),
            repetitionPenalty = values[7].toFloat(),
            maxOutputTokens = values[8].toInt(),
            cpuThreads = values[9].toInt(),
            nBatch = values[10].toInt(),
            nUbatch = values[11].toInt(),
            vulkanDeviceIndex = values[12].toInt()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelDefaults(0.8f, 0.95f, 40, 1, 4096, -1, 0.05f, 1.1f, 512, 0, 2048, 512, 0))

    // Simple non-Number defaults kept as their own tiny StateFlows rather than folded into the
    // combine above (which is typed to a single Number element type) — same "stateIn a single
    // Flow" pattern already used for downloadStates below.
    val expertMode: StateFlow<Boolean> = settings.expertMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val useMlockDefault: StateFlow<Boolean> = settings.useMlock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val flashAttentionModeDefault: StateFlow<String> = settings.flashAttentionMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val kvCacheTypeDefault: StateFlow<String> = settings.kvCacheType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "f16")

    init {
        // Auto-default convenience (user ask): whenever a role has installed models but none
        // is active yet — the first import, or every model of that role having just been
        // deactivated some other way — pick one automatically instead of leaving the role
        // defaultless until the user visits Model Manager. Goes through setActive() (not
        // straight to the coordinator) so the license-acknowledgment gate still applies: an
        // unacknowledged sole model still raises the acknowledgment dialog instead of silently
        // becoming the default. Re-evaluated on every models change, so this also covers
        // deleting a role down to a lone survivor, not just import time.
        viewModelScope.launch {
            models.collect { list ->
                val setupId = recommendedSetupCatalogId
                val setupModel = setupId?.let { id -> list.firstOrNull { it.catalogModelId == id } }
                if (setupModel != null) {
                    if (setupModel.licenseAcknowledged) {
                        recommendedSetupCatalogId = null
                        validateAndActivate(setupModel)
                    } else {
                        _pendingAcknowledgment.value = setupModel
                    }
                    return@collect
                }
                ModelRole.entries.forEach { role ->
                    val ofRole = list.filter { it.role == role }
                    val candidate = ofRole.firstOrNull()
                    if (candidate != null && ofRole.none { it.isActive }) setActive(candidate)
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

    /** Set when a model needs the one-time license acknowledgment before it can
     * be activated — the screen shows a dialog and calls [acknowledgeAndActivate] or dismisses. */
    private val _pendingAcknowledgment = MutableStateFlow<ModelInfo?>(null)
    val pendingAcknowledgment: StateFlow<ModelInfo?> = _pendingAcknowledgment

    /** Set when a freshly-verified import looks like a new version of an already-installed
     * model — the screen offers to relink defaults instead of silently
     * replacing the active model. */
    private val _pendingMigration = MutableStateFlow<Pair<ModelInfo, ModelInfo>?>(null)
    val pendingMigration: StateFlow<Pair<ModelInfo, ModelInfo>?> = _pendingMigration

    /** Which model.id is actually resident in the (single, shared) generation/embedding engine
     * right now — drives the single Load/Unload toggle per card instead of showing both buttons
     * regardless of real state, and the per-role error banner (spec: load errors must also be
     * visible in Model Manager). Sourced from the coordinator so this screen and chat/voice can
     * never disagree about what's actually loaded or what just failed. */
    val generationLoadInfo: StateFlow<ModelLoadInfo> = coordinator.observeState(ModelRole.GENERATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelLoadInfo(ModelRole.GENERATION))
    val embeddingLoadInfo: StateFlow<ModelLoadInfo> = coordinator.observeState(ModelRole.EMBEDDING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelLoadInfo(ModelRole.EMBEDDING))

    private fun loadInfoFor(role: ModelRole): ModelLoadInfo =
        if (role == ModelRole.GENERATION) generationLoadInfo.value else embeddingLoadInfo.value

    fun importModel(uri: Uri, role: ModelRole) {
        Log.i(TAG, "importModel() requested: uri=$uri, role=$role")
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = "Opening model…"
            try {
                // allowGguf = false: this is the plain "LiteRT-LM" card's import path — GGUF has
                // its own dedicated "llama.cpp" card/dialog (importLlamaCppModel below).
                when (val result = importManager.import(uri, role, allowGguf = false) { progress ->
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
                        _status.value = "Import failed. ${result.reason.toUserMessage()}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importModel() threw unexpectedly", t)
                _status.value = "Import failed. ${t.toUserMessage()}"
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
                        _status.value = "Import failed. ${result.reason.toUserMessage()}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importEmbeddingPair() threw unexpectedly", t)
                _status.value = "Import failed. ${t.toUserMessage()}"
            } finally {
                _importing.value = false
                _busyLabel.value = null
            }
        }
    }

    /** [mmprojUri] is optional — omit for a text-only GGUF model. */
    fun importLlamaCppModel(modelUri: Uri, mmprojUri: Uri? = null) {
        Log.i(TAG, "importLlamaCppModel() requested: $modelUri, mmproj=$mmprojUri")
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = "Opening model…"
            try {
                when (val result = importManager.importLlamaCppModel(modelUri, mmprojUri) { progress ->
                    _busyLabel.value = progress
                    _status.value = progress
                }) {
                    is ImportResult.Success -> {
                        Log.i(TAG, "importLlamaCppModel() copied file ok: ${result.model.displayName}")
                        validateAndActivate(result.model)
                    }
                    is ImportResult.Duplicate -> {
                        Log.i(TAG, "importLlamaCppModel() duplicate of ${result.existing.displayName}")
                        _status.value = "Already imported as ${result.existing.displayName}"
                    }
                    is ImportResult.Rejected -> {
                        Log.w(TAG, "importLlamaCppModel() rejected: ${result.reason}")
                        _status.value = "Import failed. ${result.reason.toUserMessage()}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importLlamaCppModel() threw unexpectedly", t)
                _status.value = "Import failed. ${t.toUserMessage()}"
            } finally {
                _importing.value = false
                _busyLabel.value = null
            }
        }
    }

    /** One model picked out of (or typed against) a provider's catalog. [role] and [capabilities]
     *  are per-model, not shared across a batch — one endpoint commonly serves a vision chat model,
     *  a text-only one, and an embedding model side by side, and defaulting them all to the same
     *  answer is exactly the "gemma-4-12b supports vision, tools, reasoning" gap this exists to
     *  close. See [com.vervan.chat.llm.RemoteModelCatalog], which supplies the dialog's guesses for
     *  both. */
    data class RemoteModelSelection(
        val remoteApiModelId: String,
        val displayName: String,
        val role: ModelRole,
        val capabilities: RemoteCapabilities,
        val generation: RemoteGenerationOverrides = RemoteGenerationOverrides()
    )

    /**
     * Per-model generation overrides for a remote chat model — same fields, same "unset means use
     * the app-wide default" semantics [ModelInfo]'s own temperature/topP/topK/maxOutputTokens/
     * contextTokens already have for a local model, so a remote model stops being the one model
     * type with no way to tune sampling per-model. [topK] is the one field genuinely specific to
     * this path: it isn't part of the OpenAI spec, so it's only ever sent when set (see
     * [RemoteOpenAiEngine.generate]'s own doc comment on it).
     */
    data class RemoteGenerationOverrides(
        val temperature: Float? = null,
        val topP: Float? = null,
        val topK: Int? = null,
        val maxOutputTokens: Int? = null,
        val contextTokens: Int? = null
    )

    /**
     * Registers one or more [ModelEngine.REMOTE_API] models from a single endpoint — no file
     * import, since there are no local weights: just a row per model describing where/how to reach
     * the endpoint, plus the API key stashed in [com.vervan.chat.llm.RemoteApiKeyStore] rather than
     * these rows (see that class's doc comment).
     *
     * A single selection gets the same [validateAndActivate] round trip every other import path
     * uses, so a bad URL/key/model id is caught immediately rather than on first use. A batch
     * deliberately skips it: that round trip is a real billable completion per model, and the
     * endpoint was already proven reachable by the catalog fetch that produced these ids. Batch
     * rows still become the default for their role automatically when that role has none — the
     * `models.collect` auto-default in this class's `init` covers it.
     */
    fun addRemoteApiModels(
        baseUrl: String,
        apiKey: String,
        selections: List<RemoteModelSelection>
    ) {
        Log.i(TAG, "addRemoteApiModels() requested: ${selections.size} model(s) from $baseUrl")
        if (selections.isEmpty()) return
        // The dialog already blocks Save on this, but validating here too is what guarantees no
        // unusable row is ever persisted — this is the single choke point both the add and edit
        // paths route through, and a malformed URL would only fail much later, as an opaque
        // connection error on the user's first message.
        com.vervan.chat.llm.RemoteOpenAiEngine.baseUrlError(baseUrl)?.let { error ->
            _status.value = "Could not add models. $error"
            return
        }
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = if (selections.size == 1) {
                "Adding ${selections.first().displayName}…"
            } else {
                "Adding ${selections.size} models…"
            }
            try {
                val normalizedUrl = baseUrl.trim().trimEnd('/')
                val existing = db.modelDao().observeModels().first()
                val added = mutableListOf<ModelInfo>()
                var skipped = 0
                selections.forEach { selection ->
                    // Re-adding the same id on the same endpoint would create a second row that is
                    // indistinguishable from the first in every picker — skip, and say how many.
                    val duplicate = existing.any {
                        it.engine == ModelEngine.REMOTE_API &&
                            it.remoteBaseUrl == normalizedUrl &&
                            it.remoteApiModelId == selection.remoteApiModelId
                    }
                    if (duplicate) {
                        skipped++
                        return@forEach
                    }
                    val model = ModelInfo(
                        displayName = selection.displayName,
                    // Synthetic, unique per row — never a real path on disk. Every file-path-
                    // sniffing call site elsewhere in the app already treats a missing/unreadable
                    // file as "fall back to the stored metadata" (see the effort report's
                    // File(model.filePath).takeIf{it.isFile} pattern), so this never crashes,
                    // it just never resolves to a real file, which is correct for a remote model.
                        filePath = "remote:${java.util.UUID.randomUUID()}",
                        fileSizeBytes = 0L,
                        sha256 = "",
                        role = selection.role,
                        engine = ModelEngine.REMOTE_API,
                        // Bring-your-own-API-key means this app has no license text to show for a
                        // remote provider the way it does for an on-device model file — nothing to
                        // acknowledge, so this starts true rather than blocking on a dialog that
                        // has no content.
                        licenseAcknowledged = true,
                        // Always set, never left null ("never tried") — a null here would fall back
                        // to whatever's incidentally loaded on the on-device LiteRT-LM engine (see
                        // ChatViewModel.activeEngineFor's REMOTE_API fallback) instead of what the
                        // user told us this remote model can do. Meaningless for an EMBEDDING row
                        // — nothing reads these off a non-GENERATION model.
                        supportsVision = selection.capabilities.vision,
                        supportsAudio = selection.capabilities.audio,
                        supportsTools = selection.capabilities.tools,
                        supportsThinking = selection.capabilities.thinking,
                        remoteBaseUrl = normalizedUrl,
                        remoteApiModelId = selection.remoteApiModelId,
                        temperature = selection.generation.temperature,
                        topP = selection.generation.topP,
                        topK = selection.generation.topK,
                        maxOutputTokens = selection.generation.maxOutputTokens,
                        contextTokens = selection.generation.contextTokens
                    )
                    db.modelDao().upsert(model)
                    // Per-row copy: RemoteApiKeyStore is keyed by model id, so every row needs its
                    // own entry even though they all share one provider key.
                    app.container.remoteApiKeyStore.set(model.id, apiKey.trim())
                    added += model
                }
                when {
                    added.isEmpty() -> _status.value =
                        "Already added — nothing new to import from this endpoint"
                    added.size == 1 && skipped == 0 -> validateAndActivate(added.first())
                    else -> _status.value = buildString {
                        append("Added ${added.size} model${if (added.size == 1) "" else "s"}")
                        if (skipped > 0) append(" · skipped $skipped already added")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "addRemoteApiModels() threw unexpectedly", t)
                _status.value = "Could not add models. ${t.toUserMessage()}"
            } finally {
                _importing.value = false
                _busyLabel.value = null
            }
        }
    }

    /**
     * What a remote model supports, as the user declared it. Explicit rather than probed: the
     * provider's `/models` list reports nothing reliable about capabilities, and unlike a local
     * model there is nothing on this device to feature-test — [RemoteOpenAiEngine] will happily
     * send an `image_url`/`input_audio` content part or a tool-call prompt to any model; whether
     * the far end actually understands it is between the user and their provider.
     */
    data class RemoteCapabilities(
        val vision: Boolean = false,
        val audio: Boolean = false,
        val tools: Boolean = false,
        val thinking: Boolean = false
    )

    /** Provider catalog for the "fetch models" step of the remote-API dialog. */
    suspend fun fetchRemoteModels(baseUrl: String, apiKey: String): Result<List<String>> =
        com.vervan.chat.llm.RemoteOpenAiEngine.fetchModels(baseUrl, apiKey)

    /** Updates an existing REMOTE_API model in place (same row id, so chat history/config
     * referencing it survives) — [updated] is the fully-edited row from the same
     * [com.vervan.chat.ui.models.ModelEditDialog] a local model goes through (connection fields,
     * capabilities, thinking mode, tool approval, generation overrides all live on it already).
     * [apiKey], when non-blank, replaces the stored key; null/blank keeps the existing one. */
    fun updateRemoteApiModel(updated: ModelInfo, apiKey: String?) {
        // Same guarantee as addRemoteApiModels — an edit must not be able to downgrade a working
        // row to an unreachable one.
        com.vervan.chat.llm.RemoteOpenAiEngine.baseUrlError(updated.remoteBaseUrl.orEmpty())?.let { error ->
            _status.value = "Could not update ${updated.displayName}. $error"
            return
        }
        viewModelScope.launch {
            val previous = db.modelDao().get(updated.id)
            if (previous != null && previous != updated) coordinator.forceUnloadIfLoaded(previous)
            db.modelDao().upsert(updated)
            if (!apiKey.isNullOrBlank()) app.container.remoteApiKeyStore.set(updated.id, apiKey.trim())
            _status.value = "Updated ${updated.displayName}"
        }
    }

    /** Local counterpart to downloading a whisper.cpp model from the catalog — lets a ggml/GGUF
     * whisper.cpp model already on-device be picked up without any network access. Registers
     * into the same [com.vervan.chat.data.db.dao.TtsVoiceModelDao] row
     * [com.vervan.chat.voice.WhisperCppSttEngine] reads, so it shows up in Voice settings and
     * Model Manager exactly like a catalog download would. */
    fun importWhisperCppModel(uri: Uri) {
        Log.i(TAG, "importWhisperCppModel() requested: $uri")
        viewModelScope.launch {
            _importing.value = true
            _busyLabel.value = "Opening model…"
            try {
                when (val result = app.container.ttsModelDownloadManager.importWhisperCppModel(app, uri) { progress ->
                    _busyLabel.value = progress
                    _status.value = progress
                }) {
                    is com.vervan.chat.voice.TtsDownloadResult.Success -> {
                        Log.i(TAG, "importWhisperCppModel() copied file ok: ${result.model.fileSizeBytes} bytes")
                        _status.value = "Imported whisper.cpp model."
                    }
                    is com.vervan.chat.voice.TtsDownloadResult.Failed -> {
                        Log.w(TAG, "importWhisperCppModel() failed: ${result.reason}")
                        _status.value = "Import failed. ${result.reason}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "importWhisperCppModel() threw unexpectedly", t)
                _status.value = "Import failed. ${t.toUserMessage()}"
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
                val loadResult = coordinator.loadManually(model)
                require(loadResult.success) { loadResult.errorMessage ?: "Model could not be loaded" }
                if (model.role == ModelRole.GENERATION) {
                    val persisted = db.modelDao().get(model.id) ?: model
                    val params = com.vervan.chat.llm.resolveGenerationParams(persisted, app.container.settingsRepository)
                    val output = StringBuilder()
                    app.container.generate(
                        persisted, "Reply with OK.", null, null,
                        params.temperature, params.topP, params.topK, params.seed,
                        params.minP, params.repetitionPenalty, params.maxOutputTokens.coerceAtMost(16), params.stopSequences
                    ).collect {
                        if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                            throw kotlinx.coroutines.CancellationException("Stopped by user")
                        }
                        output.append(it)
                    }
                    require(output.isNotBlank()) { "Model initialized but produced no output" }
                    if (persisted.engine == ModelEngine.LLAMA_CPP) {
                        val metadata = app.container.withLlamaCpp { engine ->
                            check(engine.loadedModelPath == persisted.filePath) { "Model changed during validation" }
                            engine.readModelInfo()
                        }
                        if (metadata != null) persisted.copy(
                            modelDesc = metadata.desc,
                            nativeMaxContext = metadata.nativeMaxContext,
                            layerCount = metadata.layerCount
                        ) else persisted
                    } else if (!persisted.traits.storesWeightsLocally) {
                        // No weights file to probe for MTP support — the "Reply with OK." round
                        // trip above already proved the endpoint/key/model id actually work.
                        persisted
                    } else {
                        val mtpSupported = app.container.llmEngine.detectSpeculativeDecodingSupport(persisted.filePath)
                        persisted.copy(mtpSupported = mtpSupported)
                    }
                } else if (!model.traits.runsOnDevice) {
                    // No native session to check "still loaded" against (see the GENERATION
                    // branch's own off-device case above) — validate by actually requesting a
                    // vector from the endpoint, the same round-trip proof "Reply with OK." gives
                    // a remote chat model.
                    val persisted = db.modelDao().get(model.id) ?: model
                    val vector = com.vervan.chat.retrieval.embedWith(
                        persisted, "model validation",
                        embeddingEngine = app.container.embeddingEngine,
                        embeddingMutex = app.container.embeddingMutex,
                        remoteOpenAiEngine = app.container.remoteOpenAiEngine,
                        remoteApiKeyStore = app.container.remoteApiKeyStore,
                        networkAuditLog = app.container.networkAuditLog
                    )
                    require(vector != null && vector.isNotEmpty()) { "Embedding model returned no vector" }
                    persisted
                } else {
                    app.container.withEmbedding { engine ->
                        check(engine.loadedModelPath == model.filePath) { "Model changed during validation" }
                        require(engine.embed("model validation")?.isNotEmpty() == true) {
                            "Embedding model returned no vector"
                        }
                        db.modelDao().get(model.id) ?: model
                    }
                }
            }
            if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                _status.value = "Model validation stopped"
                return
            }
            db.modelDao().upsert(verified)
            Log.i(TAG, "validateAndActivate() SUCCESS: ${verified.displayName} verified on ${verified.lastWorkingBackend}")
            _status.value = "Verified ${verified.displayName}"
            db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
            val previousVersion = db.modelDao().getOthersOfRole(verified.role, verified.id)
                .firstOrNull { com.vervan.chat.model.ModelFamily.sameFamily(it.displayName, verified.displayName) }
            if (previousVersion != null) {
                Log.i(TAG, "validateAndActivate() looks like a new version of ${previousVersion.displayName}; asking user")
                _pendingMigration.value = verified to previousVersion
            } else if (db.modelDao().getActiveModel(verified.role) == null) {
                // Model Loading Strategy: only the *first* valid model of a type becomes the
                // default automatically. Importing a second/third model of the same role must not
                // silently steal default status from whatever the user already has active — it
                // just joins the list, available to load/activate manually.
                setActive(verified)
            }
        } catch (t: Throwable) {
            if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                _status.value = "Model validation stopped"
                return
            }
            Log.e(TAG, "validateAndActivate() FAILED for ${model.displayName}: ${t::class.simpleName}: ${t.message}", t)
            db.modelDao().upsert(model.copy(lastWorkingBackend = ModelBackend.UNVERIFIED))
            db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = t.message ?: ""))
            _status.value = "Model could not be verified. ${t.toUserMessage()} " +
                    "The file was kept. Retry activation or delete it."
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
        viewModelScope.launch { activateModel(model) }
    }

    fun acknowledgeAndActivate(model: ModelInfo) {
        _pendingAcknowledgment.value = null
        val acknowledged = model.copy(licenseAcknowledged = true)
        if (model.catalogModelId == recommendedSetupCatalogId) {
            recommendedSetupCatalogId = null
            viewModelScope.launch {
                db.modelDao().upsert(acknowledged)
                validateAndActivate(acknowledged)
            }
        } else {
            viewModelScope.launch {
                db.modelDao().upsert(acknowledged)
                activateModel(acknowledged)
            }
        }
    }

    /** Activates [model] and, for an EMBEDDING model swap with existing indexed documents,
     * nudges toward re-indexing. Without this, RetrievalEngine silently excludes every chunk
     * embedded by the previous model from semantic scoring (see Chunk.embeddingModelId) and
     * search just quietly degrades to keyword-only with no visible reason. */
    private suspend fun activateModel(model: ModelInfo) {
        val previousActiveId = db.modelDao().getActiveModel(model.role)?.id
        coordinator.setDefault(model)
        if (model.role == ModelRole.EMBEDDING && previousActiveId != model.id && db.documentDao().countReady() > 0) {
            _status.value = "Embedding model changed. Rebuild search in Settings → Storage & backup → Search index."
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
                    val loadResult = coordinator.loadManually(model)
                    require(loadResult.success) { loadResult.errorMessage ?: "Model could not be loaded" }
                    if (model.role == ModelRole.GENERATION) {
                        val persisted = db.modelDao().get(model.id) ?: model
                        val params = com.vervan.chat.llm.resolveGenerationParams(persisted, app.container.settingsRepository)
                        var chars = 0
                        app.container.generate(
                            persisted, "Explain local-first AI in two sentences.", null, null,
                            params.temperature, params.topP, params.topK, params.seed,
                            params.minP, params.repetitionPenalty, params.maxOutputTokens.coerceAtMost(128), params.stopSequences
                        ).collect {
                            if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                                throw kotlinx.coroutines.CancellationException("Stopped by user")
                            }
                            chars += it.length
                        }
                        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                        // lastWorkingBackend never advances past UNVERIFIED for a model that never
                        // runs doLoadGeneration (see ModelLoadCoordinator's runsOnDevice
                        // short-circuit) — same fix as the chat stats row's own backend label.
                        val backendLabel = if (persisted.traits.runsOnDevice) persisted.lastWorkingBackend.toString() else "Remote"
                        "${String.format("%.1f", chars / seconds)} characters/sec on $backendLabel" +
                            (if (persisted.engine == ModelEngine.LITERT_LM && app.container.llmEngine.speculativeDecodingActive) " (MTP active)" else "")
                    } else if (!model.traits.runsOnDevice) {
                        val persisted = db.modelDao().get(model.id) ?: model
                        val vector = com.vervan.chat.retrieval.embedWith(
                            persisted, "benchmark",
                            embeddingEngine = app.container.embeddingEngine,
                            embeddingMutex = app.container.embeddingMutex,
                            remoteOpenAiEngine = app.container.remoteOpenAiEngine,
                            remoteApiKeyStore = app.container.remoteApiKeyStore,
                            networkAuditLog = app.container.networkAuditLog
                        )
                        val millis = (System.nanoTime() - started) / 1_000_000
                        "${vector?.size ?: 0} dimensions in ${millis}ms on Remote"
                    } else {
                        app.container.withEmbedding { engine ->
                            check(engine.loadedModelPath == model.filePath) { "Model changed during benchmark" }
                            val dimension = engine.embed("benchmark")?.size ?: 0
                            val millis = (System.nanoTime() - started) / 1_000_000
                            "$dimension dimensions in ${millis}ms on ${engine.activeBackend}"
                        }
                    }
                }
                if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                    _status.value = "Benchmark stopped"
                    return@launch
                }
                Log.i(TAG, "benchmark() SUCCESS for ${model.displayName}: $result")
                _status.value = "Benchmark: $result"
                db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.COMPLETED, updatedAt = System.currentTimeMillis(), detail = result))
            } catch (t: Throwable) {
                if (db.jobDao().get(job.id)?.state == com.vervan.chat.data.db.entities.JobState.CANCELLED) {
                    _status.value = "Benchmark stopped"
                    return@launch
                }
                Log.e(TAG, "benchmark() FAILED for ${model.displayName}: ${t::class.simpleName}: ${t.message}", t)
                db.jobDao().upsert(job.copy(state = com.vervan.chat.data.db.entities.JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = t.message ?: ""))
                _status.value = "Benchmark failed. ${t.toUserMessage()}"
            } finally {
                _importing.value = false
                _busyModelId.value = null
                _busyLabel.value = null
            }
        }
    }

    fun load(model: ModelInfo) {
        if (_importing.value) return
        Log.i(TAG, "load() requested: ${model.displayName} (preferredBackend=${model.preferredBackend})")
        // Already resident — skip the busy state/toast churn of "loading" a model that's
        // already loaded.
        // A remote model never actually "loads" — see EngineTraits.runsOnDevice — so every status
        // string below says "using"/"confirming" instead, matching the model card's own wording.
        val verb = if (model.traits.runsOnDevice) "loaded" else "using"
        val verbIng = if (model.traits.runsOnDevice) "Loading" else "Confirming"
        if (loadInfoFor(model.role).currentModelId == model.id) {
            _status.value = "${model.displayName} is already $verb"
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
            _busyLabel.value = "$verbIng ${model.displayName}…"
            _status.value = "$verbIng ${model.displayName}…"
            // Only one generation (or embedding) model is ever resident — remember what was
            // loaded before this call so the toast below can call out the auto-unload instead
            // of looking like nothing happened to the previous model.
            val previousId = loadInfoFor(model.role).currentModelId?.takeIf { it != model.id }
            val previousName = previousId?.let { id -> models.value.find { it.id == id }?.displayName }
            val result = coordinator.loadManually(model)
            if (result.success) {
                // a delegate fallback (e.g. GPU requested but only CPU actually worked)
                // is a materially different outcome from what was asked for, even though it's
                // technically "success" — must be disclosed, not folded silently into a plain
                // "Loaded" toast.
                val fallbackNote = if (result.delegateFallback) " (fell back to CPU — the requested backend wasn't available)" else ""
                val doneVerb = if (model.traits.runsOnDevice) "Loaded" else "Using"
                val previousDoneVerb = if (model.traits.runsOnDevice) "loaded" else "using"
                _status.value = "$doneVerb ${model.displayName}$fallbackNote"
                android.widget.Toast.makeText(
                    app,
                    (if (previousName != null) "Unloaded $previousName — $previousDoneVerb ${model.displayName}" else "$doneVerb ${model.displayName}") + fallbackNote,
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                Log.e(TAG, "load() FAILED for ${model.displayName} (preferredBackend=${model.preferredBackend}): ${result.errorMessage}")
                val backendNote = if (model.preferredBackend != BackendChoice.AUTO) " on ${model.preferredBackend}" else ""
                val failVerb = if (model.traits.runsOnDevice) "Load" else "Connection"
                _status.value = "$failVerb failed$backendNote. ${result.errorMessage.toUserMessage()}"
            }
            _importing.value = false
            _busyModelId.value = null
            _busyLabel.value = null
        }
    }

    fun unload(model: ModelInfo) {
        Log.i(TAG, "unload() requested: ${model.displayName}")
        viewModelScope.launch {
            if (loadInfoFor(model.role).currentModelId == model.id) {
                coordinator.unload(model.role)
                _status.value = "Unloaded ${model.displayName}"
                android.widget.Toast.makeText(app, "Unloaded ${model.displayName}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setMtpEnabled(model: ModelInfo, enabled: Boolean) {
        Log.i(TAG, "setMtpEnabled(): ${model.displayName} -> $enabled")
        update(model.copy(mtpEnabled = enabled))
    }

    fun update(model: ModelInfo) {
        viewModelScope.launch {
            val previous = db.modelDao().get(model.id)
            if (previous != null && previous != model) coordinator.forceUnloadIfLoaded(previous)
            db.modelDao().upsert(model)
            withContext(Dispatchers.IO) {
                previous?.mmprojPath?.takeIf { it != model.mmprojPath }
                    ?.let { com.vervan.chat.data.SecureDelete.overwriteAndDelete(java.io.File(it)) }
                previous?.loraPath?.takeIf { it != model.loraPath }
                    ?.let { com.vervan.chat.data.SecureDelete.overwriteAndDelete(java.io.File(it)) }
            }
        }
    }

    /** Relinks every folder default pointing at [previous] to [newModel] and makes [newModel]
     * active — [previous] itself is left installed and untouched (: "keep both").
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
                coordinator.forceUnloadIfLoaded(model)
                if (model.isActive) {
                    // Model Loading Strategy: default reassignment (step 4) must happen
                    // *before* file deletion (step 5) is attempted, not after — so that if file
                    // deletion fails below, the reassignment already stands (step 7) instead of
                    // silently never having run.
                    coordinator.reassignDefaultAfterDelete(model.role, model.id)
                }
                val filesDeleted = withContext(Dispatchers.IO) {
                    listOfNotNull(model.filePath, model.tokenizerPath, model.mmprojPath, model.loraPath)
                        .map { com.vervan.chat.data.SecureDelete.overwriteAndDelete(java.io.File(it)) }
                        .all { it }
                }
                if (!filesDeleted) {
                    // : the model is already unloaded and its default status already
                    // reassigned — both stand. Keep the DB row (rather than deleting it) so the
                    // broken not_loaded state is visible in Model Manager and the user can retry
                    // instead of the app silently forgetting a model whose file is still on disk.
                    Log.w(TAG, "delete() FAILED to remove all files for ${model.displayName}; keeping its row so the state is visible")
                    _status.value = "${model.displayName} was unloaded, but some files remain. Try deleting again."
                    return@launch
                }
                db.modelDao().delete(model)
                // An orphaned key left in RemoteApiKeyStore after its row is gone would be a
                // silent, unbounded leak of every API key the user ever removed — see that
                // class's own remove() doc comment. No-op (key absent) for any non-remote model.
                if (model.engine == ModelEngine.REMOTE_API) app.container.remoteApiKeyStore.remove(model.id)
                if (model.origin == com.vervan.chat.data.db.entities.ModelOrigin.DOWNLOADED) {
                    val catalogId = model.catalogModelId
                    val catalogVersion = model.catalogVersion
                    if (catalogId != null && catalogVersion != null) {
                        downloadRepo.forgetInstalledPackage(catalogId, catalogVersion)
                    }
                }
                _status.value = "Deleted ${model.displayName}"
            } catch (t: Throwable) {
                Log.e(TAG, "delete() failed for ${model.displayName}", t)
                _status.value = "Delete failed. ${t.toUserMessage()}"
            } finally {
                _busyModelId.value = null
                _busyLabel.value = null
            }
        }
    }

    private fun canLoadSafely(model: ModelInfo): Boolean =
        model.role != ModelRole.GENERATION || model.engine != ModelEngine.LITERT_LM ||
            LlmEngine.mediaPipeCompatibilityIssue(model.filePath) == null

    private fun unsupportedRuntimeMessage(model: ModelInfo): String =
        "${model.displayName} was not loaded: ${LlmEngine.mediaPipeCompatibilityIssue(model.filePath) ?: "unsupported model package"}"
}
