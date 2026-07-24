package com.vervan.chat.model

import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDefaultsTest {

    private fun chat(persona: String? = null, model: String? = null) =
        Chat(personaId = persona, modelId = model)

    private fun folder(persona: String? = null, model: String? = null) =
        Folder(name = "f", defaultPersonaId = persona, defaultModelId = model)

    private fun workspace(persona: String) = Workspace(name = "w", personaId = persona)

    // --- persona precedence: chat → folder → workspace → default ---

    @Test fun personaFallsBackToBuiltinWhenNothingSet() {
        assertEquals("builtin-general", ChatDefaults.personaId(chat(), null, null))
    }

    @Test fun personaInheritsFromWorkspace() {
        assertEquals("ws", ChatDefaults.personaId(chat(), null, workspace("ws")))
    }

    @Test fun folderPersonaOverridesWorkspace() {
        assertEquals("fold", ChatDefaults.personaId(chat(), folder(persona = "fold"), workspace("ws")))
    }

    @Test fun chatPersonaOverridesFolderAndWorkspace() {
        assertEquals("mine", ChatDefaults.personaId(chat(persona = "mine"), folder(persona = "fold"), workspace("ws")))
    }

    @Test fun blankFolderPersonaFallsThroughToWorkspace() {
        assertEquals("ws", ChatDefaults.personaId(chat(), folder(persona = ""), workspace("ws")))
    }

    // --- model precedence: chat → folder → (null = caller's loaded/active fallback) ---

    @Test fun modelIsNullWhenNeitherChatNorFolderSetsIt() {
        assertNull(ChatDefaults.modelId(chat(), folder()))
    }

    @Test fun modelInheritsFromFolder() {
        assertEquals("fm", ChatDefaults.modelId(chat(), folder(model = "fm")))
    }

    @Test fun chatModelOverridesFolder() {
        assertEquals("cm", ChatDefaults.modelId(chat(model = "cm"), folder(model = "fm")))
    }

    @Test fun blankFolderModelIsIgnored() {
        assertNull(ChatDefaults.modelId(chat(), folder(model = "")))
    }
}
