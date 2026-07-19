package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDownloadActionsTest {
    @Test
    fun exposesOnlyTheExpectedDownloadControls() {
        assertEquals(
            setOf(ModelAction.PAUSE, ModelAction.CANCEL, ModelAction.DETAILS),
            downloadActionsFor(ModelStatus.DOWNLOADING)
        )
        assertEquals(
            setOf(ModelAction.RESUME, ModelAction.DELETE, ModelAction.DETAILS),
            downloadActionsFor(ModelStatus.PAUSED)
        )
        assertEquals(
            setOf(ModelAction.RESUME, ModelAction.DELETE, ModelAction.DETAILS),
            downloadActionsFor(ModelStatus.FAILED)
        )
    }
}
