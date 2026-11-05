package com.vervan.chat.ui.omni

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.modeldownload.ModelCatalog
import com.vervan.chat.omni.OmniRuntime
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

private const val BUNDLE_ID = "lfm3-omni-2b-litert"
private const val SPEECH_ID = "lfm3-omni-2b-speech"

/**
 * Sets LFM3 Omni up where it is used: both downloads (the 3.2 GB model and the 786 MB speech
 * pack) with their progress, one license prompt for both, and a check that they fit on the phone.
 */
@Composable
internal fun OmniSetup(status: OmniRuntime.Status, onInstalled: () -> Unit, onOpenModels: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val context = LocalContext.current
    val repo = app.container.modelDownloadRepository
    val scope = rememberCoroutineScope()
    val states by repo.uiStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val bundle = ModelCatalog.find(BUNDLE_ID, "1")
    val speech = ModelCatalog.find(SPEECH_ID, "1")
    var confirm by remember { mutableStateOf(false) }

    if (bundle == null || speech == null || !bundle.runsOnThisDevice || !speech.runsOnThisDevice) {
        Text(stringResource(R.string.omni_setup_unsupported), style = MaterialTheme.typography.bodySmall)
        return
    }
    val parts = listOf(
        Triple(bundle, status.bundle != null, states.firstOrNull { it.modelId == BUNDLE_ID }),
        Triple(speech, status.speechDir != null, states.firstOrNull { it.modelId == SPEECH_ID }),
    )
    val missing = parts.filter { (_, have, state) -> !have && (state == null || state.status == ModelStatus.NOT_DOWNLOADED || state.status == ModelStatus.CANCELLED) }
    val missingBytes = missing.sumOf { (it.first.totalExpectedBytes ?: 0L) }
    val enoughSpace = status.freeBytes > missingBytes + status.prepareNeedsBytes

    // The runtime looks at the files themselves; check again once a download lands.
    val readyCount = parts.count { (_, have, state) -> have || state?.status == ModelStatus.READY }
    LaunchedEffect(readyCount) { if (readyCount > 0) onInstalled() }

    Text(stringResource(R.string.omni_needs_downloads), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.omni_setup_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.xs)
    )
    parts.forEach { (model, have, state) ->
        Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (have) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload, contentDescription = null,
                tint = if (have) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f).padding(start = Space.sm)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                val total = state?.totalBytes ?: model.totalExpectedBytes ?: 0L
                val line = when {
                    have -> stringResource(R.string.omni_setup_installed)
                    state == null || state.status == ModelStatus.NOT_DOWNLOADED || state.status == ModelStatus.CANCELLED ->
                        "%.1f GB".format((model.totalExpectedBytes ?: 0L) / 1e9)
                    state.status == ModelStatus.PAUSED -> stringResource(R.string.shruti_paused)
                    state.status == ModelStatus.FAILED -> state.error?.message ?: stringResource(R.string.shruti_failed)
                    state.status == ModelStatus.WAITING_FOR_WIFI -> stringResource(R.string.shruti_waiting_wifi)
                    state.status == ModelStatus.WAITING_FOR_NETWORK -> stringResource(R.string.shruti_waiting_network)
                    state.status == ModelStatus.WAITING_FOR_STORAGE -> stringResource(R.string.shruti_waiting_storage)
                    state.status == ModelStatus.DOWNLOADING ->
                        stringResource(R.string.omni_setup_progress, state.downloadedBytes / 1e9, total / 1e9)
                    else -> stringResource(R.string.shruti_installing)
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!have && state != null && state.status == ModelStatus.DOWNLOADING && total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
                    )
                }
            }
            if (!have && state != null && (state.status == ModelStatus.PAUSED || state.status == ModelStatus.FAILED)) {
                TextButton(onClick = { scope.launch { repo.resumeDownload(model.modelId, model.version) } }) {
                    Text(stringResource(R.string.shruti_retry))
                }
            }
        }
    }
    if (missing.isNotEmpty() && !enoughSpace) {
        Text(
            stringResource(R.string.omni_setup_no_space, (missingBytes + status.prepareNeedsBytes) / 1e9, status.freeBytes / 1e9),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Space.sm)
        )
    }
    Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (missing.isNotEmpty()) {
            Button(onClick = { confirm = true }, enabled = enoughSpace) {
                Text(stringResource(R.string.omni_setup_download, missingBytes / 1e9))
            }
        }
        TextButton(onClick = onOpenModels) { Text(stringResource(R.string.omni_open_models)) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.model_download_accept_the_license)) },
            text = { Text(stringResource(R.string.omni_setup_license, bundle.licenseName ?: "LFM Open License")) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { missing.forEach { (m, _, _) -> repo.startDownload(m.modelId, m.version) } }
                }) { Text(stringResource(R.string.model_download_accept_and_download)) }
            },
            dismissButton = {
                Row {
                    bundle.licenseUrl?.let { url ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }) { Text(stringResource(R.string.model_download_read_license)) }
                    }
                    TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }
}
