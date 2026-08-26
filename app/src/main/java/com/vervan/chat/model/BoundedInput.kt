package com.vervan.chat.model

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class InputLimitExceededException(message: String) : IOException(message)

/** Accounts for several individually-bounded parts that share one extraction budget. */
internal class AggregateTextLimit(
    private val maxChars: Int,
    private val label: String
) {
    private var consumed = 0L

    fun account(text: String): String {
        consumed += text.length.toLong()
        if (consumed > maxChars.toLong()) {
            throw InputLimitExceededException("$label parts exceed the extraction limit")
        }
        return text
    }
}

object ImportLimits {
    const val MAX_DOCUMENT_SOURCE_BYTES = 256L * 1024 * 1024
    const val MAX_EXTRACTED_CHARS = 16 * 1024 * 1024
    const val MAX_ARCHIVE_ENTRY_BYTES = 32L * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 4_096
    const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
    const val MAX_CHARACTER_CARD_BYTES = 16L * 1024 * 1024
    const val MAX_IMAGE_SOURCE_BYTES = 64L * 1024 * 1024
    const val MAX_TOKENIZER_BYTES = 64L * 1024 * 1024
    const val MAX_PRIMARY_MODEL_BYTES = 16L * 1024 * 1024 * 1024
}

fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
    require(maxBytes in 1..Int.MAX_VALUE.toLong())
    val output = ByteArrayOutputStream(minOf(maxBytes.toInt(), DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw InputLimitExceededException("Input exceeds ${maxBytes / (1024 * 1024)} MB")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/** Copies an external stream while enforcing the limit as bytes arrive. */
fun InputStream.copyToLimited(out: java.io.OutputStream, maxBytes: Long, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    require(maxBytes > 0)
    require(bufferSize > 0)
    val buffer = ByteArray(bufferSize)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw InputLimitExceededException("Input exceeds ${maxBytes / (1024 * 1024)} MB")
        }
        out.write(buffer, 0, read)
    }
    return total
}

fun Reader.readTextLimited(maxChars: Int): String {
    require(maxChars > 0)
    val output = StringBuilder(minOf(maxChars, DEFAULT_BUFFER_SIZE))
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.length + read > maxChars) {
            throw InputLimitExceededException("Extracted text exceeds ${maxChars / (1024 * 1024)} million characters")
        }
        output.append(buffer, 0, read)
    }
    return output.toString()
}

fun File.readTextLimited(maxBytes: Long, maxChars: Int = ImportLimits.MAX_EXTRACTED_CHARS): String {
    if (length() > maxBytes) throw InputLimitExceededException("File exceeds ${maxBytes / (1024 * 1024)} MB")
    return bufferedReader().use { it.readTextLimited(maxChars) }
}

fun ZipFile.checkedEntries(maxEntries: Int = ImportLimits.MAX_ARCHIVE_ENTRIES): List<ZipEntry> {
    val result = entries().asSequence().take(maxEntries + 1).toList()
    if (result.size > maxEntries) throw InputLimitExceededException("Archive contains too many entries")
    return result
}

fun ZipFile.readEntryTextLimited(
    entry: ZipEntry,
    maxBytes: Long = ImportLimits.MAX_ARCHIVE_ENTRY_BYTES,
    maxChars: Int = ImportLimits.MAX_EXTRACTED_CHARS
): String {
    if (entry.size > maxBytes) throw InputLimitExceededException("Archive entry ${entry.name} is too large")
    if (entry.size > 1024 * 1024 && entry.compressedSize > 0 && entry.size / entry.compressedSize > 100) {
        throw InputLimitExceededException("Archive entry ${entry.name} has an unsafe compression ratio")
    }
    return getInputStream(entry).bufferedReader().use { it.readTextLimited(maxChars) }
}
