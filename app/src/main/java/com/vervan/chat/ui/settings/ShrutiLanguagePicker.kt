package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.modeldownload.ModelCatalog
import com.vervan.chat.modeldownload.ModelUiState
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.voice.ShrutiSttEngine
import kotlinx.coroutines.launch

/** What each Shruti model hears besides English (udgamlabs.com/models/shruti). */
private val SHRUTI_COVERS = mapOf(
    "hi" to "Hindi, English and Hinglish",
    "mr" to "Marathi and English",
    "ta" to "Tamil and English",
    "bn" to "Bengali and English",
    "te" to "Telugu and English",
    "gu" to "Gujarati and English",
)

private val ACTIVE = setOf(
    ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI,
    ModelStatus.WAITING_FOR_STORAGE, ModelStatus.DOWNLOADING, ModelStatus.DOWNLOADED, ModelStatus.VERIFYING,
    ModelStatus.IMPORTING, ModelStatus.PAUSING, ModelStatus.CANCELLING, ModelStatus.DELETING
)

/**
 * Every Shruti language in one list: download the ones you speak, pick which one listens, and
 * remove the ones you no longer need — without leaving Voice settings. Each model is ~30 MB and
 * also understands English, so most people need exactly one.
 */
@Composable
fun ShrutiLanguagePicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as VervanApp
    val repo = app.container.modelDownloadRepository
    val scope = rememberCoroutineScope()
    val states by repo.uiStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog = remember { ModelCatalog.all.filter { it.ttsEngine == ShrutiSttEngine.ENGINE && it.runsOnThisDevice } }
    val byId = states.associateBy { it.modelId }
    val installed = catalog.filter { byId[it.modelId]?.status == ModelStatus.READY }.mapNotNull { it.ttsLanguage }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    // A first download becomes the active language; so does the next one left after a removal.
    LaunchedEffect(installed, selected) {
        if (installed.isNotEmpty() && selected !in installed) onSelect(installed.first())
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.shruti_languages_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Space.md)
        )
        Text(
            stringResource(R.string.shruti_languages_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.xs)
        )
        catalog.forEachIndexed { index, model ->
            val lang = model.ttsLanguage ?: return@forEachIndexed
            val name = ShrutiSttEngine.LANGUAGES[lang] ?: lang
            val state = byId[model.modelId]
            if (index > 0) HorizontalDivider()
            ShrutiRow(
                name = name,
                covers = SHRUTI_COVERS[lang] ?: "$name and English",
                sizeMb = ((model.totalExpectedBytes ?: 0L) / 1_000_000).toInt(),
                state = state,
                isSelected = selected == lang,
                onSelect = { onSelect(lang) },
                onDownload = { scope.launch { repo.startDownload(model.modelId, model.version) } },
                onResume = { scope.launch { repo.resumeDownload(model.modelId, model.version) } },
                onCancel = { scope.launch { repo.cancelDownload(model.modelId, model.version, keepPartial = false) } },
                onDelete = { confirmDelete = model.modelId }
            )
        }
    }

    confirmDelete?.let { id ->
        val model = catalog.first { it.modelId == id }
        val name = ShrutiSttEngine.LANGUAGES[model.ttsLanguage] ?: model.displayName
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.shruti_remove_title, name)) },
            text = { Text(stringResource(R.string.shruti_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { repo.deleteDownload(model.modelId, model.version) }
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ShrutiRow(
    name: String,
    covers: String,
    sizeMb: Int,
    state: ModelUiState?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = state?.status ?: ModelStatus.NOT_DOWNLOADED
    val ready = status == ModelStatus.READY
    val rowModifier = if (ready) {
        Modifier.selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
    } else Modifier
    Row(
        rowModifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        if (ready) RadioButton(selected = isSelected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            val detail = when (status) {
                ModelStatus.READY -> if (isSelected) stringResource(R.string.shruti_in_use, covers) else covers
                ModelStatus.NOT_DOWNLOADED, ModelStatus.CANCELLED -> stringResource(R.string.shruti_not_downloaded, covers, sizeMb)
                ModelStatus.WAITING_FOR_WIFI -> stringResource(R.string.shruti_waiting_wifi)
                ModelStatus.WAITING_FOR_NETWORK -> stringResource(R.string.shruti_waiting_network)
                ModelStatus.WAITING_FOR_STORAGE -> stringResource(R.string.shruti_waiting_storage)
                ModelStatus.PAUSED -> stringResource(R.string.shruti_paused)
                ModelStatus.FAILED -> state?.error?.message ?: stringResource(R.string.shruti_failed)
                ModelStatus.VERIFYING, ModelStatus.DOWNLOADED, ModelStatus.IMPORTING -> stringResource(R.string.shruti_installing)
                else -> {
                    val total = state?.totalBytes ?: 0L
                    val pct = if (total > 0) (state!!.downloadedBytes * 100 / total).toInt() else 0
                    stringResource(R.string.shruti_downloading, pct)
                }
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (status == ModelStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status in ACTIVE) {
                val total = state?.totalBytes ?: 0L
                if (status == ModelStatus.DOWNLOADING && total > 0) {
                    LinearProgressIndicator(
                        progress = { (state!!.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.xs))
                }
            }
        }
        when {
            ready -> IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shruti_remove_title, name))
            }
            status == ModelStatus.PAUSED || status == ModelStatus.FAILED ->
                OutlinedButton(onClick = onResume) { Text(stringResource(R.string.shruti_retry)) }
            status in ACTIVE -> IconButton(
                onClick = onCancel,
                enabled = status !in setOf(ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.CANCELLING, ModelStatus.DELETING)
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.shruti_cancel_download, name))
            }
            else -> OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.shruti_download)) }
        }
    }
}
