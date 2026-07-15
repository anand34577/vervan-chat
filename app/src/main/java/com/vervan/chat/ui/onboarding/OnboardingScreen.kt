package com.vervan.chat.ui.onboarding

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.ModelProfileType
import kotlinx.coroutines.launch

/**
 * Multi-step setup covering the spec's §6.1 onboarding flow: welcome, offline-first,
 * AI fallibility, device scan, generation-model setup, embedding-model setup, performance
 * profile, optional user profile, offline tutorial tips, and ready.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, onImportModel: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfile by rememberSaveable { mutableStateOf("BALANCED") }

    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val freeStorage = StatFs(context.filesDir.path).availableBytes
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()

    val pages = buildList {
        add(OnboardPage("Welcome to Vervan", "Your private AI workspace for chat, documents, writing, coding, study, and local actions.", "Everything important stays on this device — no cloud AI calls for core features."))
        add(OnboardPage("Offline-first by design", "After a compatible model is installed, core features work in airplane mode: chat, image understanding, voice dictation, notes, document indexing, semantic search, personas, memory, and projects.", "Optional networking is used only for explicit actions like downloading a model or opening an external link."))
        add(OnboardPage("AI can be wrong", "Treat responses as a helpful draft, not authoritative. Verify important information and confirm actions before they run.", "The app distinguishes model knowledge from retrieved local evidence and always shows you what influenced an answer."))
        add(OnboardPage("Device capability scan", "Android ${Build.VERSION.RELEASE} · $abi\n${formatGb(memory.totalMem)} GB RAM · ${formatGb(freeStorage)} GB storage free.", "Models use app-managed storage. NPU/GPU is tried first, then CPU; actual compatibility is verified during import — never assumed from the filename."))
        add(OnboardPage("Generation model", "Import a Gemma-compatible generation package for chat, image input, reasoning, and tool calls.", "Each model is copied locally, hashed, validated, sanity-tested, and activated only after license acknowledgment.", importButton = true))
        add(OnboardPage("Embedding model", "Import an EmbeddingGemma package to enable semantic search across your documents.", "Without one, the app falls back to keyword search — everything still works, just less precisely.", importButton = true))
        add(OnboardPage("Performance profile", "Pick a default profile that shapes context budget, retrieval depth, and output length. You can change it per chat anytime.", "Fast for quick questions, Balanced for everyday use, Quality for complex analysis, Battery saver and Thermal safe for constrained conditions.", profilePicker = true))
        add(OnboardPage("Optional: user profile", "Tell the AI about yourself so responses fit you — your name, occupation, languages, and coding preferences.", "Nothing here is learned from your conversations; you set it explicitly. You can do this later in Settings.", optional = true))
        add(OnboardPage("Offline tutorial", "• Type / to use slash commands and templates\n• Type @ to attach documents, notes, or memories\n• Long-press a response to branch or compare\n• Tap the info icon to inspect what's in context\n• Pin important chats and group work into projects", "Explore freely — nothing you do here can't be undone via the recycle bin."))
        add(OnboardPage("You are ready", "Start a chat, import documents into Knowledge, or use Create for notes, personas, templates, and workflows.", "Sources, memory, persona, model, tools, and context remain inspectable from each chat."))
    }
    val current = pages[page]

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
      Column(
        Modifier.fillMaxSize().widthIn(max = 720.dp).padding(horizontal = 24.dp, vertical = 16.dp)
      ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("VERVAN · SETUP ${page + 1}/${pages.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (page + 1f) / pages.size }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Spacer(Modifier.height(24.dp))
            Text(current.title, style = MaterialTheme.typography.headlineMedium)
            Text(current.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(current.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            if (current.importButton) {
                OutlinedButton(onClick = onImportModel, modifier = Modifier.padding(top = 16.dp)) { Text("Open model manager") }
            }
            if (current.profilePicker) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModelProfileType.entries.forEach { p ->
                        FilterChip(selected = selectedProfile == p.id, onClick = { selectedProfile = p.id }, label = { Text(p.label) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Back") }
            Button(onClick = {
                if (page == pages.lastIndex) {
                    scope.launch { app.container.settingsRepository.setDefaultProfile(selectedProfile) }
                    onDone()
                } else page++
            }) {
                Text(if (page == pages.lastIndex) "Finish setup" else "Continue")
            }
        }
      }
    }
}

private data class OnboardPage(
    val title: String,
    val body: String,
    val note: String,
    val importButton: Boolean = false,
    val profilePicker: Boolean = false,
    val optional: Boolean = false
)

private fun formatGb(bytes: Long): String = String.format("%.1f", bytes / (1024.0 * 1024 * 1024))
