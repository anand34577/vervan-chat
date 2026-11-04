package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.launch
import java.util.Locale

private data class TranslatedTurn(val fromA: Boolean, val original: String, val translated: String)

private val LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese", "Hindi", "Bengali",
    "Chinese", "Japanese", "Korean", "Arabic", "Russian", "Dutch", "Turkish"
)

private fun localeFor(languageName: String): Locale? =
    Locale.getAvailableLocales().firstOrNull { it.getDisplayLanguage(Locale.ENGLISH).equals(languageName, ignoreCase = true) }

/** Two-person speak-and-translate interface — each side speaks in their own language via the
 * device's offline recognizer, the LLM translates, and the result is read aloud via TTS in the
 * other side's language (best-effort voice match by language name; falls back silently if the
 * device has no matching TTS voice installed). Badges make every hop's origin explicit. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LiveConversationTranslatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var langA by remember { mutableStateOf("English") }
    var langB by remember { mutableStateOf("Spanish") }
    var menuAOpen by remember { mutableStateOf(false) }
    var menuBOpen by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf(listOf<TranslatedTurn>()) }
    var isBusy by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf("model") }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        modelName = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)?.displayName ?: "model"
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        tts = instance
        onDispose { instance.shutdown() }
    }
    fun speak(text: String, languageName: String) {
        if (!ttsReady || text.isBlank()) return
        localeFor(languageName)?.let { tts?.language = it }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "live-translate")
    }

    fun handleSpeech(fromA: Boolean, text: String) {
        val sourceLang = if (fromA) langA else langB
        val targetLang = if (fromA) langB else langA
        isBusy = true
        scope.launch {
            try {
                val prompt = "Translate the following from $sourceLang to $targetLang. Respond with ONLY the translation.\n\nText:\n$text"
                val translated = OneShotLlm.run(
                    app, prompt,
                    runContext = com.vervan.chat.llm.ToolRunContext("tools/live-translator", "Live translator · $targetLang", text),
                )?.trim()
                    ?: throw IllegalStateException("No model is ready. Open Settings → AI models and load one.")
                turns = turns + TranslatedTurn(fromA, text, translated)
                speak(translated, targetLang)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar(t.toUserMessage())
            } finally {
                isBusy = false
            }
        }
    }

    val recognizeForA = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { handleSpeech(true, it) }
        }
    }
    val recognizeForB = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { handleSpeech(false, it) }
        }
    }
    fun listen(forA: Boolean) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            localeFor(if (forA) langA else langB)?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it.toLanguageTag()) }
        }
        if (forA) recognizeForA.launch(intent) else recognizeForB.launch(intent)
    }
    val requestMicPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) listen(true)
    }
    val requestMicPermissionB = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) listen(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_conversation_translator_live_conversation_translator)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Keep the newest translated turn visible as the conversation grows.
        LaunchedEffect(turns.size) { if (turns.isNotEmpty()) runCatching { listState.animateScrollToItem(turns.lastIndex) } }
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize()) {
            ToolIntro(
                icon = Icons.Filled.Translate,
                title = stringResource(R.string.live_conversation_translator_two_people_one_private_translat),
                body = stringResource(R.string.live_conversation_translator_choose_two_languages_and_take),
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)
            )
            // Wraps instead of scrolling: the third badge was cut off mid-word on a phone.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Badge("Translation: $modelName")
                Badge("Speech: on this phone")
            }
            if (isBusy) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.live_conversation_translator_translating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Space.sm))
                }
            }
            // One or the other gets the space. Both used to take weight(1f), so an empty screen
            // was split in half and the empty state floated in the top half.
            if (turns.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    com.vervan.chat.ui.common.EmptyState(
                        icon = androidx.compose.material.icons.Icons.Filled.Translate,
                        title = stringResource(R.string.live_translator_empty_title),
                        body = stringResource(R.string.live_translator_empty_body)
                    )
                }
            } else LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(turns) { turn ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromA) Arrangement.Start else Arrangement.End) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (turn.fromA) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.padding(Space.sm)) {
                                Text(turn.original, style = MaterialTheme.typography.bodyMedium)
                                Text(turn.translated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // One clear panel per speaker: their language, then a full-size "Speak" button. The
            // mic used to be a bare 24dp icon under each language with no label or container.
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                SpeakerPanel(
                    label = stringResource(R.string.live_translator_person_a),
                    language = langA,
                    menuOpen = menuAOpen,
                    onMenuOpen = { menuAOpen = it },
                    onLanguage = { langA = it },
                    enabled = !isBusy,
                    speakDescription = stringResource(R.string.live_conversation_translator_person_a_speaks),
                    onSpeak = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
                SpeakerPanel(
                    label = stringResource(R.string.live_translator_person_b),
                    language = langB,
                    menuOpen = menuBOpen,
                    onMenuOpen = { menuBOpen = it },
                    onLanguage = { langB = it },
                    enabled = !isBusy,
                    speakDescription = stringResource(R.string.live_conversation_translator_person_b_speaks),
                    onSpeak = { requestMicPermissionB.launch(android.Manifest.permission.RECORD_AUDIO) },
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        }
    }
}

@Composable
private fun SpeakerPanel(
    label: String,
    language: String,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    enabled: Boolean,
    speakDescription: String,
    onSpeak: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.xs))
            Box {
                OutlinedButton(onClick = { onMenuOpen(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(language, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
                    LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { onLanguage(l); onMenuOpen(false) }) }
                }
            }
            val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.live_translator_speak), modifier = Modifier.padding(start = Space.xs))
            }
            val speakModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = speakDescription }
            if (primary) androidx.compose.material3.Button(onClick = onSpeak, enabled = enabled, modifier = speakModifier, content = content)
            else androidx.compose.material3.FilledTonalButton(onClick = onSpeak, enabled = enabled, modifier = speakModifier, content = content)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs))
    }
}
