package com.vervan.chat.ui.tools

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
@OptIn(ExperimentalMaterial3Api::class)
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
                    ?: throw IllegalStateException("No generation model is active. Load one from Models to translate.")
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
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen(true)
    }
    val requestMicPermissionB = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live conversation translator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
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
                title = "Two people, one private translator",
                body = "Choose two languages and take turns speaking. Translation stays local.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Row(Modifier.fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge("STT: Android Offline")
                Badge("Translation: $modelName")
                Badge("TTS: Android System")
            }
            if (isBusy) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Translating…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(turns) { turn ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromA) Arrangement.Start else Arrangement.End) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (turn.fromA) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(turn.original, style = MaterialTheme.typography.bodyMedium)
                                Text(turn.translated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { OutlinedButton(onClick = { menuAOpen = true }) { Text(langA) }
                        DropdownMenu(expanded = menuAOpen, onDismissRequest = { menuAOpen = false }) {
                            LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { langA = l; menuAOpen = false }) }
                        }
                    }
                    IconButton(
                        modifier = Modifier.padding(top = 8.dp),
                        enabled = !isBusy,
                        onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
                    ) { Icon(Icons.Filled.Mic, "Person A speaks") }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { OutlinedButton(onClick = { menuBOpen = true }) { Text(langB) }
                        DropdownMenu(expanded = menuBOpen, onDismissRequest = { menuBOpen = false }) {
                            LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { langB = l; menuBOpen = false }) }
                        }
                    }
                    IconButton(
                        modifier = Modifier.padding(top = 8.dp),
                        enabled = !isBusy,
                        onClick = { requestMicPermissionB.launch(android.Manifest.permission.RECORD_AUDIO) }
                    ) { Icon(Icons.Filled.Mic, "Person B speaks") }
                }
            }
        }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
