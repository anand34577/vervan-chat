package com.vervan.chat.ui.workflows

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.repo.resolveEditId
import com.vervan.chat.model.ExtractResult
import com.vervan.chat.model.TextExtractor
import com.vervan.chat.system.toUserMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowListViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val workflows: StateFlow<List<Workflow>> = db.workflowDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** One step's instruction (truncated for display) and the text it produced. */
data class StepResult(val instruction: String, val output: String, val done: Boolean)

class WorkflowRunViewModel(private val app: VervanApp, private val workflowId: String) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine
    private val retrievalEngine = app.container.retrievalEngine

    private val _workflow = MutableStateFlow<Workflow?>(null)
    val workflow: StateFlow<Workflow?> = _workflow

    private val _steps = MutableStateFlow<List<StepResult>>(emptyList())
    val steps: StateFlow<List<StepResult>> = _steps

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Pause/resume/cancel (Phase 5) — pauseRequested is checked between steps (not mid-stream),
    // so "pause" takes effect after the current step finishes, not instantly.
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused
    private var pauseRequested = false
    private var resumeIndex = 0
    private var carryText = ""
    private var runJob: kotlinx.coroutines.Job? = null

    /** Source selection (Phase 5, spec §31) — when set, the run is seeded with retrieved
     * passages from these knowledge bases instead of only the raw input text. */
    val knowledgeBases = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _sourceKbIds = MutableStateFlow<Set<String>>(emptySet())
    val sourceKbIds: StateFlow<Set<String>> = _sourceKbIds
    fun setSourceKbIds(ids: Set<String>) { _sourceKbIds.value = ids }

    init {
        viewModelScope.launch { _workflow.value = db.workflowDao().get(workflowId) }
    }

    /** Reads a picked file straight into text — ponytail: no persistence, this is a one-shot input, not a knowledge-base import.
     * Returns null (with [_error] set) instead of throwing on storage-full, a revoked/expired
     * SAF grant, or an OCR/extraction failure — this used to be able to throw uncaught out of
     * a bare `scope.launch` in WorkflowRunScreen with no exception handler, crashing the app
     * instead of just failing this one file pick. */
    suspend fun readFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        val tmp = try {
            File.createTempFile("workflow_input", ".tmp", app.cacheDir)
        } catch (t: Throwable) {
            _error.value = "Couldn't read file: ${t.toUserMessage()}"
            return@withContext null
        }
        try {
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val name = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                } ?: uri.lastPathSegment ?: "file.txt"
                when (val result = TextExtractor.extract(tmp, name)) {
                    is ExtractResult.Text -> result.content
                    // This is a one-shot text input, not a knowledge-base import, so a table/slide
                    // deck just gets flattened to plain text rather than routed through the
                    // row-group/per-slide chunkers those shapes get on the real import path.
                    is ExtractResult.Tabular -> result.sheets.joinToString("\n\n") { sheet ->
                        (listOfNotNull(sheet.header) + sheet.rows).joinToString("\n") { it.joinToString("\t") }
                    }
                    is ExtractResult.Slides -> result.slides.joinToString("\n\n") { slide ->
                        listOfNotNull(slide.body.takeIf { it.isNotBlank() }, slide.notes?.takeIf { it.isNotBlank() }).joinToString("\n")
                    }
                    is ExtractResult.Unsupported -> { _error.value = result.reason; null }
                    ExtractResult.NeedsOcr -> {
                        val text = if (name.substringAfterLast('.', "").lowercase() == "pdf") {
                            com.vervan.chat.model.OcrExtractor.extractFromPdf(tmp)
                        } else {
                            com.vervan.chat.model.OcrExtractor.extractFromImage(tmp)
                        }
                        text.ifBlank { _error.value = "OCR found no readable text"; null }
                    }
                }
            } catch (t: Throwable) {
                _error.value = "Couldn't read file: ${t.toUserMessage()}"
                null
            }
        } finally {
            tmp.delete()
        }
    }

    fun run(inputText: String) {
        val wf = _workflow.value ?: return
        if (inputText.isBlank() || _running.value) return
        resumeIndex = 0
        pauseRequested = false
        _paused.value = false
        _steps.value = wf.steps.map { StepResult(it, "", false) }
        viewModelScope.launch {
            carryText = if (_sourceKbIds.value.isNotEmpty()) {
                val passages = retrievalEngine.retrieve(_sourceKbIds.value.toList(), inputText, com.vervan.chat.retrieval.RetrievalMode.HYBRID, topK = 5)
                if (passages.isNotEmpty()) {
                    val refs = passages.joinToString("\n\n") { "(${it.documentName}) ${it.excerpt}" }
                    "Reference material:\n$refs\n\nTask input:\n$inputText"
                } else inputText
            } else inputText
            startRun(wf)
        }
    }

    /** Resumes a paused run from the step it stopped before. */
    fun resumeRun() {
        val wf = _workflow.value ?: return
        if (_running.value || !_paused.value) return
        pauseRequested = false
        _paused.value = false
        viewModelScope.launch { startRun(wf) }
    }

    /** Takes effect after the in-flight step finishes, not mid-stream. */
    fun pauseRun() { pauseRequested = true }

    fun cancelRun() {
        runJob?.cancel()
        _running.value = false
        _paused.value = false
        pauseRequested = false
    }

    private fun startRun(wf: Workflow) {
        runJob = viewModelScope.launch {
            _running.value = true
            _error.value = null

            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _running.value = false
                return@launch
            }
            try {
                val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, com.vervan.chat.modelload.LoadTrigger.CHAT_SEND)
                check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
                val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
                for (index in resumeIndex until wf.steps.size) {
                    if (pauseRequested) {
                        resumeIndex = index
                        _paused.value = true
                        _running.value = false
                        return@launch
                    }
                    val instruction = wf.steps[index]
                    var output = ""
                    app.container.generate(
                        model, "$instruction\n\n$carryText", null, null,
                        params.temperature, params.topP, params.topK, params.seed,
                        params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
                    ).collect { chunk ->
                        output += chunk
                        _steps.value = _steps.value.toMutableList().also { it[index] = StepResult(instruction, output, false) }
                    }
                    _steps.value = _steps.value.toMutableList().also { it[index] = StepResult(instruction, output, true) }
                    carryText = output
                    resumeIndex = index + 1
                }
            } catch (t: Throwable) {
                // Throwable, not just Exception — a multi-step run's accumulated carryText can
                // grow large enough to OutOfMemoryError on a low-RAM device; unlike ChatViewModel
                // this runner had no outer Throwable safety net at all.
                _error.value = "Workflow failed: ${t.toUserMessage()}"
                _running.value = false
                return@launch
            }
            _running.value = false
        }
    }

    fun saveAsLibraryOutput(content: String) {
        viewModelScope.launch {
            db.savedOutputDao().upsert(SavedOutput(content = content, label = _workflow.value?.name ?: "Workflow result"))
        }
    }

    fun saveAsNote(content: String) {
        viewModelScope.launch {
            db.noteDao().upsert(Note(title = _workflow.value?.name ?: "Workflow result", content = content))
        }
    }
}

