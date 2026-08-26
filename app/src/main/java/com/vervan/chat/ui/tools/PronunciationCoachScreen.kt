package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronunciationCoachScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var target by remember { mutableStateOf("") }
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
            val targetWords = target.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            val heardWords = heard.lowercase().split(Regex("\\s+")).toSet()
            missedWords = targetWords.filter { it.trim(',', '.', '!', '?') !in heardWords }
        }
    }
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
                title = { Text(stringResource(R.string.ui_pronunciationcoachscreen_90_pronunciation_coach)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            ToolIntro(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.ui_pronunciationcoachscreen_98_listen_repeat_improve),
                body = stringResource(R.string.ui_pronunciationcoachscreen_99_hear_a_phrase_repeat_it_and_compare_the_offl)
            )
            OutlinedTextField(
                value = target, onValueChange = { target = it.take(InputLimits.PRONUNCIATION_TEXT_CHARS); heardText = null },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg), placeholder = { Text(stringResource(R.string.ui_pronunciationcoachscreen_103_word_or_phrase_to_practice)) }
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(
                    onClick = { if (ttsReady) tts?.speak(target, TextToSpeech.QUEUE_FLUSH, null, "pronounce") },
                    enabled = target.isNotBlank(), modifier = Modifier.weight(1f)
                ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp)); Text(stringResource(R.string.ui_pronunciationcoachscreen_109_hear_it), modifier = Modifier.padding(start = Space.sm)) }
                OutlinedButton(
                    onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    enabled = target.isNotBlank(), modifier = Modifier.weight(1f)
                ) { Icon(Icons.Filled.Mic, null, Modifier.size(18.dp)); Text(stringResource(R.string.ui_pronunciationcoachscreen_113_repeat_it), modifier = Modifier.padding(start = Space.sm)) }
            }
            heardText?.let { heard ->
                Card(
                    Modifier.fillMaxWidth().padding(top = Space.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(Space.md)) {
                        Text(stringResource(R.string.ui_pronunciationcoachscreen_heard, heard), style = MaterialTheme.typography.bodyMedium)
                        confidence?.let { Text(stringResource(R.string.ui_pronunciationcoachscreen_confidence, (it * 100).toInt()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Space.xs)) }
                        if (missedWords.isEmpty()) {
                            Text(stringResource(R.string.ui_pronunciationcoachscreen_124_sounded_right), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.sm))
                        } else {
                            Text(
                                stringResource(R.string.ui_pronunciationcoachscreen_mispronounced, missedWords.joinToString(", ")),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = Space.sm)
                            )
                        }
                    }
                }
            }
    }
    }
}
