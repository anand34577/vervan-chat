package com.vervan.chat.model

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * One chat turn to render — [label] is "User"/"Assistant"/"Tool result" (bold), [text] its
 * content (plain, already Markdown-stripped by the caller — this writer has no Markdown
 * renderer, just wrapped monospace-free body text).
 */
data class PdfTranscriptEntry(val label: String, val text: String)

/**
 * Minimal PDF writer for chat transcripts. PDFBox-android (unlike desktop PDFBox's separate
 * text-layout modules) has no line-wrapping or pagination engine built in — both are hand-rolled
 * here: greedy word-wrap against the font's own advance widths, and a new page started whenever
 * the next line would fall past the bottom margin. Good enough for a transcript (short runs of
 * plain text, no tables/images); not a general document renderer.
 */
object ChatPdfExporter {
    private val PAGE_SIZE = PDRectangle.LETTER
    private const val MARGIN = 50f
    private const val BODY_SIZE = 11f
    private const val LABEL_SIZE = 11f
    private const val TITLE_SIZE = 16f
    private const val LEADING = 15f
    private val BODY_FONT = PDType1Font.HELVETICA
    private val BOLD_FONT = PDType1Font.HELVETICA_BOLD

    fun write(file: File, title: String, subtitle: String, entries: List<PdfTranscriptEntry>) {
        PDDocument().use { document ->
            val writer = PageWriter(document)
            writer.writeLine(title, BOLD_FONT, TITLE_SIZE)
            writer.writeLine(subtitle, BODY_FONT, BODY_SIZE * 0.85f, gray = true)
            writer.blankLine()
            entries.forEach { entry ->
                writer.writeLine("${entry.label}:", BOLD_FONT, LABEL_SIZE)
                entry.text.lineSequence().forEach { paragraph ->
                    if (paragraph.isBlank()) writer.blankLine() else writer.writeWrapped(paragraph, BODY_FONT, BODY_SIZE)
                }
                writer.blankLine()
            }
            writer.finish()
            document.save(file)
        }
    }

    /** Owns the current page/content-stream and cursor position; starts a new page transparently
     * whenever a line won't fit. Not thread-safe / reusable — one per [write] call. */
    private class PageWriter(private val document: PDDocument) {
        private val contentWidth = PAGE_SIZE.width - 2 * MARGIN
        private var page: PDPage = newPage()
        private var stream: PDPageContentStream = newStream(page)
        private var y = PAGE_SIZE.height - MARGIN

        private fun newPage(): PDPage = PDPage(PAGE_SIZE).also { document.addPage(it) }
        private fun newStream(p: PDPage) = PDPageContentStream(document, p)

        private fun ensureRoom() {
            if (y < MARGIN + LEADING) {
                stream.close()
                page = newPage()
                stream = newStream(page)
                y = PAGE_SIZE.height - MARGIN
            }
        }

        fun blankLine() {
            y -= LEADING * 0.6f
        }

        fun writeLine(text: String, font: PDType1Font, size: Float, gray: Boolean = false) {
            if (text.isBlank()) return
            ensureRoom()
            stream.beginText()
            stream.setFont(font, size)
            if (gray) stream.setNonStrokingColor(0.45f, 0.45f, 0.45f) else stream.setNonStrokingColor(0f, 0f, 0f)
            stream.newLineAtOffset(MARGIN, y)
            stream.showText(sanitize(text))
            stream.endText()
            y -= LEADING * (size / BODY_SIZE)
        }

        /** Greedy word-wrap against [font]'s own advance widths, one output line per call to
         * [writeLine] — PDFBox throws on characters outside WinAnsiEncoding (e.g. curly quotes,
         * em dashes from Markdown), so [sanitize] runs first. */
        fun writeWrapped(text: String, font: PDType1Font, size: Float) {
            val words = sanitize(text).split(' ')
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                val width = font.getStringWidth(candidate) / 1000f * size
                if (width > contentWidth && line.isNotEmpty()) {
                    writeLine(line.toString(), font, size)
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) writeLine(line.toString(), font, size)
        }

        fun finish() {
            stream.close()
        }

        /** Standard14 fonts only cover WinAnsiEncoding — swap the common Markdown/typographic
         * characters PDFBox would otherwise throw IllegalArgumentException on for plain ASCII. */
        private fun sanitize(text: String): String = buildString(text.length) {
            for (c in text) append(
                when (c) {
                    '‘', '’' -> '\''
                    '“', '”' -> '"'
                    '–', '—' -> '-'
                    '…' -> '.'
                    '\t' -> ' '
                    else -> if (c.code in 32..126 || c.code in 160..255) c else '?'
                }
            )
        }
    }
}
