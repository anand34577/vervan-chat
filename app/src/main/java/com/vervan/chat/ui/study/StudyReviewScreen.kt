package com.vervan.chat.ui.study

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyReviewScreen(setName: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: StudyReviewViewModel = viewModel(factory = viewModelFactory { initializer { StudyReviewViewModel(app, setName) } })
    val cards by vm.cards.collectAsState()
    val missedOnly by vm.missedOnly.collectAsState()

    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var shuffled by remember { mutableStateOf(false) }
    var sessionCorrect by remember { mutableIntStateOf(0) }
    var sessionSeen by remember { mutableIntStateOf(0) }
    val sessionCards = remember(cards, shuffled) { if (shuffled) cards.shuffled() else cards }
    fun resetSession() { index = 0; revealed = false; sessionCorrect = 0; sessionSeen = 0 }
    LaunchedEffect(missedOnly, shuffled) { resetSession() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(setName)
                        Text("Active recall review", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = missedOnly,
                    onClick = { vm.setMissedOnly(!missedOnly) },
                    label = { Text("Needs practice") },
                    leadingIcon = { Icon(Icons.Filled.School, contentDescription = null) }
                )
                FilterChip(
                    selected = shuffled,
                    onClick = { shuffled = !shuffled },
                    label = { Text("Shuffle") },
                    leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null) }
                )
            }

            if (sessionCards.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = if (missedOnly) "Nothing needs practice" else "This deck has no cards",
                    body = if (missedOnly) "Nice work — your missed cards are cleared for now." else "Create a new deck with study material to begin.",
                    modifier = Modifier.weight(1f)
                )
                return@Column
            }

            if (index >= sessionCards.size) {
                LaunchedEffect(Unit) { vm.recordSession() }
                SessionComplete(
                    correct = sessionCorrect,
                    seen = sessionSeen,
                    onAgain = ::resetSession,
                    onPracticeMissed = { vm.setMissedOnly(true); resetSession() },
                    modifier = Modifier.weight(1f)
                )
                return@Column
            }

            val progress = (index + 1f) / sessionCards.size
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Card ${index + 1} of ${sessionCards.size}", style = MaterialTheme.typography.labelMedium)
                Text("$sessionCorrect correct", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            val card = sessionCards[index]
            Card(
                onClick = { revealed = !revealed },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp).animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = if (revealed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, if (revealed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (revealed) "ANSWER" else "QUESTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Crossfade(targetState = revealed, label = "flashcard-face") { showAnswer ->
                        Text(
                            if (showAnswer) card.answer else card.question,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    Text(
                        if (revealed) "Tap to see the question" else "Think first, then tap to reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }

            if (revealed) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.markResult(card, false)
                            sessionSeen++; revealed = false; index++
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Again") }
                    Button(
                        onClick = {
                            vm.markResult(card, true)
                            sessionCorrect++; sessionSeen++; revealed = false; index++
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Got it") }
                }
            } else {
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Reveal answer") }
            }
        }
    }
}

@Composable
private fun SessionComplete(
    correct: Int,
    seen: Int,
    onAgain: () -> Unit,
    onPracticeMissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = if (seen == 0) 0 else correct * 100 / seen
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
        }
        Text("Review complete", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
        Text("$percent% · $correct of $seen correct", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (percent >= 80) "Strong recall. A short review later will help it stick." else "Review the cards you missed while they are still fresh.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Review again") }
        if (correct < seen) OutlinedButton(onClick = onPracticeMissed, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Practice missed cards") }
    }
}
