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
        androidAvailable: Boolean = true,
        shrutiEnabled: Boolean = false,
        shrutiAvailable: Boolean = false
    ) = SttAvailability(
        speechInputEnabled = true,
        preference = preference,
        fallbackEnabled = fallback,
        modelEnabled = modelEnabled,
        modelAvailable = modelAvailable,
        whisperEnabled = whisperEnabled,
        whisperAvailable = whisperAvailable,
        androidEnabled = androidEnabled,
        androidAvailable = androidAvailable,
        shrutiEnabled = shrutiEnabled,
        shrutiAvailable = shrutiAvailable
    )

    @Test
    fun downloadedShrutiComesBeforeWhisperInAuto() {
        val result = SttEnginePolicy.resolve(availability(shrutiEnabled = true, shrutiAvailable = true, whisperAvailable = true))
        assertEquals(listOf(SttEngineChoice.SHRUTI, SttEngineChoice.WHISPER_CPP, SttEngineChoice.ANDROID), result.candidates)
    }

    @Test
    fun pinnedShrutiWithoutModelIsUnavailable() {
        val result = SttEnginePolicy.resolve(availability(preference = "SHRUTI", fallback = false, shrutiEnabled = true))
        assertFalse(result.isAvailable)
        assertEquals(SttEngineChoice.SHRUTI, result.requested)
    }

    @Test
    fun disabledShrutiIsSkipped() {
        val result = SttEnginePolicy.resolve(availability(shrutiEnabled = false, shrutiAvailable = true))
        assertEquals(listOf(SttEngineChoice.ANDROID), result.candidates)
    }

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