/** Create ([workflowId] null) or edit an existing custom workflow. Built-ins can be opened
 * here too (read their steps as a starting point) but always save as a new copy — see [save]. */
class WorkflowEditorViewModel(private val app: VervanApp, private val workflowId: String?) : ViewModel() {
    private val db = app.container.db

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _steps = MutableStateFlow(listOf(""))
    val steps: StateFlow<List<String>> = _steps

    private val _isBuiltIn = MutableStateFlow(false)
    val isBuiltIn: StateFlow<Boolean> = _isBuiltIn

    init {
        if (workflowId != null) {
            viewModelScope.launch {
                db.workflowDao().get(workflowId)?.let { wf ->
                    _name.value = wf.name
                    _description.value = wf.description
                    _steps.value = wf.steps
                    _isBuiltIn.value = wf.isBuiltIn
                }
            }
        }
    }

    fun setName(value: String) { _name.value = value }
    fun setDescription(value: String) { _description.value = value }
    fun setStep(index: Int, value: String) {
        _steps.value = _steps.value.toMutableList().also { it[index] = value }
    }
    fun addStep() {
        if (_steps.value.size >= com.vervan.chat.ui.common.ValidationLimits.WORKFLOW_STEP_COUNT) return
        _steps.value = _steps.value + ""
    }
    fun removeStep(index: Int) {
        if (_steps.value.size <= 1) return
        _steps.value = _steps.value.toMutableList().also { it.removeAt(index) }
    }
    fun moveStep(index: Int, delta: Int) {
        val target = index + delta
        if (target !in _steps.value.indices) return
        _steps.value = _steps.value.toMutableList().also {
            val tmp = it[index]; it[index] = it[target]; it[target] = tmp
        }
    }

    /** A built-in opened for editing is saved as a new custom workflow — built-ins stay
     * fixed reference points, not edited in place. */
    suspend fun save(): Boolean {
        val cleanSteps = _steps.value.map { it.trim() }.filter { it.isNotBlank() }
        if (_name.value.isBlank() || cleanSteps.isEmpty()) return false
        val workflow = Workflow(
            id = resolveEditId(workflowId, _isBuiltIn.value),
            name = _name.value.trim(),
            description = _description.value.trim(),
            stepsJson = Workflow.encodeSteps(cleanSteps),
            isBuiltIn = false
        )
        db.workflowDao().upsert(workflow)
        return true
    }

    fun delete() {
        if (workflowId == null || _isBuiltIn.value) return
        // Soft delete (Phase 6, spec §34) — recoverable from the recycle bin instead of gone instantly.
        viewModelScope.launch { db.workflowDao().get(workflowId)?.let { db.workflowDao().upsert(it.copy(deletedAt = System.currentTimeMillis())) } }
    }
}
