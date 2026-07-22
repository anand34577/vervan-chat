package com.vervan.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.vervan.chat.security.AppLockManager
import com.vervan.chat.system.ThermalMonitor
import com.vervan.chat.data.db.AppDatabase
import com.vervan.chat.data.db.MIGRATIONS
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.data.repo.BuiltInPersonas
import com.vervan.chat.data.repo.BuiltInPromptTemplates
import com.vervan.chat.data.repo.BuiltInWorkflows
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.settings.SettingsRepository
import com.vervan.chat.llm.LlamaCppEngine
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.llm.stoppingAt
import com.vervan.chat.model.DocumentImportManager
import com.vervan.chat.model.ModelImportManager
import com.vervan.chat.model.WorkspaceManager
import com.vervan.chat.retrieval.EmbeddingEngine
import com.vervan.chat.retrieval.RetrievalEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Simple hand-rolled DI container — no framework needed for this many dependencies. */
class AppContainer(app: Application) {
    // Real Migration objects are wired below via MIGRATIONS — Room only falls back to a
    // destructive rebuild if a version bump has no matching Migration.
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "vervan.db")
        .addMigrations(*MIGRATIONS)
        .build()
    val llmEngine = LlmEngine(app)
    val llamaCppEngine = LlamaCppEngine(app)
    val embeddingEngine = EmbeddingEngine(app)
    // one global native-engine lock; split per model instance if parallel native
    // sessions become a measured need.
    val llmMutex = Mutex()
    val llamaCppMutex = Mutex()
    val embeddingMutex = Mutex()
    val modelImportManager = ModelImportManager(app, db.modelDao())
    val documentImportManager = DocumentImportManager(app, db.documentDao(), db.chunkDao(), embeddingEngine, db.jobDao())
    val retrievalEngine = RetrievalEngine(db.chunkDao(), db.documentDao(), embeddingEngine)
    val settingsRepository = SettingsRepository(app)
    val thermalMonitor = ThermalMonitor(app)
    val networkAuditLog = com.vervan.chat.system.NetworkAuditLog()
    val ttsModelDownloadManager = com.vervan.chat.voice.TtsModelDownloadManager(app, db.ttsVoiceModelDao(), db.jobDao(), networkAuditLog)
    val hfTokenStore = com.vervan.chat.modeldownload.HuggingFaceTokenStore(app)
    // Application-scoped: a download must keep running across screen navigation, and its own
    // recovery-on-restart logic (see ModelDownloadRepository.recoverOnStartup) is what handles
    // actual process death — this scope only needs to outlive individual Compose screens.
    private val modelDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val modelDownloadRepository = com.vervan.chat.modeldownload.ModelDownloadRepository(
        app, db.downloadPackageDao(), db.downloadFileDao(), db.modelDao(), modelImportManager,
        db.ttsVoiceModelDao(), settingsRepository, networkAuditLog, hfTokenStore, modelDownloadScope
    )
    // Application-scoped for the same reason as modelDownloadScope above — an ensureLoaded()
    // call started from one screen (e.g. RealtimeVoiceController) must keep running/be
    // join-able even if that screen goes away before the load finishes.
    private val modelLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val modelLoadCoordinator = com.vervan.chat.modelload.ModelLoadCoordinator(
        db.modelDao(), llmEngine, llamaCppEngine, embeddingEngine, llmMutex, llamaCppMutex, embeddingMutex,
        object : com.vervan.chat.modelload.GenerationDefaults {
            override suspend fun contextTokenLimit() = settingsRepository.contextTokenLimit.first()
            override suspend fun maxNumImages() = settingsRepository.maxNumImages.first()
            override suspend fun preferredBackend() = settingsRepository.preferredBackend.first()
            override suspend fun allowLowMemoryModelLoads() = settingsRepository.allowLowMemoryModelLoads.first()
            override suspend fun cpuThreads() = settingsRepository.cpuThreads.first()
            override suspend fun nBatch() = settingsRepository.nBatch.first()
            override suspend fun nUbatch() = settingsRepository.nUbatch.first()
            override suspend fun useMlock() = settingsRepository.useMlock.first()
            override suspend fun flashAttentionMode() = settingsRepository.flashAttentionMode.first()
            override suspend fun kvCacheType() = settingsRepository.kvCacheType.first()
            override suspend fun vulkanDeviceIndex() = settingsRepository.vulkanDeviceIndex.first()
        },
        modelLoadScope,
        // Model Loading Strategy §13 — real ActivityManager-backed estimate, queried fresh on
        // every check (not cached) since available memory shifts constantly with whatever else
        // is running on the device.
        object : com.vervan.chat.modelload.ResourceMonitor {
            private val activityManager = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            override fun availableMemoryBytes(): Long {
                val info = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(info)
                // .memClass (per-app heap cap) doesn't apply to native/mmap allocations the way
                // it does to the Java heap, so system-wide availMem is the relevant figure here —
                // but subtract the OS's own low-memory threshold as a safety margin, since
                // availMem crossing zero is already "the OS is about to start killing things",
                // not "there's zero free room".
                return (info.availMem - info.threshold).coerceAtLeast(0L)
            }
        }
    )
    val memoryRepository = com.vervan.chat.data.repo.MemoryRepository(
        db.memoryDao(), db.modelDao(), modelLoadCoordinator, embeddingEngine, embeddingMutex
    )
    // --- Model Store (com.vervan.chat.store) ---------------------------------------------------
    // Curated, signed, remote catalogue. Deliberately parallel to modelDownloadRepository above
    // rather than replacing it: that one drives the in-APK ModelCatalog and the existing
    // "Available for Download" list, this one drives the Store screen. They share nothing but
    // HttpRangeDownloader and NetworkAuditLog, so the store can ship (or be switched off) without
    // destabilising the pipeline that already works.
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val storeBlobStore = com.vervan.chat.store.storage.BlobStore(java.io.File(app.filesDir, "store/models"))
    private val storeManifestStore = com.vervan.chat.store.storage.InstalledManifestStore(storeBlobStore)
    private val storeMaintenance = com.vervan.chat.store.storage.StoreMaintenance(storeBlobStore, storeManifestStore)
    private val storeLicenseStore = com.vervan.chat.store.license.LicenseAcceptanceStore(app)

    // Only runtimes this build can actually load. llama.cpp and whisper.cpp are gated on their
    // native libraries being present (BuildConfig flags set by the Gradle script); LiteRT-LM is
    // always linked. sherpa-onnx is omitted until its AAR is vendored — a variant naming it would
    // otherwise render as installable and then fail at load.
    private val storeRuntimeAdapters = com.vervan.chat.store.runtime.RuntimeAdapterRegistry(
        buildList {
            add(com.vervan.chat.store.runtime.LiteRtLmAdapter())
            if (com.vervan.chat.BuildConfig.LLAMA_CPP_AVAILABLE) {
                add(com.vervan.chat.store.runtime.LlamaCppAdapter())
            }
            if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                add(com.vervan.chat.store.runtime.WhisperCppAdapter())
            }
        }
    )

    private val storeCatalogParser = com.vervan.chat.store.catalog.CatalogParser(
        appVersionCode = com.vervan.chat.BuildConfig.VERSION_CODE,
        availableRuntimes = storeRuntimeAdapters.availableRuntimes
    )

    private val storeCatalogRepository = com.vervan.chat.store.catalog.CatalogRepository(
        context = app,
        parser = storeCatalogParser,
        verifier = com.vervan.chat.store.catalog.CatalogSignatureVerifier.fromEmbeddedKeys(),
        networkAuditLog = networkAuditLog
    )

    private val storeInstaller = com.vervan.chat.store.install.VariantInstaller(
        blobStore = storeBlobStore,
        manifestStore = storeManifestStore,
        fetcher = com.vervan.chat.store.install.HttpArtifactFetcher(
            downloader = com.vervan.chat.modeldownload.HttpRangeDownloader(),
            networkAuditLog = networkAuditLog,
            userHuggingFaceToken = { hfTokenStore.get() }
        ),
        adapters = storeRuntimeAdapters,
        recorder = com.vervan.chat.store.install.RoomInstallSessionRecorder(
            db.storeInstallSessionDao(), db.storeInstallArtifactDao()
        ),
        usableSpaceProvider = { runCatching { app.filesDir.usableSpace }.getOrDefault(-1L) }
    )

    val storeInstallRecovery = com.vervan.chat.store.install.StoreInstallRecovery(
        db.storeInstallSessionDao(), db.storeInstallArtifactDao(), storeBlobStore
    )

    val modelStoreRepository = com.vervan.chat.store.ModelStoreRepository(
        catalogRepository = storeCatalogRepository,
        installer = storeInstaller,
        manifestStore = storeManifestStore,
        maintenance = storeMaintenance,
        licenseStore = storeLicenseStore,
        // Probed per call rather than cached at construction: available RAM shifts constantly, and
        // an eligibility verdict computed at process start would be stale by the time the user
        // reaches the store.
        eligibilityProvider = {
            com.vervan.chat.store.eligibility.VariantEligibilityChecker(
                com.vervan.chat.store.eligibility.DeviceProfile.probe(
                    context = app,
                    appVersionCode = com.vervan.chat.BuildConfig.VERSION_CODE,
                    // Vulkan-capable llama.cpp builds are the GPU path on this app; NPU delegation
                    // is not exposed by any runtime here yet, so claiming it would wrongly mark
                    // NPU-requiring variants installable.
                    hasGpuDelegate = com.vervan.chat.BuildConfig.LLAMA_CPP_AVAILABLE,
                    hasNpuDelegate = false
                )
            )
        },
        scope = storeScope,
        onInstallStarting = {
            com.vervan.chat.store.StoreDownloadService.start(app)
        }
    )

    val storeHousekeeping = com.vervan.chat.store.StoreHousekeeping(
        context = app,
        catalogRepository = storeCatalogRepository,
        maintenance = storeMaintenance,
        manifestStore = storeManifestStore
    )

    val apiServerAuth = com.vervan.chat.server.ApiServerAuth(app)
    val workspaceManager = WorkspaceManager(db, documentImportManager, settingsRepository)
    val appLockManager = AppLockManager(app)
    // The model-initiated outbound-network tool (web_search). API key lives in the same
    // Keystore-backed prefs pattern as hfTokenStore/apiServerAuth/appLockManager; the client
    // is constructed eagerly because the tool's execute lambda reads it per call.
    val knowledgeGraphStore = com.vervan.chat.search.KnowledgeGraphStore(app)
    val knowledgeGraphClient = com.vervan.chat.search.KnowledgeGraphClient(
        apiKeyProvider = { knowledgeGraphStore.get() },
        // Same audit log the model downloader and store pipeline report to — a model-initiated
        // outbound call is exactly what this dashboard exists to surface.
        onNetworkCall = { reason -> networkAuditLog.record(reason) }
    )
    // Reason-keyed set instead of a single boolean so app-wide lock and a per-chat
    // "screenshotBlocked" toggle (independent sources, see ChatScreen) don't fight over the
    // same window flag — MainActivity just sets FLAG_SECURE whenever this set is non-empty.
    val secureWindowReasons = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    // Native load/generate calls are blocking. Keeping the shared lock and engine work on a
    // worker dispatcher lets Compose render each caller's loading state immediately.
    suspend fun <T> withLlm(block: suspend (LlmEngine) -> T): T = withContext(Dispatchers.Default) {
        llmMutex.withLock { block(llmEngine) }
    }
    suspend fun <T> withLlamaCpp(block: suspend (LlamaCppEngine) -> T): T = withContext(Dispatchers.Default) {
        llamaCppMutex.withLock { block(llamaCppEngine) }
    }
    suspend fun <T> withEmbedding(block: suspend (EmbeddingEngine) -> T): T = withContext(Dispatchers.Default) {
        embeddingMutex.withLock { block(embeddingEngine) }
    }

    fun visionEnabled(model: ModelInfo): Boolean = when (model.engine) {
        ModelEngine.LITERT_LM -> llmEngine.visionEnabled
        ModelEngine.LLAMA_CPP -> llamaCppEngine.visionEnabled
    }

    fun audioEnabled(model: ModelInfo): Boolean = when (model.engine) {
        ModelEngine.LITERT_LM -> llmEngine.audioEnabled
        ModelEngine.LLAMA_CPP -> llamaCppEngine.audioEnabled
    }

    /** Single generation entry point for callers (Chat, Voice) that don't want to hand-roll a
     * `when (model.engine)` themselves — routes to whichever engine [model] actually needs.
     * A cold `flow{}` (not `withLlm`/`withLlamaCpp` returning the inner `Flow` directly) is
     * deliberate: the mutex must stay held for the whole duration of collection (native token
     * generation), not just for the instant the `Flow` object itself is constructed — the same
     * requirement `RealtimeVoiceController` already documents at its own `withLlm {... .collect
     * {...} }` call sites. Locks the mutex directly (not via `withLlm`/`withLlamaCpp`, which
     * wrap in `withContext(Dispatchers.Default)`) because `emitAll` inside a nested `withContext`
     * trips kotlinx.coroutines' flow context-preservation check ("Flow invariant is violated") —
     * callers already dispatch generation off Main themselves, so no extra dispatch is needed
     * here. [audioPath] is silently ignored for a llama.cpp model (no audio-input JNI in this
     * pass); callers that need to gate on that ahead of time should check
     * `model.engine`/`model.supportsAudio` themselves (a GGUF model never has
     * `supportsAudio == true`, since nothing in this pass ever sets it, so this is a safe
     * no-op rather than a silent expectation mismatch). */
    fun generate(
        model: ModelInfo,
        prompt: String,
        imagePath: String?,
        audioPath: String?,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int?,
        minP: Float = 0.05f,
        repetitionPenalty: Float = 1.1f,
        maxOutputTokens: Int = 512,
        stopSequences: List<String> = emptyList(),
        assistantPrefill: String? = null,
        systemPrompt: String? = null,
        // llama.cpp-only hard reasoning-token cap; -1 = unlimited/not applicable. Ignored by the
        // LiteRT-LM branch (its SDK has no native reasoning-budget hook).
        reasoningBudget: Int = -1
    ): Flow<String> = flow {
        when (model.engine) {
            // llama.cpp already gets an exact native output-token cap (nativeGenerate's maxTokens
            // loop bound) — only LiteRT-LM's MediaPipe session has no per-turn cap of its own, so
            // `.take` approximates one client-side (one emission per generated token/piece, same
            // as llama.cpp's own token-per-emission granularity).
            ModelEngine.LITERT_LM -> llmMutex.withLock {
                check(llmEngine.loadedModelPath == model.filePath) {
                    "${model.displayName} is not the model currently loaded in LiteRT-LM"
                }
                require(imagePath == null || llmEngine.visionEnabled) { "${model.displayName} does not support image input" }
                require(audioPath == null || llmEngine.audioEnabled) { "${model.displayName} does not support audio input" }
                emitAll(
                    llmEngine.generate(prompt, imagePath, audioPath, temperature, topP, topK, seed, systemPrompt = systemPrompt)
                        .take(maxOutputTokens)
                        .stoppingAt(stopSequences)
                )
            }
            ModelEngine.LLAMA_CPP -> llamaCppMutex.withLock {
                check(llamaCppEngine.loadedModelPath == model.filePath) {
                    "${model.displayName} is not the model currently loaded in llama.cpp"
                }
                require(imagePath == null || llamaCppEngine.visionEnabled) { "${model.displayName} has no loaded vision projector" }
                require(audioPath == null) { "llama.cpp audio input is not supported" }
                emitAll(
                    llamaCppEngine.generate(
                        prompt, imagePath, temperature, topP, topK, seed, maxOutputTokens, minP,
                        repetitionPenalty, chatTemplateOverride = model.chatTemplateOverride,
                        assistantPrefill = assistantPrefill, systemPrompt = systemPrompt,
                        reasoningBudget = reasoningBudget
                    ).stoppingAt(stopSequences)
                )
            }
        }
    }
}

