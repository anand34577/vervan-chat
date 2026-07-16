package com.vervan.chat.ui.tools

import com.vervan.chat.voice.VoiceControllerState
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceUiStateTest {
    @Test
    fun statusLabelsKeepSpeakerOwnershipUnambiguous() {
        assertEquals("Listening…", voiceStatusLabel(VoiceControllerState.LISTENING, false, null))
        assertEquals("Thinking…", voiceStatusLabel(VoiceControllerState.THINKING, false, null))
        assertEquals("Speaking…", voiceStatusLabel(VoiceControllerState.SPEAKING, false, null))
        assertEquals("Paused", voiceStatusLabel(VoiceControllerState.SPEAKING, true, null))
        assertEquals("Preparing Gemma…", voiceStatusLabel(VoiceControllerState.LOADING_MODEL, false, "Gemma"))
    }
}
