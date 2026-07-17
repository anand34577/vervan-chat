package com.vervan.chat.modeldownload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.vervan.chat.data.db.dao.DownloadFileDao
import com.vervan.chat.data.db.dao.DownloadPackageDao
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.db.entities.DownloadFile
import com.vervan.chat.data.db.entities.DownloadPackage
import com.vervan.chat.data.db.entities.FileDownloadStatus
import com.vervan.chat.data.db.entities.ModelErrorCode
import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelOrigin
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.data.db.entities.StopReason
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.data.settings.SettingsRepository
import com.vervan.chat.model.ImportResult
import com.vervan.chat.model.ModelImportManager
import com.vervan.chat.system.NetworkAuditLog
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the model-package download/verify/import state machine end to end — the spec's
 * "DownloadCoordinator" and "ModelInstallationRepository" collapsed into one class, since this
 * app's default concurrency (one package, one file at a time — see [tryStartNext]) makes a
 * separate coordinator layer pure indirection for what's really a single sequential worker.
 *
 * Deliberately does NOT own load/unload/delete for an already-installed model — once a package
 * reaches [ModelStatus.READY] it's registered as a completely ordinary
 * [com.vervan.chat.data.db.entities.ModelInfo] row via [ModelImportManager] (the same pipeline
 * local-file imports use), and [com.vervan.chat.ui.models.ModelManagerViewModel] already
 * implements load/unload/delete/benchmark correctly for that row — reimplementing those here
 * would just create a second, competing source of truth for the same operation. "My Models"
 * keeps rendering from [ModelDao] directly; this repository only drives "Available for
 * Download" and "Active Downloads".
 */
