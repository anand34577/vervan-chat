package com.vervan.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttEnginePolicyTest {
    private fun availability(
        preference: String = "AUTO",
        fallback: Boolean = true,
        modelEnabled: Boolean = true,
        modelAvailable: Boolean = false,
        whisperEnabled: Boolean = true,
        whisperAvailable: Boolean = false,
        androidEnabled: Boolean = true,
        androidAvailable: Boolean = true
    ) = SttAvailability(
        speechInputEnabled = true,
        preference = preference,
        fallbackEnabled = fallback,
        modelEnabled = modelEnabled,
        modelAvailable = modelAvailable,
        whisperEnabled = whisperEnabled,
        whisperAvailable = whisperAvailable,
        androidEnabled = androidEnabled,
        androidAvailable = androidAvailable
    )

    @Test
    fun autoFallsBackFromUnsupportedModelAndMissingWhisperToAndroid() {
        val result = SttEnginePolicy.resolve(availability())
        assertEquals(listOf(SttEngineChoice.ANDROID), result.candidates)
    }

    @Test
    fun pinnedMissingWhisperWithoutFallbackDisablesSpeechInput() {
        val result = SttEnginePolicy.resolve(
            availability(
                preference = "WHISPER_CPP",
                fallback = false,
                modelAvailable = true
            )
        )
        assertFalse(result.isAvailable)
        assertTrue(result.unavailableReason.orEmpty().contains("whisper.cpp"))
    }

    @Test
    fun pinnedWhisperUsesDownloadedModelEvenWhenModelAudioExists() {
        val result = SttEnginePolicy.resolve(
            availability(
                preference = "WHISPER_CPP",
                fallback = false,
                modelAvailable = true,
                whisperAvailable = true
            )
        )
        assertEquals(listOf(SttEngineChoice.WHISPER_CPP), result.candidates)
    }

    @Test
    fun allEnginesDisabledHidesSpeechInput() {
        val result = SttEnginePolicy.resolve(
            availability(
                modelEnabled = false,
                whisperEnabled = false,
                androidEnabled = false,
                androidAvailable = true
            )
        )
        assertFalse(result.isAvailable)
        assertTrue(result.unavailableReason.orEmpty().contains("turned off"))
    }

    @Test
    fun automaticWithoutFallbackUsesEnabledAudioModel() {
        val result = SttEnginePolicy.resolve(
            availability(
                fallback = false,
                modelAvailable = true,
                whisperEnabled = false,
                androidEnabled = false
            )
        )
        assertEquals(listOf(SttEngineChoice.MODEL_AUDIO), result.candidates)
    }
}
