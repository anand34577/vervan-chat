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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
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
                title = { Text(stringResource(R.string.settings_help_troubleshooting)) },
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
                    title = stringResource(R.string.ui_helpsupportscreen_64_start_with_what_you_were_trying_to_do),
                    body = stringResource(R.string.ui_helpsupportscreen_65_each_option_opens_the_setting_or_status_scre),
                    tone = StatusTone.Info
                )

                SectionLabel(stringResource(R.string.ui_helpsupportscreen_how_to))
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_74_set_up_local_ai),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_75_download_import_or_activate_a_model),
                                icon = Icons.Filled.Memory,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_82_chat_with_your_documents),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_83_add_sources_for_grounded_answers_and_citatio),
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = onOpenKnowledge
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_90_adjust_reply_behavior),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_91_change_response_length_tone_and_retrieval),
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

                SectionLabel(stringResource(R.string.ui_helpsupportscreen_fix_problem))
                SectionCard(
                    items = listOf(
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_131_chat_will_not_start),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_132_check_that_a_compatible_model_is_installed_a),
                                icon = Icons.AutoMirrored.Filled.Chat,
                                onClick = onOpenModels
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_139_camera_or_microphone_is_unavailable),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_140_review_android_permissions_for_vervan),
                                icon = Icons.Filled.PermDeviceInformation,
                                onClick = onOpenPermissions
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_147_a_download_or_task_is_stuck),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_148_view_running_work_failures_and_retry_options),
                                icon = Icons.Filled.Download,
                                onClick = onOpenJobs
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_155_the_app_is_using_too_much_space),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_156_review_models_cache_backups_and_deleted_item),
                                icon = Icons.Filled.Storage,
                                onClick = onOpenStorage
                            )
                        },
                        {
                            SectionRow(
                                title = stringResource(R.string.ui_helpsupportscreen_163_something_else_is_not_working),
                                subtitle = stringResource(R.string.ui_helpsupportscreen_164_check_app_health_and_copy_diagnostic_details),
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
