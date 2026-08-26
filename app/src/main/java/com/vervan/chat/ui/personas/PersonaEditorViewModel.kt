package com.vervan.chat.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.repo.resolveEditId
import com.vervan.chat.data.repo.nextNumberedCopyName
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** [personaId] null creates a new persona; non-null edits (a built-in opened here saves
 * as a new custom copy, same pattern as [com.vervan.chat.ui.workflows.WorkflowEditorViewModel]). */
class PersonaEditorViewModel(private val app: VervanApp, private val personaId: String?) : ViewModel() {
    private val db = app.container.db

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _systemInstruction = MutableStateFlow("")
    val systemInstruction: StateFlow<String> = _systemInstruction

    private val _isBuiltIn = MutableStateFlow(false)
    val isBuiltIn: StateFlow<Boolean> = _isBuiltIn

    private val _tone = MutableStateFlow("NEUTRAL")
    val tone: StateFlow<String> = _tone
    private val _formality = MutableStateFlow("NEUTRAL")
    val formality: StateFlow<String> = _formality
    private val _conciseness = MutableStateFlow("NORMAL")
    val conciseness: StateFlow<String> = _conciseness
    private val _creativity = MutableStateFlow(0.5f)
    val creativity: StateFlow<Float> = _creativity
    private val _responseLength = MutableStateFlow("BALANCED")
    val responseLength: StateFlow<String> = _responseLength
    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language
    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError

    private val _isLoading = MutableStateFlow(personaId != null)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _recordFound = MutableStateFlow(personaId == null)
    val recordFound: StateFlow<Boolean> = _recordFound

    /** A freshly copied/imported avatar file created during this editing session that hasn't
     * been saved onto a Persona row yet — distinct from [_avatarPath], which right after
     * `init{}` runs for an existing persona is instead that persona's already-persisted avatar
     * and must never be deleted here. Tracked separately so replacing the pick before saving
     * (import a photo, then a different one, or a character card, or switch to an emoji) doesn't
     * leak the file the user backed away from, and so leaving the editor without saving at all
     * doesn't leave one behind either — see [setScratchAvatar]/[discardPendingScratchAvatar]. */
    private var pendingScratchAvatarFile: File? = null

    init {
        load()
    }

    fun retryLoad() {
        load()
    }