class ModelDownloadRepository(
    private val context: Context,
    private val packageDao: DownloadPackageDao,
    private val fileDao: DownloadFileDao,
    private val modelDao: ModelDao,
    private val modelImportManager: ModelImportManager,
    private val ttsVoiceModelDao: TtsVoiceModelDao,
    private val settingsRepository: SettingsRepository,
    private val networkAuditLog: NetworkAuditLog,
    private val tokenStore: HuggingFaceTokenStore,
    private val scope: CoroutineScope
) {
    private val storageManager = StorageManager(context)
    private val downloader = HttpRangeDownloader()
    private val validator = ModelValidator()
    private val workMutex = Mutex()
    private val recoveryMutex = Mutex()
    private var recoveryComplete = false
    private var activeJob: Job? = null
    private var activePackageId: String? = null

    // In-memory only (spec §17.2: "do not persist ETA as authoritative data") — a rolling
    // window of (timestamp, totalBytesDownloaded) samples for whichever package is currently
    // active. Reset whenever the active package changes.
    private val speedSamples = ArrayDeque<Pair<Long, Long>>()
    private val _speedInfo = MutableStateFlow<Pair<Long?, Long?>?>(null) // (bytesPerSecond, etaSeconds)

    val uiStates: Flow<List<ModelUiState>> = combine(
        packageDao.observeAll(), fileDao.observeAll(), modelDao.observeModels(),
        ttsVoiceModelDao.observeAll(), _speedInfo
    ) { packages, allFiles, installed, voices, speed ->
        val filesByPackage = allFiles.groupBy { it.packageId }
        val voicesByKey = voices.associateBy { it.engine.uppercase() to it.language }
        val installedKeys = installed.mapNotNull { m ->
            val cid = m.catalogModelId ?: return@mapNotNull null
            val cv = m.catalogVersion ?: return@mapNotNull null
            cid to cv
        }.toSet() + ModelCatalog.all.filter { catalog ->
            isVoiceLikeCategory(catalog.category) &&
                voicesByKey.containsKey(catalog.ttsEngine?.uppercase() to catalog.ttsLanguage)
        }.map { it.modelId to it.version }.toSet()
        val activePackages = packages.filter { it.status !in setOf(ModelStatus.READY, ModelStatus.CANCELLED) }
        val downloadingUi = activePackages.map { pkg ->
            toUiState(pkg, filesByPackage[pkg.id].orEmpty(), if (pkg.id == activePackageId) speed else null)
        }
        val installedVoiceUi = ModelCatalog.all.filter { isVoiceLikeCategory(it.category) }
            .mapNotNull { catalog ->
                val voice = voicesByKey[catalog.ttsEngine?.uppercase() to catalog.ttsLanguage]
                    ?: return@mapNotNull null
                val pkg = packages.find { it.modelId == catalog.modelId && it.version == catalog.version }
                val baseState = pkg?.let { toUiState(it, emptyList(), null) }
                    ?: toNotDownloadedUiState(catalog).copy(status = ModelStatus.READY)
                baseState.copy(
                    installedSizeBytes = voice.fileSizeBytes,
                    installedAt = voice.downloadedAt,
                    allowedActions = setOf(ModelAction.DELETE, ModelAction.DETAILS)
                )
            }
        val busyKeys = activePackages.map { it.modelId to it.version }.toSet()
        val catalogUi = ModelCatalog.all
            .filter { it.enabled && (it.modelId to it.version) !in busyKeys && (it.modelId to it.version) !in installedKeys }
            .map { toNotDownloadedUiState(it) }
        downloadingUi + installedVoiceUi + catalogUi
    }

    /** Called once at process start (see ModelDownloadService) — reconciles any package left in
     * an active-looking state by a process death against the real partial-file size on disk,
     * per spec §29: a killed process is never itself treated as a failure. */
    suspend fun recoverOnStartup() = recoveryMutex.withLock {
        if (recoveryComplete) return@withLock
        val unfinished = packageDao.getUnfinished()
        for (pkg in unfinished) {
            when (pkg.status) {
                ModelStatus.DOWNLOADING, ModelStatus.PREPARING, ModelStatus.VERIFYING, ModelStatus.IMPORTING,
                ModelStatus.PAUSING, ModelStatus.CANCELLING, ModelStatus.DELETING, ModelStatus.WAITING_FOR_NETWORK,
                ModelStatus.WAITING_FOR_WIFI -> {
                    fileDao.getForPackage(pkg.id).forEach { f ->
                        val actual = File(f.tempPath).takeIf { it.isFile }?.length() ?: 0L
                        if (actual != f.downloadedBytes) fileDao.upsert(f.copy(downloadedBytes = actual))
                    }
                    packageDao.upsert(pkg.copy(status = ModelStatus.PAUSED, stopReason = StopReason.NONE, updatedAt = System.currentTimeMillis()))
                }
                else -> {}
            }
        }
        tryStartNext()
        if (settingsRepository.autoResumeModelDownloads.first()) {
            packageDao.getUnfinished().filter { it.status == ModelStatus.PAUSED }.forEach {
                packageDao.upsert(it.copy(status = ModelStatus.QUEUED, updatedAt = System.currentTimeMillis()))
            }
            tryStartNext()
        }
        recoveryComplete = true
    }

    suspend fun startDownload(modelId: String, version: String): Boolean {
        val catalog = ModelCatalog.find(modelId, version) ?: return false
        if (isVoiceLikeCategory(catalog.category)) {
            val engine = catalog.ttsEngine ?: return false
            val language = catalog.ttsLanguage ?: return false
            if (ttsVoiceModelDao.getByEngine(engine, language) != null) return false
        } else if (modelDao.findByCatalogEntry(modelId, version) != null) return false
        val pkgId = DownloadIds.packageId(modelId, version)
        val existing = packageDao.get(pkgId)
        if (existing != null) {
            if (existing.status !in setOf(ModelStatus.READY, ModelStatus.CANCELLED)) return false
            cleanupPackage(pkgId)
        }

        val stagingDir = storageManager.stagingDirFor(pkgId)
        packageDao.upsert(
            DownloadPackage(
                id = pkgId, modelId = modelId, version = version, displayName = catalog.displayName,
                role = catalog.category, status = ModelStatus.QUEUED,
                totalBytes = catalog.totalExpectedBytes, authRequired = catalog.requiresAuthToken,
                licenseAccepted = !catalog.requiresLicenseAcceptance
            )
        )
        catalog.files.forEach { spec ->
            val dest = storageManager.partFileFor(stagingDir, spec.fileName)
            fileDao.upsert(
                DownloadFile(
                    id = "$pkgId:${spec.fileId}", packageId = pkgId, fileId = spec.fileId, fileName = spec.fileName,
                    role = spec.role, sourceUrl = spec.downloadUrl, tempPath = dest.absolutePath,
                    finalPath = dest.absolutePath.removeSuffix(".part"), expectedBytes = spec.expectedBytes, sha256 = spec.sha256
                )
            )
        }
        ModelDownloadService.start(context)
        tryStartNext()
        return true
    }

    /** Whether package [pkgId] is the one [tryStartNext] currently has running, and — if so —
     * its job. Reading [activePackageId]/[activeJob] must go through [workMutex], the same lock
     * [tryStartNext] writes them under: without it there's no happens-before edge between that
     * write (on [Dispatchers.Default]) and this read (on whatever dispatcher the caller — the
     * ViewModel — happens to use), so a caller here could observe a stale/null value and think
     * nothing is running when a download genuinely is. That's what silently broke pause/cancel
     * before: it took the "not active" branch, wrote PAUSED/CANCELLING straight to the row, and
     * left the real download running untouched in the background. */
    private suspend fun activeJobFor(pkgId: String): Job? = workMutex.withLock {
        if (activePackageId == pkgId) activeJob else null
    }

    suspend fun pauseDownload(modelId: String, version: String) {
        val pkgId = DownloadIds.packageId(modelId, version)
        val pkg = packageDao.get(pkgId) ?: return
        if (pkg.status !in PAUSABLE_STATUSES) return
        val job = activeJobFor(pkgId)
        if (job != null) {
            packageDao.upsert(pkg.copy(status = ModelStatus.PAUSING, stopReason = StopReason.PAUSE_REQUESTED, updatedAt = System.currentTimeMillis()))
            job.cancel()
        } else {
            packageDao.upsert(pkg.copy(status = ModelStatus.PAUSED, stopReason = StopReason.NONE, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun resumeDownload(modelId: String, version: String) {
        val pkgId = DownloadIds.packageId(modelId, version)
        val pkg = packageDao.get(pkgId) ?: return
        if (pkg.status !in setOf(ModelStatus.PAUSED, ModelStatus.FAILED)) return
        if (pkg.status == ModelStatus.FAILED) {
            fileDao.getForPackage(pkgId).filter { it.status == FileDownloadStatus.FAILED }.forEach {
                fileDao.upsert(it.copy(status = FileDownloadStatus.PAUSED, errorMessage = null))
            }
        }
        packageDao.upsert(pkg.copy(
            status = ModelStatus.QUEUED, errorCode = null, errorMessage = null,
            stopReason = StopReason.NONE, updatedAt = System.currentTimeMillis()
        ))
        ModelDownloadService.start(context)
        tryStartNext()
    }

    /** [keepPartial] true = pause (spec's "Keep downloaded data to resume later"); false =
     * delete everything and return the model to Available for Download. Also used for the
     * FAILED/QUEUED "Remove" action with keepPartial=false. */
    suspend fun cancelDownload(modelId: String, version: String, keepPartial: Boolean) {
        if (keepPartial) {
            pauseDownload(modelId, version)
            return
        }
        val pkgId = DownloadIds.packageId(modelId, version)
        val pkg = packageDao.get(pkgId) ?: return
        packageDao.upsert(pkg.copy(status = ModelStatus.CANCELLING, stopReason = StopReason.CANCEL_REQUESTED, updatedAt = System.currentTimeMillis()))
        val job = activeJobFor(pkgId)
        if (job != null) {
            job.cancel()
        } else {
            cleanupPackage(pkgId)
        }
    }

    suspend fun deleteDownload(modelId: String, version: String) {
        val pkgId = DownloadIds.packageId(modelId, version)
        val pkg = packageDao.get(pkgId)
        val catalog = ModelCatalog.find(modelId, version)
        if (catalog != null && isVoiceLikeCategory(catalog.category)) {
            val voice = if (catalog.ttsEngine != null && catalog.ttsLanguage != null) {
                ttsVoiceModelDao.getByEngine(catalog.ttsEngine, catalog.ttsLanguage)
            } else null
            voice?.let {
                File(it.filePath).deleteRecursively()
                ttsVoiceModelDao.delete(it)
            }
            if (pkg != null) cleanupPackage(pkgId)
        } else {
            if (pkg == null) return
            cancelDownload(modelId, version, keepPartial = false)
        }
    }

    private fun tryStartNext() {
        scope.launch(Dispatchers.Default) {
            workMutex.withLock {
                if (activeJob?.isActive == true) return@withLock
                val next = packageDao.getNextQueued() ?: return@withLock
                activePackageId = next.id
                speedSamples.clear()
                _speedInfo.value = null
                activeJob = scope.launch(Dispatchers.IO) {
                    try {
                        executePackage(next.id)
                    } finally {
                        // NonCancellable: this finally runs after job.cancel(), so the coroutine
                        // is already cancelled — withLock's suspension point would throw
                        // immediately without this wrapper, and these fields would never
                        // actually get cleared, leaving pause/cancel unable to find *this*
                        // package again and blocking the next queued package from starting.
                        withContext(NonCancellable) {
                            workMutex.withLock {
                                activePackageId = null
                                activeJob = null
                            }
                        }
                        _speedInfo.value = null
                        tryStartNext()
                    }
                }
            }
        }
    }

    private suspend fun executePackage(pkgId: String) {
        val pkg = packageDao.get(pkgId) ?: return
        val catalog = ModelCatalog.find(pkg.modelId, pkg.version)
        if (catalog == null) {
            failPackage(pkgId, ModelErrorCode.UNSUPPORTED_MODEL, "This model is no longer in the catalogue")
            return
        }
        try {
            setStatus(pkgId, ModelStatus.PREPARING)
            awaitNetworkAndWifi(pkgId)

            val files = fileDao.getForPackage(pkgId)
            val remaining = files.sumOf { (it.expectedBytes ?: 0L) - it.downloadedBytes }.coerceAtLeast(0)
            // TTS_VOICE/STT_MODEL finalize via File.renameTo (finalizeVoiceModel) rather than a
            // real copy — no second on-disk copy of the download, so no need to reserve double
            // the space the generic import path needs.
            storageManager.checkAvailable(remaining, requiresImportCopy = !isVoiceLikeCategory(catalog.category))

            for (file in files) {
                if (file.status == FileDownloadStatus.COMPLETED) continue
                while (true) {
                    awaitNetworkAndWifi(pkgId)
                    setStatus(pkgId, ModelStatus.DOWNLOADING)
                    try {
                        downloadOneFile(pkgId, fileDao.get(file.id) ?: file)
                        break
                    } catch (e: ModelDownloadException) {
                        if (e.code != ModelErrorCode.NO_NETWORK) throw e
                        setStatus(pkgId, ModelStatus.WAITING_FOR_NETWORK)
                        delay(NETWORK_RETRY_MS)
                    }
                }
            }
            packageDao.upsert((packageDao.get(pkgId) ?: return).copy(status = ModelStatus.DOWNLOADED, currentFileId = null, updatedAt = System.currentTimeMillis()))

            if (!packageDao.claimForVerification(pkgId)) return
            verifyAndImport(pkgId, catalog)
        } catch (c: CancellationException) {
            handleStop(pkgId)
            throw c
        } catch (e: ModelDownloadException) {
            failPackage(pkgId, e.code, e.message ?: "Download failed")
        } catch (t: Throwable) {
            Log.e(TAG, "executePackage($pkgId) failed", t)
            failPackage(pkgId, ModelErrorCode.UNKNOWN, t.message ?: t::class.simpleName ?: "Unknown error")
        }
    }

    private suspend fun downloadOneFile(pkgId: String, file: DownloadFile) {
        val currentPackage = packageDao.get(pkgId) ?: return
        packageDao.upsert(currentPackage.copy(currentFileId = file.fileId, updatedAt = System.currentTimeMillis()))
        fileDao.upsert(file.copy(status = FileDownloadStatus.DOWNLOADING))
        networkAuditLog.record("Downloading model file: ${file.fileName}")
        val token = tokenStore.get()
        val result = try {
            downloader.download(file.sourceUrl, File(file.tempPath), file.etag, file.lastModified, token) { downloaded, total ->
                fileDao.upsert(fileDao.get(file.id)?.copy(downloadedBytes = downloaded, expectedBytes = total ?: file.expectedBytes) ?: return@download)
                recordSpeedSample(recomputePackageProgress(pkgId))
            }
        } catch (e: ModelDownloadException) {
            fileDao.upsert(
                (fileDao.get(file.id) ?: file).copy(
                    status = if (e.code == ModelErrorCode.NO_NETWORK) FileDownloadStatus.WAITING_FOR_NETWORK else FileDownloadStatus.FAILED,
                    errorMessage = e.message,
                    retryCount = if (e.code == ModelErrorCode.NO_NETWORK) file.retryCount else file.retryCount + 1
                )
            )
            throw e
        }
        fileDao.upsert(
            (fileDao.get(file.id) ?: file).copy(
                downloadedBytes = result.downloadedBytes, expectedBytes = result.expectedBytes ?: file.expectedBytes,
                etag = result.etag, lastModified = result.lastModified, acceptRanges = result.acceptRanges,
                resolvedUrl = result.resolvedUrl, status = FileDownloadStatus.COMPLETED, errorMessage = null
            )
        )
        recomputePackageProgress(pkgId)
    }

    private suspend fun verifyAndImport(pkgId: String, catalog: CatalogModel) {
        val files = fileDao.getForPackage(pkgId)
        for (spec in catalog.files.filter { it.required }) {
            val f = files.find { it.fileId == spec.fileId }
                ?: throw ModelDownloadException(ModelErrorCode.TOKENIZER_MISSING.takeIf { spec.role == ModelFileRole.TOKENIZER } ?: ModelErrorCode.INVALID_MODEL_FILE, "Missing required file ${spec.fileName}")
            validator.validateFile(File(f.tempPath), spec)
        }
        val modelFile = files.first { it.role == ModelFileRole.MODEL }
        when (catalog.format) {
            ModelFormat.LITERTLM -> validator.validateLitertlm(File(modelFile.tempPath))
            ModelFormat.TFLITE -> {
                validator.validateTflite(File(modelFile.tempPath))
                files.firstOrNull { it.role == ModelFileRole.TOKENIZER }?.let { validator.validateSentencePieceTokenizer(File(it.tempPath)) }
            }
            ModelFormat.ONNX_TTS, ModelFormat.ONNX_STT -> {} // no litertlm/tflite-specific check applies; validateFile() above already checked size/checksum
        }

        setStatus(pkgId, ModelStatus.IMPORTING)
        if (isVoiceLikeCategory(catalog.category)) {
            // Neither a TTS voice nor an STT model is a loadable chat model — skip
            // ModelImportManager/ModelInfo entirely and write it where
            // PiperTtsEngine/KokoroTtsEngine/WhisperSttEngine already look (TtsVoiceModelDao),
            // same as a voice downloaded through the older TtsModelDownloadManager path.
            ttsVoiceModelDao.upsert(finalizeVoiceModel(catalog, files))
        } else {
            // Drop the ".part" suffix now that every file passed validation — ModelImportManager
            // (both single-file import() and importEmbeddingModel()'s model-vs-tokenizer
            // disambiguation) decides file identity from the extension, and every downloaded file
            // shares the same ".part" extension, so it can never tell a ".tflite.part" model from
            // a "sentencepiece.model.part" tokenizer without this rename first.
            files.forEach { f ->
                if (f.tempPath != f.finalPath) {
                    val src = File(f.tempPath)
                    val dst = File(f.finalPath)
                    if (!src.renameTo(dst)) {
                        throw ModelDownloadException(ModelErrorCode.STORAGE_WRITE_FAILED, "Could not finalize downloaded file ${f.fileName}")
                    }
                }
            }
            val importResult = if (catalog.category == ModelRole.EMBEDDING) {
                val tokenizerFile = files.firstOrNull { it.role == ModelFileRole.TOKENIZER }
                    ?: throw ModelDownloadException(ModelErrorCode.TOKENIZER_MISSING, "Embedding model requires a tokenizer file")
                modelImportManager.importEmbeddingModel(Uri.fromFile(File(modelFile.finalPath)), Uri.fromFile(File(tokenizerFile.finalPath)))
            } else {
                modelImportManager.import(Uri.fromFile(File(modelFile.finalPath)), catalog.category)
            }
            val installed = when (importResult) {
                is ImportResult.Success -> importResult.model
                is ImportResult.Duplicate -> importResult.existing
                is ImportResult.Rejected -> throw ModelDownloadException(ModelErrorCode.IMPORT_FAILED, importResult.reason)
            }
            modelDao.upsert(
                installed.copy(
                    origin = ModelOrigin.DOWNLOADED, catalogModelId = catalog.modelId,
                    catalogVersion = catalog.version, sourceUrl = catalog.sourceUrl
                )
            )
        }

        storageManager.deleteStaging(pkgId)
        fileDao.deleteForPackage(pkgId)
        packageDao.upsert((packageDao.get(pkgId) ?: return).copy(status = ModelStatus.READY, currentFileId = null, updatedAt = System.currentTimeMillis()))
    }

    /** Moves a verified TTS_VOICE or STT_MODEL package's downloaded files (still at their `.part`
     * [DownloadFile.tempPath] at this point — this catalog/format never goes through the
     * LITERTLM/TFLITE rename-to-finalPath step) into the canonical `<root>/<engine>_<language>/`
     * directory [com.vervan.chat.voice.PiperTtsEngine]/[com.vervan.chat.voice.KokoroTtsEngine]/
     * [com.vervan.chat.voice.WhisperSttEngine] already read via [TtsVoiceModelDao] — same layout
     * the older [com.vervan.chat.voice.TtsModelDownloadManager] path writes for TTS voices, so
     * both sources are interchangeable to the engines. TTS voices land under `tts_voices/`, STT
     * models under `stt_models/` — purely a filesystem-tidiness split, both write the same
     * [TtsVoiceModel] row shape. */
    private fun finalizeVoiceModel(catalog: CatalogModel, files: List<DownloadFile>): TtsVoiceModel {
        val engine = catalog.ttsEngine ?: throw ModelDownloadException(ModelErrorCode.UNKNOWN, "Catalog entry missing engine identifier: ${catalog.modelId}")
        val language = catalog.ttsLanguage ?: throw ModelDownloadException(ModelErrorCode.UNKNOWN, "Catalog entry missing language identifier: ${catalog.modelId}")
        val rootDirName = if (catalog.category == ModelRole.STT_MODEL) "stt_models" else "tts_voices"
        val voiceDir = File(context.filesDir, "$rootDirName/${engine.lowercase()}_$language").apply { mkdirs() }
        var totalBytes = 0L
        files.forEach { f ->
            val src = File(f.tempPath)
            val dst = File(voiceDir, f.fileName)
            totalBytes += src.length()
            if (!src.renameTo(dst)) {
                src.copyTo(dst, overwrite = true)
                src.delete()
            }
        }
        val modelFile = File(voiceDir, "model.onnx")
        val hash = if (modelFile.isFile) sha256Of(modelFile) else ""
        return TtsVoiceModel(engine = engine, language = language, filePath = voiceDir.absolutePath, fileSizeBytes = totalBytes, sha256 = hash)
    }

    /** [TtsVoiceModel.sha256] is informational only (support/debugging, not verified against a
     * known-good value) — see the same rationale in [com.vervan.chat.voice.TtsModelDownloadManager]. */
    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun awaitNetworkAndWifi(pkgId: String) {
        while (true) {
            if (!hasNetwork()) {
                setStatus(pkgId, ModelStatus.WAITING_FOR_NETWORK)
                delay(NETWORK_POLL_MS)
                continue
            }
            if (settingsRepository.wifiOnlyModelDownloads.first() && !isOnWifi()) {
                setStatus(pkgId, ModelStatus.WAITING_FOR_WIFI)
                delay(NETWORK_POLL_MS)
                continue
            }
            return
        }
    }

    /** Runs entirely under [NonCancellable]: this is called from the `catch (CancellationException)`
     * branch in [executePackage], i.e. the enclosing coroutine is already cancelled. Without this
     * wrapper, every suspend DB call below (Room dispatches its suspend DAO functions onto a query
     * executor — a real suspension point) would throw immediately instead of running, so the
     * "write PAUSED"/cleanup step would silently never happen — leaving the row stuck at
     * PAUSING/CANCELLING forever. */
    private suspend fun handleStop(pkgId: String) = withContext(NonCancellable) {
        val pkg = packageDao.get(pkgId) ?: return@withContext
        when (pkg.stopReason) {
            StopReason.PAUSE_REQUESTED -> {
                val files = fileDao.getForPackage(pkgId)
                files.forEach { f ->
                    val actual = File(f.tempPath).takeIf { it.isFile }?.length() ?: 0L
                    fileDao.upsert(
                        f.copy(
                            downloadedBytes = actual,
                            status = if (f.status in setOf(FileDownloadStatus.DOWNLOADING, FileDownloadStatus.WAITING_FOR_NETWORK)) {
                                FileDownloadStatus.PAUSED
                            } else f.status
                        )
                    )
                }
                packageDao.upsert(pkg.copy(status = ModelStatus.PAUSED, currentFileId = null, updatedAt = System.currentTimeMillis()))
            }
            StopReason.CANCEL_REQUESTED, StopReason.DELETE_REQUESTED -> cleanupPackage(pkgId)
            StopReason.NONE -> {
                // Coroutine was cancelled without an explicit user request — e.g. the service's
                // scope was torn down. Leave DB state as-is; recoverOnStartup() reconciles it
                // against real partial-file bytes on the next process start rather than this
                // path guessing at a final state.
            }
        }
    }

    /** Called by ModelManagerViewModel.delete() when discarding an installed [ModelInfo] whose
     * origin is [ModelOrigin.DOWNLOADED] — the READY [DownloadPackage] row that installed it is
     * excluded from every UI list once READY (see [uiStates]), so nothing else ever cleans it
     * up; without this it lingers in the DB forever as dead debris. Only ever touches a row
     * that's actually READY — an active download's package still needs [cancelDownload] or
     * [pauseDownload], not this. */
    suspend fun forgetInstalledPackage(modelId: String, version: String) {
        val pkgId = DownloadIds.packageId(modelId, version)
        val pkg = packageDao.get(pkgId) ?: return
        if (pkg.status == ModelStatus.READY) cleanupPackage(pkgId)
    }

    private suspend fun cleanupPackage(pkgId: String) {
        storageManager.deleteStaging(pkgId)
        fileDao.deleteForPackage(pkgId)
        packageDao.delete(pkgId)
    }

    private suspend fun failPackage(pkgId: String, code: ModelErrorCode, message: String) {
        val pkg = packageDao.get(pkgId) ?: return
        packageDao.upsert(pkg.copy(status = ModelStatus.FAILED, currentFileId = null, errorCode = code, errorMessage = message, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun setStatus(pkgId: String, status: ModelStatus) {
        val pkg = packageDao.get(pkgId) ?: return
        packageDao.upsert(pkg.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun recomputePackageProgress(pkgId: String): Long {
        val files = fileDao.getForPackage(pkgId)
        val downloaded = files.sumOf { it.downloadedBytes }
        val known = files.all { it.expectedBytes != null }
        val total = if (known) files.sumOf { it.expectedBytes ?: 0L } else null
        val pkg = packageDao.get(pkgId) ?: return downloaded
        packageDao.upsert(pkg.copy(downloadedBytes = downloaded, totalBytes = total ?: pkg.totalBytes, updatedAt = System.currentTimeMillis()))
        return downloaded
    }

    /** Rolling-window speed (spec §19.1: recent window, not full-lifetime average). ETA is
     * derived, never stored — see [_speedInfo] and [toUiState]. */
    private fun recordSpeedSample(totalDownloadedForFile: Long) {
        val now = System.currentTimeMillis()
        speedSamples.addLast(now to totalDownloadedForFile)
        while (speedSamples.size > 1 && now - speedSamples.first().first > SPEED_WINDOW_MS) speedSamples.removeFirst()
        if (speedSamples.size < 3) return
        val (t0, b0) = speedSamples.first()
        val (t1, b1) = speedSamples.last()
        val elapsedSec = (t1 - t0) / 1000.0
        if (elapsedSec <= 0) return
        val bytesPerSecond = ((b1 - b0) / elapsedSec).toLong().coerceAtLeast(0)
        _speedInfo.value = bytesPerSecond to null // ETA filled in per-package in toUiState, once total bytes is known
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun toUiState(pkg: DownloadPackage, files: List<DownloadFile>, speed: Pair<Long?, Long?>?): ModelUiState {
        val catalog = ModelCatalog.find(pkg.modelId, pkg.version)
        val bytesPerSecond = speed?.first
        val etaSeconds = if (bytesPerSecond != null && bytesPerSecond > 0 && pkg.totalBytes != null) {
            ((pkg.totalBytes - pkg.downloadedBytes).coerceAtLeast(0)) / bytesPerSecond
        } else null
        return ModelUiState(
            modelId = pkg.modelId, version = pkg.version, displayName = pkg.displayName,
            description = catalog?.description.orEmpty(), category = pkg.role, status = pkg.status,
            files = files.map {
                ModelFileUiState(it.fileId, it.fileName, it.status, it.downloadedBytes, it.expectedBytes, it.errorMessage)
            },
            downloadedBytes = pkg.downloadedBytes, totalBytes = pkg.totalBytes,
            speedBytesPerSecond = bytesPerSecond, estimatedRemainingSeconds = etaSeconds,
            currentFileName = files.find { it.fileId == pkg.currentFileId }?.fileName,
            completedFileCount = files.count { it.status == FileDownloadStatus.COMPLETED },
            totalFileCount = files.size,
            error = pkg.errorCode?.let { ModelError(it, pkg.errorMessage ?: "") },
            requiresAuthToken = pkg.authRequired,
            capabilities = catalog?.capabilities.orEmpty(),
            precision = catalog?.precision,
            minimumRamBytes = catalog?.minimumRamBytes,
            sourceName = catalog?.sourceName ?: "",
            allowedActions = downloadActionsFor(pkg.status)
        )
    }

    /** TTS voices and downloadable STT models both skip ModelImportManager/ModelInfo and write
     * into [TtsVoiceModelDao] instead (see [finalizeVoiceModel]) — this is the one predicate
     * that decides "does this catalog category behave like a voice model" everywhere that
     * distinction matters, so a third such category later is a one-line change, not a hunt
     * across every `== ModelRole.TTS_VOICE` check. */
    private fun isVoiceLikeCategory(role: ModelRole): Boolean =
        role == ModelRole.TTS_VOICE || role == ModelRole.STT_MODEL

    private fun toNotDownloadedUiState(catalog: CatalogModel): ModelUiState = ModelUiState(
        modelId = catalog.modelId, version = catalog.version, displayName = catalog.displayName,
        description = catalog.description, category = catalog.category, status = ModelStatus.NOT_DOWNLOADED,
        totalBytes = catalog.totalExpectedBytes, totalFileCount = catalog.files.size,
        requiresAuthToken = catalog.requiresAuthToken, requiresLicenseAcceptance = catalog.requiresLicenseAcceptance,
        licenseName = catalog.licenseName, licenseUrl = catalog.licenseUrl, precision = catalog.precision,
        minimumRamBytes = catalog.minimumRamBytes, capabilities = catalog.capabilities, sourceName = catalog.sourceName,
        allowedActions = setOf(ModelAction.DOWNLOAD, ModelAction.DETAILS)
    )

    companion object {
        private const val TAG = "ModelDownloadRepository"
        private const val NETWORK_POLL_MS = 5_000L
        private const val NETWORK_RETRY_MS = 2_000L
        private const val SPEED_WINDOW_MS = 5_000L
        private val PAUSABLE_STATUSES = setOf(
            ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.DOWNLOADING,
            ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI
        )
    }
}
