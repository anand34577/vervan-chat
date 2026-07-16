package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao : BaseDao<Expense> {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date >= :sinceMillis ORDER BY date DESC")
    suspend fun getSince(sinceMillis: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE category LIKE '%' || :category || '%' ORDER BY date DESC")
    suspend fun getByCategory(category: String): List<Expense>
}
