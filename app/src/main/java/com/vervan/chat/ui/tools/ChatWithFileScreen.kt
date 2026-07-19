package com.vervan.chat.ui.tools

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
                title = { Text("Chat with a file") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 720.dp) {
            Column(Modifier.fillMaxSize().padding(Space.lg)) {
                ToolIntro(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Ask questions about a document",
                    body = "Pick a PDF, Word, or text file and start a chat grounded in it — answers cite the file, and nothing leaves your device."
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
                    Text("  Choose a file", modifier = Modifier.padding(start = Space.xs))
                }
            }
        }
    }
}
