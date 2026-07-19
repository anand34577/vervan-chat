package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// OCR_RUNNING is distinct from READING (B10) — a scanned PDF spends much longer here than a
// normal text extraction, and the UI can now tell the user which is happening.
enum class DocumentStatus { READING, OCR_RUNNING, EXTRACTING, CHUNKING, EMBEDDING, READY, FAILED, UNSUPPORTED }

// knowledgeBaseId backs the KB detail screen's document list; workspaceId backs the per-
// workspace document count/list. See Migration(36, 37).
@Entity(tableName = "documents", indices = [Index("knowledgeBaseId"), Index("workspaceId")])
data class Document(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val knowledgeBaseId: String,
    // Workspace System spec §16: documents added from a workspace default into it (separate
    // from knowledgeBaseId, which groups documents for retrieval — a workspace can span
    // multiple knowledge bases).
    val workspaceId: String = Workspace.DEFAULT_WORKSPACE_ID,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val status: DocumentStatus = DocumentStatus.READING,
    val failureReason: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    // Spec §40.27 — text came from on-device OCR (scanned PDF), not a native text layer.
    val ocrApplied: Boolean = false,
    // SHA-256 of the source file — lets a re-import of the same-named file detect whether the
    // content actually changed (Phase 3, spec §20) instead of always treating it as a fresh copy.
    val contentHash: String? = null
)
