package com.vervan.chat.data.db.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/** The insert/update/delete triad every entity DAO in this app repeated individually.
 * Room generates a real implementation for a generic DAO as long as a concrete DAO
 * interface extends this with a specific entity type. */
interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: T)

    @Update
    suspend fun update(entity: T)

    @Delete
    suspend fun delete(entity: T)
}
