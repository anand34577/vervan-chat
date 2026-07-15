package com.vervan.chat.model

data class RawChunk(val sectionPath: String, val text: String, val tokenCount: Int)

/** A single sheet's worth of extracted table data — used by [Chunker.chunkTable] for
 * spreadsheet-shaped sources (XLSX, legacy XLS, CSV). [header], if present, is repeated into
 * every chunk produced from [rows] so a chunk read on its own still carries column context. */
data class TableSheet(val name: String, val header: List<String>?, val rows: List<List<String>>)

/** One slide's worth of extracted text — used by [Chunker.chunkSlides] for PPTX. */
data class SlideText(val index: Int, val body: String, val notes: String?)

/**
 * Splits extracted document text into retrieval-sized chunks.
 *
 * Default token counting is a word-count proxy (no tokenizer instance is available here —
 * Chunker has no model context). Callers that own a loaded embedding model's SentencePiece
 * tokenizer (see [com.vervan.chat.retrieval.EmbeddingEngine.countTokens]) should pass a real
 * counter instead, so the ~300-500 token target actually means what it says for the model
 * that will embed these chunks.
 */
object Chunker {
    const val TARGET_TOKENS = 400
    private const val MIN_TOKENS = 40
    // ~12% of target carried into the next chunk so a passage that straddles a chunk boundary
    // still appears whole in at least one chunk, instead of being split with no shared context.
    private const val OVERLAP_FRACTION = 0.12

    fun chunk(text: String, sectionPrefix: String = "", tokenCounter: (String) -> Int = ::wordProxyCount): List<RawChunk> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<RawChunk>()
        var currentSection = sectionPrefix
        var buffer = mutableListOf<String>()
        var bufferTokens = 0

        fun flush(carryOverlap: Boolean) {
            if (buffer.isEmpty()) return
            val fullText = buffer.joinToString("\n\n")
            chunks += RawChunk(currentSection, fullText, tokenCounter(fullText))
            if (carryOverlap) {
                val words = fullText.split(Regex("\\s+")).filter { it.isNotBlank() }
                val overlapWordCount = (words.size * OVERLAP_FRACTION).toInt()
                val overlapText = if (overlapWordCount > 0) words.takeLast(overlapWordCount).joinToString(" ") else ""
                buffer = if (overlapText.isNotBlank()) mutableListOf(overlapText) else mutableListOf()
                bufferTokens = if (overlapText.isNotBlank()) tokenCounter(overlapText) else 0
            } else {
                buffer = mutableListOf()
                bufferTokens = 0
            }
        }

        for (p in paragraphs) {
            val heading = Regex("^#{1,6}\\s+(.*)").find(p)
            if (heading != null) {
                // A heading is a real topic break — start the next chunk clean, not seeded
                // with overlap from the previous (now-unrelated) section.
                flush(carryOverlap = false)
                currentSection = heading.groupValues[1].trim()
                continue
            }
            val pTokens = tokenCounter(p)
            if (bufferTokens + pTokens > TARGET_TOKENS && bufferTokens >= MIN_TOKENS) flush(carryOverlap = true)
            buffer += p
            bufferTokens += pTokens
        }
        flush(carryOverlap = false)
        return chunks.ifEmpty { listOf(RawChunk(sectionPrefix, text.trim(), tokenCounter(text.trim()))) }
    }

    /** Row-group chunking for spreadsheet-shaped data: packs rows up to [TARGET_TOKENS],
     * repeating [TableSheet.header] at the top of every chunk (a raw flattened table loses
     * its column meaning without it) and labeling each chunk with its sheet name + row range
     * so a citation can point back to roughly where in the sheet an answer came from. */
    fun chunkTable(sheets: List<TableSheet>, tokenCounter: (String) -> Int = ::wordProxyCount): List<RawChunk> {
        val chunks = mutableListOf<RawChunk>()
        for (sheet in sheets) {
            if (sheet.rows.isEmpty()) continue
            val headerLine = sheet.header?.joinToString("\t")?.takeIf { it.isNotBlank() }
            val headerTokens = headerLine?.let(tokenCounter) ?: 0
            var buffer = mutableListOf<String>()
            var bufferTokens = headerTokens
            var startRow = 1

            fun flush(endRow: Int) {
                if (buffer.isEmpty()) return
                val text = (if (headerLine != null) "$headerLine\n" else "") + buffer.joinToString("\n")
                chunks += RawChunk("${sheet.name} · rows $startRow-$endRow", text, tokenCounter(text))
                buffer = mutableListOf()
                bufferTokens = headerTokens
            }

            sheet.rows.forEachIndexed { idx, row ->
                val line = row.joinToString("\t")
                val lineTokens = tokenCounter(line)
                if (bufferTokens + lineTokens > TARGET_TOKENS && buffer.isNotEmpty()) {
                    flush(idx)
                    startRow = idx + 1
                }
                buffer += line
                bufferTokens += lineTokens
            }
            flush(sheet.rows.size)
        }
        return chunks
    }

    /** One chunk per slide (title + body together), plus a separate chunk for speaker notes
     * when present — notes are usually presenter context, not on-slide content, so citing them
     * back as a distinct "Slide N notes" section avoids implying it was visible on the slide. */
    fun chunkSlides(slides: List<SlideText>, tokenCounter: (String) -> Int = ::wordProxyCount): List<RawChunk> {
        val chunks = mutableListOf<RawChunk>()
        for (slide in slides) {
            if (slide.body.isNotBlank()) {
                chunks += RawChunk("Slide ${slide.index}", slide.body, tokenCounter(slide.body))
            }
            if (!slide.notes.isNullOrBlank()) {
                chunks += RawChunk("Slide ${slide.index} notes", slide.notes, tokenCounter(slide.notes))
            }
        }
        return chunks
    }

    private fun wordProxyCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }
}
