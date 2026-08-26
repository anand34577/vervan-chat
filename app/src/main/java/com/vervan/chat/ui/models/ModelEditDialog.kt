package com.vervan.chat.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vervan.chat.ui.common.VervanFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
import com.vervan.chat.ui.theme.vervanSubtleDividerColor
import com.vervan.chat.llm.ThinkingSpec
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.vervan.chat.ui.common.VervanToggle as Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.FileDownloadStatus
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.data.db.entities.ToolApprovalMode
import com.vervan.chat.data.db.entities.canSupportAudio
import com.vervan.chat.data.db.entities.canSupportVision
import com.vervan.chat.data.db.entities.displayName
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.modeldownload.ModelAction
import com.vervan.chat.modeldownload.ModelUiState
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.ValidationMessage
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch
import java.io.File


/** Model edit dialog and its capability/override input controls. */

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ModelEditDialog(
    model: ModelInfo,
    defaults: ModelDefaults,
    expertMode: Boolean,
    useMlockDefault: Boolean,
    flashAttentionModeDefault: String,
    kvCacheTypeDefault: String,
    onDismiss: () -> Unit,
    // apiKey is non-null only for a REMOTE_API model whose key field was touched — see the
    // `isRemote` state above. Null/blank on save means "keep whatever key is already stored".
    onSave: (ModelInfo, apiKey: String?) -> Unit
) {
    var displayName by remember(model.id) { mutableStateOf(model.displayName) }
    // Gates the load-time tuning sections (threads, batch sizes, mlock, KV cache, Vulkan device).
    // Used both inside the Configure dialog body and in the Save action (which lives in a separate
    // scope), so declared at the top of the composable.
    val isLlamaCpp = model.traits.hasNativeTuningKnobs
    // Toggle instead of Auto/On/Off (user ask): a model's capability is simply on or off,
    // defaulting to on until the model actually proves otherwise (see reconcileCapabilities).
    // A toggle is only meaningful when the engine can physically deliver the capability —
    // llama.cpp needs an mmproj projector for vision (canSupportVision) and has no audio-input
    // JNI at all (canSupportAudio). When the prerequisite is missing the toggle stays off and
    // renders disabled, instead of letting the user turn on something that can never work.
    val visionSupported = remember(model.id, model.mmprojPath) { model.canSupportVision() }
    val audioSupported = remember(model.id, model.engine) { model.canSupportAudio() }
    var vision by remember(model.id) { mutableStateOf(visionSupported && model.supportsVision != false) }
    var audio by remember(model.id) { mutableStateOf(audioSupported && model.supportsAudio != false) }
    var tools by remember(model.id) { mutableStateOf(model.supportsTools != false) }
    var thinking by remember(model.id) { mutableStateOf(model.supportsThinking != false) }
    val initialThinkingSpec = remember(model.id, model.thinkingSpecJson, model.chatTemplateOverride) {
        ThinkingSpec.forModel(model)
    }
    var thinkingActivation by remember(model.id) { mutableStateOf(initialThinkingSpec.activation) }
    var thinkingEnableText by remember(model.id) { mutableStateOf(initialThinkingSpec.enableText.orEmpty()) }
    var remoteThinkingParameter by remember(model.id) {
        mutableStateOf(initialThinkingSpec.remoteParameter ?: "reasoning_effort")
    }
    var defaultThinkingMode by remember(model.id) { mutableStateOf(model.defaultThinkingMode ?: "OFF") }
    var mtpEnabled by remember(model.id) { mutableStateOf(model.mtpEnabled) }
    // llama.cpp has no NPU backend — a stale NPU choice persisted by an older build is shown
    // (and re-saved) as AUTO, which is what the load coordinator resolves it to anyway.
    var backend by remember(model.id) {
        mutableStateOf(
            if (model.traits.hasNativeTuningKnobs && model.preferredBackend == BackendChoice.NPU) BackendChoice.AUTO
            else model.preferredBackend
        )
    }
    var approvalMode by remember(model.id) { mutableStateOf(model.toolApprovalMode) }

    // REMOTE_API only — connection fields a local model has no equivalent of. Left blank/unset,
    // the API key means "keep the existing one" (same convention the old dedicated remote dialog
    // used); it's never read back from RemoteApiKeyStore to prefill, same reasoning as never
    // showing a stored password back in plaintext.
    val isRemote = !model.traits.runsOnDevice
    var remoteBaseUrl by remember(model.id) { mutableStateOf(model.remoteBaseUrl.orEmpty()) }
    var remoteApiModelId by remember(model.id) { mutableStateOf(model.remoteApiModelId.orEmpty()) }
    var remoteApiKey by remember(model.id) { mutableStateOf("") }
    val remoteBaseUrlError = if (isRemote) com.vervan.chat.llm.RemoteOpenAiEngine.baseUrlError(remoteBaseUrl) else null

    // Every generation-default field is "use the app-wide Settings value" until the user
    // flips its own override switch — that's the default-then-customize-per-model model the
    // user asked for, instead of every field silently pinning to whatever it showed on Save.
    var temperatureOn by remember(model.id) { mutableStateOf(model.temperature != null) }
    var temperature by remember(model.id) { mutableFloatStateOf(model.temperature ?: defaults.temperature) }
    var topPOn by remember(model.id) { mutableStateOf(model.topP != null) }
    var topP by remember(model.id) { mutableFloatStateOf(model.topP ?: defaults.topP) }
    var topKOn by remember(model.id) { mutableStateOf(model.topK != null) }
    var topK by remember(model.id) { mutableFloatStateOf((model.topK ?: defaults.topK).toFloat()) }
    var maxImagesOn by remember(model.id) { mutableStateOf(model.maxNumImages != null) }
    var maxImages by remember(model.id) { mutableFloatStateOf((model.maxNumImages ?: defaults.maxNumImages).toFloat()) }
    var contextOn by remember(model.id) { mutableStateOf(model.contextTokens != null) }
    var context by remember(model.id) { mutableFloatStateOf((model.contextTokens ?: defaults.contextTokens).toFloat()) }
    var seedOn by remember(model.id) { mutableStateOf(model.seed != null) }
    var seed by remember(model.id) { mutableStateOf((model.seed ?: 0).toString()) }

    // Common (both engines) — always visible.
    var minPOn by remember(model.id) { mutableStateOf(model.minP != null) }
    var minP by remember(model.id) { mutableFloatStateOf(model.minP ?: defaults.minP) }
    var repetitionPenaltyOn by remember(model.id) { mutableStateOf(model.repetitionPenalty != null) }
    var repetitionPenalty by remember(model.id) { mutableFloatStateOf(model.repetitionPenalty ?: defaults.repetitionPenalty) }
    var maxOutputTokensOn by remember(model.id) { mutableStateOf(model.maxOutputTokens != null) }
    var maxOutputTokens by remember(model.id) { mutableFloatStateOf((model.maxOutputTokens ?: defaults.maxOutputTokens).toFloat()) }
    var stopSequencesOn by remember(model.id) { mutableStateOf(model.stopSequences != null) }
    var stopSequences by remember(model.id) { mutableStateOf(model.stopSequences ?: "") }

    // llama.cpp-only, expert-tier.
    var gpuLayerCountOn by remember(model.id) { mutableStateOf(model.gpuLayerCount != null) }
    var gpuLayerCount by remember(model.id) { mutableFloatStateOf((model.gpuLayerCount ?: (model.layerCount ?: 32)).toFloat()) }
    var cpuThreadsOn by remember(model.id) { mutableStateOf(model.cpuThreads != null) }
    var cpuThreads by remember(model.id) { mutableFloatStateOf((model.cpuThreads ?: defaults.cpuThreads.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()).toFloat()) }
    var nBatchOn by remember(model.id) { mutableStateOf(model.nBatch != null) }
    var nBatch by remember(model.id) { mutableFloatStateOf((model.nBatch ?: defaults.nBatch).toFloat()) }
    var nUbatchOn by remember(model.id) { mutableStateOf(model.nUbatch != null) }
    var nUbatch by remember(model.id) { mutableFloatStateOf((model.nUbatch ?: defaults.nUbatch).toFloat()) }
    var useMlockOn by remember(model.id) { mutableStateOf(model.useMlock != null) }
    var useMlock by remember(model.id) { mutableStateOf(model.useMlock ?: useMlockDefault) }
    var flashAttentionOn by remember(model.id) { mutableStateOf(model.flashAttention != null) }
    var flashAttentionMode by remember(model.id) {
        mutableStateOf(model.flashAttention?.let { if (it) "On" else "Off" } ?: flashAttentionModeDefault.lowercase().replaceFirstChar(Char::uppercase))
    }
    var kvCacheTypeOn by remember(model.id) { mutableStateOf(model.kvCacheType != null) }
    var kvCacheType by remember(model.id) { mutableStateOf(model.kvCacheType ?: kvCacheTypeDefault) }
    var vulkanDeviceIndexOn by remember(model.id) { mutableStateOf(model.vulkanDeviceIndex != null) }
    var vulkanDeviceIndex by remember(model.id) { mutableFloatStateOf((model.vulkanDeviceIndex ?: 0).toFloat()) }
    var ropeFreqBaseOn by remember(model.id) { mutableStateOf(model.ropeFreqBase != null) }
    var ropeFreqBase by remember(model.id) { mutableStateOf((model.ropeFreqBase ?: 0f).toString()) }
    var ropeFreqScaleOn by remember(model.id) { mutableStateOf(model.ropeFreqScale != null) }
    var ropeFreqScale by remember(model.id) { mutableStateOf((model.ropeFreqScale ?: 0f).toString()) }
    var chatTemplateOverrideOn by remember(model.id) { mutableStateOf(model.chatTemplateOverride != null) }
    var chatTemplateOverride by remember(model.id) { mutableStateOf(model.chatTemplateOverride ?: "") }
    var loraPath by remember(model.id) { mutableStateOf(model.loraPath) }
    var loraScaleOn by remember(model.id) { mutableStateOf(model.loraScale != null) }
    var loraScale by remember(model.id) { mutableFloatStateOf(model.loraScale ?: 1.0f) }
    var loraError by remember(model.id) { mutableStateOf<String?>(null) }

    val loraApp = LocalContext.current.applicationContext as VervanApp
    val loraScope = rememberCoroutineScope()
    // Copies the picked file into internal storage (same reasoning as the mmproj import flow —
    // a content:// Uri isn't a real filesystem path the native loader can fopen) rather than
    // storing the raw picked Uri.
    val pickLoraFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            loraScope.launch {
                when (val result = loraApp.container.modelImportManager.importLoraAdapter(model, it)) {
                    is com.vervan.chat.model.ImportResult.Success -> { loraPath = result.model.loraPath; loraError = null }
                    is com.vervan.chat.model.ImportResult.Rejected -> loraError = result.reason
                    is com.vervan.chat.model.ImportResult.Duplicate -> Unit
                }
            }
        }
    }

    val isGeneration = model.role == ModelRole.GENERATION

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.ui_modeleditdialog_283_configure_model), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_cancel)) }
                    },
                    actions = {
                        TextButton(
                            enabled = !isRemote || remoteBaseUrlError == null,
                            onClick = {
                                val finalModel = if (isGeneration) {
                                    model.copy(
                                        displayName = displayName.ifBlank { model.displayName }.trim(),
                                        supportsVision = vision,
                                        supportsAudio = audio,
                                        supportsTools = tools,
                                        supportsThinking = thinking,
                                        thinkingSpecJson = if (thinking) {
                                            ThinkingSpec(
                                                activation = if (isRemote) ThinkingSpec.Activation.PROMPT_ONLY else thinkingActivation,
                                                enableText = thinkingEnableText.trim().takeIf { !isRemote && thinkingActivation == ThinkingSpec.Activation.SYSTEM_TOKEN && it.isNotBlank() },
                                                remoteParameter = remoteThinkingParameter.trim().takeIf { isRemote && it.isNotBlank() },
                                                source = ThinkingSpec.Source.USER
                                            ).toJson()
                                        } else model.thinkingSpecJson,
                                        defaultThinkingMode = defaultThinkingMode.takeIf { thinking },
                                        temperature = temperature.takeIf { temperatureOn },
                                        topP = topP.takeIf { topPOn },
                                        topK = topK.toInt().takeIf { topKOn },
                                        maxNumImages = maxImages.toInt().takeIf { maxImagesOn },
                                        contextTokens = context.toInt().takeIf { contextOn },
                                        mtpEnabled = mtpEnabled,
                                        preferredBackend = backend,
                                        seed = seed.toIntOrNull().takeIf { seedOn },
                                        toolApprovalMode = approvalMode,
                                        minP = minP.takeIf { minPOn },
                                        repetitionPenalty = repetitionPenalty.takeIf { repetitionPenaltyOn },
                                        maxOutputTokens = maxOutputTokens.toInt().takeIf { maxOutputTokensOn },
                                        stopSequences = stopSequences.takeIf { stopSequencesOn },
                                        gpuLayerCount = gpuLayerCount.toInt().takeIf { gpuLayerCountOn },
                                        cpuThreads = cpuThreads.toInt().takeIf { cpuThreadsOn },
                                        nBatch = nBatch.toInt().takeIf { nBatchOn },
                                        nUbatch = nUbatch.toInt().takeIf { nUbatchOn },
                                        useMlock = useMlock.takeIf { useMlockOn },
                                        flashAttention = (when (flashAttentionMode) { "On" -> true; "Off" -> false; else -> null }).takeIf { flashAttentionOn },
                                        kvCacheType = kvCacheType.takeIf { kvCacheTypeOn },
                                        vulkanDeviceIndex = vulkanDeviceIndex.toInt().takeIf { vulkanDeviceIndexOn },
                                        ropeFreqBase = ropeFreqBase.toFloatOrNull().takeIf { ropeFreqBaseOn },
                                        ropeFreqScale = ropeFreqScale.toFloatOrNull().takeIf { ropeFreqScaleOn },
                                        chatTemplateOverride = chatTemplateOverride.takeIf { chatTemplateOverrideOn && chatTemplateOverride.isNotBlank() },
                                        loraPath = loraPath,
                                        loraScale = loraScale.takeIf { loraScaleOn },
                                        remoteBaseUrl = if (isRemote) remoteBaseUrl.trim().trimEnd('/') else model.remoteBaseUrl,
                                        remoteApiModelId = if (isRemote) remoteApiModelId.trim() else model.remoteApiModelId
                                    )
                                } else {
                                    model.copy(
                                        displayName = displayName.ifBlank { model.displayName }.trim(),
                                        remoteBaseUrl = if (isRemote) remoteBaseUrl.trim().trimEnd('/') else model.remoteBaseUrl,
                                        remoteApiModelId = if (isRemote) remoteApiModelId.trim() else model.remoteApiModelId
                                    )
                                }
                                onSave(finalModel, if (isRemote) remoteApiKey else null)
                            }
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                )
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    com.vervan.chat.ui.common.BoundedTextField(
                        value = displayName, onValueChange = { displayName = it }, label = stringResource(R.string.ui_modeleditdialog_342_display_name), singleLine = true,
                        maxLength = com.vervan.chat.ui.common.ValidationLimits.MODEL_DISPLAY_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                    if (isRemote) {
                        Text(
                            "Connects to an OpenAI-compatible endpoint. Requests leave this device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        SectionDivider()
                        SectionLabel(stringResource(R.string.ui_modeledit_connection))
                        com.vervan.chat.ui.common.BoundedTextField(
                            value = remoteBaseUrl, onValueChange = { remoteBaseUrl = it }, label = stringResource(R.string.model_base_url), singleLine = true,
                            maxLength = 512,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        remoteBaseUrlError?.takeIf { remoteBaseUrl.isNotBlank() }?.let { ValidationMessage(it) }
                        com.vervan.chat.ui.common.BoundedTextField(
                            value = remoteApiModelId, onValueChange = { remoteApiModelId = it }, label = stringResource(R.string.ui_modeleditdialog_362_model_id), singleLine = true,
                            maxLength = 256,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = remoteApiKey,
                            onValueChange = { remoteApiKey = it.take(128) },
                            label = { Text(stringResource(R.string.model_api_key)) },
                            supportingText = { Text(stringResource(R.string.ui_modeleditdialog_370_leave_blank_to_keep_the_existing_key)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            "Storage: ${model.filePath}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = VervanMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    // Capabilities/generation defaults/tool approval are all properties of text
                    // generation — an embedding model only ever turns text into a vector, so
                    // none of these apply and showing them was pure confusion.
                    if (isGeneration) {
                        // No hardware to pick between for a stateless HTTP call — see EngineTraits.
                        // runsOnDevice.
                        if (!isRemote) {
                        SectionDivider()
                        SectionLabel(stringResource(R.string.ui_modeledit_performance_mode))
                        if (expertMode) {
                            // llama.cpp offloads via Vulkan and has no NPU backend, so GGUF
                            // models get Auto/GPU/CPU only.
                            val backendChoices = if (isLlamaCpp) listOf(
                                BackendChoice.AUTO to "Auto", BackendChoice.GPU to "GPU (Vulkan)",
                                BackendChoice.CPU to "CPU"
                            ) else listOf(
                                BackendChoice.AUTO to "Auto", BackendChoice.GPU to "GPU",
                                BackendChoice.CPU to "CPU", BackendChoice.NPU to "NPU"
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                backendChoices.forEach { (choice, label) ->
                                    VervanFilterChip(selected = backend == choice, onClick = { backend = choice }, label = { Text(label) })
                                }
                            }
                            Text(
                                when {
                                    backend == BackendChoice.AUTO && isLlamaCpp -> "Tries Vulkan GPU offload, then falls back to CPU."
                                    backend == BackendChoice.AUTO -> "Tries NPU, then GPU, then falls back to CPU."
                                    else -> "Strict: use ${backend.name} only, with no fallback."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        } else {
                            // Simplified view: a single on/off toggle instead of the full
                            // AUTO/GPU/CPU/NPU chip row — maps straight onto the same
                            // BackendChoice the expert row edits.
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_modeleditdialog_426_use_gpu_acceleration), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = backend != BackendChoice.CPU,
                                    onCheckedChange = { backend = if (it) BackendChoice.GPU else BackendChoice.CPU }
                                )
                            }
                        }
                        if (isLlamaCpp && model.modelDesc != null) {
                            Text(
                                buildString {
                                    append(model.modelDesc)
                                    model.layerCount?.let { append(" · $it layers") }
                                    model.nativeMaxContext?.let { append(" · ${it} native max context") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        }

                        SectionDivider()
                        SectionLabel(stringResource(R.string.ui_modeledit_capabilities))
                        CapabilityToggle(
                            "Vision", vision, enabled = visionSupported,
                            disabledHint = if (!visionSupported)
                                "Needs an mmproj projector file — re-import this GGUF with one to enable vision."
                            else null
                        ) { vision = it }
                        CapabilityToggle(
                            "Audio", audio, enabled = audioSupported,
                            disabledHint = if (!audioSupported)
                                "llama.cpp has no audio input in this build."
                            else null
                        ) { audio = it }
                        CapabilityToggle("Tools", tools) { tools = it }
                        CapabilityToggle("Thinking", thinking) { thinking = it }
                        if (thinking && isRemote) {
                            OutlinedTextField(
                                value = remoteThinkingParameter,
                                onValueChange = { remoteThinkingParameter = it.take(64) },
                                label = { Text("Thinking API parameter") },
                                supportingText = {
                                    Text("Default: reasoning_effort. Use enable_thinking for providers that expose a boolean switch.")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                        if (thinking && !isRemote) {
                            Text(
                                "Thinking activation",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    ThinkingSpec.Activation.PROMPT_ONLY to "Prompt only",
                                    ThinkingSpec.Activation.SYSTEM_TOKEN to "System token"
                                ).forEach { (activation, label) ->
                                    VervanFilterChip(
                                        selected = thinkingActivation == activation,
                                        onClick = {
                                            thinkingActivation = activation
                                            if (activation == ThinkingSpec.Activation.SYSTEM_TOKEN && thinkingEnableText.isBlank()) {
                                                thinkingEnableText = "<|think|>"
                                            }
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            if (thinkingActivation == ThinkingSpec.Activation.SYSTEM_TOKEN) {
                                OutlinedTextField(
                                    value = thinkingEnableText,
                                    onValueChange = { thinkingEnableText = it.take(128) },
                                    label = { Text("Enable token") },
                                    supportingText = { Text("Read from the model template when available; change only if needed.") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    "The app will ask the model to expose reasoning using its prompt format.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        if (thinking) {
                            Text(
                                "Default thinking mode",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.vervan.chat.llm.ThinkingPolicy.MODES.forEach { mode ->
                                    VervanFilterChip(
                                        selected = defaultThinkingMode == mode,
                                        onClick = { defaultThinkingMode = mode },
                                        label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                            Text(
                                "New chats on this model start here; a chat can still override it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                            )
                        }
                        // A load that couldn't actually deliver a capability the user asked for
                        // auto-turns it off here (see reconcileCapabilities) instead of quietly
                        // pretending it still works — surfacing that as a plain fact, not an error.
                        Text(
                            "Turns off if the loaded model cannot support it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )

                        if (tools) {
                            SectionDivider()
                            SectionLabel(stringResource(R.string.ui_modeledit_tool_approval))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    ToolApprovalMode.ALWAYS_ASK to "Always ask",
                                    ToolApprovalMode.AUTO_APPROVE_REVERSIBLE to "Auto (safe writes)",
                                    ToolApprovalMode.AUTO_APPROVE_ALL to "Auto (all)"
                                ).forEach { (mode, label) ->
                                    VervanFilterChip(selected = approvalMode == mode, onClick = { approvalMode = mode }, label = { Text(label) })
                                }
                            }
                        }

                        // No MTP equivalent wired up for llama.cpp, and none exists at all for a
                        // remote model — speculative decoding is an on-device inference trick.
                        if (!isLlamaCpp && !isRemote) {
                            SectionDivider()
                            SectionLabel(stringResource(R.string.ui_modeledit_speculative_decoding))
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (model.mtpSupported) {
                                        false -> "Last attempt failed. Turn on to retry at the next load."
                                        true -> "Speeds up generation on GPU; no effect on CPU/NPU."
                                        null -> "Tried automatically on load; auto-disabled if unsupported."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Switch(checked = mtpEnabled, onCheckedChange = { mtpEnabled = it })
                            }
                        }

                        SectionDivider()
                        SectionLabel(stringResource(R.string.ui_modeledit_generation_defaults))
                        if (expertMode) {
                        Text(
                            "Raw per-model overrides. Disabled values use the app default.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OverrideSlider("Temperature", temperatureOn, { temperatureOn = it }, temperature, { temperature = it }, defaults.temperature, "%.2f", 0f..2f)
                        OverrideSlider("Top-p", topPOn, { topPOn = it }, topP, { topP = it }, defaults.topP, "%.2f", 0.1f..1f)
                        OverrideSlider("Top-k", topKOn, { topKOn = it }, topK, { topK = it }, defaults.topK.toFloat(), "%.0f", 1f..64f)
                        if (isRemote) {
                            Text(
                                "Top-k isn't part of the OpenAI API — only sent if the endpoint happens to accept it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        // Min-p/repetition-penalty/max-images/seed are on-device sampling and
                        // load-time knobs — RemoteOpenAiEngine.generate() never sends any of them
                        // (see its param list), so showing them here would be a slider that quietly
                        // does nothing, exactly the kind of "feature" that reads as broken.
                        if (!isRemote) {
                            OverrideSlider("Min-p", minPOn, { minPOn = it }, minP, { minP = it }, defaults.minP, "%.2f", 0f..1f)
                            OverrideSlider("Repetition penalty", repetitionPenaltyOn, { repetitionPenaltyOn = it }, repetitionPenalty, { repetitionPenalty = it }, defaults.repetitionPenalty, "%.2f", 1f..2f)
                        }
                        OverrideSlider("Max output tokens", maxOutputTokensOn, { maxOutputTokensOn = it }, maxOutputTokens, { maxOutputTokens = it }, defaults.maxOutputTokens.toFloat(), "%.0f", 64f..4096f, steps = 20)
                        if (!isRemote) {
                            OverrideSlider("Max images", maxImagesOn, { maxImagesOn = it }, maxImages, { maxImages = it }, defaults.maxNumImages.toFloat(), "%.0f", 1f..4f)
                        }
                        OverrideSlider(
                            "Context length", contextOn, { contextOn = it }, context, { context = it }, defaults.contextTokens.toFloat(),
                            "%.0f", 1024f..(model.nativeMaxContext?.toFloat() ?: 32768f), steps = 30
                        )
                        OverrideField(
                            "Stop sequences", stopSequencesOn, { stopSequencesOn = it }, stopSequences, { stopSequences = it },
                            "None", singleLine = false
                        )
                        if (!isRemote) {
                        OverrideField("Seed", seedOn, { seedOn = it }, seed, { seed = it.filter(Char::isDigit) }, "Random")
                        if (seedOn) {
                            TextButton(onClick = { seed = kotlin.random.Random.nextInt(0, Int.MAX_VALUE).toString() }) { Text(stringResource(R.string.ui_modeleditdialog_572_randomize)) }
                        }
                        }
                        } else {
                            Text(
                                "Simple controls for this model. Choose Default to follow the app settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(stringResource(R.string.ui_modeleditdialog_582_response_style), style = MaterialTheme.typography.titleSmall)
                            val styleChoice = when {
                                !temperatureOn -> "DEFAULT"
                                temperature <= 0.45f -> "FOCUSED"
                                temperature >= 1.05f -> "CREATIVE"
                                else -> "BALANCED"
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEFAULT" to "Default", "FOCUSED" to "Focused", "BALANCED" to "Balanced", "CREATIVE" to "Creative").forEach { (id, label) ->
                                    VervanFilterChip(selected = styleChoice == id, onClick = {
                                        if (id == "DEFAULT") {
                                            temperatureOn = false; topPOn = false; topKOn = false; minPOn = false; repetitionPenaltyOn = false
                                        } else {
                                            temperatureOn = true; topPOn = true; topKOn = true; minPOn = true; repetitionPenaltyOn = true
                                            when (id) {
                                                "FOCUSED" -> { temperature = 0.3f; topP = 0.85f; topK = 24f; minP = 0.08f; repetitionPenalty = 1.12f }
                                                "CREATIVE" -> { temperature = 1.15f; topP = 0.98f; topK = 56f; minP = 0.03f; repetitionPenalty = 1.05f }
                                                else -> { temperature = 0.8f; topP = 0.95f; topK = 40f; minP = 0.05f; repetitionPenalty = 1.1f }
                                            }
                                        }
                                    }, label = { Text(label) })
                                }
                            }

                            Text(stringResource(R.string.ui_modeleditdialog_606_response_size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                            val sizeChoice = when {
                                !maxOutputTokensOn -> "DEFAULT"
                                maxOutputTokens <= 320f -> "SHORT"
                                maxOutputTokens >= 900f -> "LONG"
                                else -> "STANDARD"
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEFAULT" to "Default", "SHORT" to "Short", "STANDARD" to "Standard", "LONG" to "Long").forEach { (id, label) ->
                                    VervanFilterChip(selected = sizeChoice == id, onClick = {
                                        maxOutputTokensOn = id != "DEFAULT"
                                        maxOutputTokens = when (id) { "SHORT" -> 256f; "LONG" -> 1024f; else -> 512f }
                                    }, label = { Text(label) })
                                }
                            }

                            Text(stringResource(R.string.ui_modeleditdialog_622_conversation_memory), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                            val memoryChoice = if (!contextOn) 0 else context.toInt()
                            // "Default" literally means the app-wide Settings value, which happens
                            // to be 4096 out of the box — the same number "Standard" sets. Spelling
                            // that number out on both chips (instead of two same-looking labels
                            // whose values silently agree) is what tells the user their tap actually
                            // did something, rather than looking unchanged after picking Standard.
                            val defaultTokens = defaults.contextTokens
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Default (${defaultTokens})", 4096 to "Standard (4096)", 8192 to "More (8192)", 16384 to "Maximum (16384)").forEach { (tokens, label) ->
                                    VervanFilterChip(selected = memoryChoice == tokens, onClick = {
                                        contextOn = tokens != 0
                                        if (tokens != 0) context = tokens.toFloat().coerceAtMost(model.nativeMaxContext?.toFloat() ?: 32768f)
                                    }, label = { Text(label) })
                                }
                            }
                            Text(
                                "More memory keeps a longer conversation but uses more RAM.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (isLlamaCpp && expertMode) {
                            SectionDivider()
                            SectionLabel(stringResource(R.string.ui_modeledit_advanced_llama))
                            // GPU layers: default (override off) = offload the whole model on
                            // GPU/Auto; 0 keeps this model on CPU even under Auto.
                            run {
                                val maxGpuLayers = ((model.layerCount ?: 32) + 1).toFloat()
                                OverrideSlider(
                                    "GPU layers (Vulkan)", gpuLayerCountOn, { gpuLayerCountOn = it },
                                    gpuLayerCount.coerceIn(0f, maxGpuLayers), { gpuLayerCount = it },
                                    maxGpuLayers, "%.0f", 0f..maxGpuLayers
                                )
                            }
                            OverrideSlider(
                                "Vulkan device index", vulkanDeviceIndexOn, { vulkanDeviceIndexOn = it },
                                vulkanDeviceIndex, { vulkanDeviceIndex = it }, 0f, "%.0f", 0f..3f, steps = 2
                            )
                            OverrideSlider(
                                "CPU threads", cpuThreadsOn, { cpuThreadsOn = it }, cpuThreads, { cpuThreads = it },
                                Runtime.getRuntime().availableProcessors().toFloat(), "%.0f", 1f..16f
                            )
                            OverrideSlider("Batch size (n_batch)", nBatchOn, { nBatchOn = it }, nBatch, { nBatch = it }, defaults.nBatch.toFloat(), "%.0f", 128f..4096f, steps = 30)
                            OverrideSlider("Physical batch size (n_ubatch)", nUbatchOn, { nUbatchOn = it }, nUbatch, { nUbatch = it }, defaults.nUbatch.toFloat(), "%.0f", 32f..2048f, steps = 30)
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_modeleditdialog_670_lock_model_in_ram_mlock), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    if (useMlockOn) (if (useMlock) "On" else "Off") else "Default (${if (useMlockDefault) "On" else "Off"})",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Switch(checked = useMlockOn, onCheckedChange = { useMlockOn = it })
                            }
                            if (useMlockOn) {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.privacy_enabled), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = useMlock, onCheckedChange = { useMlock = it })
                                }
                            }
                            OverrideDropdown(
                                "Flash attention", flashAttentionOn, { flashAttentionOn = it }, flashAttentionMode,
                                { flashAttentionMode = it }, listOf("Auto", "On", "Off"),
                                defaultValue = flashAttentionModeDefault.lowercase().replaceFirstChar(Char::uppercase)
                            )
                            OverrideDropdown(
                                "KV cache type", kvCacheTypeOn, { kvCacheTypeOn = it }, kvCacheType,
                                { kvCacheType = it }, listOf("f16", "q8_0", "q4_0"), defaultValue = kvCacheTypeDefault
                            )
                            OverrideField("RoPE freq base", ropeFreqBaseOn, { ropeFreqBaseOn = it }, ropeFreqBase, { ropeFreqBase = it.filter { c -> c.isDigit() || c == '.' } }, "From model")
                            OverrideField("RoPE freq scale", ropeFreqScaleOn, { ropeFreqScaleOn = it }, ropeFreqScale, { ropeFreqScale = it.filter { c -> c.isDigit() || c == '.' } }, "From model")
                            OverrideField(
                                "Chat template override", chatTemplateOverrideOn, { chatTemplateOverrideOn = it }, chatTemplateOverride,
                                { chatTemplateOverride = it }, "From model (embedded)", singleLine = false,
                                maxLength = InputLimits.MAX_CHAT_TEMPLATE_CHARS
                            )
                            if (chatTemplateOverrideOn) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Choose a supported llama.cpp preset. Raw Jinja text is not executable in this build.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(top = 2.dp)
                                    )
                                }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.xs),
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                                ) {
                                    com.vervan.chat.llm.LlamaCppEngine.builtinChatTemplates.forEach { name ->
                                        VervanFilterChip(
                                            selected = chatTemplateOverride == name,
                                            onClick = { chatTemplateOverride = name },
                                            label = { Text(name) }
                                        )
                                    }
                                }
                            }

                            SectionDivider()
                            SectionLabel(stringResource(R.string.ui_modeledit_lora_adapter))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    loraPath?.let { File(it).name } ?: "None attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { pickLoraFile.launch(arrayOf("*/*")) }) { Text(if (loraPath != null) "Replace" else "Attach") }
                                if (loraPath != null) {
                                    TextButton(onClick = { loraPath = null }) { Text(stringResource(R.string.action_remove)) }
                                }
                            }
                            loraError?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (loraPath != null) {
                                OverrideSlider("LoRA scale", loraScaleOn, { loraScaleOn = it }, loraScale, { loraScale = it }, 1.0f, "%.2f", 0f..2f)
                            }
                        }
                    } else {
                            Text(
                            "Embedding models power semantic search and have no generation settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.md)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(Space.xxl))
                }
            }
        }
    }
}

@Composable
internal fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = Space.sm), color = vervanSubtleDividerColor())
}

@Composable
internal fun CapabilityToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    disabledHint: String? = null,
    onChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
        if (!enabled && disabledHint != null) {
            Text(
                disabledHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A generation-default slider that's either "inherit the app-wide Settings value" (off, shown
 * disabled at the default) or "override for this model" (on, editable) — all such fields share
 * this exact label/value/switch layout so they read as one consistent, aligned group. */
@Composable
internal fun OverrideSlider(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    defaultValue: Float,
    format: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (override) String.format(java.util.Locale.getDefault(), format, value) else stringResource(R.string.ui_modeledit_default_value, String.format(java.util.Locale.getDefault(), format, defaultValue)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp)
            )
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        val effectiveValue = if (override) value else defaultValue
        val sliderDescription = stringResource(R.string.ui_modeledit_slider_value, label, String.format(java.util.Locale.getDefault(), format, effectiveValue))
        Slider(
            value = effectiveValue,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = override,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics {
                contentDescription = sliderDescription
            }
        )
    }
}

/** Same override pattern as [OverrideSlider] but for a free-form numeric field (seed has no
 * meaningful "scale" to slide). */
@Composable
internal fun OverrideField(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    defaultLabel: String,
    singleLine: Boolean = true,
    maxLength: Int = 128
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!override) {
                Text(
                    defaultLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp)
                )
            }
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        if (override) {
            OutlinedTextField(
                value, { onValueChange(it.take(maxLength)) }, singleLine = singleLine,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

/** Same override-switch header as [OverrideSlider]/[OverrideField], but for a fixed set of
 * string choices (KV cache type, flash-attention Auto/On/Off) instead of a numeric range. */
@Composable
internal fun OverrideDropdown(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    defaultValue: String
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (override) value else "Default ($defaultValue)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp)
            )
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        if (override) {
            Box(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { expanded = true }) { Text(value) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
                    }
                }
            }
        }
    }
}

internal fun formatModelSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun ModelInfo.runtimeSummary(): String {
    // No on-device execution means no hardware backend to choose between — preferredBackend is
    // stored on every row but only means something for an engine that actually runs here.
    val hardware = if (!traits.runsOnDevice) "Network" else when (preferredBackend) {
        BackendChoice.AUTO -> if (traits.hasNativeTuningKnobs) "Auto: Vulkan → CPU" else "Auto: NPU → GPU → CPU"
        BackendChoice.GPU -> if (traits.hasNativeTuningKnobs) "Vulkan GPU" else "GPU"
        BackendChoice.CPU -> "CPU"
        BackendChoice.NPU -> "NPU"
    }
    return "${traits.label} • $hardware"
}

/** Rough "would this comfortably fit" check for a catalogue entry against a device budget —
 * same need-estimate reasoning as [com.vervan.chat.ui.onboarding.recommendModel]: prefer the
 * catalogue's own declared minimum RAM, else fall back to ~1.3x the download size. */
internal fun com.vervan.chat.modeldownload.ModelUiState.fitsBudget(budgetBytes: Long): Boolean {
    val needBytes = minimumRamBytes ?: ((totalBytes ?: 0L) * 13 / 10)
    return needBytes <= budgetBytes
}
