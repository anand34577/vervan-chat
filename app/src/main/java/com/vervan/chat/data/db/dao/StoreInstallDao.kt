package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.StoreInstallArtifact
import com.vervan.chat.data.db.entities.StoreInstallSession
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreInstallSessionDao : BaseDao<StoreInstallSession> {
    @Query("SELECT * FROM store_install_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StoreInstallSession>>

    @Query("SELECT * FROM store_install_sessions WHERE state NOT IN ('READY','CANCELLED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<StoreInstallSession>>

    @Query("SELECT * FROM store_install_sessions WHERE variantId = :variantId")
    suspend fun get(variantId: String): StoreInstallSession?

    /** Sessions that were mid-flight when the process died. Recovery reconciles these against the
     * real `.part` files on disk rather than assuming their recorded byte counts are current —
     * the last progress write can lag the actual bytes written by up to one throttle interval. */
    @Query(
        "SELECT * FROM store_install_sessions WHERE state IN " +
            "('DOWNLOADING','VERIFYING','VALIDATING','INSTALLING','QUEUED') ORDER BY createdAt"
    )
    suspend fun getUnfinished(): List<StoreInstallSession>

    @Query("DELETE FROM store_install_sessions WHERE variantId = :variantId")
    suspend fun delete(variantId: String)
}

@Dao
interface StoreInstallArtifactDao : BaseDao<StoreInstallArtifact> {
    @Query("SELECT * FROM store_install_artifacts WHERE variantId = :variantId")
    suspend fun getForVariant(variantId: String): List<StoreInstallArtifact>

    @Query("SELECT * FROM store_install_artifacts")
    fun observeAll(): Flow<List<StoreInstallArtifact>>

    @Query("DELETE FROM store_install_artifacts WHERE variantId = :variantId")
    suspend fun deleteForVariant(variantId: String)
}
