package com.vervan.chat.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vervan.chat.IncomingShare
import com.vervan.chat.IncomingShareKind
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.modelload.ModelLoadPhase
import com.vervan.chat.ui.chat.BranchTreeScreen
import com.vervan.chat.ui.chat.ChatScreen
import com.vervan.chat.ui.chat.ChatInfoScreen
import com.vervan.chat.ui.chats.ChatListScreen
import com.vervan.chat.ui.collections.SmartCollectionsScreen
import com.vervan.chat.ui.dev.DevWorkspaceScreen
import com.vervan.chat.ui.folders.FolderDetailScreen
import com.vervan.chat.ui.folders.FoldersListScreen
import com.vervan.chat.ui.home.HomeScreen
import com.vervan.chat.ui.knowledge.DocumentViewerScreen
import com.vervan.chat.ui.knowledge.KnowledgeBaseDetailScreen
import com.vervan.chat.ui.knowledge.KnowledgeScreen
import com.vervan.chat.ui.knowledge.SourcePassageScreen
import com.vervan.chat.ui.library.LibraryScreen
import com.vervan.chat.ui.memory.MemorySuggestionsScreen
import com.vervan.chat.ui.models.ModelManagerScreen
import com.vervan.chat.ui.models.ModelCalculatorScreen
import com.vervan.chat.ui.notes.NoteEditorScreen
import com.vervan.chat.ui.notes.NotesListScreen
import com.vervan.chat.ui.onboarding.OnboardingScreen
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.vervanBrandGradient
import com.vervan.chat.ui.personas.PersonaEditorScreen
import com.vervan.chat.ui.personas.PersonaTestBenchScreen
import com.vervan.chat.ui.profile.UserProfileScreen
import com.vervan.chat.ui.projects.ProjectDashboardScreen
import com.vervan.chat.ui.projects.ProjectsListScreen
import com.vervan.chat.ui.search.SearchScreen
import com.vervan.chat.ui.settings.AppearanceSettingsScreen
import com.vervan.chat.ui.settings.AccessibilitySettingsScreen
import com.vervan.chat.ui.settings.BackupScreen
import com.vervan.chat.ui.settings.DiagnosticsScreen
import com.vervan.chat.ui.settings.ExperienceControlsSettingsScreen
import com.vervan.chat.ui.settings.GenerationRetrievalSettingsScreen
import com.vervan.chat.ui.settings.IndexMaintenanceScreen
import com.vervan.chat.ui.settings.JobQueueScreen
import com.vervan.chat.ui.settings.RecycleBinScreen
import com.vervan.chat.ui.settings.SettingsScreen
import com.vervan.chat.ui.settings.StorageDataSettingsScreen
import com.vervan.chat.ui.settings.VoiceSettingsScreen
import com.vervan.chat.ui.study.StudyReviewScreen
import com.vervan.chat.ui.study.StudyWorkspaceScreen
import com.vervan.chat.ui.templates.TemplateEditorScreen
import com.vervan.chat.ui.workflows.WorkflowEditorScreen
import com.vervan.chat.ui.workflows.WorkflowListScreen
import com.vervan.chat.ui.workflows.WorkflowRunScreen
import com.vervan.chat.ui.workspaces.WorkspaceDetailScreen
import com.vervan.chat.ui.workspaces.WorkspacesScreen
import com.vervan.chat.ui.writing.WritingWorkspaceScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private data class PendingChatAttachment(
    val chatId: String,
    val uri: android.net.Uri,
    val asImage: Boolean,
    val showPreview: Boolean,
)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("chats", "Chats", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat)
)
private val libraryTab = Tab("library", "Library", Icons.Outlined.Folder, Icons.Filled.Folder)
private val toolsTab = Tab("tools", "Tools", Icons.Outlined.GridView, Icons.Filled.GridView)
private val trailingTabs = listOf(
    libraryTab,
    toolsTab
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun VervanNavGraph(
    app: VervanApp,
    incomingShare: IncomingShare? = null,
    onShareConsumed: () -> Unit = {},
    shortcut: String? = null,
    intentVersion: Int = 0,
    windowSizeClass: WindowSizeClass? = null,
) {
    val navController = rememberNavController()
    val prefs = LocalContext.current.getSharedPreferences("vervan", 0)
    val startDestination = if (prefs.getBoolean("onboarded", false)) "home" else "onboarding"
    var pendingStudyMaterialText by remember { mutableStateOf<String?>(null) }
    // Targeted by chat ID so a share received while another chat is open cannot attach to the
    // old composer during the navigation frame.
    var pendingChatAttachment by remember { mutableStateOf<PendingChatAttachment?>(null) }
    var pendingMessageJump by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Launcher shortcuts — navigate to the relevant destination on launch.
    androidx.compose.runtime.LaunchedEffect(shortcut, intentVersion) {
        if (shortcut == null || !prefs.getBoolean("onboarded", false)) return@LaunchedEffect
        // "Open in Vervan" from the screen-assist overlay deep-links straight to the saved chat.
        if (shortcut.startsWith("open_chat:")) {
            navController.navigate("chat/${shortcut.removePrefix("open_chat:")}")
            return@LaunchedEffect
        }
        when (shortcut) {
            "new_chat", "voice" -> {
                val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                app.container.db.chatDao().upsert(chat)
                navController.navigate(if (shortcut == "voice") "chat/${chat.id}/voice" else "chat/${chat.id}")
            }
            "capture" -> {
                val note = Note(title = "Quick note")
                app.container.db.noteDao().upsert(note)
                navController.navigate("note/${note.id}")
            }
            "search" -> navController.navigate("search")
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    androidx.compose.runtime.LaunchedEffect(incomingShare, intentVersion, currentRoute?.route) {
        val share = incomingShare ?: return@LaunchedEffect
        if (!prefs.getBoolean("onboarded", false)) return@LaunchedEffect
        val chat = app.container.workspaceManager.applyDefaults(
            Chat(
                draft = share.text.orEmpty(),
                workspaceId = app.container.settingsRepository.activeWorkspaceId.first(),
            )
        )
        app.container.db.chatDao().upsert(chat)
        share.uri?.let { uri ->
            pendingChatAttachment = PendingChatAttachment(
                chatId = chat.id,
                uri = uri,
                asImage = share.kind == IncomingShareKind.IMAGE,
                showPreview = true,
            )
        }
        onShareConsumed()
        navController.navigate("chat/${chat.id}") { launchSingleTop = true }
    }
    val allTabs = tabs + trailingTabs
    val showBottomBar = allTabs.any { currentRoute?.hierarchy?.any { d -> d.route == it.route } == true }
    // Tablet/foldable: a side rail instead of a bottom bar once the window is wider than a
    // phone (adaptive-layout gap) — same destinations, just repositioned.
    val useRail = windowSizeClass?.widthSizeClass != null && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val useTwoPane = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val activeJobs by app.container.db.jobDao().observeActive().collectAsState(initial = emptyList())
    val modelLoadState by app.container.modelLoadCoordinator.state.collectAsState()
    val loadingModels = modelLoadState.values.count { it.phase == ModelLoadPhase.LOADING }
    val downloadStates by app.container.modelDownloadRepository.uiStates.collectAsState(initial = emptyList())
    val activeDownloads = downloadStates.count {
        it.status in setOf(
            ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.WAITING_FOR_NETWORK,
            ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE, ModelStatus.DOWNLOADING,
            ModelStatus.PAUSING, ModelStatus.VERIFYING, ModelStatus.IMPORTING,
        )
    }

    Row(Modifier.fillMaxSize()) {
        if (useRail && showBottomBar) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(vervanBrandGradient())
                        .clickable { showCreateSheet = true }
                        .semantics { contentDescription = "Create" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                tabs.forEach { tab -> RailTabItem(tab, currentRoute, navController) }
                Spacer(Modifier.weight(1f))
                trailingTabs.forEach { tab -> RailTabItem(tab, currentRoute, navController) }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            // This shell has no topBar of its own — every screen inside NavHost brings its
            // own Scaffold + TopAppBar, which already reserves the status-bar inset. Without
            // this override, Scaffold's default `contentWindowInsets` (WindowInsets.systemBars)
            // reserves that same top inset a second time in the padding handed to NavHost,
            // stacking two status-bar-height gaps above every screen's title.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && showBottomBar) {
                    VervanNavDock(
                        leading = tabs,
                        trailing = trailingTabs,
                        currentRoute = currentRoute,
                        navController = navController,
                        onCreate = { showCreateSheet = true }
                    )
                }
            }
        ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onDone = {
                        prefs.edit().putBoolean("onboarded", true).apply()
                        navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                    },
                    onImportModel = { navController.navigate("models") }
                )
            }
            composable("home") {
                HomeScreen(
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenModels = { navController.navigate("models") },
                    onOpenNotes = { navController.navigate("notes") },
                    onOpenProjects = { navController.navigate("projects") },
                    onOpenLibrary = { navController.navigate("library") },
                    onOpenChats = { navController.navigate("chats") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenProject = { projectId -> navController.navigate("project/$projectId") },
                    onOpenKnowledge = { navController.navigate("knowledge") },
                    onOpenSearch = { navController.navigate("search") },
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenWorkspaces = { navController.navigate("workspaces") },
                    onOpenDocScanner = { navController.navigate("tools/document-scanner") },
                    onOpenVoiceChat = { navController.navigate("tools/voice-chat") },
                    onOpenTranslate = { navController.navigate("tools/translate") },
                    onOpenWritingAssistant = { navController.navigate("tools/writing-assistant") },
                    onOpenAllTools = { navController.navigatePrimaryRoot("tools") }
                )
            }
            composable("tools/document-scanner") {
                com.vervan.chat.ui.tools.DocumentScannerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onProcessAsStudyMaterial = { text -> pendingStudyMaterialText = text; navController.navigate("tools/study-material") }
                )
            }
            composable("tools/ocr-scanner") {
                com.vervan.chat.ui.tools.OcrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") }
                )
            }
            composable("tools/voice-chat") {
                com.vervan.chat.ui.tools.VoiceChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenKeyboard = {
                        scope.launch {
                            val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                            app.container.db.chatDao().upsert(chat)
                            navController.navigate("chat/${chat.id}")
                        }
                    },
                    onOpenModelManager = { navController.navigate("models") }
                )
            }
            composable("tools/translate") { com.vervan.chat.ui.tools.TranslationScreen(onBack = { navController.popBackStack() }) }
            composable("tools/writing-assistant") { com.vervan.chat.ui.tools.WritingAssistantScreen(onBack = { navController.popBackStack() }) }
            composable("tools/smart-notes") { com.vervan.chat.ui.tools.SmartNotesScreen(onBack = { navController.popBackStack() }) }
            composable("tools/clipboard-assistant") { com.vervan.chat.ui.tools.ClipboardAssistantScreen(onBack = { navController.popBackStack() }) }
            composable("tools/explain-level") { com.vervan.chat.ui.tools.ExplainLikeImScreen(onBack = { navController.popBackStack() }) }
            composable(
                "tools/screenshot-intel?imagePath={imagePath}",
                arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
            ) { entry ->
                val imagePath = entry.arguments?.getString("imagePath")?.let { android.net.Uri.decode(it) } ?: return@composable
                com.vervan.chat.ui.tools.ScreenshotIntelligenceScreen(onBack = { navController.popBackStack() }, imagePath = imagePath)
            }
            composable("tools/receipt-scanner") {
                com.vervan.chat.ui.tools.StructuredScanScreen(kind = com.vervan.chat.ui.tools.ScanKind.RECEIPT, onBack = { navController.popBackStack() })
            }
            composable("tools/table-scanner") {
                com.vervan.chat.ui.tools.StructuredScanScreen(kind = com.vervan.chat.ui.tools.ScanKind.TABLE, onBack = { navController.popBackStack() })
            }
            composable("tools/quiz-generator") { com.vervan.chat.ui.tools.QuizGeneratorScreen(onBack = { navController.popBackStack() }) }
            composable("tools") {
                com.vervan.chat.ui.tools.AllToolsScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable("tools/all") {
                com.vervan.chat.ui.tools.AllToolsScreen(onBack = { navController.popBackStack() }, onNavigate = { route -> navController.navigate(route) })
            }
            composable(
                "tools/runs?highlightId={highlightId}",
                arguments = listOf(navArgument("highlightId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                com.vervan.chat.ui.tools.ToolRunHistoryScreen(
                    onBack = { navController.popBackStack() },
                    highlightId = entry.arguments?.getString("highlightId"),
                    onContinueInChat = { text ->
                        scope.launch {
                            val chat = app.container.workspaceManager.applyDefaults(
                                Chat(draft = "Continue from this result:\n\n$text", workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                            )
                            app.container.db.chatDao().upsert(chat)
                            navController.navigate("chat/${chat.id}")
                        }
                    },
                    onRerun = { route -> navController.navigate(route) },
                )
            }
            composable("tools/socratic-tutor") { com.vervan.chat.ui.tools.SocraticTutorScreen(onBack = { navController.popBackStack() }) }
            composable("tools/exam-prep") { com.vervan.chat.ui.tools.ExamPreparationScreen(onBack = { navController.popBackStack() }) }
            composable("tools/homework-checker") { com.vervan.chat.ui.tools.HomeworkCheckerScreen(onBack = { navController.popBackStack() }) }
            composable("tools/language-practice") { com.vervan.chat.ui.tools.LanguagePracticeScreen(onBack = { navController.popBackStack() }) }
            composable("tools/pronunciation-coach") { com.vervan.chat.ui.tools.PronunciationCoachScreen(onBack = { navController.popBackStack() }) }
            composable("tools/live-translator") { com.vervan.chat.ui.tools.LiveConversationTranslatorScreen(onBack = { navController.popBackStack() }) }
            composable("tools/interview-practice") { com.vervan.chat.ui.tools.InterviewPracticeScreen(onBack = { navController.popBackStack() }) }
            composable("tools/presentation-practice") { com.vervan.chat.ui.tools.PresentationPracticeScreen(onBack = { navController.popBackStack() }) }
            composable("tools/daily-planner") { com.vervan.chat.ui.tools.DailyPlannerScreen(onBack = { navController.popBackStack() }) }
            composable("tools/goal-breakdown") { com.vervan.chat.ui.tools.GoalBreakdownScreen(onBack = { navController.popBackStack() }) }
            composable("tools/decision-assistant") { com.vervan.chat.ui.tools.DecisionAssistantScreen(onBack = { navController.popBackStack() }) }
            composable("tools/smart-checklist") { com.vervan.chat.ui.tools.SmartChecklistScreen(onBack = { navController.popBackStack() }) }
            composable("tools/code-explainer") { com.vervan.chat.ui.tools.CodeExplainerScreen(onBack = { navController.popBackStack() }) }
            composable("tools/regex-sql-helper") { com.vervan.chat.ui.tools.RegexSqlHelperScreen(onBack = { navController.popBackStack() }) }
            composable("tools/json-log-analyzer") { com.vervan.chat.ui.tools.JsonLogAnalyzerScreen(onBack = { navController.popBackStack() }) }
            composable("tools/image-caption") { com.vervan.chat.ui.tools.ImageCaptionScreen(onBack = { navController.popBackStack() }) }
            composable("tools/flashcards-photo") {
                com.vervan.chat.ui.tools.FlashcardsFromPhotoScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSet = { setName ->
                        navController.navigate("study/${android.net.Uri.encode(setName)}") {
                            popUpTo("tools/flashcards-photo") { inclusive = true }
                        }
                    }
                )
            }
            composable("tools/chat-with-file") {
                com.vervan.chat.ui.tools.ChatWithFileScreen(
                    onBack = { navController.popBackStack() },
                    onFileChosen = { uri ->
                        scope.launch {
                            val chat = app.container.workspaceManager.applyDefaults(
                                com.vervan.chat.data.db.entities.Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                            )
                            app.container.db.chatDao().upsert(chat)
                            pendingChatAttachment = PendingChatAttachment(
                                chatId = chat.id,
                                uri = uri,
                                asImage = false,
                                showPreview = false,
                            )
                            navController.navigate("chat/${chat.id}") {
                                popUpTo("tools/chat-with-file") { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable("tools/smart-form-filler") {
                com.vervan.chat.ui.tools.StructuredScanScreen(kind = com.vervan.chat.ui.tools.ScanKind.CUSTOM, onBack = { navController.popBackStack() })
            }
            composable("tools/document-comparison") { com.vervan.chat.ui.tools.DocumentComparisonScreen(onBack = { navController.popBackStack() }) }
            composable("tools/email-composer") { com.vervan.chat.ui.tools.EmailComposerScreen(onBack = { navController.popBackStack() }) }
            composable("tools/model-dashboard") { com.vervan.chat.ui.tools.ModelCapabilityDashboardScreen(onBack = { navController.popBackStack() }) }
            composable("tools/study-material") {
                val text = pendingStudyMaterialText
                if (text == null) {
                    navController.popBackStack()
                } else {
                    com.vervan.chat.ui.tools.StudyMaterialScreen(onBack = { navController.popBackStack() }, scannedText = text)
                }
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") },
                    onOpenKnowledge = { kbId -> navController.navigate("knowledge/$kbId") },
                    onOpenPersona = { id -> navController.navigate("persona/$id/edit") },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenMemory = { memoryId -> navController.navigate("memory?highlightId=$memoryId") },
                    onOpenMessage = { chatId, messageId ->
                        pendingMessageJump = chatId to messageId
                        navController.navigate("chat/$chatId")
                    },
                    onOpenProject = { id -> navController.navigate("project/$id") },
                    onOpenWorkspace = { id -> navController.navigate("workspace/$id") },
                    onOpenFolder = { id -> navController.navigate("folder/$id") },
                    onOpenTemplate = { id -> navController.navigate("template/$id/edit") },
                    onOpenWorkflow = { id -> navController.navigate("workflow/$id") },
                    onOpenSavedOutput = { _ -> navController.navigate("library") },
                    onOpenTool = { route -> navController.navigate(route) },
                    onOpenToolRun = { id -> navController.navigate("tools/runs?highlightId=$id") },
                )
            }
            composable("writing") { WritingWorkspaceScreen(onBack = { navController.popBackStack() }) }
            composable("dev") { DevWorkspaceScreen(onBack = { navController.popBackStack() }) }
            composable("study") {
                StudyWorkspaceScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSet = { setName -> navController.navigate("study/${android.net.Uri.encode(setName)}") }
                )
            }
            composable("study/{setName}") { entry ->
                val setName = entry.arguments?.getString("setName")?.let { android.net.Uri.decode(it) } ?: return@composable
                StudyReviewScreen(setName = setName, onBack = { navController.popBackStack() })
            }
            composable("workflows") {
                WorkflowListScreen(
                    onOpenWorkflow = { workflowId -> navController.navigate("workflow/$workflowId") },
                    onNewWorkflow = { navController.navigate("workflow-new") },
                    onEditWorkflow = { workflowId -> navController.navigate("workflow/$workflowId/edit") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("workflow/{workflowId}") { entry ->
                val workflowId = entry.arguments?.getString("workflowId") ?: return@composable
                WorkflowRunScreen(workflowId = workflowId, onBack = { navController.popBackStack() })
            }
            composable("workflow-new") {
                WorkflowEditorScreen(workflowId = null, onBack = { navController.popBackStack() })
            }
            composable("workflow/{workflowId}/edit") { entry ->
                val workflowId = entry.arguments?.getString("workflowId") ?: return@composable
                WorkflowEditorScreen(workflowId = workflowId, onBack = { navController.popBackStack() })
            }
            composable("library") {
                LibraryScreen(
                    onOpenPersona = { id -> navController.navigate("persona/$id/edit") },
                    onNewPersona = { navController.navigate("persona-new") },
                    onOpenWorkflow = { id -> navController.navigate("workflow/$id") },
                    onNewWorkflow = { navController.navigate("workflow-new") },
                    onEditWorkflow = { id -> navController.navigate("workflow/$id/edit") },
                    onOpenTemplate = { id -> navController.navigate("template/$id/edit") },
                    onNewTemplate = { navController.navigate("template-new") }
                )
            }
            composable("template-new") {
                TemplateEditorScreen(templateId = null, onBack = { navController.popBackStack() })
            }
            composable("template/{templateId}/edit") { entry ->
                val templateId = entry.arguments?.getString("templateId") ?: return@composable
                TemplateEditorScreen(templateId = templateId, onBack = { navController.popBackStack() })
            }
            composable("persona-new") {
                PersonaEditorScreen(personaId = null, onBack = { navController.popBackStack() }, onDuplicated = { id -> navController.navigate("persona/$id/edit") })
            }
            composable("persona/{personaId}/edit") { entry ->
                val personaId = entry.arguments?.getString("personaId") ?: return@composable
                PersonaEditorScreen(
                    personaId = personaId,
                    onBack = { navController.popBackStack() },
                    onDuplicated = { id -> navController.navigate("persona/$id/edit") },
                    onTest = { id -> navController.navigate("persona/$id/test") }
                )
            }
            composable(
                "memory?highlightId={highlightId}",
                arguments = listOf(navArgument("highlightId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                com.vervan.chat.ui.memory.MemoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSuggestions = { navController.navigate("memory-suggestions") },
                    highlightMemoryId = entry.arguments?.getString("highlightId")
                )
            }
            composable("notes") {
                NotesListScreen(
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("note/{noteId}") { entry ->
                val noteId = entry.arguments?.getString("noteId") ?: return@composable
                NoteEditorScreen(
                    noteId = noteId,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }
            composable("projects") {
                ProjectsListScreen(
                    onOpenProject = { projectId -> navController.navigate("project/$projectId") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("project/{projectId}") { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                ProjectDashboardScreen(
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") }
                )
            }
            composable("chats") {
                if (useTwoPane) {
                    com.vervan.chat.ui.chats.ChatsTwoPaneScreen(
                        onOpenBranchTree = { chatId -> navController.navigate("chat/$chatId/tree") },
                        onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                        onOpenChatInfo = { chatId -> navController.navigate("chat/$chatId/info") },
                        onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                        onOpenModels = { navController.navigate("models") }
                    )
                } else {
                    ChatListScreen(onOpenChat = { chatId -> navController.navigate("chat/$chatId") })
                }
            }
            composable("chat/{chatId}") { backStackEntry2 ->
                val chatId = backStackEntry2.arguments?.getString("chatId") ?: return@composable
                val attachment = pendingChatAttachment?.takeIf { it.chatId == chatId }
                ChatScreen(
                    chatId = chatId,
                    initialMessageId = pendingMessageJump?.takeIf { it.first == chatId }?.second,
                    onInitialMessageConsumed = { if (pendingMessageJump?.first == chatId) pendingMessageJump = null },
                    pendingAttachUri = attachment?.uri,
                    pendingAttachAsImage = attachment?.asImage == true,
                    pendingAttachShowPreview = attachment?.showPreview == true,
                    onAttachConsumed = {
                        if (pendingChatAttachment?.chatId == chatId) pendingChatAttachment = null
                    },
                    onBack = { navController.popBackStack() },
                    onOpenChatInfo = { navController.navigate("chat/$chatId/info") },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenBranchTree = { navController.navigate("chat/$chatId/tree") },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenFolders = { navController.navigate("folders") },
                    onOpenModels = { navController.navigate("models") },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") },
                    // Forking replaces this chat in the back stack instead of stacking on top of
                    // it — otherwise forking twice then pressing Back walks back through each
                    // fork instead of leaving the chat entirely (user ask).
                    onForkChat = { forkedChatId ->
                        navController.navigate("chat/$forkedChatId") {
                            popUpTo(backStackEntry2.destination.id) { inclusive = true }
                        }
                    }
                )
            }
            composable("chat/{chatId}/{startAction}") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    initialAction = entry.arguments?.getString("startAction"),
                    onBack = { navController.popBackStack() },
                    onOpenChatInfo = { navController.navigate("chat/$chatId/info") },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenBranchTree = { navController.navigate("chat/$chatId/tree") },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenFolders = { navController.navigate("folders") },
                    onOpenModels = { navController.navigate("models") },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") },
                    onForkChat = { forkedChatId ->
                        navController.navigate("chat/$forkedChatId") {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                )
            }
            composable("chat/{chatId}/tree") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                BranchTreeScreen(chatId = chatId, onBack = { navController.popBackStack() })
            }
            composable("chat/{chatId}/info") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                ChatInfoScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") }
                )
            }
            composable("knowledge") {
                KnowledgeScreen(onOpenKb = { kbId -> navController.navigate("knowledge/$kbId") })
            }
            composable("knowledge/{kbId}") { entry ->
                val kbId = entry.arguments?.getString("kbId") ?: return@composable
                KnowledgeBaseDetailScreen(kbId = kbId, onBack = { navController.popBackStack() }, onOpenDocument = { docId -> navController.navigate("document/$docId") })
            }
            composable("models") {
                ModelManagerScreen(
                    onOpenStore = { navController.navigate("models/store") },
                    onBack = { navController.popBackStack() },
                    onOpenCalculator = { navController.navigate("models/calculator") }
                )
            }
            composable("models/calculator") {
                ModelCalculatorScreen(
                    onBack = { navController.popBackStack() },
                    // Pops back to the existing Model Manager entry rather than pushing a fresh
                    // one — that recomposes it, which is what actually consumes the just-stashed
                    // budget (see PendingModelBrowseFilter/browseBudgetBytes).
                    onBrowseModels = { navController.popBackStack() }
                )
            }
            composable("models/store") {
                com.vervan.chat.ui.store.ModelStoreScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navController.navigate("models") },
                    onOpenMemory = { navController.navigate("memory") },
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenMemorySuggestions = { navController.navigate("memory-suggestions") },
                    onOpenAppearance = { navController.navigate("settings/appearance") },
                    onOpenExperience = { navController.navigate("settings/experience") },
                    onOpenAccessibility = { navController.navigate("settings/accessibility") },
                    onOpenGeneration = { navController.navigate("settings/generation") },
                    onOpenVoice = { navController.navigate("settings/voice") },
                    onOpenStorage = { navController.navigate("settings/storage") },
                    onOpenSecurity = { navController.navigate("settings/security") },
                    onOpenTools = { navController.navigate("settings/tools") }
                )
            }
            composable("settings/tools") { com.vervan.chat.ui.settings.ToolsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/appearance") { AppearanceSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/experience") {
                ExperienceControlsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGeneration = { navController.navigate("settings/generation") },
                    onOpenModels = { navController.navigate("models") }
                )
            }
            composable("settings/accessibility") { AccessibilitySettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/generation") { GenerationRetrievalSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/voice") {
                VoiceSettingsScreen(onBack = { navController.popBackStack() }, onOpenModelManager = { navController.navigate("models") })
            }
            composable("settings/security") {
                com.vervan.chat.ui.settings.SecuritySettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate("settings/permissions") },
                    onOpenApiServer = { navController.navigate("settings/api-server") }
                )
            }
            composable("settings/permissions") { com.vervan.chat.ui.settings.PermissionsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/api-server") { com.vervan.chat.ui.settings.ApiServerScreen(onBack = { navController.popBackStack() }) }
            composable("settings/storage") {
                StorageDataSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate("backup") },
                    onOpenRecycleBin = { navController.navigate("recycle-bin") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenJobs = { navController.navigate("jobs") },
                    onOpenIndexMaintenance = { navController.navigate("index-maintenance") },
                    onOpenModelCalculator = { navController.navigate("models/calculator") }
                )
            }
            composable("backup") { BackupScreen(onBack = { navController.popBackStack() }) }
            composable("recycle-bin") { RecycleBinScreen(onBack = { navController.popBackStack() }) }
            composable("diagnostics") {
                DiagnosticsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate("settings/permissions") }
                )
            }
            composable("jobs") { JobQueueScreen(onBack = { navController.popBackStack() }) }
            composable("index-maintenance") { IndexMaintenanceScreen(onBack = { navController.popBackStack() }) }
            composable("profile") { UserProfileScreen(onBack = { navController.popBackStack() }) }
            composable("folders") {
                FoldersListScreen(onBack = { navController.popBackStack() }, onOpenFolder = { id -> navController.navigate("folder/$id") })
            }
            composable("workspaces") {
                WorkspacesScreen(onBack = { navController.popBackStack() }, onOpenWorkspace = { id -> navController.navigate("workspace/$id") })
            }
            composable("workspace/{workspaceId}") { entry ->
                val workspaceId = entry.arguments?.getString("workspaceId") ?: return@composable
                WorkspaceDetailScreen(
                    workspaceId = workspaceId,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenFolder = { id -> navController.navigate("folder/$id") }
                )
            }
            composable("folder/{folderId}") { entry ->
                val folderId = entry.arguments?.getString("folderId") ?: return@composable
                FolderDetailScreen(
                    folderId = folderId,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") }
                )
            }
            composable("collections") {
                SmartCollectionsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") },
                    onOpenKnowledge = { kbId -> navController.navigate("knowledge/$kbId") }
                )
            }
            composable("memory-suggestions") { MemorySuggestionsScreen(onBack = { navController.popBackStack() }) }
            composable("persona/{personaId}/test") { entry ->
                val personaId = entry.arguments?.getString("personaId") ?: return@composable
                PersonaTestBenchScreen(personaId = personaId, onBack = { navController.popBackStack() })
            }
            composable("document/{documentId}") { entry ->
                val documentId = entry.arguments?.getString("documentId") ?: return@composable
                DocumentViewerScreen(documentId = documentId, onBack = { navController.popBackStack() })
            }
            composable("passage/{chunkId}") { entry ->
                val chunkId = entry.arguments?.getString("chunkId") ?: return@composable
                SourcePassageScreen(chunkId = chunkId, onBack = { navController.popBackStack() })
            }
        }
        if (activeJobs.isNotEmpty() || loadingModels > 0 || activeDownloads > 0) {
            val count = activeJobs.size + loadingModels + activeDownloads
            val label = when {
                activeJobs.size == 1 && loadingModels == 0 -> activeJobs.first().label
                loadingModels == 1 && activeJobs.isEmpty() && activeDownloads == 0 -> "Loading model"
                activeDownloads == 1 && activeJobs.isEmpty() && loadingModels == 0 -> "Downloading model"
                else -> "$count activities running"
            }
            Surface(
                onClick = { navController.navigate(if (activeJobs.isNotEmpty()) "jobs" else "models") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.md),
                shape = VervanExtraShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.sm))
                }
            }
        }
        }
        }
    }

    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState()
        fun go(route: String) { showCreateSheet = false; navController.navigate(route) }
        fun newChat(startAction: String? = null) {
            showCreateSheet = false
            scope.launch {
                val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                app.container.db.chatDao().upsert(chat)
                navController.navigate("chat/${chat.id}${startAction?.let { "/$it" }.orEmpty()}")
            }
        }
        CreateSheet(
            sheetState = sheetState,
            actions = listOf(
                // Groups match the simplified IA vocabulary (Space/Project/Sources/Library) end
                // users are shown — not the underlying entity names (Workspace/Folder/
                // KnowledgeBase/Persona/PromptTemplate/Workflow stay exactly as they are in code
                // and in the database; this is a presentation-layer regroup only, see
                // ChatDefaults/AppDatabase for why an actual entity merge isn't in scope here).
                // Previously Start/Build/Organize/Import/Capture, where "Knowledge base" and
                // "Import document" were two near-duplicate entries to the same "knowledge" route
                // (now merged into one), and "New workspace"/"New folder" sat under the generic
                // "Organize" label instead of the "Space" concept users are actually taught.
                CreateAction(Icons.AutoMirrored.Filled.Chat, "New chat", "Open an empty composer with keyboard focus", "Start") { newChat() },
                CreateAction(Icons.Filled.Edit, "New note", "Capture long-form local writing", "Start") {
                    showCreateSheet = false
                    scope.launch {
                        val note = Note()
                        app.container.db.noteDao().upsert(note)
                        navController.navigate("note/${note.id}")
                    }
                },
                CreateAction(Icons.Filled.Workspaces, "New project", "Group instructions, chats, and notes", "Start") { go("projects") },
                CreateAction(Icons.AutoMirrored.Filled.MenuBook, "Add source", "Create a source collection or import a document for grounded answers", "Sources") { go("knowledge") },
                CreateAction(Icons.Outlined.Person, "New persona", "Save reusable behavior and style", "Library") { go("persona-new") },
                CreateAction(Icons.Filled.Extension, "Prompt template", "Create slash-command reusable prompts", "Library") { go("template-new") },
                CreateAction(Icons.Filled.Widgets, "New workflow", "Chain repeatable AI steps", "Library") { go("workflow-new") },
                CreateAction(Icons.Filled.Dashboard, "New space", "Separate personal, work, or research contexts", "Space") { go("workspaces") },
                CreateAction(Icons.Filled.Folder, "New folder", "Manual filing with inherited defaults", "Space") { go("folders") },
                CreateAction(Icons.Filled.AutoAwesome, "Import model", "Prepare local AI generation", "Import") { go("models") },
                CreateAction(Icons.Filled.PhotoCamera, "Scan image", "Start a chat with an image attachment", "Capture") { newChat("image") },
                CreateAction(Icons.Filled.Mic, "Voice note", "Record audio into a new chat", "Capture") { newChat("voice") }
            ),
            onDismiss = { showCreateSheet = false }
        )
    }
}

