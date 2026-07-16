package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao : BaseDao<ModelInfo> {
    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    fun observeModels(): Flow<List<ModelInfo>>

    @Query("SELECT * FROM models WHERE role = :role AND isActive = 1 LIMIT 1")
    suspend fun getActiveModel(role: ModelRole): ModelInfo?

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ModelInfo?

    @Query("SELECT * FROM models WHERE role = :role AND isActive = 1 LIMIT 1")
    fun observeActiveModel(role: ModelRole): Flow<ModelInfo?>

    @Query("SELECT * FROM models WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): ModelInfo?

    @Query("SELECT * FROM models WHERE role = :role AND id != :excludeId ORDER BY importedAt DESC")
    suspend fun getOthersOfRole(role: ModelRole, excludeId: String): List<ModelInfo>

    @Query("UPDATE models SET isActive = 0 WHERE role = :role")
    suspend fun clearActive(role: ModelRole)

    @Query("SELECT * FROM models WHERE catalogModelId = :modelId AND catalogVersion = :version LIMIT 1")
    suspend fun findByCatalogEntry(modelId: String, version: String): ModelInfo?
}
