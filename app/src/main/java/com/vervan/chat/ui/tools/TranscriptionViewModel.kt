package com.vervan.chat.ui.tools

import com.vervan.chat.R
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.WavRecorder
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.TranscriptionProject
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.llm.ToolRunContext
import com.vervan.chat.system.pruneOldExports
import com.vervan.chat.voice.AudioDecoder
import com.vervan.chat.voice.WavPcmDecoder
import com.vervan.chat.voice.WhisperCppSttEngine
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.validation.InputLimits
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backs the Transcription screen (see acceptance criteria §5/§9/§15): import or record audio,
 * transcribe it with a chosen whisper.cpp model (defaulting to the app-wide default from Voice
 * Settings — see [com.vervan.chat.modeldownload.ModelCatalog]'s whisper entries — without
 * changing that default unless the user picks a different one), edit the transcript with
 * search/replace/undo, get per-segment timestamps for tap-to-seek playback (see
 * [WhisperCppSttEngine.transcribeWithTimestamps]), run offline-LLM actions on it, save it to a
 * Knowledge Base, and export it.
 *
 * Still deferred: speaker diarization. whisper.cpp has no diarization of its own — real speaker
 * separation needs a second model (a speaker-embedding/clustering model, e.g. pyannote), which
 * isn't integrated anywhere in this app yet. A same-speaker-vs-different-speaker heuristic from
 * silence gaps alone would be actively misleading (long pauses mid-sentence are common; short
 * ones between different speakers are too), so this is a real follow-up needing a new model, not
 * a UI-only gap like the others were.
 */
class TranscriptionViewModel(private val app: VervanApp) : ViewModel() {
    private val dao = app.container.db.transcriptionProjectDao()
    private val voiceModelDao = app.container.db.ttsVoiceModelDao()
    private val settings = app.container.settingsRepository
    private val whisperEngine = WhisperCppSttEngine(app, voiceModelDao, settings)
    private val shrutiEngine = com.vervan.chat.voice.ShrutiSttEngine(app, voiceModelDao, settings)

