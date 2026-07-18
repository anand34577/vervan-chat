package com.vervan.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.vervan.chat.data.db.dao.ChatDao
import com.vervan.chat.data.db.dao.ChunkDao
import com.vervan.chat.data.db.dao.DocumentDao
import com.vervan.chat.data.db.dao.DownloadFileDao
import com.vervan.chat.data.db.dao.DownloadPackageDao
import com.vervan.chat.data.db.dao.ExpenseDao
import com.vervan.chat.data.db.dao.FlashcardSetDao
import com.vervan.chat.data.db.dao.FolderDao
import com.vervan.chat.data.db.dao.JobDao
import com.vervan.chat.data.db.dao.KnowledgeBaseDao
import com.vervan.chat.data.db.dao.MemoryDao
import com.vervan.chat.data.db.dao.MemorySuggestionDao
import com.vervan.chat.data.db.dao.MessageDao
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.dao.NoteDao
import com.vervan.chat.data.db.dao.SavedOutputDao
import com.vervan.chat.data.db.dao.PersonaDao
import com.vervan.chat.data.db.dao.ProjectDao
import com.vervan.chat.data.db.dao.PromptTemplateDao
import com.vervan.chat.data.db.dao.StudyCardDao
import com.vervan.chat.data.db.dao.ToolAuditDao
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.db.dao.WorkflowDao
import com.vervan.chat.data.db.dao.WorkspaceDao
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.DownloadFile
import com.vervan.chat.data.db.entities.DownloadPackage
import com.vervan.chat.data.db.entities.Expense
import com.vervan.chat.data.db.entities.FileDownloadStatus
import com.vervan.chat.data.db.entities.ModelErrorCode
import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelOrigin
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.data.db.entities.StopReason
import com.vervan.chat.data.db.entities.FlashcardSet
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.MemorySuggestion
import com.vervan.chat.data.db.entities.MemorySuggestionStatus
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.MemoryScope
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.StudyCard
import com.vervan.chat.data.db.entities.ToolAudit
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.db.entities.Workspace

class Converters {
    @TypeConverter fun fromRole(v: MessageRole) = v.name
    @TypeConverter fun toRole(v: String) = MessageRole.valueOf(v)
    @TypeConverter fun fromState(v: MessageState) = v.name
    @TypeConverter fun toState(v: String) = MessageState.valueOf(v)
    @TypeConverter fun fromBackend(v: ModelBackend) = v.name
    @TypeConverter fun toBackend(v: String) = ModelBackend.valueOf(v)
    @TypeConverter fun fromModelRole(v: ModelRole) = v.name
    @TypeConverter fun toModelRole(v: String) = ModelRole.valueOf(v)
    @TypeConverter fun fromDocStatus(v: DocumentStatus) = v.name
    @TypeConverter fun toDocStatus(v: String) = DocumentStatus.valueOf(v)
    @TypeConverter fun fromMemoryScope(v: MemoryScope) = v.name
    @TypeConverter fun toMemoryScope(v: String) = MemoryScope.valueOf(v)
    @TypeConverter fun fromMemorySuggestionStatus(v: MemorySuggestionStatus) = v.name
    @TypeConverter fun toMemorySuggestionStatus(v: String) = MemorySuggestionStatus.valueOf(v)
    @TypeConverter fun fromJobType(v: JobType) = v.name
    @TypeConverter fun toJobType(v: String) = JobType.valueOf(v)
    @TypeConverter fun fromJobState(v: JobState) = v.name
    @TypeConverter fun toJobState(v: String) = JobState.valueOf(v)
    @TypeConverter fun fromModelOrigin(v: ModelOrigin) = v.name
    @TypeConverter fun toModelOrigin(v: String) = ModelOrigin.valueOf(v)
    @TypeConverter fun fromModelStatus(v: ModelStatus) = v.name
    @TypeConverter fun toModelStatus(v: String) = ModelStatus.valueOf(v)
    @TypeConverter fun fromStopReason(v: StopReason) = v.name
    @TypeConverter fun toStopReason(v: String) = StopReason.valueOf(v)
    @TypeConverter fun fromModelErrorCode(v: ModelErrorCode?) = v?.name
    @TypeConverter fun toModelErrorCode(v: String?) = v?.let { ModelErrorCode.valueOf(it) }
    @TypeConverter fun fromFileDownloadStatus(v: FileDownloadStatus) = v.name
    @TypeConverter fun toFileDownloadStatus(v: String) = FileDownloadStatus.valueOf(v)
    @TypeConverter fun fromModelFileRole(v: ModelFileRole) = v.name
    @TypeConverter fun toModelFileRole(v: String) = ModelFileRole.valueOf(v)
    @TypeConverter fun fromModelEngine(v: ModelEngine) = v.name
    @TypeConverter fun toModelEngine(v: String) = ModelEngine.valueOf(v)
}

@Database(
    entities = [
        Chat::class, Message::class, Persona::class, ModelInfo::class,
        KnowledgeBase::class, Document::class, Chunk::class, Note::class, Project::class,
        PromptTemplate::class, SavedOutput::class, Memory::class, Workflow::class, StudyCard::class,
        Folder::class, FlashcardSet::class, MemorySuggestion::class, ToolAudit::class, JobRecord::class,
        Workspace::class, Expense::class, TtsVoiceModel::class,
        DownloadPackage::class, DownloadFile::class
    ],
    version = 37,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun modelDao(): ModelDao
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun noteDao(): NoteDao
    abstract fun projectDao(): ProjectDao
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun savedOutputDao(): SavedOutputDao
    abstract fun memoryDao(): MemoryDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun studyCardDao(): StudyCardDao
    abstract fun folderDao(): FolderDao
    abstract fun flashcardSetDao(): FlashcardSetDao
    abstract fun memorySuggestionDao(): MemorySuggestionDao
    abstract fun toolAuditDao(): ToolAuditDao
    abstract fun jobDao(): JobDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ttsVoiceModelDao(): TtsVoiceModelDao
    abstract fun downloadPackageDao(): DownloadPackageDao
    abstract fun downloadFileDao(): DownloadFileDao
}
