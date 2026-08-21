package com.vervan.chat.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Executes the real Room migration SQL against an Android SQLite implementation. */
@RunWith(AndroidJUnit4::class)
class MigrationsInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migratesFromFirstSchemaToLatestWithoutDestructiveFallback() {
        val databaseName = "migration-instrumented-test"
        helper.createDatabase(databaseName, 1).close()
        helper.runMigrationsAndValidate(databaseName, 5, true, *MIGRATIONS)
    }
}
