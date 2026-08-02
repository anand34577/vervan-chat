package com.vervan.chat.data.repo

import com.vervan.chat.data.db.AppDatabase
import java.io.File

/**
 * Deletes the on-disk files a chat's messages point at (imagePath/audioPath/voiceRecordingPath)
 * before the chat itself is hard-deleted. Every call site that used to just run
 * `messageDao().deleteForChat(chatId)` — permanent chat deletion, the recycle-bin's 30-day
 * auto-purge, the incognito/temporary-chat purge, and workspace deletion — dropped the DB rows
 * without ever touching the files those rows referenced, leaking them on disk indefinitely
 * (the same class of bug documents avoid via DocumentImportManager.delete()).
 *
 * Not a bare `File(path).delete()` per message: editAndResend, forkChat and
 * ChatViewModel.duplicate all copy an attachment's path verbatim onto a new [Message] row instead
 * of copying the underlying file, so the same path can be legitimately referenced by messages in
 * other chats (a fork, a duplicate) that are not being deleted. Deleting blindly would corrupt
 * whichever chat still shows that attachment. [MessageDao.countOtherReferencesToPath] is checked
 * per path first — a path is only unlinked once nothing outside this chat still points at it.
 * Sibling branches *within* the chat being deleted don't need special handling: they're all being
 * removed together, so once no message outside this chatId references the path it's genuinely
 * orphaned.
 */
object MessageAttachmentCleanup {

    /** Must run before [com.vervan.chat.data.db.dao.MessageDao.deleteForChat] — it reads the rows
     * that call is about to remove. */
    suspend fun deleteOrphanedFiles(db: AppDatabase, chatId: String) {
        val paths = db.messageDao().getMessages(chatId)
            .flatMap { listOfNotNull(it.imagePath, it.audioPath, it.voiceRecordingPath) }
            .distinct()
        for (path in paths) {
            if (db.messageDao().countOtherReferencesToPath(chatId, path) == 0) {
                runCatching { File(path).delete() }
            }
        }
    }
}
