package com.vervan.chat.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.data.db.entities.StudyCard
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
    // Snapshot the deck once per session instead of re-deriving from `cards` on every
    // recomposition — `cards` re-emits on every markResult() write (it's backed by a Room Flow),
    // which used to reshuffle the deck under the user mid-session and, with "Needs practice" on,
    // could shrink the deck the instant a card was answered correctly and skip/end the session
    // early. A session now only resets on an explicit user action (toggling the filter/shuffle
    // chips, "Review again", "Practice missed cards") or the very first time real data arrives.
    var sessionCards by remember { mutableStateOf<List<StudyCard>>(emptyList()) }
    var sessionDataLoaded by remember { mutableStateOf(false) }
    fun resetSession() {
        sessionCards = if (shuffled) cards.shuffled() else cards
        index = 0; revealed = false; sessionCorrect = 0; sessionSeen = 0
    }
    LaunchedEffect(missedOnly, shuffled) { resetSession() }
    LaunchedEffect(cards) {
        if (!sessionDataLoaded && cards.isNotEmpty()) {
            sessionDataLoaded = true
            resetSession()
        }
    }

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
                VervanFilterChip(
                    selected = missedOnly,
                    onClick = { vm.setMissedOnly(!missedOnly) },
                    label = { Text("Needs practice") },
                    leadingIcon = { Icon(Icons.Filled.School, contentDescription = null) }
                )
                VervanFilterChip(
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
            // Real 3D flashcard flip: front (question) and back (answer) share one Card whose
            // rotationY animates 0→180; past 90° the content swaps and is counter-mirrored so
            // the answer face isn't rendered as mirror text. Each card gets a stable categorical
            // accent so a deck reads as a colorful stack instead of forty identical grey cards.
            val accent = com.vervan.chat.ui.theme.vervanAccentFor(index)
            val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(durationMillis = 380), label = "card-flip")
            val showAnswer = rotation > 90f
            Card(
                onClick = { revealed = !revealed },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (showAnswer) accent.container else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    if (showAnswer) 1.dp else 2.dp,
                    if (showAnswer) accent.onContainer.copy(alpha = 0.25f) else accent.container
                )
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .graphicsLayer { if (showAnswer) rotationY = 180f }
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (showAnswer) "ANSWER" else "QUESTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showAnswer) accent.onContainer else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (showAnswer) card.answer else card.question,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = if (showAnswer) accent.onContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        if (showAnswer) "Tap to flip back" else "Think first, then tap to flip",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showAnswer) accent.onContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
        // Animated score ring — sweeps up to the session score on entry.
        val sweep by animateFloatAsState(percent / 100f, tween(durationMillis = 900), label = "score-ring")
        val ringColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(trackColor, -90f, 360f, useCenter = false, style = stroke, topLeft = topLeft, size = arcSize)
                drawArc(ringColor, -90f, 360f * sweep, useCenter = false, style = stroke, topLeft = topLeft, size = arcSize)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$percent%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("$correct of $seen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Review complete", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
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