    private fun load() {
        if (personaId == null) {
            _isLoading.value = false
            _recordFound.value = true
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                val persona = db.personaDao().getPersona(personaId)
                _recordFound.value = persona != null
                persona?.let {
                    _name.value = it.name
                    _description.value = it.description
                    _systemInstruction.value = it.systemInstruction
                    _isBuiltIn.value = it.isBuiltIn
                    _tone.value = it.tone
                    _formality.value = it.formality
                    _conciseness.value = it.conciseness
                    _creativity.value = it.creativity
                    _responseLength.value = it.responseLength
                    _language.value = it.language
                    _avatarPath.value = it.avatarPath
                }
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                if (t is CancellationException) throw t
                _recordFound.value = false
                _loadError.value = t.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setName(value: String) { _name.value = value; _saveError.value = null }
    fun setDescription(value: String) { _description.value = value; _saveError.value = null }
    fun setSystemInstruction(value: String) { _systemInstruction.value = value; _saveError.value = null }
    fun setTone(value: String) { _tone.value = value }
    fun setFormality(value: String) { _formality.value = value }
    fun setConciseness(value: String) { _conciseness.value = value }
    fun setCreativity(value: Float) { _creativity.value = value }
    fun setResponseLength(value: String) { _responseLength.value = value }
    fun setLanguage(value: String) { _language.value = value }

    /** Character card import (SillyTavern PNG cards) — fills the editor fields from the card,
     * same as if the user had typed them in, so Save behaves identically either way. Off-main:
     * these PNGs embed a JSON blob in metadata, and decoding a larger card would otherwise block
     * the UI thread on an onClick, same reasoning as [importAvatar] below. */
    fun importCharacterCard(context: android.content.Context, uri: android.net.Uri) {
        _importError.value = null
        viewModelScope.launch {
            try {
                val card = withContext(Dispatchers.IO) { com.vervan.chat.model.CharacterCardImporter.import(context, uri) }
                _name.value = card.name
                _description.value = card.description
                _systemInstruction.value = card.systemInstruction
                card.avatarFile?.let { setScratchAvatar(it) } ?: run { discardPendingScratchAvatar(); _avatarPath.value = null }
            } catch (e: com.vervan.chat.model.CharacterCardImporter.NotACharacterCardException) {
                _importError.value = e.message
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                android.util.Log.e(TAG, "importCharacterCard failed for $uri", t)
                _importError.value = "Could not import this file: ${t.message ?: t::class.simpleName}"
            }
        }
    }

    fun dismissImportError() { _importError.value = null }

    /** Sets the persona's avatar from an arbitrary picked image (gallery/camera), normalized and
     *  copied to the same personas/avatars/ dir that character-card portraits land in. Off-main
     *  because decoding a camera photo can be a few hundred KB of work; the StateFlow drives the
     *  preview once it lands. */
    fun importAvatar(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val dest = File(File(context.filesDir, "personas/avatars"), "${UUID.randomUUID()}.png")
            val ok = withContext(Dispatchers.IO) { ImageUtils.copyNormalizedPng(context, uri, dest, 512) }
            if (ok) setScratchAvatar(dest)
            else _importError.value = "Could not use this image as an avatar. Try a different file."
        }
    }

    /** Replaces the avatar with a freshly created scratch file, deleting whatever scratch file
     * (if any) it's superseding — but never the persona's original persisted avatar, since that
     * one was never recorded in [pendingScratchAvatarFile] to begin with. */
    private fun setScratchAvatar(file: File) {
        val previous = pendingScratchAvatarFile
        pendingScratchAvatarFile = file
        _avatarPath.value = file.absolutePath
        if (previous != null && previous != file) previous.delete()
    }

    private fun discardPendingScratchAvatar() {
        pendingScratchAvatarFile?.delete()
        pendingScratchAvatarFile = null
    }

    fun setEmojiAvatar(emoji: String) { discardPendingScratchAvatar(); _avatarPath.value = "emoji:$emoji" }

    fun clearAvatar() { discardPendingScratchAvatar(); _avatarPath.value = null }

    suspend fun save(): Boolean {
        if (_name.value.isBlank() || _systemInstruction.value.isBlank()) {
            _saveError.value = "Name and system instruction are required."
            return false
        }
        if (_name.value.length > ValidationLimits.PERSONA_NAME ||
            _description.value.length > ValidationLimits.PERSONA_ROLE ||
            _systemInstruction.value.length > ValidationLimits.PERSONA_SYSTEM_INSTRUCTION ||
            _language.value.length > 80 ||
            !_creativity.value.isFinite() || _creativity.value !in 0f..1f ||
            _tone.value !in setOf("NEUTRAL", "WARM", "DIRECT", "PLAYFUL") ||
            _formality.value !in setOf("CASUAL", "NEUTRAL", "FORMAL") ||
            _conciseness.value !in setOf("NORMAL", "TERSE", "ELABORATE") ||
            _responseLength.value !in setOf("BALANCED", "SHORT", "LONG")
        ) {
            _saveError.value = "Shorten the highlighted fields before saving."
            return false
        }
        val cleanName = _name.value.trim()
        val editId = resolveEditId(personaId, _isBuiltIn.value)
        val existing = db.personaDao().findByName(cleanName)
        if (existing != null && existing.id != editId) {
            _saveError.value = "A persona named \"$cleanName\" already exists."
            return false
        }
        val persona = Persona(
            id = editId,
            name = cleanName,
            description = _description.value.trim(),
            systemInstruction = _systemInstruction.value.trim(),
            isBuiltIn = false,
            tone = _tone.value,
            formality = _formality.value,
            conciseness = _conciseness.value,
            creativity = _creativity.value,
            responseLength = _responseLength.value,
            language = _language.value.trim(),
            avatarPath = _avatarPath.value
        )
        db.personaDao().upsert(persona)
        // The scratch file (if any) is now the persona's real, persisted avatar — stop treating
        // it as an unsaved leftover this ViewModel owns and would otherwise delete on dispose.
        pendingScratchAvatarFile = null
        return true
    }

    /** Soft delete — recoverable from the recycle bin instead of gone instantly. */
    fun delete() {
        if (personaId == null || _isBuiltIn.value) return
        viewModelScope.launch {
            db.personaDao().getPersona(personaId)?.let {
                db.chatDao().clearPersona(personaId)
                db.folderDao().clearDefaultPersona(personaId)
                db.projectDao().clearPersona(personaId)
                db.knowledgeBaseDao().clearDefaultPersona(personaId)
                db.personaDao().upsert(it.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun duplicate(): String {
        val duplicateName = nextNumberedCopyName(_name.value) { candidate ->
            db.personaDao().findByName(candidate) != null
        }
        val copy = Persona(
            name = duplicateName,
            description = _description.value.trim(),
            systemInstruction = _systemInstruction.value.trim(),
            isBuiltIn = false,
            tone = _tone.value,
            formality = _formality.value,
            conciseness = _conciseness.value,
            creativity = _creativity.value,
            responseLength = _responseLength.value,
            language = _language.value.trim(),
            avatarPath = _avatarPath.value
        )
        db.personaDao().upsert(copy)
        return copy.id
    }

    /** Leaving the editor (back button, process death) without ever calling [save] must not
     * leave whatever avatar was picked/imported during this session sitting in
     * `personas/avatars` forever — [save] already clears [pendingScratchAvatarFile] the instant
     * the file becomes a real, persisted avatar, so anything still here was never saved. */
    override fun onCleared() {
        discardPendingScratchAvatar()
    }

    companion object {
        private const val TAG = "PersonaEditorViewModel"
    }
}
