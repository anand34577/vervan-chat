package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class QuizQuestion(val type: String, val question: String, val options: List<String>, val correctAnswer: String, val explanation: String)

private val DIFFICULTIES = listOf("Easy", "Medium", "Hard")

/** Paste/scanned text -> LLM-generated quiz (JSON array), answered interactively, scored
 * client-side. Grading for free-text answers is a simple case-insensitive containment check —
 * ponytail: no semantic grading, good enough for self-check practice, not exam scoring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizGeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var sourceText by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Medium") }
    var difficultyMenuOpen by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf(listOf<QuizQuestion>()) }
    var answers by remember { mutableStateOf(mapOf<Int, String>()) }
    var submitted by remember { mutableStateOf(false) }

    fun generate() {
        if (sourceText.isBlank()) return
        isGenerating = true
        questions = emptyList()
        answers = emptyMap()
        submitted = false
        scope.launch {
            val prompt = "Generate 5 quiz questions from the following text, at $difficulty difficulty, mixing types " +
                "multiple_choice, true_false, fill_in_blank, and short_answer. Respond with ONLY a JSON array, each " +
                "object having exactly these keys: type, question, options (array of strings, only for multiple_choice " +
                "or true_false, empty array otherwise), correctAnswer (string), explanation (one short sentence).\n\nText:\n$sourceText"
            val raw = OneShotLlm.run(app, prompt)?.trim().orEmpty()
            val jsonText = raw.substringAfter("[", "").let { if (it.isBlank()) raw else "[$it" }.substringBeforeLast("]", "").let { if (it.isBlank()) raw else "$it]" }
            questions = runCatching {
                val arr = JSONArray(jsonText)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val opts = obj.optJSONArray("options")
                    QuizQuestion(
                        type = obj.optString("type", "short_answer"),
                        question = obj.optString("question"),
                        options = opts?.let { o -> (0 until o.length()).map { o.optString(it) } }.orEmpty(),
                        correctAnswer = obj.optString("correctAnswer"),
                        explanation = obj.optString("explanation")
                    )
                }
            }.getOrDefault(emptyList())
            isGenerating = false
        }
    }

    val score = remember(submitted) {
        if (!submitted) 0 else questions.indices.count { i ->
            val given = answers[i].orEmpty().trim()
            val correct = questions[i].correctAnswer.trim()
            given.isNotBlank() && (given.equals(correct, true) || correct.contains(given, true) || given.contains(correct, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz generator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = sourceText, onValueChange = { sourceText = it },
                modifier = Modifier.fillMaxWidth(), minLines = 4,
                placeholder = { Text("Paste study material to generate a quiz from") }
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { difficultyMenuOpen = true }) { Text(difficulty) }
                    DropdownMenu(expanded = difficultyMenuOpen, onDismissRequest = { difficultyMenuOpen = false }) {
                        DIFFICULTIES.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { difficulty = d; difficultyMenuOpen = false }) }
                    }
                }
                Button(onClick = ::generate, enabled = sourceText.isNotBlank() && !isGenerating, modifier = Modifier.padding(start = 8.dp)) { Text("Generate quiz") }
            }
            if (isGenerating) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Generating…")
                }
            }
            if (submitted) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) { Text("Score: $score / ${questions.size}", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium) }
            }
            questions.forEachIndexed { i, q ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${i + 1}. ${q.question}", style = MaterialTheme.typography.bodyMedium)
                        when (q.type) {
                            "multiple_choice", "true_false" -> {
                                val opts = q.options.ifEmpty { listOf("True", "False") }
                                opts.forEach { opt ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = answers[i] == opt,
                                            enabled = !submitted,
                                            onClick = { answers = answers + (i to opt) }
                                        )
                                        Text(opt, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            else -> OutlinedTextField(
                                value = answers[i].orEmpty(),
                                onValueChange = { answers = answers + (i to it) },
                                enabled = !submitted,
                                placeholder = { Text("Your answer") },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        if (submitted) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text("Correct answer: ${q.correctAnswer}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(q.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (questions.isNotEmpty() && !submitted) {
                Button(onClick = { submitted = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Submit answers") }
            }
        }
    }
}
