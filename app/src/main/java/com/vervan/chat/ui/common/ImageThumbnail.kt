package com.vervan.chat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vervan.chat.model.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a thumbnail off the main thread. [ImageUtils.decodeThumbnail] is a blocking disk read
 * + bitmap decode; calling it directly inside `remember {}` (the pattern this replaces, and
 * still the pattern in a couple of one-off screens) runs that work synchronously during
 * composition — i.e. on the main thread — which is exactly what shows up as Choreographer's
 * "Skipped Nn frames" the first time a not-yet-cached image enters a chat, a media grid, or any
 * list of thumbnails, or when scrolling past several such images quickly.
 *
 * This checks [ImageUtils.peekThumbnailCache] synchronously first, so an already-decoded
 * thumbnail (e.g. scrolling back onto a previously visible message) still renders on the very
 * first frame with no flash of empty space — only a genuine cache miss is dispatched to
 * [Dispatchers.IO].
 *
 * [invalidationKey] forces a re-check when a caller knows the file at [path] may have been
 * overwritten in place (e.g. a re-crop that saves over the original path) — pair it with an
 * explicit [ImageUtils.invalidateThumbnail] call at the write site; this key alone does not
 * invalidate [ImageUtils]'s own cache, it only makes this composable re-run its lookup so a
 * cache that was invalidated elsewhere is actually re-read instead of staying on whatever
 * [ImageBitmap] this composable already held in state from before the edit.
 */
@Composable
fun rememberThumbnail(path: String?, sizePx: Int, invalidationKey: Any? = null): ImageBitmap? {
    if (path.isNullOrBlank() || sizePx <= 0) return null
    val state = produceState<ImageBitmap?>(
        initialValue = ImageUtils.peekThumbnailCache(path, sizePx)?.asImageBitmap(),
        key1 = path,
        key2 = sizePx,
        key3 = invalidationKey
    ) {
        value = ImageUtils.peekThumbnailCache(path, sizePx)?.asImageBitmap()
        if (value == null) {
            value = withContext(Dispatchers.IO) { ImageUtils.decodeThumbnail(path, sizePx)?.asImageBitmap() }
        }
    }
    return state.value
}
