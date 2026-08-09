package com.vervan.chat.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one failure mode [MIGRATIONS] exists to prevent: [AppDatabase] deliberately does
 * NOT call `fallbackToDestructiveMigration()`, so a schema version with no registered migration
 * path is not a silent data wipe — it is an `IllegalStateException` on every launch for every
 * existing install, unrecoverable without clearing app data.
 *
 * The expected chain is derived from the committed schema exports rather than hardcoded. A
 * hardcoded list (the previous version of this test) passes forever: bumping
 * `@Database(version = N)` without adding `MIGRATION_(N-1)_N` left this test, lint, and
 * assembleDebug all green and only failed on a real device. Room writes `<version>.json` into
 * `app/schemas` on every build and CI fails if those exports aren't committed
 * (`.github/workflows/android.yml`), so the newest schema file is an accurate, self-updating
 * stand-in for the current `@Database.version` — which is itself unreadable here, since Room's
 * `@Database` annotation is not retained at runtime.
 */
class MigrationsTest {

    private fun exportedSchemaVersions(): List<Int> {
        // Unit tests run with the module directory (`app/`) as the working directory.
        val dir = File("schemas/${AppDatabase::class.java.canonicalName}")
        assertTrue(
            "Expected exported Room schemas at ${dir.absolutePath} — is exportSchema still true?",
            dir.isDirectory
        )
        val versions = dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
        assertTrue("No exported schema JSON files found in ${dir.absolutePath}", versions.isNotEmpty())
        return versions
    }

    @Test
    fun migrationChainIsContiguousFromOneToLatestSchema() {
        val latest = exportedSchemaVersions().max()
        assertEquals(
            "MIGRATIONS must form an unbroken 1..$latest chain. Bumping @Database.version " +
                "requires adding the matching Migration to Migrations.kt.",
            (1 until latest).map { it to it + 1 },
            MIGRATIONS.sortedBy { it.startVersion }.map { it.startVersion to it.endVersion }
        )
    }

    @Test
    fun everySchemaVersionBetweenOneAndLatestIsExported() {
        // A gap here means a version was bumped past without its schema being committed, which
        // would let the contiguity check above compare against the wrong "latest".
        val versions = exportedSchemaVersions()
        assertEquals((1..versions.max()).toList(), versions)
    }
}
