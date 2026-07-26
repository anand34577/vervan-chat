package com.vervan.chat.voice

import com.vervan.chat.BuildConfig
import com.vervan.chat.VervanApp
import kotlinx.coroutines.flow.first

enum class SttEngineChoice(val settingValue: String, val label: String) {
    MODEL_AUDIO("MODEL_AUDIO", "Active model"),
    WHISPER_CPP("WHISPER_CPP", "whisper.cpp"),
    ANDROID("ANDROID", "Android speech service");

    companion object {
        fun fromSetting(value: String): SttEngineChoice? =
            entries.firstOrNull { it.settingValue == value }
    }
}

data class SttAvailability(
    val speechInputEnabled: Boolean,
    val preference: String,
    val fallbackEnabled: Boolean,
    val modelEnabled: Boolean,
    val modelAvailable: Boolean,
    val whisperEnabled: Boolean,
    val whisperAvailable: Boolean,
    val androidEnabled: Boolean,
    val androidAvailable: Boolean
) {
    fun enabled(choice: SttEngineChoice): Boolean = when (choice) {
        SttEngineChoice.MODEL_AUDIO -> modelEnabled
        SttEngineChoice.WHISPER_CPP -> whisperEnabled
        SttEngineChoice.ANDROID -> androidEnabled
    }

    fun available(choice: SttEngineChoice): Boolean = when (choice) {
        SttEngineChoice.MODEL_AUDIO -> modelAvailable
        SttEngineChoice.WHISPER_CPP -> whisperAvailable
        SttEngineChoice.ANDROID -> androidAvailable
    }
}

data class SttResolution(
    val candidates: List<SttEngineChoice>,
    val requested: SttEngineChoice?,
    val unavailableReason: String?
) {
    val isAvailable: Boolean get() = candidates.isNotEmpty()
}

/**
 * One source of truth for composer visibility, settings status, inline dictation and hands-free
 * recognition. AUTO follows the configured fallback order. A pinned engine is strict when
 * fallback is off, which prevents silently using model audio when the user explicitly selected
 * a missing whisper.cpp model.
 */
object SttEnginePolicy {
    private val defaultOrder = listOf(
        SttEngineChoice.MODEL_AUDIO,
        SttEngineChoice.WHISPER_CPP,
        SttEngineChoice.ANDROID
    )

    fun resolve(availability: SttAvailability): SttResolution {
        if (!availability.speechInputEnabled) {
            return SttResolution(emptyList(), null, "Speech input is turned off")
        }
        val enabled = defaultOrder.filter(availability::enabled)
        if (enabled.isEmpty()) {
            return SttResolution(emptyList(), null, "All speech-to-text engines are turned off")
        }

        val requested = SttEngineChoice.fromSetting(availability.preference)
        val ordered = if (requested == null) {
            if (availability.fallbackEnabled) enabled else enabled.take(1)
        } else {
            // A manual choice is a privacy/performance decision, not a hint. Never silently
            // substitute model audio when the user explicitly pinned whisper.cpp (or vice versa).
            listOf(requested)
        }
        val candidates = ordered.distinct().filter(availability::enabled).filter(availability::available)
        if (candidates.isNotEmpty()) return SttResolution(candidates, requested, null)

        val reason = when (requested) {
            SttEngineChoice.MODEL_AUDIO -> "The active model does not support audio input"
            SttEngineChoice.WHISPER_CPP -> "The whisper.cpp model is not downloaded or cannot load"
            SttEngineChoice.ANDROID -> "Android speech recognition is unavailable on this device"
            null -> "No enabled speech-to-text engine is currently available"
        }
        return SttResolution(emptyList(), requested, reason)
    }

    suspend fun resolve(app: VervanApp, modelSupportsAudio: Boolean? = null): SttResolution {
        val settings = app.container.settingsRepository
        val whisperRow = app.container.db.ttsVoiceModelDao()
            .getByEngine(WhisperCppSttEngine.ENGINE, WhisperCppSttEngine.MODEL_LANGUAGE_KEY)
        val modelAvailable = modelSupportsAudio
            ?: (app.container.db.modelDao()
                .getActiveModel(com.vervan.chat.data.db.entities.ModelRole.GENERATION)
                ?.supportsAudio == true)
        return resolve(
            SttAvailability(
                speechInputEnabled = settings.speechInputEnabled.first(),
                preference = settings.sttEnginePreference.first(),
                fallbackEnabled = settings.sttFallbackEnabled.first(),
                modelEnabled = settings.modelAudioSttEnabled.first(),
                modelAvailable = modelAvailable,
                whisperEnabled = settings.inbuiltSttEnabled.first(),
                whisperAvailable = BuildConfig.WHISPER_CPP_AVAILABLE &&
                    (whisperRow?.isReady != false) &&
                    WhisperCppSttEngine.findInstalledModelFile(app, whisperRow?.filePath) != null,
                androidEnabled = settings.androidSttEnabled.first(),
                androidAvailable = AndroidSystemSttRecognizer.isAvailable(app)
            )
        )
    }
}
