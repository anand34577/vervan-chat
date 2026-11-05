package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space

/**
 * One-tap "open a chat already grounded in this file" launcher — the zero-ceremony path to the
 * RAG features that otherwise need a Knowledge base set up first. Picking a file is all this
 * screen does; the nav graph creates the chat, stashes the Uri, and the freshly-opened
 * [com.vervan.chat.ui.chat.ChatScreen] attaches + indexes it (see ChatViewModel.attachDocument).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWithFileScreen(onBack: () -> Unit, onFileChosen: (Uri) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var rejected by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull().orEmpty()
        val type = context.contentResolver.getType(uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (type in SUPPORTED_TYPES || type.startsWith("text/") || ext in SUPPORTED_EXTENSIONS) {
            rejected = null
            onFileChosen(uri)
        } else {
            rejected = name.ifBlank { type }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_with_file_chat_with_a_file)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 720.dp) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
                ToolIntro(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.chat_with_file_ask_questions_about_a_document),
                    body = stringResource(R.string.chat_with_file_choose_a_pdf_word_or)
                )
                ToolStep(1, stringResource(R.string.cwf_how_title))
                listOf(R.string.cwf_how_1, R.string.cwf_how_2, R.string.cwf_how_3).forEachIndexed { i, res ->
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = Space.sm))
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.cwf_formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
                rejected?.let {
                    com.vervan.chat.ui.common.SystemStatusStrip(
                        title = stringResource(R.string.cwf_unsupported_title),
                        body = stringResource(R.string.cwf_unsupported_body, it),
                        tone = com.vervan.chat.ui.common.StatusTone.Warning,
                        modifier = Modifier.padding(top = Space.md)
                    )
                }
                ToolPrimaryButton(
                    label = stringResource(R.string.chat_with_file_choose_a_file),
                    icon = Icons.Filled.UploadFile,
                    onClick = { pickFile.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(top = Space.md)
                )
            }
        }
    }
}

private val SUPPORTED_TYPES = setOf(
    "application/pdf", "application/epub+zip", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/json", "application/rtf",
)
private val SUPPORTED_EXTENSIONS = setOf("pdf", "epub", "doc", "docx", "ppt", "pptx", "txt", "md", "markdown", "csv", "json", "html", "htm", "rtf", "log")
