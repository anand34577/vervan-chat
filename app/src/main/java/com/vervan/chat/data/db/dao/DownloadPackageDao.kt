package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.vervan.chat.data.db.entities.DownloadPackage
import com.vervan.chat.data.db.entities.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadPackageDao : BaseDao<DownloadPackage> {
    @Query("SELECT * FROM download_packages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadPackage>>

    @Query(
        "SELECT * FROM download_packages WHERE status NOT IN " +
            "('READY','CANCELLED','NOT_DOWNLOADED') ORDER BY createdAt DESC"
    )
    fun observeActive(): Flow<List<DownloadPackage>>

    @Query("SELECT * FROM download_packages WHERE id = :id")
    suspend fun get(id: String): DownloadPackage?

    @Query("SELECT * FROM download_packages WHERE status NOT IN ('READY','CANCELLED') ORDER BY createdAt")
    suspend fun getUnfinished(): List<DownloadPackage>

    @Query("SELECT * FROM download_packages WHERE status = 'QUEUED' ORDER BY createdAt LIMIT 1")
    suspend fun getNextQueued(): DownloadPackage?

    @Query("DELETE FROM download_packages WHERE id = :id")
    suspend fun delete(id: String)

    /** Package completion must be claimed exactly once — this is the only place that moves a
     * package DOWNLOADED -> VERIFYING, guarded by the WHERE clause so two racing "last file
     * finished" observers can't both launch a verification/import job for the same package. */
    @Transaction
    suspend fun claimForVerification(id: String): Boolean {
        val pkg = get(id) ?: return false
        if (pkg.status != ModelStatus.DOWNLOADED) return false
        upsert(pkg.copy(status = ModelStatus.VERIFYING, updatedAt = System.currentTimeMillis()))
        return true
    }
}
