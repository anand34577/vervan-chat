package com.vervan.chat.model

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ponytail: one runnable check per non-trivial extraction path — regex table parsing is exactly
 * the kind of logic that silently rots (a Jsoup upgrade, a regex edge case) without a test. */
class TextExtractorTableTest {

    @Test
    fun `html table survives extraction as a markdown pipe table`() {
        val html = """
            <html><body>
            <p>Intro paragraph.</p>
            <table><tr><th>Name</th><th>Qty</th></tr><tr><td>Widget</td><td>3</td></tr></table>
            </body></html>
        """.trimIndent()
        val file = File.createTempFile("tbl", ".html").apply { writeText(html) }
        val result = TextExtractor.extract(file, file.name)
        check(result is ExtractResult.Text)
        assertTrue("expected a pipe-table row", result.content.contains("| Name | Qty |"))
        assertTrue("expected a separator row", result.content.contains("| --- | --- |"))
        assertTrue("expected the data row", result.content.contains("| Widget | 3 |"))
        assertTrue("prose outside the table must survive too", result.content.contains("Intro paragraph."))
    }

    @Test
    fun `html table with no other tagged content is no longer dropped entirely`() {
        val html = "<html><body><table><tr><td>Only</td><td>Cell</td></tr></table></body></html>"
        val file = File.createTempFile("tbl", ".html").apply { writeText(html) }
        val result = TextExtractor.extract(file, file.name)
        check(result is ExtractResult.Text)
        assertTrue(result.content.contains("Only"))
        assertTrue(result.content.contains("Cell"))
    }

    @Test
    fun `docx table columns survive as a markdown pipe table alongside surrounding prose`() {
        val documentXml = """
            <w:document xmlns:w="ns"><w:body>
            <w:p><w:r><w:t>Before the table.</w:t></w:r></w:p>
            <w:tbl>
              <w:tr><w:tc><w:p><w:r><w:t>Name</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Qty</w:t></w:r></w:p></w:tc></w:tr>
              <w:tr><w:tc><w:p><w:r><w:t>Widget</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>3</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl>
            <w:p><w:r><w:t>After the table.</w:t></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        val file = File.createTempFile("tbl", ".docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        val result = TextExtractor.extract(file, file.name)
        check(result is ExtractResult.Text)
        assertTrue(result.content.contains("Before the table."))
        assertTrue(result.content.contains("| Name | Qty |"))
        assertTrue(result.content.contains("| --- | --- |"))
        assertTrue(result.content.contains("| Widget | 3 |"))
        assertTrue(result.content.contains("After the table."))
    }
}
