package com.vervan.chat.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vervan.chat.data.db.dao.ChunkDao
import com.vervan.chat.data.db.dao.DocumentDao
import com.vervan.chat.data.db.dao.JobDao
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.toBytes
import com.vervan.chat.retrieval.EmbeddingEngine
import com.vervan.chat.system.NotificationHelper
import com.vervan.chat.system.toUserMessage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of [DocumentImportManager.import] — a name+content conflict with an existing,
 * non-deleted document in the same knowledge base doesn't auto-overwrite;
 * the caller decides via [DocumentImportManager.resolveVersionConflict]. */
sealed class DocumentImportOutcome {
    data class Imported(val document: Document) : DocumentImportOutcome()
    data class Duplicate(val existing: Document) : DocumentImportOutcome()
    data class VersionConflict(val existing: Document, val tempFilePath: String, val mimeType: String, val newHash: String) : DocumentImportOutcome()
}

/**
 * Ingestion pipeline: copy -> extract -> chunk -> embed (if an embedding model is loaded)
 * -> persist. Runs to completion or leaves the document row marked FAILED/UNSUPPORTED —
 * never half-committed (chunks are only inserted after all of them are ready).
 */
class DocumentImportManager(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val embeddingEngine: EmbeddingEngine,
    private val modelDao: ModelDao,
    private val jobDao: JobDao? = null
) {
    private val docsDir: File
        get() = File(context.filesDir, "documents").apply { mkdirs() }

    suspend fun import(kbId: String, uri: Uri): DocumentImportOutcome = withContext(Dispatchers.IO) {
        val name = safeFileName(queryDisplayName(uri) ?: uri.lastPathSegment ?: "document")
        val sourceSize = queryFileSize(uri)
        val freeBytes = docsDir.usableSpace
        if (sourceSize != null && freeBytes < sourceSize + STORAGE_SAFETY_MARGIN_BYTES) {
            return@withContext DocumentImportOutcome.Imported(
                Document(
                    knowledgeBaseId = kbId, displayName = name, filePath = "", mimeType = "",
                    status = DocumentStatus.FAILED, failureReason = "Not enough storage on device"
                ).also { documentDao.upsert(it) }
            )
        }

        val dest = File(docsDir, "${System.currentTimeMillis()}_$name")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            } ?: return@withContext DocumentImportOutcome.Imported(
                Document(
                    knowledgeBaseId = kbId, displayName = name, filePath = "", mimeType = "",
                    status = DocumentStatus.FAILED, failureReason = "Could not open selected file"
                ).also { documentDao.upsert(it) }
            )
        } catch (e: java.io.IOException) {
            dest.delete()
            return@withContext DocumentImportOutcome.Imported(
                Document(
                    knowledgeBaseId = kbId, displayName = name, filePath = "", mimeType = "",
                    status = DocumentStatus.FAILED, failureReason = "Ran out of storage while copying"
                ).also { documentDao.upsert(it) }
            )
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        val existing = documentDao.findActiveByNameInKb(kbId, name)
        if (existing != null) {
            if (existing.contentHash == hash) {
                dest.delete()
                return@withContext DocumentImportOutcome.Duplicate(existing)
            }
            return@withContext DocumentImportOutcome.VersionConflict(existing, dest.absolutePath, mimeType, hash)
        }

        val job = jobDao?.let { dao ->
            val record = JobRecord(type = JobType.DOCUMENT_INDEXING, label = name, state = JobState.RUNNING)
            dao.upsert(record)
            record
        }
        val document = processLocalFile(kbId, name, dest, mimeType, hash, job?.id)
        reportJobOutcome(job, document, name)
        DocumentImportOutcome.Imported(document)
    }

    /** Imports raw text directly — used to bring a note or a chat export
     * into a knowledge base without a source file/Uri, reusing the same extract-free tail of
     * the normal pipeline (chunk -> embed -> persist). Written to a real .txt file so the
     * document viewer and re-index still work on it like any other import. */
    suspend fun importRawText(kbId: String, name: String, content: String): Document = withContext(Dispatchers.IO) {
        val safeName = safeFileName(name)
        val dest = File(docsDir, "${System.currentTimeMillis()}_$safeName.txt")
        dest.writeText(content)
        val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
        processLocalFile(kbId, name, dest, "text/plain", hash)
    }

    /** Caller's decision on a [DocumentImportOutcome.VersionConflict]: [replace] discards the
     * old document (file + chunks) first, otherwise the re-import is kept alongside it as a
     * second document with the same display name. */
    suspend fun resolveVersionConflict(existing: Document, tempFilePath: String, mimeType: String, hash: String, replace: Boolean): Document =
        withContext(Dispatchers.IO) {
            if (replace) {
                File(existing.filePath).delete()
                chunkDao.deleteForDocument(existing.id)
                documentDao.delete(existing)
            }
            val job = jobDao?.let { dao ->
                val record = JobRecord(type = JobType.DOCUMENT_INDEXING, label = existing.displayName, state = JobState.RUNNING)
                dao.upsert(record)
                record
            }
            val document = processLocalFile(existing.knowledgeBaseId, existing.displayName, File(tempFilePath), mimeType, hash, job?.id)
            reportJobOutcome(job, document, existing.displayName)
            document
        }

    private suspend fun reportJobOutcome(job: JobRecord?, result: Document, name: String) {
        if (job == null) return
        if (jobDao?.get(job.id)?.state == JobState.CANCELLED) return
        val finalState = when (result.status) {
            DocumentStatus.READY -> JobState.COMPLETED
            DocumentStatus.UNSUPPORTED, DocumentStatus.FAILED -> JobState.FAILED
            else -> JobState.COMPLETED
        }
        jobDao?.upsert(job.copy(state = finalState, updatedAt = System.currentTimeMillis(), detail = result.status.name))
        // Post a notification for completion or failure — only meaningful
        // if the app is backgrounded, but cheap to post regardless.
        if (finalState == JobState.COMPLETED) {
            NotificationHelper.post(context, job.id.hashCode(), "Import complete", "\"$name\" is ready to search.")
        } else {
            NotificationHelper.post(context, job.id.hashCode(), "Import failed", "\"$name\": ${result.failureReason ?: result.status.name}", NotificationHelper.CHANNEL_IMPORT)
        }
    }

    private suspend fun processLocalFile(kbId: String, name: String, dest: File, mimeType: String, hash: String, jobId: String? = null): Document {
        var document = Document(
            knowledgeBaseId = kbId,
            displayName = name,
            filePath = dest.absolutePath,
            mimeType = mimeType,
            status = DocumentStatus.EXTRACTING,
            contentHash = hash
        )
        documentDao.upsert(document)

        // The whole extract/OCR/chunk/embed sequence used to have no outer safety net: an
        // OutOfMemoryError from OCR-ing a large scanned PDF, or from PDFBox/POI loading a huge
        // document, propagated straight out of import()/reindexLocal() (crashing the app) AND
        // left the document row permanently stuck at whatever intermediate status
        // (EXTRACTING/OCR_RUNNING/CHUNKING/EMBEDDING) it last had, with no retry path. Now any
        // failure here — including an Error, not just an Exception — is caught and recorded as
        // a normal FAILED status the user can see and re-attempt.
        return try {
            ensureJobActive(jobId)
            when (val extracted = TextExtractor.extract(dest, name)) {
                is ExtractResult.Unsupported -> {
                    document = document.copy(status = DocumentStatus.UNSUPPORTED, failureReason = extracted.reason)
                    documentDao.update(document)
                    document
                }
                ExtractResult.NeedsOcr -> {
                    ensureJobActive(jobId)
                    document = document.copy(status = DocumentStatus.OCR_RUNNING, failureReason = null)
                    documentDao.update(document)
                    val ocrText = try { runOcr(name, dest) } catch (t: Throwable) { "" }
                    document = if (ocrText.isBlank()) {
                        document.copy(status = DocumentStatus.FAILED, failureReason = "OCR found no readable text")
                    } else {
                        persistChunks(document, kbId, Chunker.chunk(ocrText, tokenCounter = tokenCounter()), ocrApplied = true, jobId = jobId)
                    }
                    documentDao.update(document)
                    document
                }
                else -> {
                    document = persistChunks(document, kbId, chunksFor(extracted), ocrApplied = false, jobId = jobId)
                    documentDao.update(document)
                    document
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            val failed = document.copy(status = DocumentStatus.FAILED, failureReason = "Indexing stopped")
            documentDao.update(failed)
            failed
        } catch (t: Throwable) {
            val failed = document.copy(status = DocumentStatus.FAILED, failureReason = t.toUserMessage())
            documentDao.update(failed)
            failed
        }
    }

    private fun runOcr(name: String, file: File): String =
        if (name.substringAfterLast('.', "").lowercase() == "pdf") OcrExtractor.extractFromPdf(file) else OcrExtractor.extractFromImage(file)

    /** Dispatches an already-normalized [ExtractResult] to the chunker that matches its shape —
     * flowing prose through the paragraph chunker, spreadsheets through the row-group chunker
     * (header repeated per chunk), slide decks through the per-slide chunker. Every format
     * TextExtractor supports ends up here as one of these three shapes; the rest of the
     * pipeline (embed/store) never needs to know which source format it came from. */
    private fun chunksFor(extracted: ExtractResult): List<RawChunk> = when (extracted) {
        is ExtractResult.Text -> Chunker.chunk(extracted.content, tokenCounter = tokenCounter())
        is ExtractResult.Tabular -> Chunker.chunkTable(extracted.sheets, tokenCounter())
        is ExtractResult.Slides -> Chunker.chunkSlides(extracted.slides, tokenCounter())
        ExtractResult.NeedsOcr, is ExtractResult.Unsupported -> emptyList()
    }

    /** Real SentencePiece token counts when an embedding model is loaded (matching what will
     * actually encode these chunks), a word-count proxy otherwise — chunking still has to work
     * before/without an embedding model, just less precisely sized. */
    private fun tokenCounter(): (String) -> Int = { text ->
        (if (embeddingEngine.isLoaded) embeddingEngine.countTokens(text) else null) ?: wordProxyCount(text)
    }

    private fun wordProxyCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Chunks, embeds, and stores [raw] for [document], returning its final status. Shared by
     * every extraction shape (direct text, OCR-recovered text, tables, slides). */
    private suspend fun persistChunks(document: Document, kbId: String, raw: List<RawChunk>, ocrApplied: Boolean, jobId: String? = null): Document {
        ensureJobActive(jobId)
        var doc = document.copy(status = DocumentStatus.CHUNKING, ocrApplied = ocrApplied)
        documentDao.update(doc)
        if (raw.isEmpty()) {
            return doc.copy(status = DocumentStatus.FAILED, failureReason = "Document contains no readable text")
        }

        doc = doc.copy(status = DocumentStatus.EMBEDDING)
        documentDao.update(doc)
        ensureJobActive(jobId)
        // Looked up once per document, not per chunk — the active embedding model can't change
        // mid-import, and this is what stamps every chunk's embeddingModelId so a later model
        // switch can be told apart from "still current" (see Chunk.embeddingModelId).
        val activeEmbeddingModelId = if (embeddingEngine.isLoaded) modelDao.getActiveModel(ModelRole.EMBEDDING)?.id else null
        var embedFailures = 0
        val chunks = raw.map { rc ->
            val embedding = if (embeddingEngine.isLoaded) embeddingEngine.embed(rc.text, title = rc.sectionPath)?.toBytes() else null
            if (embeddingEngine.isLoaded && embedding == null) embedFailures++
            Chunk(
                documentId = doc.id,
                knowledgeBaseId = kbId,
                sectionPath = rc.sectionPath,
                text = rc.text,
                tokenCount = rc.tokenCount,
                embedding = embedding,
                embeddingModelId = if (embedding != null) activeEmbeddingModelId else null
            )
        }
        ensureJobActive(jobId)
        chunkDao.insertAll(chunks)

        // B7: embedding failures used to be silently swallowed — surface a non-fatal warning
        // (document still imports and stays keyword-searchable) instead of masking it.
        val warning = if (embedFailures > 0) {
            "$embedFailures of ${chunks.size} chunks couldn't be embedded and are keyword-only searchable"
        } else null
        return doc.copy(status = DocumentStatus.READY, failureReason = warning)
    }

    /** Re-indexes an already-imported document from its stored file (index repair /
     * embedding-model-changed re-index). Deletes old chunks, re-extracts and re-embeds. */
    suspend fun reindexLocal(documentId: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.get(documentId) ?: return@withContext
        // B12: INDEX_REBUILD is another job type the Job Queue promised but never created.
        val job = jobDao?.let { dao ->
            val record = JobRecord(type = JobType.INDEX_REBUILD, label = doc.displayName, state = JobState.RUNNING)
            dao.upsert(record)
            record
        }
        val file = File(doc.filePath)
        if (!file.exists()) {
            documentDao.update(doc.copy(status = DocumentStatus.FAILED, failureReason = "Source file no longer available"))
            job?.let { jobDao?.upsert(it.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = "Source file no longer available")) }
            return@withContext
        }
        chunkDao.deleteForDocument(documentId)
        // Same outer safety net as processLocalFile — without it, a re-index failure (OOM on a
        // large document, a corrupt source file) left the row stuck at CHUNKING/EMBEDDING
        // forever and could crash the app instead of reporting FAILED.
        try {
            when (val extracted = TextExtractor.extract(file, doc.displayName)) {
                is ExtractResult.Unsupported -> documentDao.update(doc.copy(status = DocumentStatus.UNSUPPORTED, failureReason = extracted.reason))
                ExtractResult.NeedsOcr -> {
                    val ocrText = try { runOcr(doc.displayName, file) } catch (t: Throwable) { "" }
                    if (ocrText.isBlank()) {
                        documentDao.update(doc.copy(status = DocumentStatus.FAILED, failureReason = "OCR found no readable text"))
                    } else {
                        documentDao.update(persistChunks(doc.copy(failureReason = null), doc.knowledgeBaseId, Chunker.chunk(ocrText, tokenCounter = tokenCounter()), ocrApplied = true, jobId = job?.id))
                    }
                }
                else -> {
                    documentDao.update(persistChunks(doc.copy(failureReason = null), doc.knowledgeBaseId, chunksFor(extracted), ocrApplied = false, jobId = job?.id))
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            documentDao.update(doc.copy(status = DocumentStatus.FAILED, failureReason = "Indexing stopped"))
        } catch (t: Throwable) {
            documentDao.update(doc.copy(status = DocumentStatus.FAILED, failureReason = t.toUserMessage()))
        }
        job?.let {
            if (jobDao?.get(it.id)?.state == JobState.CANCELLED) return@let
            val finalStatus = documentDao.get(documentId)?.status
            val state = if (finalStatus == DocumentStatus.READY) JobState.COMPLETED else JobState.FAILED
            jobDao?.upsert(it.copy(state = state, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun ensureJobActive(jobId: String?) {
        if (jobId != null && jobDao?.get(jobId)?.state == JobState.CANCELLED) {
            throw kotlinx.coroutines.CancellationException("Stopped by user")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return null
    }

    private fun queryFileSize(uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) return cursor.getLong(sizeIndex)
        }
        return null
    }

    private fun safeFileName(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .ifBlank { "document" }

    /** Recycle-bin delete — marks the row, keeps the file and chunks so restore is cheap. */
    suspend fun softDelete(document: Document) = withContext(Dispatchers.IO) {
        documentDao.update(document.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun restore(document: Document) = withContext(Dispatchers.IO) {
        documentDao.update(document.copy(deletedAt = null))
    }

    /** Permanent delete — used once a document is already in the recycle bin (or purged
     * automatically after the retention window). */
    suspend fun delete(document: Document) = withContext(Dispatchers.IO) {
        com.vervan.chat.data.SecureDelete.overwriteAndDelete(File(document.filePath))
        chunkDao.deleteForDocument(document.id)
        documentDao.delete(document)
    }

    companion object {
        private const val STORAGE_SAFETY_MARGIN_BYTES = 100L * 1024 * 1024 // 100MB headroom
    }
}
