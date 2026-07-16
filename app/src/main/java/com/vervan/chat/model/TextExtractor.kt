package com.vervan.chat.model

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

sealed class ExtractResult {
    data class Text(val content: String) : ExtractResult()
    /** Spreadsheet-shaped content (XLSX, legacy XLS, CSV) — chunked via [Chunker.chunkTable]
     * instead of the generic paragraph chunker, so rows stay row-grouped with their header. */
    data class Tabular(val sheets: List<TableSheet>) : ExtractResult()
    /** Slide deck content (PPTX) — chunked via [Chunker.chunkSlides], one chunk per slide. */
    data class Slides(val slides: List<SlideText>) : ExtractResult()
    object NeedsOcr : ExtractResult()
    data class Unsupported(val reason: String) : ExtractResult()
}

/**
 * Normalizes every supported document format down to plain text, table rows, or slide text —
 * the RAG pipeline downstream (chunk -> embed -> store) never needs to know what the source
 * format was. Text-like formats read directly, PDF via pdfbox-android (falling back to OCR for
 * scanned pages), HTML/EPUB via Jsoup, DOCX/XLSX/PPTX via this app's own zip/XML parsing
 * (deliberately not Apache POI's OOXML modules — see build.gradle.kts for why), and legacy
 * binary DOC/XLS via Apache POI's `poi`/`poi-scratchpad` (safe on Android: pure OLE2, no
 * XML/AWT dependency, unlike poi-ooxml).
 */
