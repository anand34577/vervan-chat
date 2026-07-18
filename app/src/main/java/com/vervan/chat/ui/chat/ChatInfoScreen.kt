package com.vervan.chat.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(chatId: String, onBack: () -> Unit, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val chat by app.container.db.chatDao().observeChat(chatId).collectAsState(initial = null)
    val messages by app.container.db.messageDao().observeMessages(chatId).collectAsState(initial = emptyList())
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val personas by app.container.db.personaDao().observePersonas().collectAsState(initial = emptyList())
    val models by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val linkedDocuments = documents.filter { it.knowledgeBaseId in (chat?.kbIdList() ?: emptyList()) }
    val imagePaths = messages.mapNotNull { it.imagePath }.distinct()
    val links = messages.flatMap { message -> URL.findAll(message.content).map { it.value }.toList() }.distinct()
    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    var previewPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat info") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Text(chat?.title ?: "Chat", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            personas.find { it.id == chat?.personaId }?.name ?: "Default persona",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            models.find { it.id == chat?.modelId }?.displayName ?: "Default generation model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { SectionTitle("Status") }
            item {
                val row = chat
                ListItem(
                    headlineContent = { Text(if (row?.archived == true) "Archived" else "Active") },
                    supportingContent = {
                        Text(
                            "${messages.count { it.role != MessageRole.SYSTEM }} messages · ${if (row?.pinned == true) "Pinned · " else ""}Private on device\n" +
                                "Created ${row?.createdAt?.let { date.format(java.util.Date(it)) } ?: "—"}"
                        )
                    }
                )
            }
            item { SectionTitle("Shared media · ${imagePaths.size}") }
            if (imagePaths.isEmpty()) item { EmptyLine("No shared images") }
            // WhatsApp-style grid of small thumbnails, not one full-width image per row — tap
            // opens the real in-app preview (which itself has an "Open with…" action), instead
            // of jumping straight to an external app chooser.
            items(imagePaths.chunked(3), key = { it.first() }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { path ->
                        val bitmap = com.vervan.chat.model.ImageUtils.decodeThumbnail(path, 240)?.asImageBitmap()
                        Card(
                            Modifier.size(100.dp).clickable { previewPath = path }
                        ) {
                            if (bitmap != null) {
                                Image(bitmap, "Shared image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                // Same "Shared image" description as the decoded-bitmap branch
                                // above — this Card is the tappable target either way, so a
                                // screen reader needs an announcement for a failed thumbnail too.
                                Icon(Icons.Filled.Image, "Shared image", Modifier.fillMaxSize().padding(24.dp))
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Documents · ${linkedDocuments.size}") }
            if (linkedDocuments.isEmpty()) item { EmptyLine("No shared documents") }
            items(linkedDocuments, key = { it.id }) { doc ->
                ListItem(
                    modifier = Modifier.clickable { onOpenDocument(doc.id) },
                    headlineContent = { Text(doc.displayName) },
                    supportingContent = { Text("${doc.mimeType} · ${doc.status.name.lowercase()}") },
                    leadingContent = { Icon(Icons.Filled.Description, null) }
                )
            }
            item { SectionTitle("Shared links · ${links.size}") }
            if (links.isEmpty()) item { EmptyLine("No shared links") }
            items(links, key = { it }) { link ->
                ListItem(
                    modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } },
                    headlineContent = { Text(link, maxLines = 2) },
                    leadingContent = { Icon(Icons.Filled.Link, null) }
                )
            }
        }
        }
    }
    previewPath?.let { path ->
        FullScreenImagePreview(path = path, title = "Shared image", onDismiss = { previewPath = null })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

private fun openFile(context: android.content.Context, file: java.io.File, mimeType: String) {
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with…")) }
}

private val URL = Regex("""https?://[^\s<>\"]+""", RegexOption.IGNORE_CASE)