    val projects: StateFlow<List<TranscriptionProject>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knowledgeBases: StateFlow<List<KnowledgeBase>> =
        app.container.db.knowledgeBaseDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedModelVariants: StateFlow<List<com.vervan.chat.data.db.entities.TtsVoiceModel>> =
        voiceModelDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentId = MutableStateFlow<String?>(null)
    val current: StateFlow<TranscriptionProject?> = _currentId
        .let { idFlow -> kotlinx.coroutines.flow.combine(idFlow, projects) { id, list -> list.find { it.id == id } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    sealed interface Phase {
        data object Idle : Phase
        data class Recording(val elapsedMs: Long) : Phase
        data object Transcribing : Phase
        data class Failed(val message: String) : Phase
    }
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase

    private var recorder: WavRecorder? = null
    private var transcribeJob: Job? = null

    private fun projectDir(id: String) = File(app.filesDir, "transcriptions/$id").apply { mkdirs() }

    fun open(id: String) { _currentId.value = id }
    fun closeCurrent() { _currentId.value = null }

    /** Copies a picked document/video Uri into app-private storage and creates a PENDING
     * project row — content:// Uris from a picker aren't guaranteed readable on a later launch,
     * so the file has to be copied in, same pattern as [com.vervan.chat.model.DocumentImportManager]. */
    fun importFile(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val safeName = displayName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "audio" }.take(180)
            val dest = File(projectDir(id), safeName)
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyToLimited(output, 50L * 1024 * 1024) }
                    } != null
                }.getOrDefault(false)
            }
            if (!copied || dest.length() == 0L) {
                _phase.value = Phase.Failed(app.getString(R.string.transcription_could_not_read_that_file))
                return@launch
            }
            val variant = defaultVariant()
            dao.upsert(
                TranscriptionProject(
                    id = id, fileName = dest.name, audioPath = dest.absolutePath,
                    durationMs = 0L, modelVariant = variant, status = "PENDING"
                )
            )
            _currentId.value = id
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val dest = File(projectDir(id), "recording.wav")
            val rec = WavRecorder(dest)
            recorder = rec
            _currentId.value = id
            runCatching { rec.start() }.onFailure {
                _phase.value = Phase.Failed(app.getString(R.string.transcription_could_not_start_recording_check))
                recorder = null
                return@launch
            }
            val startedAt = System.currentTimeMillis()
            while (recorder === rec) {
                _phase.value = Phase.Recording(System.currentTimeMillis() - startedAt)
                kotlinx.coroutines.delay(200)
            }
        }
    }

    fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        rec.stop()
        _phase.value = Phase.Idle
        val id = _currentId.value ?: return
        viewModelScope.launch {
            val variant = defaultVariant()
            dao.upsert(
                TranscriptionProject(
                    id = id, fileName = "Recording", audioPath = rec.outputFile.absolutePath,
                    durationMs = 0L, modelVariant = variant, status = "PENDING"
                )
            )
        }
    }

    fun cancelRecording() {
        val rec = recorder ?: return
        recorder = null
        rec.cancel()
        _phase.value = Phase.Idle
        _currentId.value = null
    }

    /** Transcribes [id]'s audio with [variant] (defaults to the project's own, i.e. whatever
     * was the app-wide default when it was imported/recorded). Picking a different variant here
     * is a one-time override — [SettingsRepository.whisperModelVariant] is restored to its
     * previous value afterward, so this never silently changes the global default. */
    fun transcribe(id: String, variant: String? = null) {
        transcribeJob?.cancel()
        transcribeJob = viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            dao.upsert(project.copy(status = "TRANSCRIBING", errorMessage = null, updatedAt = System.currentTimeMillis()))
            _phase.value = Phase.Transcribing
            val requestedVariant = variant ?: project.modelVariant
            val useShruti = requestedVariant.startsWith(SHRUTI_PREFIX)
            val shrutiLanguage = requestedVariant.removePrefix(SHRUTI_PREFIX)
            val previousDefault = if (useShruti) settings.shrutiModelVariant.first() else settings.whisperModelVariant.first()
            val target = if (useShruti) shrutiLanguage else requestedVariant
            val overriding = target != previousDefault
            if (overriding) {
                if (useShruti) settings.setShrutiModelVariant(target) else settings.setWhisperModelVariant(target)
            }
            try {
                val file = File(project.audioPath)
                val (pcm, durationMs) = withContext(Dispatchers.Default) {
                    if (file.extension.equals("wav", ignoreCase = true)) {
                        require(file.length() <= 50L * 1024 * 1024) { app.getString(R.string.transcription_audio_file_exceeds_the_50) }
                        val audio = file.inputStream().use { WavPcmDecoder.decode(it.readBytesLimited(50L * 1024 * 1024)) }
                        val duration = audio.samples.size * 1000L / audio.sampleRateHz
                        require(duration <= InputLimits.MAX_AUDIO_DURATION_MS) { app.getString(R.string.transcription_audio_must_be_30_minutes) }
                        val samples = if (audio.sampleRateHz == 16_000) audio.samples
                        else com.vervan.chat.voice.ShrutiSttEngine.resampleTo16k(audio.samples, audio.sampleRateHz)
                        samples to duration
                    } else {
                        AudioDecoder.decodeToPcm16k(file)
                    }
                }
                val ready = if (useShruti) shrutiEngine.isReady() else whisperEngine.isReady()
                if (!ready) {
                    val name = if (useShruti) "Shruti ${com.vervan.chat.voice.ShrutiSttEngine.LANGUAGES[shrutiLanguage] ?: shrutiLanguage}"
                    else "\"$requestedVariant\" whisper.cpp"
                    dao.upsert(
                        project.copy(
                            status = "FAILED", durationMs = durationMs,
                            errorMessage = "The $name model isn't downloaded. Download it in Model Manager first.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    _phase.value = Phase.Failed(app.getString(R.string.transcription_model_not_downloaded))
                    return@launch
                }
                val segments = if (useShruti) shrutiEngine.transcribeWithTimestamps(pcm) else whisperEngine.transcribeWithTimestamps(pcm)
                val text = segments?.joinToString(" ") { it.text }
                val segmentsJson = segments?.let { list ->
                    org.json.JSONArray().apply {
                        list.forEach { seg ->
                            put(org.json.JSONObject().put("start", seg.startMs).put("end", seg.endMs).put("text", seg.text))
                        }
                    }.toString()
                }
                dao.upsert(
                    project.copy(
                        status = if (text != null) "DONE" else "FAILED",
                        transcript = text ?: project.transcript,
                        segmentsJson = segmentsJson ?: project.segmentsJson,
                        durationMs = durationMs, modelVariant = requestedVariant,
                        engine = if (useShruti) com.vervan.chat.voice.ShrutiSttEngine.ENGINE else "WHISPER_CPP",
                        errorMessage = if (text == null) "Transcription produced no text — the audio may be silent or unclear." else null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _phase.value = if (text != null) Phase.Idle else Phase.Failed(app.getString(R.string.transcription_no_speech_detected))
            } catch (c: kotlinx.coroutines.CancellationException) {
                dao.upsert(project.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis()))
                throw c
            } catch (t: Throwable) {
                dao.upsert(
                    project.copy(
                        status = "FAILED", errorMessage = t.message ?: "Transcription failed.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _phase.value = Phase.Failed(t.message ?: app.getString(R.string.transcription_failed))
            } finally {
                if (overriding) {
                    if (useShruti) settings.setShrutiModelVariant(previousDefault) else settings.setWhisperModelVariant(previousDefault)
                }
            }
        }
    }

    fun parseSegments(project: TranscriptionProject): List<WhisperCppSttEngine.TranscriptSegment> {
        val json = project.segmentsJson ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                WhisperCppSttEngine.TranscriptSegment(obj.getLong("start"), obj.getLong("end"), obj.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    fun cancelTranscription() {
        transcribeJob?.cancel()
        transcribeJob = null
        _phase.value = Phase.Idle
    }

    fun updateTranscript(id: String, text: String) {
        if (text.length > InputLimits.TRANSCRIPT_CHARS) return
        viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            dao.upsert(project.copy(transcript = text, updatedAt = System.currentTimeMillis()))
        }
    }

    sealed interface AiActionState {
        data object Idle : AiActionState
        data class Running(val label: String) : AiActionState
        data class Failed(val message: String) : AiActionState
        data class Done(val label: String, val text: String) : AiActionState
    }
    private val _aiActionState = MutableStateFlow<AiActionState>(AiActionState.Idle)
    val aiActionState: StateFlow<AiActionState> = _aiActionState

    /** Runs one offline-LLM action over the transcript and appends the (editable) result as a
     * new section — never replaces the transcript, so a bad generation just adds a section the
     * user can delete rather than destroying their edited text. [label] is both the button text
     * and the heading written into the transcript. */
    fun runAiAction(id: String, label: String, promptTemplate: (String) -> String) {
        if (_aiActionState.value is AiActionState.Running) return
        viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            if (project.transcript.isBlank()) return@launch
            _aiActionState.value = AiActionState.Running(label)
            val result = runCatching {
                OneShotLlm.run(
                    app, promptTemplate(project.transcript),
                    runContext = ToolRunContext("tools/transcribe", label, project.transcript)
                )
            }.onFailure { Log.e(TAG, "runAiAction($label) failed", it) }.getOrNull()
            if (result.isNullOrBlank()) {
                _aiActionState.value = AiActionState.Failed(app.getString(R.string.transcription_could_not_generate_arg1_no, label))
                return@launch
            }
            // The result is shown on its own; the transcript only changes when the user adds it.
            _aiActionState.value = AiActionState.Done(label, result.trim())
        }
    }

    fun dismissAiResult() { _aiActionState.value = AiActionState.Idle }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Saved : SaveState
        data class Failed(val message: String) : SaveState
    }
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    /** Saves the transcript as a document in [existingKbId], or a newly created KB named
     * [newKbName] when [existingKbId] is null — reuses [com.vervan.chat.model.DocumentImportManager]'s
     * existing chunk/embed pipeline, same as every other document import in the app, so an
     * indexed transcript is immediately askable via the Knowledge screen's chat. */
    fun saveToKnowledgeBase(id: String, existingKbId: String?, newKbName: String?) {
        if (_saveState.value == SaveState.Saving) return
        viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            if (project.transcript.isBlank()) return@launch
            _saveState.value = SaveState.Saving
            try {
                val kbId = existingKbId ?: run {
                    val kb = KnowledgeBase(name = newKbName?.takeIf { it.isNotBlank() } ?: "Transcripts")
                    app.container.db.knowledgeBaseDao().upsert(kb)
                    kb.id
                }
                val document = app.container.documentImportManager.importRawText(kbId, project.fileName, project.transcript)
                if (document.status == DocumentStatus.READY) {
                    _saveState.value = SaveState.Saved
                } else {
                    _saveState.value = SaveState.Failed(
                        document.failureReason ?: "The document could not be indexed (${document.status.name})."
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "saveToKnowledgeBase failed for project=$id", t)
                _saveState.value = SaveState.Failed(t.message ?: app.getString(R.string.transcription_could_not_save_to_knowledge))
            }
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }

    fun delete(id: String) {
        viewModelScope.launch {
            val project = dao.get(id)
            dao.deleteById(id)
            project?.let { File(it.audioPath).parentFile?.deleteRecursively() }
            if (_currentId.value == id) _currentId.value = null
        }
    }

    /** Clears the whole history, audio files included; the screen confirms first. */
    fun deleteAll(ids: Collection<String>) {
        ids.forEach(::delete)
    }

    suspend fun exportTxt(project: TranscriptionProject): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
        pruneOldExports(dir)
        val file = File(dir, "${safeName(project.fileName)}.txt")
        file.writeText(project.transcript)
        file
    }

    suspend fun exportMarkdown(project: TranscriptionProject): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
        pruneOldExports(dir)
        val file = File(dir, "${safeName(project.fileName)}.md")
        file.writeText(
            buildString {
                appendLine("# ${project.fileName}")
                appendLine()
                appendLine("_Transcribed with whisper.cpp (${project.modelVariant}) on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(project.createdAt))}_")
                appendLine()
                appendLine(project.transcript)
            }
        )
        file
    }

    suspend fun exportPdf(project: TranscriptionProject): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
        pruneOldExports(dir)
        val file = File(dir, "${safeName(project.fileName)}.pdf")
        val subtitle = "Transcribed with whisper.cpp (${project.modelVariant}) on " +
            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(project.createdAt))
        com.vervan.chat.model.ChatPdfExporter.write(
            file, project.fileName, subtitle,
            listOf(com.vervan.chat.model.PdfTranscriptEntry("Transcript", project.transcript))
        )
        file
    }

    private fun safeName(name: String) = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "transcript" }.take(60)

    /** The model a new project starts with: Shruti when the user prefers it (or has no whisper.cpp
     *  model but does have Shruti), otherwise the whisper.cpp default. */
    private suspend fun defaultVariant(): String {
        val whisper = settings.whisperModelVariant.first()
        val shruti = settings.shrutiModelVariant.first()
        val hasShruti = com.vervan.chat.BuildConfig.NEEDLE_AVAILABLE &&
            voiceModelDao.getByEngine(com.vervan.chat.voice.ShrutiSttEngine.ENGINE, shruti)?.isReady == true
        val hasWhisper = voiceModelDao.getByEngine(WhisperCppSttEngine.ENGINE, whisper)?.isReady == true
        val prefersShruti = settings.sttEnginePreference.first() == "SHRUTI"
        return if (hasShruti && (prefersShruti || !hasWhisper)) SHRUTI_PREFIX + shruti else whisper
    }

    override fun onCleared() {
        recorder?.cancel()
        whisperEngine.release()
        shrutiEngine.release()
    }

    companion object {
        /** Variant key prefix for a Shruti language model in the shared model picker. */
        const val SHRUTI_PREFIX = "shruti:"
        private const val TAG = "TranscriptionViewModel"
    }
}
