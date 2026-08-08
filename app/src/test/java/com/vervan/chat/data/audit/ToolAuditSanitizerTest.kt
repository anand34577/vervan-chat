package com.vervan.chat.data.audit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolAuditSanitizerTest {
    @Test
    fun redactsTextAndKeepsNonSensitiveScalarShape() {
        val sanitized = JSONObject(
            ToolAuditSanitizer.sanitize(
                JSONObject().put("content", "private note").put("confirmed", true).put("count", 2)
            )
        )

        assertFalse(sanitized.toString().contains("private note"))
        assertEquals("<redacted:12 chars>", sanitized.getString("content"))
        assertEquals(true, sanitized.getBoolean("confirmed"))
        assertEquals(2, sanitized.getInt("count"))
    }

    @Test
    fun redactsToolOutputSoClipboardContentIsNeverPersisted() {
        // read_clipboard returns the clipboard verbatim as its summary; that must not reach the
        // tool_audit row, which outlives the turn by up to 30 days.
        val clipboard = "correct horse battery staple"

        val sanitized = ToolAuditSanitizer.sanitizeSummary(clipboard)

        assertFalse(sanitized.contains("horse"))
        assertEquals("<redacted:28 chars>", sanitized)
    }

    @Test
    fun keepsEmptySummaryEmptyRatherThanReportingZeroLength() {
        assertEquals("", ToolAuditSanitizer.sanitizeSummary(""))
    }
}
