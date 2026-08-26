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

    @Test
    fun byteLimitIsEnforcedBeforeWritingAndWithoutOverflow() {
        assertFalse(exceedsDownloadLimit(90, 10, 100))
        assertTrue(exceedsDownloadLimit(90, 11, 100))
        assertTrue(exceedsDownloadLimit(Long.MAX_VALUE, 1, Long.MAX_VALUE))
        assertFalse(exceedsDownloadLimit(Long.MAX_VALUE, 1, null))
    }
}
