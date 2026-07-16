package com.vervan.chat.modeldownload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRangeDownloaderTest {
    @Test
    fun resumeValidatorRejectsChangedOrMissingMetadata() {
        assertFalse(resumeSourceChanged("etag-1", null, "etag-1", null))
        assertTrue(resumeSourceChanged("etag-1", null, "etag-2", null))
        assertTrue(resumeSourceChanged("etag-1", null, null, null))
        assertFalse(resumeSourceChanged(null, "yesterday", null, "yesterday"))
        assertTrue(resumeSourceChanged(null, "yesterday", null, "today"))
    }
}
