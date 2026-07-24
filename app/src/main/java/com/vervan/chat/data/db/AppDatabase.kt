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
import com.vervan.chat.data.db.dao.StoreInstallArtifactDao
import com.vervan.chat.data.db.dao.StoreInstallSessionDao
import com.vervan.chat.data.db.dao.StudyCardDao
import com.vervan.chat.data.db.dao.ToolAuditDao
import com.vervan.chat.data.db.dao.ToolRunDao
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
import com.vervan.chat.data.db.entities.StoreArtifactState
import com.vervan.chat.data.db.entities.StoreInstallArtifact
import com.vervan.chat.data.db.entities.StoreInstallSession
import com.vervan.chat.data.db.entities.StoreInstallState
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
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.db.entities.Workspace

class Converters {
    /**
     * Defensive enum lookup: a DB row written by a newer build (then downgraded) or a hand-edited
     * DB contains a value this build doesn't know. The original `Enum.valueOf(v)` throws
     * IllegalArgumentException, which propagates through the Flow/suspend query and crashes the
     * reading screen. Falling back to a known value (and logging once) keeps the row readable so
     * the user can delete or fix it from the UI instead of being locked out.
     */
    private inline fun <reified E : Enum<E>> safeEnum(v: String, fallback: E): E =
        runCatching { enumValueOf<E>(v) }.getOrElse {
            android.util.Log.w("Converters", "Unknown ${E::class.simpleName} '$v' in DB, defaulting to $fallback")
            fallback
        }

    @TypeConverter fun fromRole(v: MessageRole) = v.name
    @TypeConverter fun toRole(v: String) = safeEnum<MessageRole>(v, MessageRole.USER)
    @TypeConverter fun fromState(v: MessageState) = v.name
    @TypeConverter fun toState(v: String) = safeEnum<MessageState>(v, MessageState.FAILED)
    @TypeConverter fun fromBackend(v: ModelBackend) = v.name
    @TypeConverter fun toBackend(v: String) = safeEnum<ModelBackend>(v, ModelBackend.UNVERIFIED)
    @TypeConverter fun fromModelRole(v: ModelRole) = v.name
    @TypeConverter fun toModelRole(v: String) = safeEnum<ModelRole>(v, ModelRole.GENERATION)
    @TypeConverter fun fromDocStatus(v: DocumentStatus) = v.name
    @TypeConverter fun toDocStatus(v: String) = safeEnum<DocumentStatus>(v, DocumentStatus.FAILED)
    @TypeConverter fun fromMemoryScope(v: MemoryScope) = v.name
    @TypeConverter fun toMemoryScope(v: String) = safeEnum<MemoryScope>(v, MemoryScope.GLOBAL)
    @TypeConverter fun fromMemorySuggestionStatus(v: MemorySuggestionStatus) = v.name
    @TypeConverter fun toMemorySuggestionStatus(v: String) = safeEnum<MemorySuggestionStatus>(v, MemorySuggestionStatus.PENDING)
    @TypeConverter fun fromJobType(v: JobType) = v.name
    @TypeConverter fun toJobType(v: String) = safeEnum<JobType>(v, JobType.LONG_GENERATION)
    @TypeConverter fun fromJobState(v: JobState) = v.name
    @TypeConverter fun toJobState(v: String) = safeEnum<JobState>(v, JobState.FAILED)
    @TypeConverter fun fromModelOrigin(v: ModelOrigin) = v.name
    @TypeConverter fun toModelOrigin(v: String) = safeEnum<ModelOrigin>(v, ModelOrigin.LOCAL_IMPORT)
    @TypeConverter fun fromModelStatus(v: ModelStatus) = v.name
    @TypeConverter fun toModelStatus(v: String) = safeEnum<ModelStatus>(v, ModelStatus.FAILED)
    @TypeConverter fun fromStopReason(v: StopReason) = v.name
    @TypeConverter fun toStopReason(v: String) = safeEnum<StopReason>(v, StopReason.NONE)
    @TypeConverter fun fromModelErrorCode(v: ModelErrorCode?) = v?.name
    @TypeConverter fun toModelErrorCode(v: String?) = v?.let { runCatching { ModelErrorCode.valueOf(it) }.getOrNull() }
    @TypeConverter fun fromFileDownloadStatus(v: FileDownloadStatus) = v.name
    @TypeConverter fun toFileDownloadStatus(v: String) = safeEnum<FileDownloadStatus>(v, FileDownloadStatus.FAILED)
    @TypeConverter fun fromModelFileRole(v: ModelFileRole) = v.name
    @TypeConverter fun toModelFileRole(v: String) = safeEnum<ModelFileRole>(v, ModelFileRole.MODEL)
    @TypeConverter fun fromModelEngine(v: ModelEngine) = v.name
    @TypeConverter fun toModelEngine(v: String) = safeEnum<ModelEngine>(v, ModelEngine.LITERT_LM)
    @TypeConverter fun fromStoreInstallState(v: StoreInstallState) = v.name
    @TypeConverter fun toStoreInstallState(v: String) = safeEnum<StoreInstallState>(v, StoreInstallState.FAILED_RETRYABLE)
    @TypeConverter fun fromStoreArtifactState(v: StoreArtifactState) = v.name
    @TypeConverter fun toStoreArtifactState(v: String) = safeEnum<StoreArtifactState>(v, StoreArtifactState.FAILED)
    @TypeConverter fun fromToolRunState(v: ToolRunState) = v.name
    @TypeConverter fun toToolRunState(v: String) = safeEnum<ToolRunState>(v, ToolRunState.FAILED)
}

@Database(
    entities = [
        Chat::class, Message::class, Persona::class, ModelInfo::class,
        KnowledgeBase::class, Document::class, Chunk::class, Note::class, Project::class,
        PromptTemplate::class, SavedOutput::class, Memory::class, Workflow::class, StudyCard::class,
        Folder::class, FlashcardSet::class, MemorySuggestion::class, ToolAudit::class, JobRecord::class,
        Workspace::class, Expense::class, TtsVoiceModel::class, ToolRun::class,
        DownloadPackage::class, DownloadFile::class,
        StoreInstallSession::class, StoreInstallArtifact::class
    ],
    version = 45,
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
    abstract fun toolRunDao(): ToolRunDao
    abstract fun jobDao(): JobDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ttsVoiceModelDao(): TtsVoiceModelDao
    abstract fun downloadPackageDao(): DownloadPackageDao
    abstract fun downloadFileDao(): DownloadFileDao
    abstract fun storeInstallSessionDao(): StoreInstallSessionDao
    abstract fun storeInstallArtifactDao(): StoreInstallArtifactDao
}
