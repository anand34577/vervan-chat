package com.vervan.chat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.io.File

/**
 * A single scratch image path (a camera capture or a gallery import copied into app storage)
 * whose previous file is deleted the instant it's superseded. Several standalone tool screens
 * (Image caption, OCR scanner, Structured scan) each copy the picked/captured image into
 * `filesDir/images` before using it, keep only the latest path around for display, and only ever
 * needed the file for the lifetime of that screen — but previously just reassigned a plain
 * `mutableStateOf<String?>` with no delete, leaking one file per capture/pick into that directory
 * forever. Centralized here so the cleanup only has to be right once instead of independently in
 * every screen that follows this pattern.
 */
class ManagedImagePath internal constructor(initial: String?) {
    var path: String? by mutableStateOf(initial)
        private set

    /** Replaces the current path, deleting the file it pointed at — unless [newPath] is the same
     * path already held, since that's not a real replacement (e.g. a redundant re-set) and must
     * not delete the file still being shown. */
    fun set(newPath: String?) {
        val previous = path
        if (previous == newPath) return
        path = newPath
        if (previous != null) runCatching { File(previous).delete() }
    }
}

/** Deletes the held file when the caller leaves composition, in addition to [ManagedImagePath.set]
 * deleting it on every replacement — between the two, nothing written through this holder outlives
 * the screen that created it. Safe for every current caller: none of them persist the path itself
 * anywhere (OCR/vision/translation callers only ever extract text from the image and keep that). */
@Composable
fun rememberManagedImagePath(): ManagedImagePath {
    val state = remember { ManagedImagePath(null) }
    val latestPath = rememberUpdatedState(state.path)
    DisposableEffect(Unit) {
        onDispose { latestPath.value?.let { runCatching { File(it).delete() } } }
    }
    return state
}
