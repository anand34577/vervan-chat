package com.vervan.chat.ui.store

import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.store.eligibility.EligibilityVerdict
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
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
                title = { Text(stringResource(R.string.ui_modelstorescreen_86_model_store)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.sync() }, enabled = !syncing) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (syncing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.ui_modelstorescreen_98_check_for_catalogue_updates))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.sm, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
           ) {
             item {
                 FeatureHero(
                     icon = Icons.Filled.CloudDownload,
                     eyebrow = stringResource(R.string.ui_modelstorescreen_115_curated_and_device_aware),
                     title = stringResource(R.string.ui_modelstorescreen_116_download_a_model_with_confidence),
                     body = stringResource(R.string.ui_modelstorescreen_117_review_runtime_size_licensing_and_device_eli)
                 )
             }
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
            OverflowTooltipText(
                text = entry.model.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
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
                OverflowTooltipText(
                    text = listOfNotNull(variant.runtime.wireName, variant.quantization).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    formatBytes(variant.totalSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                variantUi.installed -> OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.padding(start = Space.sm),
                ) { Text(stringResource(R.string.action_remove)) }
                // The device check is enforced before the download, never after it —
                !variantUi.eligibility.canInstall -> AssistChip(
                    onClick = {},
                    enabled = false,
                    shape = MaterialTheme.shapes.small,
                    label = { Text(stringResource(R.string.ui_modelstorescreen_253_incompatible)) },
                    colors = AssistChipDefaults.assistChipColors(),
                    modifier = Modifier.padding(start = Space.sm),
                )
                else -> Button(
                    onClick = onInstall,
                    enabled = !installBusy,
                    modifier = Modifier.padding(start = Space.sm),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.ui_modelstorescreen_262_install)) }
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
            OverflowTooltipText(displayName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.sm))
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(Space.sm))
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.ui_modelstorescreen_300_dismiss)) }
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
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
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
            Text(stringResource(R.string.ui_modelstorescreen_341_no_models_available_yet), style = MaterialTheme.typography.titleSmall)
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
 * Explicit tap-to-accept, shown before every first download of a model. Renders the
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
    val safeLicenseName = licenseName.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        ?: "Licence information unavailable"
    val safeLicenseUrl = licenseUrl.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    val safeUsageThresholdClause = usageThresholdClause?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_modelstorescreen_379_accept_licence)) },
        text = {
            Column {
                Text(stringResource(R.string.ui_modelstorescreen_license_provider, modelName, safeLicenseName), style = MaterialTheme.typography.bodyMedium)
                if (safeLicenseUrl != null) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        safeLicenseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (restrictions.isNotEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Text(stringResource(R.string.ui_modelstorescreen_395_use_restrictions), style = MaterialTheme.typography.labelLarge)
                    restrictions.forEach { Text(stringResource(R.string.ui_modelstorescreen_restriction, it), style = MaterialTheme.typography.bodySmall) }
                }
                safeUsageThresholdClause?.let {
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
        confirmButton = { Button(onClick = onAccept, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.ui_modelstorescreen_411_accept_and_download)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024 * 1024)
    if (gib >= 1) return "%.2f GB".format(gib)
    val mib = bytes / (1024.0 * 1024)
    return if (mib >= 1) "%.0f MB".format(mib) else "$bytes B"
}
