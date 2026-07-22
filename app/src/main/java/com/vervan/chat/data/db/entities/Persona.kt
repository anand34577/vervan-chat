package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "personas")
data class Persona(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val systemInstruction: String,
    val isBuiltIn: Boolean = false,
    // declarative behavior dials layered on top of the free-text
    // systemInstruction, turned into an extra prompt line by PersonaTraits.instructionFor().
    val tone: String = "NEUTRAL", // WARM, NEUTRAL, DIRECT, PLAYFUL
    val formality: String = "NEUTRAL", // CASUAL, NEUTRAL, FORMAL
    val conciseness: String = "NORMAL", // TERSE, NORMAL, ELABORATE
    val creativity: Float = 0.5f, // 0..1, informs generation temperature
    val responseLength: String = "BALANCED", // SHORT, BALANCED, LONG
    val language: String = "", // free-text preferred reply language; blank = no preference
    // Recycle bin coverage — same soft-delete pattern as Chat/Note/Document/Folder.
    val deletedAt: Long? = null,
    // Character card import (SillyTavern PNG cards, see CharacterCardImporter) — a copy of the
    // card's embedded portrait under filesDir/personas/avatars/, shown in place of the generic
    // person icon. Null for hand-authored personas; not user-editable beyond re-importing.
    val avatarPath: String? = null
)
