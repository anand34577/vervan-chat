package com.vervan.chat.ui.personas

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ValidationLimits
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
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (personaId == null) "New persona" else "Edit persona") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (personaId != null && onTest != null) {
                        IconButton(onClick = { onTest(personaId) }) { Icon(Icons.Filled.Science, contentDescription = "Test bench") }
                    }
                    if (personaId != null && !isBuiltIn) {
                        IconButton(onClick = { vm.delete(); onBack() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Box(
                    Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (isBuiltIn) {
                Text(
                    "This is a built-in — saving creates your own editable copy instead of changing the original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            SectionHeader("Identity")
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = "Name",
                maxLength = ValidationLimits.PERSONA_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = "Role & expertise",
                maxLength = ValidationLimits.PERSONA_ROLE,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            SectionHeader("Behavior")
            BoundedTextField(
                value = systemInstruction, onValueChange = vm::setSystemInstruction, label = "System instruction",
                maxLength = ValidationLimits.PERSONA_SYSTEM_INSTRUCTION, minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text("Tone", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
            DialRow(listOf("NEUTRAL", "WARM", "DIRECT", "PLAYFUL"), tone, vm::setTone)

            Text("Formality", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            DialRow(listOf("NEUTRAL", "CASUAL", "FORMAL"), formality, vm::setFormality)

            Text("Conciseness", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            DialRow(listOf("NORMAL", "TERSE", "ELABORATE"), conciseness, vm::setConciseness)

            Text("Response length", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            DialRow(listOf("BALANCED", "SHORT", "LONG"), responseLength, vm::setResponseLength)

            Text("Creativity: ${String.format("%.1f", creativity)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            Slider(value = creativity, onValueChange = vm::setCreativity, valueRange = 0f..1f)

            SectionHeader("Defaults")
            OutlinedTextField(
                value = language, onValueChange = vm::setLanguage, label = { Text("Preferred reply language (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            if (personaId != null && onTest != null) {
                SectionHeader("Test")
                OutlinedButton(onClick = { onTest(personaId) }, modifier = Modifier.padding(top = 8.dp)) { Text("Open test bench") }
            }

            val withinLimits = name.length <= ValidationLimits.PERSONA_NAME &&
                description.length <= ValidationLimits.PERSONA_ROLE &&
                systemInstruction.length <= ValidationLimits.PERSONA_SYSTEM_INSTRUCTION
            ResponsiveActions(Modifier.padding(top = 16.dp)) {
                OutlinedButton(onClick = { scope.launch { onDuplicated(vm.duplicate()) } }) { Text("Duplicate") }
                Button(enabled = withinLimits, onClick = { scope.launch { if (vm.save()) onBack() } }) { Text("Save changes") }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun DialRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}