object TextExtractor {
    fun extract(file: File, displayName: String): ExtractResult {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in PLAIN_TEXT_EXTENSIONS -> ExtractResult.Text(file.readText())
            "csv" -> extractCsv(file)
            "pdf" -> extractPdf(file)
            "html", "htm" -> extractHtml(file.readText())
            "docx" -> extractDocx(file)
            "xlsx" -> extractXlsx(file)
            "pptx" -> extractPptx(file)
            "epub" -> extractEpub(file)
            "doc" -> extractDoc(file)
            "xls" -> extractXls(file)
            // Images route through the OCR path (Phase 3, spec §17) — same NeedsOcr signal
            // DocumentImportManager already handles for scanned PDFs, but resolved via
            // OcrExtractor.extractFromImage instead of PDF page rendering.
            in IMAGE_EXTENSIONS -> ExtractResult.NeedsOcr
            else -> ExtractResult.Unsupported("Unsupported file type: .$ext")
        }
    }

    private fun extractPdf(file: File): ExtractResult {
        return try {
            PDDocument.load(file).use { doc ->
                val text = PDFTextStripper().getText(doc)
                if (text.isBlank()) ExtractResult.NeedsOcr else ExtractResult.Text(text)
            }
        } catch (e: Exception) {
            ExtractResult.Unsupported("Could not read PDF: ${e.message}")
        }
    }

    private fun extractDocx(file: File): ExtractResult = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: return ExtractResult.Unsupported("DOCX has no word/document.xml")
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                .replace("</w:p>", "</w:p>\n")
                .replace("<w:tab/>", "\t")
            ExtractResult.Text(Jsoup.parse(xml).text())
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read DOCX: ${e.message}")
    }

    /** Legacy binary Word (.doc) via Apache POI's HWPF reader — see build.gradle.kts for why
     * this format specifically gets a POI dependency instead of hand-rolled parsing: OLE2
     * Compound File binary parsing isn't a "few lines" job the way zip/XML formats are. */
    private fun extractDoc(file: File): ExtractResult = try {
        HWPFDocument(FileInputStream(file)).use { doc ->
            val text = WordExtractor(doc).text.trim()
            if (text.isBlank()) ExtractResult.Unsupported("DOC contains no readable text") else ExtractResult.Text(text)
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read DOC: ${e.message}")
    }

    /** XLSX is a zip of XML parts: shared strings are interned in one table (`t="s"` cells
     * hold an index into it, `t="inlineStr"`/numeric cells hold their value directly), and
     * each sheet is a separate `xl/worksheets/sheetN.xml`. Sheets are named positionally
     * ("Sheet 1", "Sheet 2"...) — resolving real sheet names needs following
     * xl/workbook.xml's relationships to each sheetN.xml, which isn't worth the extra
     * indirection just for a nicer citation label. */
    private fun extractXlsx(file: File): ExtractResult = try {
        ZipFile(file).use { zip ->
            val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
                    Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[1])
                        .joinToString("") { it.groupValues[1] }
                        .let { unescapeXml(it) }
                }.toList()
            } ?: emptyList()

            val sheetEntries = zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedBy { it.name }
                .toList()
            if (sheetEntries.isEmpty()) return@use ExtractResult.Unsupported("XLSX has no worksheet parts")

            val sheets = sheetEntries.mapIndexed { i, entry ->
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { rowMatch ->
                    Regex("<c[^>]*?(?:\\s+t=\"(\\w+)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rowMatch.groupValues[1]).map { cellMatch ->
                        val type = cellMatch.groupValues[1]
                        val valueMatch = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(cellMatch.groupValues[2])
                        val raw = valueMatch?.groupValues?.get(1).orEmpty()
                        when (type) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(cellMatch.groupValues[2])?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
                            else -> raw
                        }
                    }.toList()
                }.toList()
                TableSheet("Sheet ${i + 1}", rows.firstOrNull(), if (rows.size > 1) rows.drop(1) else rows)
            }
            if (sheets.all { it.rows.isEmpty() }) ExtractResult.Unsupported("XLSX contains no readable cell data") else ExtractResult.Tabular(sheets)
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read XLSX: ${e.message}")
    }

    /** Legacy binary Excel (.xls) via Apache POI's HSSF reader — same OLE2 rationale as
     * [extractDoc]. [DataFormatter] renders any cell type (string/numeric/formula/boolean) to
     * its displayed text the same way Excel would, so this doesn't need its own per-type
     * switch the way the hand-rolled XLSX parser does for raw XML cell values. */
    private fun extractXls(file: File): ExtractResult = try {
        HSSFWorkbook(FileInputStream(file)).use { wb ->
            val formatter = DataFormatter()
            val sheets = (0 until wb.numberOfSheets).map { i ->
                val sheet = wb.getSheetAt(i)
                val rows = sheet.map { row ->
                    (0 until row.lastCellNum.coerceAtLeast(0)).map { c -> row.getCell(c)?.let { formatter.formatCellValue(it) } ?: "" }
                }
                TableSheet(sheet.sheetName, rows.firstOrNull(), if (rows.size > 1) rows.drop(1) else rows)
            }
            if (sheets.all { it.rows.isEmpty() }) ExtractResult.Unsupported("XLS contains no readable cell data") else ExtractResult.Tabular(sheets)
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read XLS: ${e.message}")
    }

    /** PPTX is a zip of `ppt/slides/slideN.xml` (+ optional `ppt/notesSlides/notesSlideN.xml`
     * for speaker notes) — text runs live in DrawingML `<a:t>` tags inside each, read via
     * Jsoup's XML parser mode rather than hand-rolled regex/unescape. Not POI, for the same
     * StAX-avoidance reason as DOCX/XLSX. */
    private fun extractPptx(file: File): ExtractResult = try {
        ZipFile(file).use { zip ->
            val slideEntries = zip.entries().asSequence()
                .filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it.name) }
                .sortedBy { slideNumber(it.name) }
                .toList()
            if (slideEntries.isEmpty()) return@use ExtractResult.Unsupported("PPTX has no slide parts")
            val slides = slideEntries.map { entry ->
                val num = slideNumber(entry.name)
                val body = extractDrawingMlText(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                val notes = zip.getEntry("ppt/notesSlides/notesSlide$num.xml")?.let { ne ->
                    extractDrawingMlText(zip.getInputStream(ne).bufferedReader().use { it.readText() })
                }
                SlideText(num, body, notes?.takeIf { it.isNotBlank() })
            }
            if (slides.all { it.body.isBlank() && it.notes.isNullOrBlank() }) {
                ExtractResult.Unsupported("PPTX contains no readable text")
            } else {
                ExtractResult.Slides(slides)
            }
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read PPTX: ${e.message}")
    }

    private fun slideNumber(entryName: String): Int = Regex("(\\d+)\\.xml$").find(entryName)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun extractDrawingMlText(xml: String): String =
        Jsoup.parse(xml, "", Parser.xmlParser()).select("a|t").joinToString(" ") { it.text() }.trim()

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    private fun extractEpub(file: File): ExtractResult = try {
        ZipFile(file).use { zip ->
            val text = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "xhtml") }
                .sortedBy { it.name }
                .joinToString("\n\n") { entry ->
                    Jsoup.parse(zip.getInputStream(entry).bufferedReader().use { it.readText() }).text()
                }
            if (text.isBlank()) ExtractResult.Unsupported("EPUB contains no readable HTML") else ExtractResult.Text(text)
        }
    } catch (e: Exception) {
        ExtractResult.Unsupported("Could not read EPUB: ${e.message}")
    }

    /** Walks the DOM instead of a flat [Jsoup] `.text()` call so headings and list items keep
     * the markers [Chunker] already looks for (`# Heading`, `- item`) — a flat text() call
     * would collapse a page's whole structure into one undifferentiated blob. ponytail: selects
     * a fixed tag set (h1-h6/p/li/pre/blockquote) rather than a full block-model walk, so a
     * `<p>` nested inside a matched `<li>` (or similar unusual nesting) can double-count — rare
     * enough in real-world HTML exports not to be worth a proper DOM-block-model rewrite yet. */
    private fun extractHtml(html: String): ExtractResult {
        val doc = Jsoup.parse(html)
        val sb = StringBuilder()
        fun block(text: String) {
            if (text.isNotBlank()) sb.append(text.trim()).append("\n\n")
        }
        doc.body().select("h1,h2,h3,h4,h5,h6,p,li,pre,blockquote").forEach { el ->
            when (el.tagName()) {
                "h1" -> block("# ${el.text()}")
                "h2" -> block("## ${el.text()}")
                "h3" -> block("### ${el.text()}")
                "h4" -> block("#### ${el.text()}")
                "h5" -> block("##### ${el.text()}")
                "h6" -> block("###### ${el.text()}")
                "li" -> block("- ${el.text()}")
                else -> block(el.text())
            }
        }
        val text = sb.toString().trim()
        return if (text.isBlank()) ExtractResult.Unsupported("HTML contains no readable text") else ExtractResult.Text(text)
    }

    /** RFC4180-ish quoted-field parsing (embedded commas/newlines, escaped `""`) — enough for
     * real-world CSV exports without pulling in a dedicated CSV library for it. */
    private fun extractCsv(file: File): ExtractResult {
        val content = file.readText()
        if (content.isBlank()) return ExtractResult.Unsupported("CSV is empty")
        val rows = parseCsv(content)
        if (rows.isEmpty()) return ExtractResult.Unsupported("CSV is empty")
        return ExtractResult.Tabular(listOf(TableSheet(file.nameWithoutExtension, rows.firstOrNull(), if (rows.size > 1) rows.drop(1) else rows)))
    }

    private fun parseCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes && c == '"' && i + 1 < content.length && content[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { fields.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    fields.add(field.toString())
                    field.clear()
                    if (fields.size > 1 || fields[0].isNotEmpty()) rows.add(fields)
                    fields = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            rows.add(fields)
        }
        return rows
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")

    private val PLAIN_TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "xml", "yaml", "yml", "log",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp",
        "cs", "go", "rs", "swift", "sql", "sh", "ps1", "gradle", "properties"
    )
}
