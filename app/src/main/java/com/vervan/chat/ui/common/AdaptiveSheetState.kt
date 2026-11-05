package com.vervan.chat.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Sheet state for every bottom sheet in the app. On a short window (a phone held sideways) a
 * half-open sheet shows little more than its title, so sheets open fully there; on taller
 * windows they keep the usual half-open first stop unless [skipPartiallyExpanded] asks otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAdaptiveSheetState(skipPartiallyExpanded: Boolean = false): SheetState {
    val shortWindow = LocalConfiguration.current.screenHeightDp < SHORT_WINDOW_DP
    return rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded || shortWindow)
}

private const val SHORT_WINDOW_DP = 480
