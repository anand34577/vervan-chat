package com.vervan.chat.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.ui.chat.BranchTreeScreen
import com.vervan.chat.ui.chat.ChatScreen
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
import com.vervan.chat.ui.notes.NoteEditorScreen
import com.vervan.chat.ui.notes.NotesListScreen
import com.vervan.chat.ui.onboarding.OnboardingScreen
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("chats", "Chats", Icons.AutoMirrored.Filled.Chat)
)
private val libraryTab = Tab("library", "Library", Icons.Filled.Folder)
private val trailingTabs = listOf(
    Tab("knowledge", "Knowledge", Icons.AutoMirrored.Filled.MenuBook),
    libraryTab
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun VervanNavGraph(app: VervanApp, sharedText: String? = null, shortcut: String? = null, intentVersion: Int = 0, windowSizeClass: WindowSizeClass? = null) {
    val navController = rememberNavController()
    val prefs = LocalContext.current.getSharedPreferences("vervan", 0)
    val startDestination = if (prefs.getBoolean("onboarded", false)) "home" else "onboarding"
    var pendingShare by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Launcher shortcuts (spec §37.3) — navigate to the relevant destination on launch.
    androidx.compose.runtime.LaunchedEffect(sharedText, intentVersion) {
        if (sharedText != null) pendingShare = sharedText
    }
    androidx.compose.runtime.LaunchedEffect(shortcut, intentVersion) {
        if (shortcut == null || !prefs.getBoolean("onboarded", false)) return@LaunchedEffect
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
    val allTabs = tabs + trailingTabs
    val showBottomBar = allTabs.any { currentRoute?.hierarchy?.any { d -> d.route == it.route } == true }
    // Tablet/foldable: a side rail instead of a bottom bar once the window is wider than a
    // phone (spec §4's adaptive-layout gap) — same destinations, just repositioned.
    val useRail = windowSizeClass?.widthSizeClass != null && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val useTwoPane = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    Row(Modifier.fillMaxSize()) {
        if (useRail && showBottomBar) {
            NavigationRail {
                tabs.forEach { tab -> RailTabItem(tab, currentRoute, navController) }
                NavigationRailItem(
                    selected = false,
                    onClick = { showCreateSheet = true },
                    icon = {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    label = { Text("Create") }
                )
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
                    NavigationBar {
                        tabs.forEach { tab -> BottomTabItem(tab, currentRoute, navController) }
                        NavigationBarItem(
                            selected = false,
                            onClick = { showCreateSheet = true },
                            icon = {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            },
                            label = { Text("Create") },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                        )
                        trailingTabs.forEach { tab -> BottomTabItem(tab, currentRoute, navController) }
                    }
                }
            }
        ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
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
                    onOpenMemory = { navController.navigate("memory") },
                    onOpenWorkflows = { navController.navigate("workflows") },
                    onOpenChats = { navController.navigate("chats") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenProject = { projectId -> navController.navigate("project/$projectId") },
                    onOpenKnowledge = { navController.navigate("knowledge") },
                    onOpenSearch = { navController.navigate("search") },
                    onOpenWriting = { navController.navigate("writing") },
                    onOpenDev = { navController.navigate("dev") },
                    onOpenStudy = { navController.navigate("study") },
                    onOpenFolders = { navController.navigate("folders") },
                    onOpenCollections = { navController.navigate("collections") },
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenWorkspaces = { navController.navigate("workspaces") }
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") },
                    onOpenKnowledge = { kbId -> navController.navigate("knowledge/$kbId") },
                    onOpenPersona = { id -> navController.navigate("persona/$id/edit") },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenMemory = { memoryId -> navController.navigate("memory?highlightId=$memoryId") }
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
                        onOpenModels = { navController.navigate("models") }
                    )
                } else {
                    ChatListScreen(onOpenChat = { chatId -> navController.navigate("chat/$chatId") })
                }
            }
            composable("chat/{chatId}") { backStackEntry2 ->
                val chatId = backStackEntry2.arguments?.getString("chatId") ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() },
                    onOpenBranchTree = { navController.navigate("chat/$chatId/tree") },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenFolders = { navController.navigate("folders") },
                    onOpenModels = { navController.navigate("models") },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") }
                )
            }
            composable("chat/{chatId}/{startAction}") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    initialAction = entry.arguments?.getString("startAction"),
                    onBack = { navController.popBackStack() },
                    onOpenBranchTree = { navController.navigate("chat/$chatId/tree") },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenFolders = { navController.navigate("folders") },
                    onOpenModels = { navController.navigate("models") },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") }
                )
            }
            composable("chat/{chatId}/tree") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                BranchTreeScreen(chatId = chatId, onBack = { navController.popBackStack() })
            }
            composable("knowledge") {
                KnowledgeScreen(onOpenKb = { kbId -> navController.navigate("knowledge/$kbId") })
            }
            composable("knowledge/{kbId}") { entry ->
                val kbId = entry.arguments?.getString("kbId") ?: return@composable
                KnowledgeBaseDetailScreen(kbId = kbId, onBack = { navController.popBackStack() }, onOpenDocument = { docId -> navController.navigate("document/$docId") })
            }
            composable("models") { ModelManagerScreen(onBack = { navController.popBackStack() }) }
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
            composable("settings/experience") { ExperienceControlsSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/accessibility") { AccessibilitySettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/generation") { GenerationRetrievalSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/voice") { VoiceSettingsScreen(onBack = { navController.popBackStack() }) }
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
                    onOpenIndexMaintenance = { navController.navigate("index-maintenance") }
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
        }
    }

    pendingShare?.let { text ->
        ShareTargetDialog(
            app = app,
            text = text,
            onDismiss = { pendingShare = null },
            onOpenChat = { chatId -> pendingShare = null; navController.navigate("chat/$chatId") },
            onOpenNote = { noteId -> pendingShare = null; navController.navigate("note/$noteId") }
        )
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
                // §7.10.2 taxonomy — Start/Build/Organize/Import/Capture, matching the spec's
                // group order exactly instead of the previous ad hoc Create/Knowledge/System/Library names.
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
                CreateAction(Icons.AutoMirrored.Filled.MenuBook, "Knowledge base", "Create a reusable local source collection", "Build") { go("knowledge") },
                CreateAction(Icons.Outlined.Person, "New persona", "Save reusable behavior and style", "Build") { go("persona-new") },
                CreateAction(Icons.Filled.Extension, "Prompt template", "Create slash-command reusable prompts", "Build") { go("template-new") },
                CreateAction(Icons.Filled.Widgets, "New workflow", "Chain repeatable AI steps", "Build") { go("workflow-new") },
                CreateAction(Icons.Filled.Dashboard, "New workspace", "Separate personal, work, or research contexts", "Organize") { go("workspaces") },
                CreateAction(Icons.Filled.Folder, "New folder", "Manual filing with inherited defaults", "Organize") { go("folders") },
                CreateAction(Icons.Filled.FileDownload, "Import document", "Add files for grounded answers", "Import") { go("knowledge") },
                CreateAction(Icons.Filled.AutoAwesome, "Import model", "Prepare local AI generation", "Import") { go("models") },
                CreateAction(Icons.Filled.PhotoCamera, "Scan image", "Start a chat with an image attachment", "Capture") { newChat("image") },
                CreateAction(Icons.Filled.Mic, "Voice note", "Record audio into a new chat", "Capture") { newChat("voice") }
            ),
            onDismiss = { showCreateSheet = false }
        )
    }
}

