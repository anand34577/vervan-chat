package com.vervan.chat.ui.onboarding

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

/** A short setup path. Detailed controls remain discoverable in Settings and Model Manager. */
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
    val pages = listOf(
        OnboardPage(
            icon = Icons.Filled.AutoAwesome,
            title = "Your private AI workspace",
            body = "Chat, work with documents, write, study, and use practical tools without sending core AI tasks to the cloud.",
            note = "Vervan is offline-first. AI can still be wrong, so review important information and confirm actions before applying them."
        ),
        OnboardPage(
            icon = Icons.Filled.Memory,
            title = "Ready for local models",
            body = "Android ${Build.VERSION.RELEASE}  •  $abi\n${formatGb(memory.totalMem)} GB RAM  •  ${formatGb(freeStorage)} GB storage free",
            note = "Import compatible generation and embedding models when you are ready. Vervan validates each package and falls back safely when a capability is unavailable.",
            importButton = true
        ),
        OnboardPage(
            icon = Icons.Filled.Tune,
            title = "Choose your starting balance",
            body = "Pick how Vervan should balance speed, context, answer depth, battery use, and device temperature.",
            note = "Balanced works well for most people. You can change this globally or per conversation later.",
            profilePicker = true
        ),
        OnboardPage(
            icon = Icons.Filled.CheckCircle,
            title = "You are ready",
            body = "Start a private chat, add documents to Knowledge, or use Create for notes, personas, templates, and workflows.",
            note = "Tip: use the bottom navigation for your main spaces. Create is always nearby for starting something new."
        )
    )
    val current = pages[page]

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 680.dp).padding(horizontal = Space.xxl, vertical = Space.lg)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(Space.sm).size(20.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text("Vervan", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Private on-device AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${page + 1} of ${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { (page + 1f) / pages.size },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(40.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(current.icon, contentDescription = null, modifier = Modifier.padding(Space.lg).size(36.dp))
                }
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = Space.xxl)
                )
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xxl),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(current.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg))
                }
                if (current.importButton) {
                    OutlinedButton(
                        onClick = onImportModel,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
                    ) { Text("Open Model Manager") }
                }
                if (current.profilePicker) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        ModelProfileType.entries.forEach { profile ->
                            FilterChip(
                                selected = selectedProfile == profile.id,
                                onClick = { selectedProfile = profile.id },
                                label = { Text(profile.label) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.xxl))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (page == pages.lastIndex) {
                        scope.launch { app.container.settingsRepository.setDefaultProfile(selectedProfile) }
                        onDone()
                    } else {
                        page++
                    }
                }) {
                    Text(if (page == pages.lastIndex) "Get started" else "Continue")
                }
            }
        }
    }
}

private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val note: String,
    val importButton: Boolean = false,
    val profilePicker: Boolean = false
)

private fun formatGb(bytes: Long): String = String.format("%.1f", bytes / (1024.0 * 1024 * 1024))
