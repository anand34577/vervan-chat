package com.vervan.chat.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.common.ScrollablePage
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
                title = { Text(stringResource(R.string.help_support_help_troubleshooting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
                SystemStatusStrip(
                    title = stringResource(R.string.help_support_start_with_what_you_were),
                    body = stringResource(R.string.help_support_each_option_opens_the_setting),
                    tone = StatusTone.Info
                )

                SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_help_support_how_to))
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_set_up_local_ai),
                                subtitle = stringResource(R.string.help_support_download_import_or_activate_a),
                                icon = Icons.Filled.Memory,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_chat_with_your_documents),
                                subtitle = stringResource(R.string.help_support_add_sources_for_grounded_answers),
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = onOpenKnowledge
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_adjust_reply_behavior),
                                subtitle = stringResource(R.string.help_support_change_response_length_tone_and),
                                icon = Icons.Filled.Tune,
                                onClick = onOpenGeneration
                            )
                        }
                    )
                )

                SectionLabel(stringResource(R.string.shortcut_section))
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = stringResource(R.string.shortcut_new_chat),
                                subtitle = stringResource(R.string.shortcut_new_chat_keys),
                                icon = Icons.Filled.Keyboard
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.shortcut_search),
                                subtitle = stringResource(R.string.shortcut_search_keys),
                                icon = Icons.Filled.Keyboard
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.shortcut_settings),
                                subtitle = stringResource(R.string.shortcut_settings_keys),
                                icon = Icons.Filled.Keyboard
                            )
                        }
                    )
                )

                SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_help_support_fix_a_problem))
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_chat_will_not_start),
                                subtitle = stringResource(R.string.help_support_check_that_a_compatible_model),
                                icon = Icons.AutoMirrored.Filled.Chat,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_camera_or_microphone_is_unavailable),
                                subtitle = stringResource(R.string.help_support_review_android_permissions_for_vervan),
                                icon = Icons.Filled.PermDeviceInformation,
                                onClick = onOpenPermissions
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_a_download_or_task_is),
                                subtitle = stringResource(R.string.help_support_view_running_work_failures_and),
                                icon = Icons.Filled.Download,
                                onClick = onOpenJobs
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_the_app_is_using_too),
                                subtitle = stringResource(R.string.help_support_review_models_cache_backups_and),
                                icon = Icons.Filled.Storage,
                                onClick = onOpenStorage
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.help_support_something_else_is_not_working),
                                subtitle = stringResource(R.string.help_support_check_app_health_and_copy),
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
