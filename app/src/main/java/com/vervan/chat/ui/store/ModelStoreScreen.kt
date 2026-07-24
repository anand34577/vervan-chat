package com.vervan.chat.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.store.eligibility.EligibilityVerdict
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.theme.Space

/**
 * Tier-1 curated Model Store.
 *
 * Kept as its own screen rather than folded into ModelManagerScreen's "Available for Download"
 * list: that list renders the in-APK [com.vervan.chat.modeldownload.ModelCatalog] through a
 * different install pipeline, and the two have genuinely different concepts (variants,
 * per-device eligibility, licence acceptance, catalogue sync state). Merging them would mean a
 * lowest-common-denominator UI state over both and a second source of truth for install status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ModelStoreViewModel = viewModel(factory = viewModelFactory {
        initializer { ModelStoreViewModel(app) }
    })

    val models by vm.models.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncError by vm.syncError.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    val activeInstall by vm.activeInstall.collectAsStateWithLifecycle()
    val pendingLicense by vm.pendingLicense.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.sync() }, enabled = !syncing) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Check for catalogue updates")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            // A sync failure is advisory: the previously accepted catalogue is still on screen
            // below, so this must not read as a dead end.
            syncError?.let { error ->
                item { NoticeCard("Catalogue update failed", error, isError = true) }
            }
            syncMessage?.let { message ->
                item { NoticeCard("Catalogue", message, isError = false) }
            }

            activeInstall?.let { install ->
                item {
                    ActiveInstallCard(
                        displayName = install.displayName,
                        bytesDownloaded = install.progress?.bytesDownloaded ?: 0,
                        totalBytes = install.progress?.totalBytes ?: 0,
                        error = install.error,
                        onCancel = { vm.cancelInstall() },
                        onDismissError = { vm.dismissInstallError() }
                    )
                }
            }

            if (models.isEmpty()) {
                item { EmptyCatalogCard(syncing = syncing) }
            }

            items(models, key = { it.model.modelId }) { entry ->
                StoreModelCard(
                    entry = entry,
                    installBusy = activeInstall != null,
                    onInstall = { variant -> vm.install(entry.model, variant) },
                    onUninstall = { variantId -> vm.uninstall(variantId) }
                )
            }

            item { Spacer(Modifier.height(Space.xl)) }
        }
    }

    pendingLicense?.let { (model, _) ->
        LicenseDialog(
            modelName = model.displayName,
            licenseName = model.license.name,
            licenseUrl = model.license.url,
            restrictions = model.license.acceptableUseRestrictions,
            usageThresholdClause = model.license.usageThresholdClause,
            onAccept = { vm.acceptLicenseAndInstall() },
            onDismiss = { vm.dismissLicensePrompt() }
        )
    }
}

@Composable
private fun StoreModelCard(
    entry: StoreModelUi,
    installBusy: Boolean,
    onInstall: (ModelVariant) -> Unit,
    onUninstall: (String) -> Unit
) {
    ContentCard {
        Column(Modifier.padding(Space.lg)) {
            Text(entry.model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                entry.model.publisher,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.model.description.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(entry.model.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(Space.sm))
            Text(
                "Licence: ${entry.model.license.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.variants.forEach { variantUi ->
                Spacer(Modifier.height(Space.md))
                VariantRow(
                    variantUi = variantUi,
                    installBusy = installBusy,
                    onInstall = { onInstall(variantUi.variant) },
                    onUninstall = { onUninstall(variantUi.variant.variantId) }
                )
            }
        }
    }
}

@Composable
private fun VariantRow(
    variantUi: StoreVariantUi,
    installBusy: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    val variant = variantUi.variant
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(variant.runtime.wireName, variant.quantization).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatBytes(variant.totalSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                variantUi.installed -> OutlinedButton(onClick = onUninstall) { Text("Remove") }
                // The device check is enforced before the download, never after it — spec §5.
                !variantUi.eligibility.canInstall -> AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Incompatible") },
                    colors = AssistChipDefaults.assistChipColors()
                )
                else -> Button(onClick = onInstall, enabled = !installBusy) { Text("Install") }
            }
        }

        // Degraded and incompatible both explain themselves. Silently hiding a GPU-wanting variant
        // on a CPU-only device would be worse than letting the user choose knowingly.
        if (variantUi.eligibility.verdict != EligibilityVerdict.INSTALLABLE) {
            variantUi.eligibility.reasons.forEach { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (variantUi.eligibility.canInstall) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveInstallCard(
    displayName: String,
    bytesDownloaded: Long,
    totalBytes: Long,
    error: String?,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    ContentCard {
        Column(Modifier.padding(Space.lg)) {
            Text(displayName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.sm))
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(Space.sm))
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            } else {
                if (totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "${formatBytes(bytesDownloaded)} of ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.sm))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, isError: Boolean) {
    ContentCard {
        Column(Modifier.padding(Space.lg)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyCatalogCard(syncing: Boolean) {
    ContentCard {
        Column(Modifier.padding(Space.lg)) {
            Text("No models available yet", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.xs))
            Text(
                if (syncing) {
                    "Checking for the latest catalogue…"
                } else {
                    "Tap refresh to fetch the model catalogue. Downloads always come from the " +
                        "publisher (usually Hugging Face) — this app does not host model weights."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Explicit tap-to-accept, shown before every first download of a model (spec §11/§14). Renders the
 * reviewed licence facts as plain text rather than the model card's Markdown — model-card content
 * is publisher-controlled and must not be rendered as rich text in-app without sanitising.
 */
@Composable
private fun LicenseDialog(
    modelName: String,
    licenseName: String,
    licenseUrl: String,
    restrictions: List<String>,
    usageThresholdClause: String?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accept licence") },
        text = {
            Column {
                Text("$modelName is provided under $licenseName.", style = MaterialTheme.typography.bodyMedium)
                if (licenseUrl.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(licenseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (restrictions.isNotEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Text("Use restrictions:", style = MaterialTheme.typography.labelLarge)
                    restrictions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                usageThresholdClause?.let {
                    Spacer(Modifier.height(Space.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "You are responsible for complying with this licence. The model is downloaded " +
                        "directly from its publisher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("Accept and download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024 * 1024)
    if (gib >= 1) return "%.2f GB".format(gib)
    val mib = bytes / (1024.0 * 1024)
    return if (mib >= 1) "%.0f MB".format(mib) else "$bytes B"
}
