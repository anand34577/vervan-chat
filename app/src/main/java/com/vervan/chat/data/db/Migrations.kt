package com.vervan.chat.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit migration history. Missing future migrations fail closed so the original database
 * remains recoverable instead of being silently rebuilt. */
/** Versions 1 and 2 share the same exported Room identity; version 2 only established the
 * post-pre-release migration baseline, so preserving data requires an explicit no-op step. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chats_deletedAt_archived_pinned_updatedAt` " +
                "ON `chats` (`deletedAt`, `archived`, `pinned`, `updatedAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chats_workspaceId_deletedAt_pinned_updatedAt` " +
                "ON `chats` (`workspaceId`, `deletedAt`, `pinned`, `updatedAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_chatId_createdAt` " +
                "ON `messages` (`chatId`, `createdAt`)"
        )
    }
}

/** Adds the two columns [com.vervan.chat.data.db.entities.ModelInfo.remoteBaseUrl]/
 * [com.vervan.chat.data.db.entities.ModelInfo.remoteApiModelId] backing external OpenAI-
 * compatible API models — both nullable with no default, so every pre-existing row (which has
 * no remote config) just gets NULL in both, identical to a fresh column add anywhere else in
 * this app's history. The API key itself is never a DB column (see RemoteApiKeyStore), so
 * there's nothing else to backfill here. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `models` ADD COLUMN `remoteBaseUrl` TEXT")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `remoteApiModelId` TEXT")
    }
}

val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
