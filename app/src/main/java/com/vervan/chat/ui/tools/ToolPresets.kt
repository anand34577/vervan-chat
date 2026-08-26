package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.vervan.chat.model.OcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Action-list presets for [TextActionScreen] — see that file for why these share one screen
 * instead of five near-identical ones. Each prompt is self-contained (see [com.vervan.chat.llm.OneShotLlm]). */

private fun instruct(task: String) = { text: String -> "$task Respond with ONLY the result, no preamble or notes.\n\nText:\n$text" }

private val writingActions = listOf(
    TextAction("Rewrite", instruct("Rewrite the following text to be clearer and more polished, keeping the same meaning and roughly the same length.")),
    TextAction("Shorten", instruct("Shorten the following text while keeping its key meaning.")),
    TextAction("Expand", instruct("Expand the following text with more useful detail and supporting points.")),
    TextAction("Fix grammar", instruct("Fix grammar and spelling in the following text without changing its meaning or tone.")),
    TextAction("Casual tone", instruct("Rewrite the following text in a casual, friendly tone.")),
    TextAction("Formal tone", instruct("Rewrite the following text in a formal, professional tone.")),
    TextAction("Simplify", instruct("Simplify the language in the following text so it's easy to understand.")),
    TextAction("To email", instruct("Convert the following into a well-formatted email, with a subject line and greeting/sign-off.")),
    TextAction("To message", instruct("Convert the following into a short, friendly chat message.")),
    TextAction("To social post", instruct("Convert the following into a short, engaging social media post.")),
    TextAction("To formal letter", instruct("Convert the following into a formal letter, with a proper salutation and closing."))
)

private val smartNotesActions = listOf(
    TextAction("Clean notes", instruct("Turn the following rough notes into clean, well-organized notes with clear headings and bullet points where useful.")),
    TextAction("Action items", instruct("Extract a bullet list of concrete action items from the following text. If there are none, say so.")),
    TextAction("Checklist", instruct("Turn the following into a plain checklist, one item per line, no numbering.")),
    TextAction("Meeting summary", instruct("Summarize the following as meeting minutes: attendees (if mentioned), key points discussed, and outcomes.")),
    TextAction("Key decisions", instruct("List the key decisions made in the following text. If none were made, say so.")),
    TextAction("Follow-up questions", instruct("List follow-up questions that should be asked based on the following text."))
)

private val clipboardActions = listOf(
    TextAction("Summarize", instruct("Summarize the following text in a few sentences.")),
    TextAction("Translate", instruct("Translate the following text to English. If it's already in English, translate it to Spanish instead.")),
    TextAction("Explain", instruct("Explain what the following text means in plain language.")),
    TextAction("Rewrite", instruct("Rewrite the following text to be clearer.")),
    TextAction("Extract details", instruct("Extract any dates, links, addresses, and tasks mentioned in the following text, each on its own labeled line.")),
    TextAction("Draft a reply", instruct("Draft a short, polite reply to the following message."))
)

private val explainLevels = listOf("a child", "a beginner", "a student", "a professional", "an expert")
private val explainLevelActions = listOf("Child", "Beginner", "Student", "Professional", "Expert").mapIndexed { i, label ->
    TextAction(label, instruct("Explain the following in a way ${explainLevels[i]} would understand."))
}

private val screenshotActions = listOf(
    TextAction("Explain the screen", instruct("Explain what is shown on this screen and what's happening, based on its extracted text.")),
    TextAction("Summarize", instruct("Summarize the conversation or content in the following extracted screen text.")),
    TextAction("Translate", instruct("Translate the following extracted screen text to English.")),
    TextAction("Extract error", instruct("Find the error message in the following extracted screen text. State the likely cause and suggested next step.")),
    TextAction("Create a task", instruct("Turn the following extracted screen text into a single concrete task description."))
)

@Composable fun WritingAssistantScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_writing_assistant), writingActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_writing_assistant_hint), allowSaveAsNote = true)

@Composable fun SmartNotesScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_smart_notes), smartNotesActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_smart_notes_hint), allowVoice = true, allowImageOcr = true, allowSaveAsNote = true)

@Composable
fun ClipboardAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var clip by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
    }
    val text = clip ?: return
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_clipboard_assistant), clipboardActions, onBack, initialText = text, hint = stringResource(com.vervan.chat.R.string.tool_clipboard_assistant_hint), allowSaveAsNote = true)
}

@Composable fun ExplainLikeImScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_explain_like_me), explainLevelActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_explain_like_me_hint), allowImageOcr = true, allowSaveAsNote = true)

