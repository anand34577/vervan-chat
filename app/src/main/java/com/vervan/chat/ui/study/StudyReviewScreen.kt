package com.vervan.chat.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyReviewScreen(setName: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: StudyReviewViewModel = viewModel(factory = viewModelFactory { initializer { StudyReviewViewModel(app, setName) } })
    val cards by vm.cards.collectAsState()
    val missedOnly by vm.missedOnly.collectAsState()

    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var sessionCorrect by remember { mutableIntStateOf(0) }
    var sessionSeen by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(missedOnly) { index = 0; revealed = false; sessionCorrect = 0; sessionSeen = 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(setName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Review missed cards only", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = missedOnly, onCheckedChange = vm::setMissedOnly)
            }
            if (cards.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = if (missedOnly) "No missed cards" else "This set has no cards",
                    body = if (missedOnly) "Nice work — nothing left to review." else "Add flashcards to this set to start reviewing."
                )
                return@Column
            }
            if (index >= cards.size) {
                androidx.compose.runtime.LaunchedEffect(Unit) { vm.recordSession() }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Session complete", style = MaterialTheme.typography.titleLarge)
                    Text("$sessionCorrect / $sessionSeen correct", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { index = 0; sessionCorrect = 0; sessionSeen = 0; revealed = false }, modifier = Modifier.padding(top = 20.dp)) {
                        Text("Review again")
                    }
                }
                return@Column
            }

            LinearProgressIndicator(progress = { (index + 1f) / cards.size }, modifier = Modifier.fillMaxWidth())
            Text(
                "Card ${index + 1} of ${cards.size}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            val card = cards[index]
            Card(
                onClick = { revealed = !revealed },
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (revealed) "Answer" else "Question",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (revealed) card.answer else card.question,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)
                    )
                    if (!revealed) {
                        Text(
                            "Tap to reveal", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp)
                        )
                    }
                }
            }

            if (revealed) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.markResult(card, false)
                            sessionSeen++; revealed = false; index++
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Missed it") }
                    Button(
                        onClick = {
                            vm.markResult(card, true)
                            sessionCorrect++; sessionSeen++; revealed = false; index++
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Got it") }
                }
            }
        }
    }
}
