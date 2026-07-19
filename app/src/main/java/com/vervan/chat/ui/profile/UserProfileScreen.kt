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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
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
import com.vervan.chat.ui.common.ValidationLimits

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                "Added to prompts only when a field is filled. Never inferred from your chats.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ProfileField("Preferred name", name, maxLength = ValidationLimits.USER_PREFERRED_NAME, onChange = vm::setName)
            ProfileField("Occupation", occupation, "e.g. mobile engineer", maxLength = ValidationLimits.USER_OCCUPATION, onChange = vm::setOccupation)
            ProfileField("Expertise level", expertise, "e.g. advanced", maxLength = ValidationLimits.USER_EXPERTISE, onChange = vm::setExpertise)
            ChipInputField(
                items = interests.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onItemsChange = { vm.setInterests(it.joinToString(",")) },
                label = "Interests",
                maxItemLength = ValidationLimits.USER_INTEREST_ITEM,
                maxItemCount = ValidationLimits.USER_INTEREST_COUNT,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text("Languages", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageOptions.common.forEach { lang ->
                    VervanFilterChip(
                        selected = lang in languages,
                        onClick = { vm.toggleLanguage(lang, languages) },
                        label = { Text(lang) }
                    )
                }
            }

            Text("Units", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow {
                listOf("metric" to "Metric", "imperial" to "Imperial").forEachIndexed { i, (id, label) ->
                    SegmentedButton(
                        selected = units == id,
                        onClick = { vm.setUnits(id) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }

            ChipInputField(
                items = avoid.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onItemsChange = { vm.setAvoid(it.joinToString(",")) },
                label = "Topics to avoid",
                maxItemLength = ValidationLimits.USER_AVOID_TOPIC_ITEM,
                maxItemCount = ValidationLimits.USER_AVOID_TOPIC_COUNT,
                modifier = Modifier.padding(top = 12.dp)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

object LanguageOptions {
    val common = listOf("English", "Spanish", "French", "German", "Hindi", "Chinese", "Japanese", "Arabic", "Portuguese", "Russian")
}
