package com.vervan.chat.ui.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ContextGuideCard
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanAccentFor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonaEditorScreen(personaId: String?, onBack: () -> Unit, onDuplicated: (String) -> Unit, onTest: ((String) -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: PersonaEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { PersonaEditorViewModel(app, personaId) }
    })
    val name by vm.name.collectAsState()
    val description by vm.description.collectAsState()
    val systemInstruction by vm.systemInstruction.collectAsState()
    val isBuiltIn by vm.isBuiltIn.collectAsState()
    val tone by vm.tone.collectAsState()
    val formality by vm.formality.collectAsState()
    val conciseness by vm.conciseness.collectAsState()
    val creativity by vm.creativity.collectAsState()
    val responseLength by vm.responseLength.collectAsState()
    val language by vm.language.collectAsState()
    val avatarPath by vm.avatarPath.collectAsState()
    val importError by vm.importError.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val recordFound by vm.recordFound.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAvatarChooser by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickAvatarLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.importAvatar(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (personaId == null) R.string.persona_new_title else R.string.persona_edit_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    if (personaId != null && onTest != null) {
                        IconButton(onClick = { onTest(personaId) }) { Icon(Icons.Filled.Science, contentDescription = stringResource(R.string.persona_test_bench_short)) }
                    }
                    if (personaId != null && !isBuiltIn) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.persona_delete_accessibility))
                        }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.persona_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.persona_unavailable_recovery),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 7, modifier = Modifier.padding(Space.md))
            personaId != null && !recordFound -> EmptyState(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.persona_not_found),
                body = stringResource(R.string.persona_not_found_body),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                val avatarAccent = vervanAccentFor((name.hashCode() and Int.MAX_VALUE) % 6)
                val avatarBitmap = rememberThumbnail(avatarPath?.takeUnless { it.startsWith("emoji:") }, 128)
                val avatarEmoji = avatarPath?.takeIf { it.startsWith("emoji:") }?.removePrefix("emoji:")
                Box(
                    Modifier
                        .size(64.dp)
                        .background(avatarAccent.container, CircleShape)
                        .then(if (!isBuiltIn) Modifier.clickable { showAvatarChooser = true } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = avatarBitmap,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                        )
                    } else if (avatarEmoji != null) {
                        Text(avatarEmoji, style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Text(
                            name.trim().firstOrNull()?.uppercase() ?: "P",
                            style = MaterialTheme.typography.headlineSmall,
                            color = avatarAccent.onContainer,
                        )
                    }
                }
            }
            if (isBuiltIn) {
                Text(
                stringResource(R.string.persona_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            if (!isBuiltIn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    TextButton(onClick = { showAvatarChooser = true }, modifier = Modifier.padding(top = Space.sm)) {
                        Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(if (avatarPath != null) R.string.persona_change_icon else R.string.persona_choose_icon), modifier = Modifier.padding(start = Space.sm))
                    }
                    if (avatarPath != null) {
                        TextButton(onClick = vm::clearAvatar, modifier = Modifier.padding(top = Space.sm)) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.persona_remove_icon), modifier = Modifier.padding(start = Space.sm))
                        }
                    }
                }
                Text(
                    stringResource(R.string.persona_avatar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
                )
            }
            ContextGuideCard(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.persona_changes_title),
            body = stringResource(R.string.persona_changes_body),
                modifier = Modifier.padding(top = Space.md),
                accentIndex = 2,
            )
            if (importError != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = vm::dismissImportError,
                    title = { Text(stringResource(R.string.persona_image_error)) },
                    text = { Text(importError.orEmpty()) },
                    confirmButton = { TextButton(onClick = vm::dismissImportError) { Text(stringResource(R.string.persona_ok)) } }
                )
            }
            SectionHeader(stringResource(R.string.persona_identity))
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = stringResource(R.string.persona_name),
                maxLength = ValidationLimits.PERSONA_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = stringResource(R.string.persona_role),
                maxLength = ValidationLimits.PERSONA_ROLE,
                modifier = Modifier.fillMaxWidth().padding(top = Space.md)
            )

            SectionHeader(stringResource(R.string.persona_behavior))
            BoundedTextField(
                value = systemInstruction, onValueChange = vm::setSystemInstruction, label = stringResource(R.string.persona_system_instruction),
                maxLength = ValidationLimits.PERSONA_SYSTEM_INSTRUCTION, minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )
            Text(stringResource(R.string.persona_tone), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.lg))
            DialRow(listOf("NEUTRAL", "WARM", "DIRECT", "PLAYFUL"), tone, vm::setTone)

            Text(stringResource(R.string.persona_formality), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.md))
            DialRow(listOf("NEUTRAL", "CASUAL", "FORMAL"), formality, vm::setFormality)

            Text(stringResource(R.string.persona_conciseness), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.md))
            DialRow(listOf("NORMAL", "TERSE", "ELABORATE"), conciseness, vm::setConciseness)

            Text(stringResource(R.string.persona_response_length), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.md))
            DialRow(listOf("BALANCED", "SHORT", "LONG"), responseLength, vm::setResponseLength)

            val creativityValue = String.format("%.1f", creativity)
            val creativityDescription = stringResource(R.string.persona_creativity_accessibility, creativityValue)
            Text(stringResource(R.string.persona_creativity, creativityValue), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.md))
            Slider(
                value = creativity, onValueChange = vm::setCreativity, valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = creativityDescription }
            )

            SectionHeader(stringResource(R.string.persona_defaults))
            OutlinedTextField(
                value = language, onValueChange = { vm.setLanguage(it.take(80)) }, label = { Text(stringResource(R.string.persona_language)) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )

            if (personaId != null && onTest != null) {
                SectionHeader(stringResource(R.string.persona_test))
                OutlinedButton(onClick = { onTest(personaId) }, modifier = Modifier.padding(top = Space.sm)) { Text(stringResource(R.string.persona_test_bench)) }
            }

            // Mirrors PersonaEditorViewModel.save()'s own requirement — disabling the button on
            // the same condition that would otherwise make it silently no-op is the fix, not a
            // dialog explaining a failure the user could never have triggered in the first place.
            val withinLimits = name.isNotBlank() && systemInstruction.isNotBlank() &&
                name.length <= ValidationLimits.PERSONA_NAME &&
                description.length <= ValidationLimits.PERSONA_ROLE &&
                systemInstruction.length <= ValidationLimits.PERSONA_SYSTEM_INSTRUCTION
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                OutlinedButton(onClick = { scope.launch { onDuplicated(vm.duplicate()) } }) { Text(stringResource(R.string.persona_duplicate)) }
                Button(enabled = withinLimits, onClick = { scope.launch { if (vm.save()) onBack() } }) { Text(stringResource(R.string.persona_save_changes)) }
            }
            }
        }
    }
    }
    if (showAvatarChooser) {
        val emojis = listOf("🙂", "🧠", "✍️", "🔎", "🎓", "💡", "🧭", "🤝")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAvatarChooser = false },
            title = { Text(stringResource(R.string.persona_choose_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.persona_choose_hint), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                        maxItemsInEachRow = 4,
                    ) {
                        emojis.forEach { emoji ->
                            androidx.compose.material3.Surface(
                                onClick = { vm.setEmojiAvatar(emoji); showAvatarChooser = false },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(Space.md))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarChooser = false
                    pickAvatarLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }) {
                    Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.persona_choose_image), modifier = Modifier.padding(start = Space.sm))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearAvatar(); showAvatarChooser = false }) { Text(stringResource(R.string.persona_use_initial)) }
            },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.persona_delete_title),
            body = stringResource(R.string.persona_delete_body, name),
            confirmLabel = stringResource(R.string.persona_delete_action),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider(Modifier.padding(top = Space.xl, bottom = Space.xs))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun DialRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.xs),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
    ) {
        options.forEach { option ->
            VervanFilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(personaOptionLabel(option)) }
            )
        }
    }
}

@Composable
private fun personaOptionLabel(option: String): String = when (option) {
    "NEUTRAL" -> stringResource(R.string.persona_neutral)
    "WARM" -> stringResource(R.string.persona_warm)
    "DIRECT" -> stringResource(R.string.persona_direct)
    "PLAYFUL" -> stringResource(R.string.persona_playful)
    "CASUAL" -> stringResource(R.string.persona_casual)
    "FORMAL" -> stringResource(R.string.persona_formal)
    "NORMAL" -> stringResource(R.string.persona_normal)
    "TERSE" -> stringResource(R.string.persona_terse)
    "ELABORATE" -> stringResource(R.string.persona_elaborate)
    "BALANCED" -> stringResource(R.string.persona_balanced)
    "SHORT" -> stringResource(R.string.persona_short)
    "LONG" -> stringResource(R.string.persona_long)
    else -> option
}
