package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.DownloadFile
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadFileDao : BaseDao<DownloadFile> {
    @Query("SELECT * FROM download_files")
    fun observeAll(): Flow<List<DownloadFile>>

    @Query("SELECT * FROM download_files WHERE packageId = :packageId ORDER BY fileId")
    fun observeForPackage(packageId: String): Flow<List<DownloadFile>>

    @Query("SELECT * FROM download_files WHERE packageId = :packageId ORDER BY fileId")
    suspend fun getForPackage(packageId: String): List<DownloadFile>

    @Query("SELECT * FROM download_files WHERE id = :id")
    suspend fun get(id: String): DownloadFile?

    @Query("DELETE FROM download_files WHERE packageId = :packageId")
    suspend fun deleteForPackage(packageId: String)
}
