package com.vervan.chat.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared adaptive-layout values. These are intentionally few: screens choose a canonical
 * content width while [com.vervan.chat.ui.common.PageContainer] owns centering and gutters.
 */
object VervanBreakpoints {
    val medium = 600.dp
    val expanded = 840.dp
}

object VervanContentWidth {
    val action = 360.dp
    val dialog = 560.dp
    val reading = 720.dp
    val standard = 840.dp
    val wide = 1040.dp
}

object VervanGridMinWidth {
    val compactCard = 220.dp
    val standardCard = 260.dp
}
