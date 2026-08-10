package com.vervan.chat.data.repo

import android.util.Log
import com.vervan.chat.data.db.AppDatabase
import com.vervan.chat.data.db.entities.Persona
import java.io.File

private const val TAG = "PersonaAvatarCleanup"

/**
 * Deletes a persona's on-disk avatar file (a picked/imported image copied into
 * `filesDir/personas/avatars`) once the [Persona] row that named it is permanently deleted —
 * hard-deleting a persona from the recycle bin, or the 30-day auto-purge in VervanApp, previously
 * dropped the DB row without ever touching the avatar file it pointed at, leaking it on disk
 * indefinitely. Same fix shape as [MessageAttachmentCleanup].
 *
 * Not a bare `File(avatarPath).delete()`: [com.vervan.chat.ui.personas.PersonaEditorViewModel.duplicate]
 * copies `avatarPath` verbatim onto a new persona row instead of copying the file, so the same
 * image can legitimately be referenced by more than one persona. [PersonaDao.countOtherReferencesToAvatarPath]
 * is checked first — the file is only deleted once no other persona still points at it. An emoji
 * avatar (`"emoji:..."`) is never a file path and is ignored here.
 */
object PersonaAvatarCleanup {
    suspend fun deleteOrphanedAvatar(db: AppDatabase, persona: Persona) {
        val path = persona.avatarPath ?: return
        if (path.startsWith("emoji:")) return
        if (db.personaDao().countOtherReferencesToAvatarPath(persona.id, path) == 0) {
            runCatching { File(path).delete() }
                .onFailure { Log.w(TAG, "Failed to delete orphaned avatar $path", it) }
        }
    }
}
