package com.vervan.chat.data.db

import androidx.room.migration.Migration

/**
 * Schema history. The app is still pre-release (no shipped installs to preserve), so the
 * previous 49-step incremental migration chain (versions 9 through 50) was squashed into the
 * entity classes as a single version-1 schema instead of being carried forward — every column,
 * table, and index those migrations added is already reflected in the current `@Entity`
 * definitions in `com.vervan.chat.data.db.entities`; nothing was lost, only the step-by-step
 * history collapsed. [AppDatabase] falls back to a destructive rebuild for anyone upgrading from
 * an old dev build with an old schema (see `Room.databaseBuilder(...).fallbackToDestructiveMigration()`
 * in `VervanApp`), which is the correct behavior pre-release: a schema wipe on upgrade, not a
 * crash.
 *
 * Once this app actually ships and has real user data to preserve across updates, migrations
 * start here again: every future schema change bumps [AppDatabase]'s `@Database.version` and
 * adds a `Migration(old, new)` entry to this array, same discipline as before — just starting
 * fresh from version 1 instead of continuing the pre-release numbering.
 */
val MIGRATIONS = arrayOf<Migration>()