/**
 * The Aurora navigation dock: a floating pill that replaces the full-width Material
 * NavigationBar + separate Create FAB. Two destinations sit either side of an integrated
 * brand-gradient Create button, content scrolls underneath the dock's inset margins, and
 * selection is an animated tonal pill rather than the stock indicator.
 */
@Composable
private fun VervanNavDock(
    leading: List<Tab>,
    trailing: List<Tab>,
    currentRoute: NavDestination?,
    navController: NavHostController,
    onCreate: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            // The shell already reserves this bottom bar's full height for the NavHost.
            // Keeping another top inset here created a visible empty strip between every
            // primary screen and the dock. Only the safe-area breathing room belongs below it.
            .padding(start = Space.lg, end = Space.lg, bottom = Space.sm)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = VervanExtraShapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = SurfaceRole.Floating.border(),
            shadowElevation = SurfaceRole.Floating.shadowElevation
        ) {
            Row(
                Modifier.padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading.forEach { tab -> DockItem(tab, currentRoute, navController, Modifier.weight(1f)) }
                Box(
                    Modifier
                        .padding(horizontal = Space.sm)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(vervanBrandGradient())
                        .clickable(onClick = onCreate)
                        .semantics { contentDescription = "Create" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                trailing.forEach { tab -> DockItem(tab, currentRoute, navController, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: Tab,
    currentRoute: NavDestination?,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val isSelected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
    val tint by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dockTint"
    )
    val pill by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "dockPill"
    )
    Column(
        modifier
            .clip(VervanExtraShapes.pill)
            .clickable { navController.navigatePrimaryRoot(tab.route) }
            .padding(vertical = Space.xs)
            .semantics { selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .background(pill, VervanExtraShapes.pill)
                .padding(horizontal = Space.lg, vertical = Space.xs),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint)
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun RailTabItem(tab: Tab, currentRoute: NavDestination?, navController: NavHostController) {
    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
    NavigationRailItem(
        selected = selected,
        onClick = {
            navController.navigatePrimaryRoot(tab.route)
        },
        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
        label = { Text(tab.label) }
    )
}

private fun NavHostController.navigatePrimaryRoot(route: String) {
    navigate(route) {
        // Home is the stable app shell root; the graph's configured start can be onboarding,
        // which is removed after setup and therefore cannot anchor restored tab stacks.
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
