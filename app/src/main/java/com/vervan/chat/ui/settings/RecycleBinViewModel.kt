package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.Workflow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecycleBinState(
    val chats: List<Chat> = emptyList(),
    val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList(),
    val folders: List<Folder> = emptyList(),
    // recycle bin coverage extended to authored content that previously
    // had no soft-delete path at all: personas, workflows, templates, projects, memories,
    // saved outputs.
    val personas: List<Persona> = emptyList(),
    val workflows: List<Workflow> = emptyList(),
    val templates: List<PromptTemplate> = emptyList(),
    val projects: List<Project> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val savedOutputs: List<SavedOutput> = emptyList()
) {
    val isEmpty get() = chats.isEmpty() && notes.isEmpty() && documents.isEmpty() && folders.isEmpty() &&
        personas.isEmpty() && workflows.isEmpty() && templates.isEmpty() && projects.isEmpty() &&
        memories.isEmpty() && savedOutputs.isEmpty()
}

class RecycleBinViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    // combine's fixed-arity overloads top out at 5 flows — 10 categories needs the
    // array-based form instead.
    val state: StateFlow<RecycleBinState> = combine(
        listOf(
            db.chatDao().observeDeleted(),
            db.noteDao().observeDeleted(),
            db.documentDao().observeDeleted(),
            db.folderDao().observeDeleted(),
            db.personaDao().observeDeleted(),
            db.workflowDao().observeDeleted(),
            db.promptTemplateDao().observeDeleted(),
            db.projectDao().observeDeleted(),
            db.memoryDao().observeDeleted(),
            db.savedOutputDao().observeDeleted()
        )
    ) { results ->
        @Suppress("UNCHECKED_CAST")
        RecycleBinState(
            chats = results[0] as List<Chat>,
            notes = results[1] as List<Note>,
            documents = results[2] as List<Document>,
            folders = results[3] as List<Folder>,
            personas = results[4] as List<Persona>,
            workflows = results[5] as List<Workflow>,
            templates = results[6] as List<PromptTemplate>,
            projects = results[7] as List<Project>,
            memories = results[8] as List<Memory>,
            savedOutputs = results[9] as List<SavedOutput>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecycleBinState())

    fun restoreChat(chat: Chat) { viewModelScope.launch { db.chatDao().update(chat.copy(deletedAt = null)) } }
    fun deleteChatForever(chat: Chat) {
        viewModelScope.launch {
            db.withTransaction {
                db.messageDao().deleteForChat(chat.id)
                db.toolAuditDao().deleteForChat(chat.id)
                db.savedOutputDao().clearSourceChat(chat.id)
                db.chatDao().delete(chat)
            }
        }
    }

    fun restoreNote(note: Note) { viewModelScope.launch { db.noteDao().update(note.copy(deletedAt = null)) } }
    fun deleteNoteForever(note: Note) { viewModelScope.launch { db.noteDao().delete(note) } }

    fun restoreDocument(document: Document) { viewModelScope.launch { app.container.documentImportManager.restore(document) } }
    fun deleteDocumentForever(document: Document) { viewModelScope.launch { app.container.documentImportManager.delete(document) } }

    fun restoreFolder(folder: Folder) { viewModelScope.launch { db.folderDao().update(folder.copy(deletedAt = null)) } }
    fun deleteFolderForever(folder: Folder) {
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearFolder(folder.id)
                db.noteDao().clearFolder(folder.id)
                db.folderDao().delete(folder)
            }
        }
    }

    fun restorePersona(persona: Persona) { viewModelScope.launch { db.personaDao().upsert(persona.copy(deletedAt = null)) } }
    fun deletePersonaForever(persona: Persona) {
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearPersona(persona.id)
                db.folderDao().clearDefaultPersona(persona.id)
                db.projectDao().clearPersona(persona.id)
                db.knowledgeBaseDao().clearDefaultPersona(persona.id)
                db.personaDao().delete(persona)
            }
        }
    }

    fun restoreWorkflow(workflow: Workflow) { viewModelScope.launch { db.workflowDao().upsert(workflow.copy(deletedAt = null)) } }
    fun deleteWorkflowForever(workflow: Workflow) { viewModelScope.launch { db.workflowDao().delete(workflow) } }

    fun restoreTemplate(template: PromptTemplate) { viewModelScope.launch { db.promptTemplateDao().upsert(template.copy(deletedAt = null)) } }
    fun deleteTemplateForever(template: PromptTemplate) { viewModelScope.launch { db.promptTemplateDao().delete(template) } }

    fun restoreProject(project: Project) { viewModelScope.launch { db.projectDao().upsert(project.copy(deletedAt = null)) } }
    fun deleteProjectForever(project: Project) {
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearProject(project.id)
                db.noteDao().clearProject(project.id)
                db.knowledgeBaseDao().clearDefaultProject(project.id)
                db.projectDao().delete(project)
            }
        }
    }

    fun restoreMemory(memory: Memory) { viewModelScope.launch { db.memoryDao().update(memory.copy(deletedAt = null)) } }
    fun deleteMemoryForever(memory: Memory) { viewModelScope.launch { db.memoryDao().delete(memory) } }

    fun restoreSavedOutput(output: SavedOutput) { viewModelScope.launch { db.savedOutputDao().upsert(output.copy(deletedAt = null)) } }
    fun deleteSavedOutputForever(output: SavedOutput) { viewModelScope.launch { db.savedOutputDao().delete(output) } }

    fun restoreAll() = state.value.also { s ->
        s.chats.forEach(::restoreChat); s.notes.forEach(::restoreNote); s.documents.forEach(::restoreDocument)
        s.folders.forEach(::restoreFolder); s.personas.forEach(::restorePersona); s.workflows.forEach(::restoreWorkflow)
        s.templates.forEach(::restoreTemplate); s.projects.forEach(::restoreProject); s.memories.forEach(::restoreMemory)
        s.savedOutputs.forEach(::restoreSavedOutput)
    }

    fun emptyTrash() = state.value.also { s ->
        s.chats.forEach(::deleteChatForever); s.notes.forEach(::deleteNoteForever); s.documents.forEach(::deleteDocumentForever)
        s.folders.forEach(::deleteFolderForever); s.personas.forEach(::deletePersonaForever); s.workflows.forEach(::deleteWorkflowForever)
        s.templates.forEach(::deleteTemplateForever); s.projects.forEach(::deleteProjectForever); s.memories.forEach(::deleteMemoryForever)
        s.savedOutputs.forEach(::deleteSavedOutputForever)
    }
}
