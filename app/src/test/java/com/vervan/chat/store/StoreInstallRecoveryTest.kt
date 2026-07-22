package com.vervan.chat.store

import com.vervan.chat.data.db.dao.StoreInstallArtifactDao
import com.vervan.chat.data.db.dao.StoreInstallSessionDao
import com.vervan.chat.data.db.entities.StoreArtifactState
import com.vervan.chat.data.db.entities.StoreInstallArtifact
import com.vervan.chat.data.db.entities.StoreInstallSession
import com.vervan.chat.data.db.entities.StoreInstallState
import com.vervan.chat.store.install.StoreInstallRecovery
import com.vervan.chat.store.storage.BlobStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Startup recovery after process death. The rule under test is that **the filesystem wins over the
 * database**: the last progress write can lag the bytes actually on disk by up to one throttle
 * interval, so trusting the recorded byte count would make a resume request the wrong Range and
 * silently corrupt the file.
 */
class StoreInstallRecoveryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sessionDao: FakeSessionDao
    private lateinit var artifactDao: FakeArtifactDao
    private lateinit var blobStore: BlobStore

    private class FakeSessionDao : StoreInstallSessionDao {
        val rows = LinkedHashMap<String, StoreInstallSession>()
        override suspend fun upsert(entity: StoreInstallSession) { rows[entity.variantId] = entity }
        override suspend fun update(entity: StoreInstallSession) { rows[entity.variantId] = entity }
        override suspend fun delete(entity: StoreInstallSession) { rows.remove(entity.variantId) }
        override fun observeAll(): Flow<List<StoreInstallSession>> = flowOf(rows.values.toList())
        override fun observeActive(): Flow<List<StoreInstallSession>> = flowOf(rows.values.toList())
        override suspend fun get(variantId: String) = rows[variantId]
        override suspend fun getUnfinished() = rows.values.filter {
            it.state in setOf(
                StoreInstallState.DOWNLOADING, StoreInstallState.VERIFYING,
                StoreInstallState.VALIDATING, StoreInstallState.INSTALLING, StoreInstallState.QUEUED
            )
        }
        override suspend fun delete(variantId: String) { rows.remove(variantId) }
    }

    private class FakeArtifactDao : StoreInstallArtifactDao {
        val rows = LinkedHashMap<String, StoreInstallArtifact>()
        override suspend fun upsert(entity: StoreInstallArtifact) { rows[entity.id] = entity }
        override suspend fun update(entity: StoreInstallArtifact) { rows[entity.id] = entity }
        override suspend fun delete(entity: StoreInstallArtifact) { rows.remove(entity.id) }
        override suspend fun getForVariant(variantId: String) = rows.values.filter { it.variantId == variantId }
        override fun observeAll(): Flow<List<StoreInstallArtifact>> = flowOf(rows.values.toList())
        override suspend fun deleteForVariant(variantId: String) {
            rows.values.removeAll { it.variantId == variantId }
        }
    }

    @Before
    fun setUp() {
        sessionDao = FakeSessionDao()
        artifactDao = FakeArtifactDao()
        blobStore = BlobStore(temp.newFolder())
    }

    private fun recovery() = StoreInstallRecovery(sessionDao, artifactDao, blobStore)

    private suspend fun seedSession(state: StoreInstallState, recordedBytes: Long) {
        sessionDao.upsert(
            StoreInstallSession(
                variantId = "v1", modelId = "m1", displayName = "M", version = "1",
                runtime = "llama.cpp", state = state, totalBytes = 1000,
                downloadedBytes = recordedBytes
            )
        )
    }

    private suspend fun seedArtifact(partBytes: Long?, recordedBytes: Long, sha: String = "a".repeat(64)): File {
        val part = File(temp.newFolder(), "w.part")
        if (partBytes != null) {
            part.parentFile.mkdirs()
            part.writeBytes(ByteArray(partBytes.toInt()))
        }
        artifactDao.upsert(
            StoreInstallArtifact(
                id = "v1:w", variantId = "v1", artifactId = "w", role = "weights",
                sourceUrl = "https://x/w", tempPath = part.absolutePath,
                expectedBytes = 1000, downloadedBytes = recordedBytes, expectedSha256 = sha,
                state = StoreArtifactState.DOWNLOADING
            )
        )
        return part
    }

    @Test
    fun `an interrupted session becomes PAUSED not FAILED`() = runBlocking {
        seedSession(StoreInstallState.DOWNLOADING, recordedBytes = 400)
        seedArtifact(partBytes = 400, recordedBytes = 400)

        recovery().recoverOnStartup()

        // Process death is not a failure — the user must see a resumable download, not an error.
        assertEquals(StoreInstallState.PAUSED, sessionDao.rows["v1"]!!.state)
    }

    @Test
    fun `recorded bytes are corrected upward to match the real part file`() = runBlocking {
        // The DB says 400 but 700 actually made it to disk before the kill.
        seedSession(StoreInstallState.DOWNLOADING, recordedBytes = 400)
        seedArtifact(partBytes = 700, recordedBytes = 400)

        recovery().recoverOnStartup()

        assertEquals(700, artifactDao.rows["v1:w"]!!.downloadedBytes)
        assertEquals(700, sessionDao.rows["v1"]!!.downloadedBytes)
    }

    @Test
    fun `recorded bytes are corrected downward when the part file is smaller`() = runBlocking {
        // The opposite skew: the DB is optimistic, disk is authoritative. Resuming from the
        // recorded offset here would leave a hole in the file.
        seedSession(StoreInstallState.DOWNLOADING, recordedBytes = 900)
        seedArtifact(partBytes = 250, recordedBytes = 900)

        recovery().recoverOnStartup()

        assertEquals(250, artifactDao.rows["v1:w"]!!.downloadedBytes)
    }

    @Test
    fun `a missing part file resets progress to zero`() = runBlocking {
        seedSession(StoreInstallState.DOWNLOADING, recordedBytes = 500)
        val part = seedArtifact(partBytes = 500, recordedBytes = 500)
        part.delete()

        recovery().recoverOnStartup()

        assertEquals(0, artifactDao.rows["v1:w"]!!.downloadedBytes)
        assertEquals(StoreArtifactState.PENDING, artifactDao.rows["v1:w"]!!.state)
    }

    @Test
    fun `an artifact already promoted to a blob counts as complete despite no part file`() = runBlocking {
        // This is the case a naive "size of .part" reconciliation gets wrong: the file is gone
        // precisely *because* it succeeded, and counting it as zero would re-download it.
        val payload = ByteArray(1000) { 7 }
        val staged = File(temp.newFolder(), "done.bin").apply { writeBytes(payload) }
        val sha = BlobStore.sha256Of(staged)
        blobStore.put(staged, sha)

        seedSession(StoreInstallState.DOWNLOADING, recordedBytes = 0)
        seedArtifact(partBytes = null, recordedBytes = 0, sha = sha)

        recovery().recoverOnStartup()

        assertEquals(1000, artifactDao.rows["v1:w"]!!.downloadedBytes)
        assertEquals(StoreArtifactState.COMPLETED, artifactDao.rows["v1:w"]!!.state)
        assertEquals(1000, sessionDao.rows["v1"]!!.downloadedBytes)
    }

    @Test
    fun `finished sessions are left alone`() = runBlocking {
        seedSession(StoreInstallState.READY, recordedBytes = 1000)

        recovery().recoverOnStartup()

        assertEquals(StoreInstallState.READY, sessionDao.rows["v1"]!!.state)
    }

    @Test
    fun `a user-requested stop flag is cleared so recovery does not look like a user pause`() = runBlocking {
        sessionDao.upsert(
            StoreInstallSession(
                variantId = "v1", modelId = "m1", displayName = "M", version = "1",
                runtime = "llama.cpp", state = StoreInstallState.DOWNLOADING,
                totalBytes = 1000, downloadedBytes = 100, userRequestedStop = true
            )
        )
        seedArtifact(partBytes = 100, recordedBytes = 100)

        recovery().recoverOnStartup()

        assertEquals(false, sessionDao.rows["v1"]!!.userRequestedStop)
    }
}
