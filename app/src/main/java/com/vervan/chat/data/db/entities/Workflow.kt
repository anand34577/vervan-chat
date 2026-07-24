package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import org.json.JSONArray

/**
 * A workflow is an ordered list of instructions run against on-device generation, each
 * step's output feeding the next as input (step 1 runs against the user-supplied text).
 * steps are plain instruction strings, JSON-array-encoded — no per-step config
 * (model choice, temperature, branching) beyond "what to tell the model to do".
 */
@Entity(tableName = "workflows")
data class Workflow(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val stepsJson: String,
    val isBuiltIn: Boolean = false,
    // Recycle bin coverage.
    val deletedAt: Long? = null
) {
    val steps: List<String>
        get() {
            val arr = JSONArray(stepsJson)
            return (0 until arr.length()).map { arr.getString(it) }
        }

    companion object {
        fun encodeSteps(steps: List<String>): String =
            JSONArray(steps).toString()
    }
}
