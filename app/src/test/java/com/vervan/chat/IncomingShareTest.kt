package com.vervan.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingShareTest {
    @Test
    fun classifiesImagesByMimeOrExtension() {
        assertEquals(IncomingShareKind.IMAGE, classifyIncomingShare("image/jpeg", "shared.bin"))
        assertEquals(IncomingShareKind.IMAGE, classifyIncomingShare(null, "photo.HEIC"))
        assertEquals(IncomingShareKind.DOCUMENT, classifyIncomingShare("application/pdf", "notes.pdf"))
    }

    @Test
    fun keepsUsefulSubjectAndBodyWithoutDuplicatingThem() {
        assertEquals("Subject\n\nBody", mergeSharedText("Subject", "Body"))
        assertEquals("Subject\nDetails", mergeSharedText("Subject", "Subject\nDetails"))
        assertEquals("A\n\nApple", mergeSharedText("A", "Apple"))
        assertEquals("Body", mergeSharedText(null, " Body "))
    }
}
