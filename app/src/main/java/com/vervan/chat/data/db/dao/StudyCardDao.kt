package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.StudyCard
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyCardDao {
    @Query("SELECT * FROM study_cards ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<StudyCard>>

    @Query("SELECT DISTINCT setName FROM study_cards ORDER BY setName ASC")
    fun observeSetNames(): Flow<List<String>>

    @Query("SELECT * FROM study_cards WHERE setName = :setName ORDER BY createdAt ASC")
    fun observeSet(setName: String): Flow<List<StudyCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<StudyCard>)

    @Update
    suspend fun update(card: StudyCard)

    @Delete
    suspend fun delete(card: StudyCard)

    @Query("DELETE FROM study_cards WHERE setName = :setName")
    suspend fun deleteSet(setName: String)
}