class VervanApp : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var crashLogManager: com.vervan.chat.system.CrashLogManager
        private set

    override fun onCreate() {
        super.onCreate()
        // First thing, before any other init: container construction itself (Room migrations,
        // native lib loading) is a realistic crash site, and an offline app has no remote
        // reporter — an uninstalled handler here means an invisible field failure.
        crashLogManager = com.vervan.chat.system.CrashLogManager(this)
        crashLogManager.install()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
        // App-wide backgrounded/foregrounded detection for auto-lock (Phase A) — observes the
        // whole process's lifecycle, not one Activity's, so in-app navigation (which pauses/
        // resumes the single Activity) never trips it, only actually leaving the app does.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appLockManager.onAppBackgrounded()
                // Quick stays available while the app process is running, in foreground or background.
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        if (container.settingsRepository.quickActionBubbleEnabled.first() &&
                            !com.vervan.chat.overlay.BubbleService.shouldRemainRunningInForeground()
                        ) {
                            withContext(Dispatchers.Main.immediate) {
                                if (!com.vervan.chat.overlay.BubbleService.setVisible(true)) {
                                    com.vervan.chat.overlay.BubbleService.start(this@VervanApp)
                                }
                            }
                        }
                    }.onFailure { Log.e(TAG, "onAppBackgrounded housekeeping failed", it) }
                }
            }
            override fun onStart(owner: LifecycleOwner) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        if (container.settingsRepository.quickActionBubbleEnabled.first() &&
                            !com.vervan.chat.overlay.BubbleService.shouldRemainRunningInForeground()
                        ) {
                            withContext(Dispatchers.Main.immediate) {
                                if (!com.vervan.chat.overlay.BubbleService.setVisible(true)) {
                                    com.vervan.chat.overlay.BubbleService.start(this@VervanApp)
                                }
                            }
                        }
                        if (container.settingsRepository.appLockEnabled.first()) {
                            container.appLockManager.onAppForegrounded(container.settingsRepository.autoLockTimeoutSeconds.first())
                        }
                    }.onFailure { Log.e(TAG, "onAppForegrounded housekeeping failed", it) }
                }
            }
        })
        // Process death mid-generation leaves messages stuck PENDING/STREAMING — mark them
        // interrupted so the UI shows them accurately instead of a phantom spinner forever.
        // The whole block is one runCatching: a SQLiteException/disk hiccup here ran on an
        // unsupervised coroutine before this fix, which crashes the whole process even though
        // it's background housekeeping — every launch, until the underlying issue cleared.
        CoroutineScope(Dispatchers.IO).launch { runCatching {
            // Native crashes, ANRs, and low-memory kills never reach the in-process handler —
            // ApplicationExitInfo on the next launch is the only way to learn about them.
            crashLogManager.recordSystemExits()
            container.db.messageDao().getUnfinished().forEach {
                container.db.messageDao().update(it.copy(state = MessageState.INTERRUPTED))
            }
            // Incognito mode (Phase B) — a temporary chat is meant to hard-delete itself on
            // close (see ChatViewModel.purgeTemporaryChat); this is the fallback for a process
            // that died before that ran. Any chat still marked isTemporary at cold start is one
            // nothing ever cleaned up.
            container.db.chatDao().observeAllChats().first().filter { it.isTemporary }.forEach { chat ->
                chat.kbIdList().forEach { kbId ->
                    container.db.documentDao().getForKb(kbId).forEach { container.documentImportManager.delete(it) }
                    container.db.knowledgeBaseDao().get(kbId)?.let { container.db.knowledgeBaseDao().delete(it) }
                }
                container.db.messageDao().deleteForChat(chat.id)
                container.db.toolAuditDao().deleteForChat(chat.id)
                container.db.chatDao().delete(chat)
            }
            // Retention policy (Phase C) — soft-deletes (not hard-deletes) chats older than the
            // configured window, same reversible-first pattern as every other delete path in
            // this app; the existing 30-day recycle-bin sweep below eventually hard-deletes
            // them. Pinned chats are exempt — a retention timer silently eating something the
            // user deliberately pinned would be a surprise, not a privacy win. 0 (default) means
            // off entirely.
            val retentionDays = container.settingsRepository.autoDeleteAfterDays.first()
            if (retentionDays > 0) {
                val retentionCutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
                container.db.chatDao().observeAllChats().first()
                    .filter { !it.pinned && !it.isTemporary && it.deletedAt == null && it.updatedAt < retentionCutoff }
                    .forEach { container.db.chatDao().update(it.copy(deletedAt = System.currentTimeMillis())) }
            }
            container.db.personaDao().insertAll(BuiltInPersonas.defaults)
            // Built-ins are immutable in the editor, so refresh the default definition on
            // existing installs as well as seeding it on fresh installs.
            container.db.personaDao().update(BuiltInPersonas.vervan)
            // Fresh-install seed for the permanent Default Workspace (Workspace System spec
            // §2) — a no-op once it exists, whether created here or by migration 22->23.
            container.db.workspaceDao().insertDefault(
                Workspace(
                    id = Workspace.DEFAULT_WORKSPACE_ID,
                    name = "Default Workspace",
                    description = "General-purpose workspace for conversations and documents",
                    personaId = "builtin-general",
                    isDefault = true
                )
            )
            container.db.promptTemplateDao().insertAll(BuiltInPromptTemplates.defaults)
            container.db.workflowDao().insertAll(BuiltInWorkflows.defaults)

            // Recycle bin auto-purge — anything soft-deleted more than 30 days ago is gone
            // for good. Runs once per cold start, cheap enough not to need WorkManager.
            val cutoff = System.currentTimeMillis() - RECYCLE_BIN_RETENTION_MS
            container.db.chatDao().observeDeleted().first().forEach { chat ->
                if (chat.deletedAt != null && chat.deletedAt < cutoff) {
                    container.db.messageDao().deleteForChat(chat.id)
                    container.db.toolAuditDao().deleteForChat(chat.id)
                    container.db.savedOutputDao().clearSourceChat(chat.id)
                }
            }
            container.db.chatDao().purgeDeletedBefore(cutoff)
            container.db.noteDao().purgeDeletedBefore(cutoff)
            container.db.folderDao().observeDeleted().first().forEach { folder ->
                if (folder.deletedAt != null && folder.deletedAt < cutoff) {
                    container.db.chatDao().clearFolder(folder.id)
                    container.db.noteDao().clearFolder(folder.id)
                }
            }
            container.db.folderDao().purgeDeletedBefore(cutoff)
            container.db.personaDao().observeDeleted().first().forEach { persona ->
                if (persona.deletedAt != null && persona.deletedAt < cutoff) {
                    container.db.chatDao().clearPersona(persona.id)
                    container.db.folderDao().clearDefaultPersona(persona.id)
                    container.db.projectDao().clearPersona(persona.id)
                    // Workspace.personaId is NOT NULL (§4 — a workspace always has exactly one
                    // persona), so a deleted persona repoints its workspaces at the Default
                    // Persona instead of being nulled out like the other FKs here.
                    container.db.workspaceDao().relinkPersona(persona.id, "builtin-general")
                    container.db.knowledgeBaseDao().clearDefaultPersona(persona.id)
                }
            }
            container.db.personaDao().purgeDeletedBefore(cutoff)
            container.db.workflowDao().purgeDeletedBefore(cutoff)
            container.db.promptTemplateDao().purgeDeletedBefore(cutoff)
            container.db.projectDao().observeDeleted().first().forEach { project ->
                if (project.deletedAt != null && project.deletedAt < cutoff) {
                    container.db.chatDao().clearProject(project.id)
                    container.db.noteDao().clearProject(project.id)
                    container.db.knowledgeBaseDao().clearDefaultProject(project.id)
                }
            }
            container.db.projectDao().purgeDeletedBefore(cutoff)
            container.db.memoryDao().purgeDeletedBefore(cutoff)
            container.db.savedOutputDao().purgeDeletedBefore(cutoff)
            // Tool audit older than 30 days also purged.
            container.db.toolAuditDao().purgeBefore(cutoff)
            container.db.jobDao().purgeFinishedBefore(cutoff)
            // Documents go through the import manager, not a raw DELETE — it also removes
            // the copied file and embedded chunks, which a bare SQL delete would orphan.
            container.db.documentDao().observeDeleted().first().forEach { doc ->
                if (doc.deletedAt != null && doc.deletedAt < cutoff) container.documentImportManager.delete(doc)
            }
            // A download package left active/paused by a process death (spec §29) needs the
            // foreground service back up to reconcile and (if settings allow) auto-resume it —
            // ModelDownloadService.onCreate() is what actually calls recoverOnStartup().
            if (container.db.downloadPackageDao().getUnfinished().isNotEmpty()) {
                com.vervan.chat.modeldownload.ModelDownloadService.start(this@VervanApp)
            }
            // Same treatment for the Model Store's own pipeline: an install interrupted by
            // process death is reconciled against the real .part files on disk and parked as
            // PAUSED, never as FAILED. No service is started here — the store has no auto-resume
            // policy yet, so recovery only makes the session resumable when the user returns.
            container.storeInstallRecovery.recoverOnStartup()
            // Catalogue refresh, blob GC and the integrity spot-check, each on its own throttle.
            // Skipped entirely on a device that has never opened the store, so a feature the user
            // has not touched never reaches the network on their behalf.
            if (!container.storeHousekeeping.isDormant()) {
                // recoverOnStartup() above may have left install sessions in PAUSED — their
                // staging dirs (.part files) MUST be spared from the GC pass, otherwise the
                // partial download the recovery just reconciled is deleted and the next resume
                // re-downloads from zero. Pass the live set of in-flight variant ids.
                val inFlight = container.db.storeInstallSessionDao().getUnfinished().map { it.variantId }.toSet()
                container.storeHousekeeping.runIfDue(inFlight)
            }
        }.onFailure { Log.e(TAG, "Cold-start housekeeping failed", it) } }
    }

    /**
     * Frees the loaded native model under real memory pressure (spec §40 / Model Loading
     * Strategy §6.2) rather than letting the OS kill the process outright. Safe to do at any
     * time — [LlmEngine] is lazily reloaded by whichever ChatViewModel/workspace next calls
     * `generate()`, same path as a cold start; the only cost is that next call re-pays the model
     * load time. Legacy callback (pre-API 14 fallback, and some OEMs still fire it alongside
     * `onTrimMemory`) — routed through the same handler as [onTrimMemory]'s CRITICAL tier so
     * there's exactly one place that decides what "critical" does.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        handleMemoryPressure(com.vervan.chat.modelload.MemoryPressureLevel.CRITICAL)
    }

    /** §6.2's actual moderate/critical distinction — [onLowMemory] only ever signals the most
     * severe tier, so without this override the app never learns about pressure building up
     * *before* it's already critical, and the "moderate: stop speculative preloading" tier the
     * spec calls out has nothing to trigger it. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val mapped = when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> com.vervan.chat.modelload.MemoryPressureLevel.CRITICAL
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> com.vervan.chat.modelload.MemoryPressureLevel.MODERATE
            else -> com.vervan.chat.modelload.MemoryPressureLevel.NORMAL
        }
        handleMemoryPressure(mapped)
    }

    private fun handleMemoryPressure(level: com.vervan.chat.modelload.MemoryPressureLevel) {
        val unloadedPath = container.llmEngine.loadedModelPath
        val unloadedRoles = container.modelLoadCoordinator.onMemoryPressure(level)
        if (com.vervan.chat.data.db.entities.ModelRole.GENERATION in unloadedRoles && unloadedPath != null) {
            notifyModelUnloadedBySystem(unloadedPath)
        }
    }

    private fun notifyModelUnloadedBySystem(filePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val name = runCatching {
                container.db.modelDao().observeModels().first().find { it.filePath == filePath }?.displayName
            }.getOrNull() ?: java.io.File(filePath).nameWithoutExtension
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@VervanApp, "Low memory — unloaded $name", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val RECYCLE_BIN_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val TAG = "VervanApp"
    }
}
