package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Quiz
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
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.theme.Space
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
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.validation.InputLimits
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class QuizQuestion(val type: String, val question: String, val options: List<String>, val correctAnswer: String, val explanation: String)

private val DIFFICULTIES = listOf("Easy", "Medium", "Hard")

/** Paste/scanned text -> LLM-generated quiz (JSON array), answered interactively, scored
 * client-side. Grading for free-text answers is a simple case-insensitive containment check —
 * no semantic grading, good enough for self-check practice, not exam scoring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizGeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var sourceText by rememberSaveable { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Medium") }
    var difficultyMenuOpen by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf(listOf<QuizQuestion>()) }
    var answers by remember { mutableStateOf(mapOf<Int, String>()) }
    var submitted by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun generate() {
        if (sourceText.isBlank()) return
        isGenerating = true
        questions = emptyList()
        answers = emptyMap()
        submitted = false
        errorText = null
        scope.launch {
            try {
                val prompt = "Generate 5 quiz questions from the following text, at $difficulty difficulty, mixing types " +
                    "multiple_choice, true_false, fill_in_blank, and short_answer. Respond with ONLY a JSON array, each " +
                    "object having exactly these keys: type, question, options (array of strings, only for multiple_choice " +
                    "or true_false, empty array otherwise), correctAnswer (string), explanation (one short sentence).\n\nText:\n$sourceText"
                // run() (not stream()) — the whole JSON array must be complete before it can be parsed.
                val raw = OneShotLlm.run(
                    app, prompt,
                    runContext = com.vervan.chat.llm.ToolRunContext("tools/quiz-generator", "Quiz generator", sourceText),
                )?.trim()
                if (raw == null) {
                    errorText = "No model is ready. Open Settings → AI models, load one, then generate again."
                } else if (raw.length > 100_000) {
                    errorText = "The generated quiz response was too large. Try again with shorter material."
                } else {
                    val jsonText = raw.substringAfter("[", "").let { if (it.isBlank()) raw else "[$it" }.substringBeforeLast("]", "").let { if (it.isBlank()) raw else "$it]" }
                    questions = runCatching {
                        val arr = JSONArray(jsonText)
                        (0 until minOf(arr.length(), 5)).map { i ->
                            val obj = arr.getJSONObject(i)
                            val opts = obj.optJSONArray("options")
                            QuizQuestion(
                                type = obj.optString("type", "short_answer"),
                                question = obj.optString("question"),
                                options = opts?.let { o -> (0 until minOf(o.length(), 4)).map { o.optString(it).take(1_000) } }.orEmpty(),
                                correctAnswer = obj.optString("correctAnswer").take(1_000),
                                explanation = obj.optString("explanation").take(2_000)
                            )
                        }
                    }.getOrDefault(emptyList())
                    if (questions.isEmpty()) {
            errorText = "Could not create the quiz. Shorten the text, then try again."
                    }
                }
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isGenerating = false
            }
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
                title = { Text(stringResource(R.string.quiz_generator_quiz_generator)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.Filled.Quiz,
                title = stringResource(R.string.quiz_generator_turn_material_into_active_recall),
                body = stringResource(R.string.quiz_generator_add_study_material_and_create)
            )
            OutlinedTextField(
                value = sourceText, onValueChange = { sourceText = it.take(100_000) },
                modifier = Modifier.fillMaxWidth(), minLines = 4,
                placeholder = { Text(stringResource(R.string.quiz_generator_paste_study_material_to_generate)) }
            )
            // Difficulty is a labelled choice of three, and Generate is the screen's full-width
            // primary action like every other tool — not a bare "Medium" button beside a small one.
            Text(stringResource(R.string.quiz_difficulty), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                DIFFICULTIES.forEach { d ->
                    com.vervan.chat.ui.common.VervanFilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d) })
                }
            }
            Button(
                onClick = ::generate,
                enabled = sourceText.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.quiz_generator_generate_quiz)) }
            if (isGenerating) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.quiz_generator_building_your_quiz),
                    body = stringResource(R.string.quiz_generator_creating_five_arg1_questions_from, difficulty)
                )
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.quiz_generator_couldn_t_generate_a_quiz),
                    message = it,
                    recovery = stringResource(R.string.quiz_generator_shorten_the_material_or_load),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = { generate() },
                    modifier = Modifier.padding(top = Space.lg)
                )
            }
            if (submitted) {
                ToolResultHeader(
                    title = stringResource(R.string.quiz_generator_quiz_complete),
                    supportingText = stringResource(R.string.quiz_generator_you_answered_arg1_of_arg2, score, questions.size)
                )
                Card(
                    Modifier.fillMaxWidth().padding(top = Space.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) { Text(stringResource(R.string.quiz_generator_score_arg1_arg2, score, questions.size), Modifier.padding(Space.md), style = MaterialTheme.typography.titleMedium) }
            }
            questions.forEachIndexed { i, q ->
                Card(Modifier.fillMaxWidth().padding(top = Space.md)) {
                    Column(Modifier.padding(Space.md)) {
                        Text(stringResource(R.string.quiz_generator_arg1_arg2, i + 1, q.question), style = MaterialTheme.typography.bodyMedium)
                        when (q.type) {
                            "multiple_choice", "true_false" -> {
                                val opts = q.options.ifEmpty { listOf("True", "False") }
                                opts.forEach { opt ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = Space.xs),
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
                                onValueChange = { answers = answers + (i to it.take(1_000)) },
                                enabled = !submitted,
                                placeholder = { Text(stringResource(R.string.quiz_generator_your_answer)) },
                                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                            )
                        }
                        if (submitted) {
                            HorizontalDivider(Modifier.padding(vertical = Space.sm))
                            Text(stringResource(R.string.quiz_generator_correct_answer_arg1, q.correctAnswer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(q.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (questions.isNotEmpty() && !submitted) {
                Button(onClick = { submitted = true }, modifier = Modifier.fillMaxWidth().padding(top = Space.md)) { Text(stringResource(R.string.quiz_generator_submit_answers)) }
            }
        }
        }
    }
}
