package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

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
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onFileChosen)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_with_file_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 720.dp) {
            Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
                ToolIntro(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.ui_chatwithfilescreen_55_ask_questions_about_a_document),
        body = stringResource(R.string.ui_chatwithfilescreen_56_choose_a_pdf_word_or_text_file_answers_cite)
                )
                Button(
                    onClick = {
                        pickFile.launch(
                            arrayOf(
                                "application/pdf", "text/*", "application/epub+zip",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xl)
                ) {
                    Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.chat_with_file_choose), modifier = Modifier.padding(start = Space.sm))
                }
            }
        }
    }
}
