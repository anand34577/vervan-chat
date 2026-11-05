package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.validation.InputLimits

/**
 * Reference pronunciation via TTS, attempt via the device's offline recognizer — a real phonetic
 * comparison needs a dedicated model this app doesn't bundle, so feedback here is a word-level
 * diff (which target words the recognizer didn't hear back) plus its raw confidence score.
 * heuristic, not true phonetic scoring — good enough to flag likely trouble spots.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PronunciationCoachScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf("") }
    var heardText by remember { mutableStateOf<String?>(null) }
    var confidence by remember { mutableStateOf<Float?>(null) }
    var missedWords by remember { mutableStateOf(listOf<String>()) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        tts = instance
        onDispose { instance.shutdown() }
    }

    val recognize = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val scores = result.data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
            heardText = heard
            confidence = scores?.firstOrNull()
            // Punctuation and case are ignored on both sides: "that?" and "That" are the same word.
            val heardWords = heard.split(Regex("\\s+")).map(::normalizeWord).filter { it.isNotBlank() }.toSet()
            missedWords = target.split(Regex("\\s+")).filter { it.isNotBlank() && normalizeWord(it) !in heardWords }
        }
    }
    val requestMicPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) {
            recognize.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pronunciation_coach_pronunciation_coach)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            ToolIntro(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.pronunciation_coach_listen_repeat_improve),
                body = stringResource(R.string.pronunciation_coach_hear_a_phrase_repeat_it)
            )
            ToolStep(1, stringResource(R.string.pron_step_phrase))
            OutlinedTextField(
                value = target, onValueChange = { target = it.take(InputLimits.PRONUNCIATION_TEXT_CHARS); heardText = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.pronunciation_coach_word_or_phrase_to_practice)) },
                supportingText = { ToolCharCount(target.length, InputLimits.PRONUNCIATION_TEXT_CHARS) },
                maxLines = 3
            )
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                PRACTICE_PHRASES.forEach { phrase ->
                    com.vervan.chat.ui.common.VervanFilterChip(
                        selected = target == phrase,
                        onClick = { target = phrase; heardText = null },
                        label = { Text(phrase, maxLines = 1) }
                    )
                }
            }

            ToolStep(2, stringResource(R.string.pron_step_listen))
            ToolSourceRow {
                OutlinedButton(
                    onClick = { tts?.setSpeechRate(1f); tts?.speak(target, TextToSpeech.QUEUE_FLUSH, null, "pronounce") },
                    enabled = target.isNotBlank() && ttsReady, modifier = Modifier.weight(1f)
                ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp)); Text(stringResource(R.string.pronunciation_coach_hear_it), modifier = Modifier.padding(start = Space.sm)) }
                OutlinedButton(
                    onClick = { tts?.setSpeechRate(0.6f); tts?.speak(target, TextToSpeech.QUEUE_FLUSH, null, "pronounce-slow") },
                    enabled = target.isNotBlank() && ttsReady, modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.pron_slowly)) }
            }
            if (!ttsReady) {
                Text(stringResource(R.string.pron_tts_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.xs))
            }

            ToolStep(3, stringResource(R.string.pron_step_say))
            ToolPrimaryButton(
                label = stringResource(if (heardText == null) R.string.pronunciation_coach_repeat_it else R.string.pron_try_again),
                icon = Icons.Filled.Mic,
                onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                blockedReason = if (target.isBlank()) stringResource(R.string.pron_blocked) else null,
                modifier = Modifier.padding(top = 0.dp)
            )
            heardText?.let { heard ->
                val words = target.split(Regex("\\s+")).filter { it.isNotBlank() }
                val matched = words.size - missedWords.size
                val score = if (words.isEmpty()) 0 else matched * 100 / words.size
                Card(
                    Modifier.fillMaxWidth().padding(top = Space.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(Space.md)) {
                        Text(
                            stringResource(R.string.pron_score, score),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (missedWords.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().padding(top = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalArrangement = Arrangement.spacedBy(Space.xs)
                        ) {
                            words.forEach { w ->
                                val ok = normalizeWord(w) !in missedWords.map(::normalizeWord)
                                androidx.compose.material3.Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        w, style = MaterialTheme.typography.bodyMedium,
                                        color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.pronunciation_coach_heard_arg1, heard.ifBlank { "…" }), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
                        confidence?.let { Text(stringResource(R.string.pronunciation_confidence, (it * 100).toInt()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Space.xs)) }
                        Text(
                            stringResource(if (missedWords.isEmpty()) R.string.pronunciation_coach_sounded_right else R.string.pron_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                }
            }
        }
    }
}

private val PRACTICE_PHRASES = listOf("Thank you very much", "Could you repeat that?", "Comfortable", "Entrepreneur", "Specifically")

private fun normalizeWord(w: String) = w.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
