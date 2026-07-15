package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@Entity(tableName = "chunks")
data class Chunk(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val knowledgeBaseId: String,
    val sectionPath: String,
    val text: String,
    val tokenCount: Int,
    // Null until an embedding model is active and has processed this chunk — chunks
    // stay keyword-searchable in the meantime.
    val embedding: ByteArray? = null
) {
    override fun equals(other: Any?) = other is Chunk && id == other.id
    override fun hashCode() = id.hashCode()
}

fun FloatArray.toBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}
