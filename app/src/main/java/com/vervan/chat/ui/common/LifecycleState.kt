package com.vervan.chat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Source-compatible lifecycle-aware replacements for Compose's basic Flow collectors.
 * Keeping the familiar parameter name lets large screens migrate without noisy call-site
 * rewrites while collection still pauses when their lifecycle is not visible.
 */
@Composable
fun <T> StateFlow<T>.collectAsState(): State<T> =
    collectAsStateWithLifecycle()

@Composable
fun <T> Flow<T>.collectAsState(initial: T): State<T> =
    collectAsStateWithLifecycle(initialValue = initial)
