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
import com.vervan.chat.data.settings.SettingsRepository
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.model.DocumentImportManager
import com.vervan.chat.model.ModelImportManager
import com.vervan.chat.model.WorkspaceManager
import com.vervan.chat.retrieval.EmbeddingEngine
import com.vervan.chat.retrieval.RetrievalEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
    val embeddingEngine = EmbeddingEngine(app)
    // ponytail: one global native-engine lock; split per model instance if parallel native
    // sessions become a measured need.
    val llmMutex = Mutex()
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
        db.modelDao(), llmEngine, embeddingEngine, llmMutex, embeddingMutex,
        object : com.vervan.chat.modelload.GenerationDefaults {
            override suspend fun contextTokenLimit() = settingsRepository.contextTokenLimit.first()
            override suspend fun maxNumImages() = settingsRepository.maxNumImages.first()
            override suspend fun preferredBackend() = settingsRepository.preferredBackend.first()
        },
        modelLoadScope
    )
    val apiServerAuth = com.vervan.chat.server.ApiServerAuth(app)
    val workspaceManager = WorkspaceManager(db, documentImportManager, settingsRepository)
    val appLockManager = AppLockManager(app)
    // Reason-keyed set instead of a single boolean so app-wide lock and a per-chat
    // "screenshotBlocked" toggle (independent sources, see ChatScreen) don't fight over the
    // same window flag — MainActivity just sets FLAG_SECURE whenever this set is non-empty.
    val secureWindowReasons = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    // Native load/generate calls are blocking. Keeping the shared lock and engine work on a
    // worker dispatcher lets Compose render each caller's loading state immediately.
    suspend fun <T> withLlm(block: suspend (LlmEngine) -> T): T = withContext(Dispatchers.Default) {
        llmMutex.withLock { block(llmEngine) }
    }
    suspend fun <T> withEmbedding(block: suspend (EmbeddingEngine) -> T): T = withContext(Dispatchers.Default) {
        embeddingMutex.withLock { block(embeddingEngine) }
    }
}

class VervanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
        // App-wide backgrounded/foregrounded detection for auto-lock (Phase A) — observes the
        // whole process's lifecycle, not one Activity's, so in-app navigation (which pauses/
        // resumes the single Activity) never trips it, only actually leaving the app does.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appLockManager.onAppBackgrounded()
                // The quick-action bubble only makes sense while the user is doing something
                // else — showing it over the app's own UI is pointless, and (re-)starting it on
                // every single background transition, rather than only once when the setting was
                // first turned on, is what makes it self-healing: if the OS silently killed the
                // service earlier (a background-restarted START_STICKY service can't always
                // re-call startForeground()), the very next time the app backgrounds it comes
                // back on its own instead of needing an off/on toggle in Settings.
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        if (container.settingsRepository.quickActionBubbleEnabled.first()) {
                            com.vervan.chat.overlay.BubbleService.start(this@VervanApp)
                        }
                    }.onFailure { Log.e(TAG, "onAppBackgrounded housekeeping failed", it) }
                }
            }
            override fun onStart(owner: LifecycleOwner) {
                // The transparent capture-consent/region activity belongs to the bubble flow;
                // stopping the service here would remove its required mediaProjection FGS just
                // before getMediaProjection(). Normal app foregrounding still hides the bubble.
                if (!com.vervan.chat.overlay.BubbleService.shouldRemainRunningInForeground()) {
                    com.vervan.chat.overlay.BubbleService.stop(this@VervanApp)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
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
        }.onFailure { Log.e(TAG, "Cold-start housekeeping failed", it) } }
    }

    /**
     * Frees the loaded native model under real memory pressure (spec §40) rather than
     * letting the OS kill the process outright. Safe to do at any time — [LlmEngine] is
     * lazily reloaded by whichever ChatViewModel/workspace next calls `generate()`, same
     * path as a cold start; the only cost is that next call re-pays the model load time.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        // Native models are rebuildable caches, so release both before the OS has to kill
        // the process. A later generation or retrieval request loads them again normally.
        val unloadedPath = container.llmEngine.loadedModelPath
        val unloadedRoles = container.modelLoadCoordinator.unloadUnderMemoryPressure()
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
