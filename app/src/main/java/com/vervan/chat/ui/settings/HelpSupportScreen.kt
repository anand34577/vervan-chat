package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space

/**
 * A plain-language recovery hub. Configuration screens remain focused on changing settings;
 * this screen starts with the user's intent ("chat won't start", "permission is blocked") and
 * routes them to the place that can actually resolve it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenGeneration: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & troubleshooting") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Space.sm)
            ) {
                SystemStatusStrip(
                    title = "Start with what you were trying to do",
                    body = "Each option opens the setting or status screen that can resolve it.",
                    tone = StatusTone.Info
                )

                SectionLabel("How to")
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = "Set up local AI",
                                subtitle = "Download, import, or activate a model",
                                icon = Icons.Filled.Memory,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = "Chat with your documents",
                                subtitle = "Add sources for grounded answers and citations",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = onOpenKnowledge
                            )
                        },
                        {
                            SectionRow(
                                title = "Adjust reply behavior",
                                subtitle = "Change response length, tone, and retrieval",
                                icon = Icons.Filled.Tune,
                                onClick = onOpenGeneration
                            )
                        }
                    )
                )

                SectionLabel("Fix a problem")
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = "Chat will not start",
                                subtitle = "Check that a compatible model is installed and active",
                                icon = Icons.AutoMirrored.Filled.Chat,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = "Camera or microphone is unavailable",
                                subtitle = "Review Android permissions for Vervan",
                                icon = Icons.Filled.PermDeviceInformation,
                                onClick = onOpenPermissions
                            )
                        },
                        {
                            SectionRow(
                                title = "A download or task is stuck",
                                subtitle = "View running work, failures, and retry options",
                                icon = Icons.Filled.Download,
                                onClick = onOpenJobs
                            )
                        },
                        {
                            SectionRow(
                                title = "The app is using too much space",
                                subtitle = "Review models, cache, backups, and deleted items",
                                icon = Icons.Filled.Storage,
                                onClick = onOpenStorage
                            )
                        },
                        {
                            SectionRow(
                                title = "Something else is not working",
                                subtitle = "Check app health and copy diagnostic details",
                                icon = Icons.Filled.BugReport,
                                onClick = onOpenDiagnostics
                            )
                        }
                    ),
                    modifier = Modifier.padding(bottom = Space.xxl)
                )
            }
        }
    }
}
