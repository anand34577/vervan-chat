package com.vervan.chat.model

import java.io.File
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.junit.Assert.assertTrue
import org.junit.Test

/** ponytail: one runnable check for the legacy .ppt path — it was silently falling through to
 * "Unsupported file type" (the file pickers already advertised application/vnd.ms-powerpoint,
 * but TextExtractor never had a case for the extension) until this fix. */
class TextExtractorPptTest {

    @Test
    fun `legacy ppt slide text is extracted`() {
        val file = File.createTempFile("legacy", ".ppt")
        HSLFSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val textbox = slide.createTextBox()
            textbox.text = "Quarterly results"
            file.outputStream().use { ppt.write(it) }
        }

        val result = TextExtractor.extract(file, file.name)
        check(result is ExtractResult.Slides) { "expected Slides, got $result" }
        assertTrue(result.slides.any { it.body.contains("Quarterly results") })
    }
}
