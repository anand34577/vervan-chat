package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "knowledge_bases")
data class KnowledgeBase(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // display customization and default-context wiring. Chunking profile
    // deliberately not added yet (lowest-value field of this group per the plan).
    val icon: String = "MenuBook",
    val color: String? = null,
    val defaultPersonaId: String? = null,
    val defaultProjectId: String? = null,
    val autoIndex: Boolean = true
) {
    companion object {
        // Well-known id for the single shared "Scans" KB that DocumentScannerScreen/
        // OcrScannerScreen save into — a stable id (not a fresh UUID.randomUUID() per save) is
        // what lets a second save find and reuse the same row instead of creating a duplicate.
        const val SCANS_KNOWLEDGE_BASE_ID = "builtin-scans"
    }
}