/** [imagePath] comes from a shared screenshot/image (see [com.vervan.chat.ui.nav.VervanNavGraph]'s
 * ACTION_SEND image handling) — OCR runs once here before [TextActionScreen] composes with the
 * result as its starting text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotIntelligenceScreen(onBack: () -> Unit, imagePath: String) {
    var extractedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(imagePath) {
        extractedText = withContext(Dispatchers.IO) {
            runCatching { OcrExtractor.extractFromImage(java.io.File(imagePath)) }.getOrDefault("")
        }
    }
    val text = extractedText
    if (text == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(com.vervan.chat.R.string.tool_screenshot_intelligence)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_screenshot_intelligence), screenshotActions, onBack, initialText = text, hint = stringResource(com.vervan.chat.R.string.tool_extracted_screen_text), allowSaveAsNote = true)
}

// --- Developer tools ---

private val codeExplainerActions = listOf(
    TextAction("Explain", instruct("Explain what the following code does, in plain language.")),
    TextAction("Potential bugs", instruct("List potential bugs or edge cases in the following code.")),
    TextAction("Complexity", instruct("Give a short time/space complexity summary for the following code.")),
    TextAction("Add comments", instruct("Add short, useful comments to the following code. Respond with ONLY the commented code.")),
    TextAction("Simpler version", instruct("Rewrite the following code more simply, keeping the same behavior. Respond with ONLY the code.")),
    TextAction("Unit test ideas", instruct("List unit test cases (inputs and expected outcomes) that should exist for the following code."))
)

private val regexSqlActions = listOf(
    TextAction("Generate regex", instruct("Generate a regular expression that matches the following description. Respond with ONLY the regex and one line explaining it.")),
    TextAction("Explain regex", instruct("Explain what the following regular expression matches, in plain language.")),
    TextAction("Generate SQL", instruct("Generate a SQL query for the following description. Respond with ONLY the SQL.")),
    TextAction("Explain query", instruct("Explain what the following SQL query does, in plain language.")),
    TextAction("NL to filter", instruct("Convert the following natural-language filter description into a structured filter expression (field, operator, value)."))
)

private val jsonLogActions = listOf(
    TextAction("Format JSON", instruct("Reformat the following malformed or minified JSON into clean, indented, valid JSON. Fix obvious syntax errors. Respond with ONLY the JSON.")),
    TextAction("Extract errors", instruct("Extract every error or exception line from the following log text, one per line.")),
    TextAction("Group failures", instruct("Group the repeated failures in the following log text by root cause, with a count for each group.")),
    TextAction("Summarize root cause", instruct("Summarize the most likely root cause of the failures in the following log text."))
)

@Composable fun CodeExplainerScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_code_explainer), codeExplainerActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_code_explainer_hint))

@Composable fun RegexSqlHelperScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_regex_sql_helper), regexSqlActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_regex_sql_helper_hint))

@Composable fun JsonLogAnalyzerScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_json_log_analyzer), jsonLogActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_json_log_analyzer_hint))

// --- Personal organization tools ---

private val checklistActions = listOf(
    TextAction("Travel", instruct("Generate a practical travel packing/prep checklist. Extra context from the user, if any:")),
    TextAction("Moving house", instruct("Generate a practical moving-house checklist. Extra context from the user, if any:")),
    TextAction("App release", instruct("Generate a practical app-release checklist. Extra context from the user, if any:")),
    TextAction("Interview prep", instruct("Generate a practical interview-preparation checklist. Extra context from the user, if any:")),
    TextAction("Shopping", instruct("Generate a practical shopping checklist. Extra context from the user, if any:")),
    TextAction("Emergency kit", instruct("Generate a practical emergency-kit checklist. Extra context from the user, if any:")),
    TextAction("Event planning", instruct("Generate a practical event-planning checklist. Extra context from the user, if any:"))
)

private val plannerActions = listOf(
    TextAction("Build my plan", instruct("Convert the following natural-language goal or task list into a realistic schedule/task list with suggested priorities and a rough effort estimate per item."))
)

private val goalBreakdownActions = listOf(
    TextAction("Break it down", instruct("Break the following goal down into milestones, concrete tasks under each milestone, and the very next action to take. Keep it short and actionable."))
)

private val decisionActions = listOf(
    TextAction("Analyze", instruct("Analyze the options in the following text using a structured decision framework: pros and cons, cost, risk, time, and reversibility for each option, then recommend one based on any stated priorities."))
)

@Composable fun SmartChecklistScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_smart_checklist), checklistActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_smart_checklist_hint), requireInput = false)

@Composable fun DailyPlannerScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_daily_planner), plannerActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_daily_planner_hint), allowVoice = true)

@Composable fun GoalBreakdownScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_goal_breakdown), goalBreakdownActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_goal_breakdown_hint), allowVoice = true, allowSaveAsNote = true)

@Composable fun DecisionAssistantScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_decision_assistant), decisionActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_decision_assistant_hint))

// --- Study tools (beyond the existing Quiz Generator / Explain Like I'm…) ---

private val homeworkCheckerActions = listOf(
    TextAction("Check my work", instruct("The user provided a homework question and their own answer/attempt below. Check their approach and identify missing or incorrect steps. Give hints toward fixing it WITHOUT stating the final answer outright, unless their approach is already fully correct."))
)

private val examPrepActions = listOf(
    TextAction("Study plan", instruct("Create a short study plan for the following syllabus or chapter.")),
    TextAction("Important topics", instruct("List the most important topics to focus on from the following syllabus or chapter.")),
    TextAction("Practice questions", instruct("Write 5 practice questions (with short answers) based on the following syllabus or chapter.")),
    TextAction("Revision cards", instruct("Write 8 short revision flashcards (Q: / A: pairs) based on the following syllabus or chapter.")),
    TextAction("Last-minute summary", instruct("Write a very short last-minute-cram summary of the following syllabus or chapter."))
)

private val presentationActions = listOf(
    TextAction("Analyze delivery", instruct("The following is a transcript of a spoken presentation. Identify filler words, repetition, unclear phrasing, speaking pace issues, and anything important that seems to be missing. Then suggest 3 questions an audience member might ask."))
)

@Composable fun HomeworkCheckerScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_homework_checker), homeworkCheckerActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_homework_checker_hint), allowImageOcr = true)

@Composable fun ExamPreparationScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_exam_preparation), examPrepActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_exam_preparation_hint), allowImageOcr = true, allowSaveAsNote = true)

@Composable fun PresentationPracticeScreen(onBack: () -> Unit) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_presentation_practice), presentationActions, onBack, hint = stringResource(com.vervan.chat.R.string.tool_presentation_practice_hint), allowVoice = true)

/** OCR'd multi-page text (whiteboard photos or scanned textbook pages, see
 * [DocumentScannerScreen]'s "Process as study material" export) turned into notes, tasks, a
 * mind map, or a summary — covers Whiteboard Scanner and Book/Textbook Scanner without a
 * separate capture screen, since both are just Document Scanner's OCR output routed here. */
