package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A single logged expense — created either manually via the `log_expense` tool or from the
 * Receipt Scanner's "Log as expense" action ([com.vervan.chat.ui.tools.StructuredScanScreen]). */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchant: String,
    val amount: Double,
    val currency: String = "",
    val category: String = "",
    val paymentMethod: String = "",
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
