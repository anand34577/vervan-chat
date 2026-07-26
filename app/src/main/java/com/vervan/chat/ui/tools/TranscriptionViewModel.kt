package com.vervan.chat.ui.tools

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.WavRecorder
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.TranscriptionProject
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.llm.ToolRunContext
import com.vervan.chat.voice.AudioDecoder
import com.vervan.chat.voice.WavPcmDecoder
import com.vervan.chat.voice.WhisperCppSttEngine
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
            val dest = File(projectDir(id), displayName.ifBlank { "audio" })
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)
            }
            if (!copied || dest.length() == 0L) {
                _phase.value = Phase.Failed("Could not read that file — it may be corrupted or an unsupported format.")
                return@launch
            }
            val variant = settings.whisperModelVariant.first()
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
                _phase.value = Phase.Failed("Could not start recording — check the microphone permission.")
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
            val variant = settings.whisperModelVariant.first()
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
            val previousDefault = settings.whisperModelVariant.first()
            val overriding = requestedVariant != previousDefault
            if (overriding) settings.setWhisperModelVariant(requestedVariant)
            try {
                val file = File(project.audioPath)
                val (pcm, durationMs) = withContext(Dispatchers.Default) {
                    if (file.extension.equals("wav", ignoreCase = true)) {
                        val audio = WavPcmDecoder.decode(file.readBytes())
                        audio.samples to (audio.samples.size * 1000L / audio.sampleRateHz)
                    } else {
                        AudioDecoder.decodeToPcm16k(file)
                    }
                }
                if (!whisperEngine.isReady()) {
                    dao.upsert(
                        project.copy(
                            status = "FAILED", durationMs = durationMs,
                            errorMessage = "The \"$requestedVariant\" whisper.cpp model isn't downloaded. Download it in Model Manager first.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    _phase.value = Phase.Failed("Model not downloaded")
                    return@launch
                }
                val segments = whisperEngine.transcribeWithTimestamps(pcm)
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
                        errorMessage = if (text == null) "Transcription produced no text — the audio may be silent or unclear." else null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _phase.value = if (text != null) Phase.Idle else Phase.Failed("No speech detected")
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
                _phase.value = Phase.Failed(t.message ?: "Transcription failed")
            } finally {
                if (overriding) settings.setWhisperModelVariant(previousDefault)
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
        viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            dao.upsert(project.copy(transcript = text, updatedAt = System.currentTimeMillis()))
        }
    }

    sealed interface AiActionState {
        data object Idle : AiActionState
        data class Running(val label: String) : AiActionState
        data class Failed(val message: String) : AiActionState
    }
    private val _aiActionState = MutableStateFlow<AiActionState>(AiActionState.Idle)
    val aiActionState: StateFlow<AiActionState> = _aiActionState

    /** Runs one offline-LLM action over the transcript and appends the (editable) result as a
     * new section — never replaces the transcript, so a bad generation just adds a section the
     * user can delete rather than destroying their edited text. [label] is both the button text
     * and the heading written into the transcript. */
    fun runAiAction(id: String, label: String, promptTemplate: (String) -> String) {
        viewModelScope.launch {
            val project = dao.get(id) ?: return@launch
            if (project.transcript.isBlank()) return@launch
            _aiActionState.value = AiActionState.Running(label)
            val result = runCatching {
                OneShotLlm.run(
                    app, promptTemplate(project.transcript),
                    runContext = ToolRunContext("tools/transcribe", label, project.transcript)
                )
            }.getOrNull()
            if (result.isNullOrBlank()) {
                _aiActionState.value = AiActionState.Failed("Could not generate \"$label\" — no active model, or it produced nothing.")
                return@launch
            }
            val updated = project.transcript.trimEnd() + "\n\n## $label\n\n${result.trim()}\n"
            dao.upsert(project.copy(transcript = updated, updatedAt = System.currentTimeMillis()))
            _aiActionState.value = AiActionState.Idle
        }
    }

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
                app.container.documentImportManager.importRawText(kbId, project.fileName, project.transcript)
                _saveState.value = SaveState.Saved
            } catch (t: Throwable) {
                _saveState.value = SaveState.Failed(t.message ?: "Could not save to Knowledge Base.")
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

    suspend fun exportTxt(project: TranscriptionProject): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "${safeName(project.fileName)}.txt")
        file.writeText(project.transcript)
        file
    }

    suspend fun exportMarkdown(project: TranscriptionProject): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
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

    override fun onCleared() {
        recorder?.cancel()
        whisperEngine.release()
    }
}
