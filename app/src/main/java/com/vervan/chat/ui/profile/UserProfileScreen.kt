package com.vervan.chat.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ChipInputField
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: UserProfileViewModel = viewModel(factory = viewModelFactory { initializer { UserProfileViewModel(app) } })
    val name by vm.name.collectAsState()
    val occupation by vm.occupation.collectAsState()
    val expertise by vm.expertise.collectAsState()
    val interests by vm.interests.collectAsState()
    val languages by vm.languages.collectAsState()
    val units by vm.units.collectAsState()
    val avoid by vm.avoid.collectAsState()
    val goals by vm.goals.collectAsState()
    val filledFields = listOf(name, occupation, expertise, interests, languages, units, avoid, goals).count { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Collection<*> -> value.isNotEmpty()
            else -> false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(vertical = Space.lg).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            FeatureHero(
                icon = Icons.Filled.Person,
                eyebrow = "Personal context",
                title = "Make replies feel like yours",
                body = "Optional details help Vervan tailor responses. They are added to prompts only when you fill them in and are never inferred from chats.",
                trailing = {
                    Text(
                        "$filledFields filled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

            VervanSectionHeader("About you", topPadding = Space.xs)
            ProfileField("Preferred name", name, maxLength = ValidationLimits.USER_PREFERRED_NAME, onChange = vm::setName)
            ProfileField("Occupation", occupation, "e.g. mobile engineer", maxLength = ValidationLimits.USER_OCCUPATION, onChange = vm::setOccupation)
            ProfileField("Expertise level", expertise, "e.g. advanced", maxLength = ValidationLimits.USER_EXPERTISE, onChange = vm::setExpertise)
            ChipInputField(
                items = interests.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onItemsChange = { vm.setInterests(it.joinToString(",")) },
                label = "Interests",
                maxItemLength = ValidationLimits.USER_INTEREST_ITEM,
                maxItemCount = ValidationLimits.USER_INTEREST_COUNT
            )

            VervanSectionHeader("Preferences", topPadding = Space.xs)
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Languages", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    LanguageOptions.common.forEach { lang ->
                        VervanFilterChip(
                            selected = lang in languages,
                            onClick = { vm.toggleLanguage(lang, languages) },
                            label = { Text(lang) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Units", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow {
                    listOf("metric" to "Metric", "imperial" to "Imperial").forEachIndexed { i, (id, label) ->
                        SegmentedButton(
                            selected = units == id,
                            onClick = { vm.setUnits(id) },
                            shape = MaterialTheme.shapes.small
                        ) { Text(label) }
                    }
                }
            }

            VervanSectionHeader("Conversation context", topPadding = Space.xs)
            ChipInputField(
                items = avoid.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onItemsChange = { vm.setAvoid(it.joinToString(",")) },
                label = "Topics to avoid",
                maxItemLength = ValidationLimits.USER_AVOID_TOPIC_ITEM,
                maxItemCount = ValidationLimits.USER_AVOID_TOPIC_COUNT
            )
            ProfileField("Current goals", goals, "what are you working toward?", maxLength = ValidationLimits.USER_GOALS, onChange = vm::setGoals)
        }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, placeholder: String = "", maxLength: Int, onChange: (String) -> Unit) {
    // DataStore emits asynchronously. Keep the active edit local so an older persisted value
    // cannot replace text or move the cursor while the user is typing.
    var draft by rememberSaveable(label) { mutableStateOf<String?>(null) }
    LaunchedEffect(value, draft) {
        if (draft != null && value == draft) draft = null
    }
    BoundedTextField(
        value = draft ?: value,
        onValueChange = {
            draft = it
            onChange(it)
        },
        label = label,
        placeholder = placeholder.ifBlank { null },
        maxLength = maxLength,
        modifier = Modifier.fillMaxWidth()
    )
}

object LanguageOptions {
    val common = listOf("English", "Spanish", "French", "German", "Hindi", "Chinese", "Japanese", "Arabic", "Portuguese", "Russian")
}