@Composable
private fun RowScope.BottomTabItem(tab: Tab, currentRoute: NavDestination?, navController: NavHostController) {
    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigatePrimaryRoot(tab.route)
        },
        icon = { Icon(tab.icon, contentDescription = tab.label) },
        label = { Text(tab.label) }
    )
}

@Composable
private fun RailTabItem(tab: Tab, currentRoute: NavDestination?, navController: NavHostController) {
    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
    NavigationRailItem(
        selected = selected,
        onClick = {
            navController.navigatePrimaryRoot(tab.route)
        },
        icon = { Icon(tab.icon, contentDescription = tab.label) },
        label = { Text(tab.label) }
    )
}

private fun NavHostController.navigatePrimaryRoot(route: String) {
    val poppedToExistingRoot = popBackStack(route, inclusive = false)
    if (!poppedToExistingRoot) {
        navigate(route) {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
            restoreState = false
        }
    }
}

@Composable
private fun ShareTargetDialog(
    app: VervanApp,
    text: String,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shared text") },
        text = { Text(text.take(200), maxLines = 4) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    val chat = com.vervan.chat.data.db.entities.Chat(
                        draft = text,
                        workspaceId = app.container.settingsRepository.activeWorkspaceId.first()
                    )
                    app.container.db.chatDao().upsert(chat)
                    onOpenChat(chat.id)
                }
            }) { Text("Ask AI") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    val note = com.vervan.chat.data.db.entities.Note(title = text.take(60), content = text)
                    app.container.db.noteDao().upsert(note)
                    onOpenNote(note.id)
                }
            }) { Text("Save as note") }
        }
    )
}
