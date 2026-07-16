package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.KnowledgeBase
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeBaseDao : BaseDao<KnowledgeBase> {
    @Query("SELECT * FROM knowledge_bases ORDER BY name ASC")
    fun observeAll(): Flow<List<KnowledgeBase>>

    @Query("SELECT * FROM knowledge_bases WHERE id = :id")
    suspend fun get(id: String): KnowledgeBase?

    @Query("SELECT * FROM knowledge_bases WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<KnowledgeBase>

    @Query("UPDATE knowledge_bases SET defaultPersonaId = NULL WHERE defaultPersonaId = :personaId")
    suspend fun clearDefaultPersona(personaId: String)

    @Query("UPDATE knowledge_bases SET defaultProjectId = NULL WHERE defaultProjectId = :projectId")
    suspend fun clearDefaultProject(projectId: String)
}