private val studyMaterialActions = listOf(
    TextAction("Clean notes", instruct("Turn the following OCR'd scanned pages into clean, well-organized notes, removing obvious headers/footers/page numbers and merging split paragraphs.")),
    TextAction("Tasks", instruct("Extract a bullet list of action items/tasks from the following OCR'd scanned pages.")),
    TextAction("Mind map", instruct("Turn the following OCR'd scanned pages into an indented text mind map: main topic, then branches, then sub-branches.")),
    TextAction("Chapter summary", instruct("Write a chapter summary of the following OCR'd scanned pages, removing obvious headers/footers/page numbers first.")),
    TextAction("Summary", instruct("Summarize the following OCR'd scanned pages in a few paragraphs."))
)

@Composable
fun StudyMaterialScreen(onBack: () -> Unit, scannedText: String) =
    TextActionScreen(stringResource(com.vervan.chat.R.string.tool_study_material), studyMaterialActions, onBack, initialText = scannedText, hint = stringResource(com.vervan.chat.R.string.tool_scanned_text), allowSaveAsNote = true)

// --- Multi-turn interactive tools (see TurnBasedChatScreen) ---

@Composable fun SocraticTutorScreen(onBack: () -> Unit) = TurnBasedChatScreen(
    title = stringResource(com.vervan.chat.R.string.tool_socratic_tutor),
    systemInstruction = "You are a Socratic tutor. Never give the final answer directly — instead ask a guiding question or give a small hint that leads the user to figure it out themselves. Only confirm the answer once the user has clearly reached it.",
    setupHint = stringResource(com.vervan.chat.R.string.tool_socratic_tutor_hint),
    onBack = onBack
)

@Composable fun InterviewPracticeScreen(onBack: () -> Unit) = TurnBasedChatScreen(
    title = stringResource(com.vervan.chat.R.string.tool_interview_practice),
    systemInstruction = "You are a job interviewer. Ask one interview question at a time. After each answer, give brief feedback on clarity, relevance, confidence, and completeness, then ask the next question.",
    setupHint = stringResource(com.vervan.chat.R.string.tool_interview_practice_hint),
    onBack = onBack
)

@Composable fun LanguagePracticeScreen(onBack: () -> Unit) = TurnBasedChatScreen(
    title = stringResource(com.vervan.chat.R.string.tool_language_practice),
    systemInstruction = "You are role-playing a real-life conversation scenario with the user to help them practice a language. Stay in character for the scenario. After each of the user's replies, gently correct any grammar mistakes and suggest better phrasing or vocabulary in brackets, then continue the roleplay.",
        setupHint = stringResource(com.vervan.chat.R.string.tool_language_practice_hint),
    onBack = onBack
)
