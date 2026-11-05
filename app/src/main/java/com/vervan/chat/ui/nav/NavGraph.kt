package com.vervan.chat.ui.nav

import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Collections
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.StringRes
import com.vervan.chat.R
import com.vervan.chat.IncomingShare
import com.vervan.chat.IncomingShareKind
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.modelload.ModelLoadPhase
import com.vervan.chat.system.toUserMessage
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
import com.vervan.chat.ui.knowledge.KnowledgeTwoPaneScreen
import com.vervan.chat.ui.knowledge.SourcePassageScreen
import com.vervan.chat.ui.library.LibraryScreen
import com.vervan.chat.ui.memory.MemorySuggestionsScreen
import com.vervan.chat.ui.models.ModelManagerScreen
import com.vervan.chat.ui.models.ModelCalculatorScreen
import com.vervan.chat.ui.notes.NoteEditorScreen
import com.vervan.chat.ui.notes.NotesListScreen
import com.vervan.chat.ui.onboarding.OnboardingScreen
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
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
import com.vervan.chat.ui.workflows.WorkflowRunScreen
import com.vervan.chat.ui.workspaces.WorkspaceDetailScreen
import com.vervan.chat.ui.workspaces.WorkspacesScreen
import com.vervan.chat.ui.writing.WritingWorkspaceScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class Tab(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private data class PendingChatAttachment(
    val chatId: String,
    val uri: android.net.Uri,
    val asImage: Boolean,
    val showPreview: Boolean,
)

private val PendingChatAttachmentSaver = Saver<PendingChatAttachment?, List<Any>>(
    save = { it?.let { a -> listOf(a.chatId, a.uri.toString(), a.asImage, a.showPreview) } ?: emptyList() },
    restore = { v ->
        if (v.size < 4) null else PendingChatAttachment(
            chatId = v[0] as String,
            uri = android.net.Uri.parse(v[1] as String),
            asImage = v[2] as Boolean,
            showPreview = v[3] as Boolean,
        )
    }
)

private val tabs = listOf(
    Tab(AppRoutes.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(AppRoutes.CHATS, R.string.nav_chats, Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat)
)
private val libraryTab = Tab(AppRoutes.LIBRARY, R.string.nav_library, Icons.Outlined.Folder, Icons.Filled.Folder)
private val toolsTab = Tab(AppRoutes.TOOLS, R.string.nav_tools, Icons.Outlined.GridView, Icons.Filled.GridView)
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
    onShortcutConsumed: () -> Unit = {},
    intentVersion: Int = 0,
    windowSizeClass: WindowSizeClass? = null,
) {
    val navController = rememberNavController()
    val legacyPrefs = LocalContext.current.getSharedPreferences("vervan", 0)
    val legacyOnboarded = remember { legacyPrefs.getBoolean("onboarded", false) }
    var onboardingReadError by remember { mutableStateOf<String?>(null) }
    var onboardingReadAttempt by remember { mutableIntStateOf(0) }
    val nullableOnboardedFlow = remember(onboardingReadAttempt) {
        app.container.settingsRepository.onboarded
            .map<Boolean, Boolean?> { it }
            .catch { error ->
                onboardingReadError = error.toUserMessage()
                emit(null)
            }
    }
    val storedOnboarded by nullableOnboardedFlow.collectAsStateWithLifecycle(initialValue = null)
    if (storedOnboarded == null) {
        val loadingLabel = stringResource(R.string.startup_loading)
        Box(
            Modifier.fillMaxSize().padding(Space.lg),
            contentAlignment = Alignment.Center
        ) {
            val error = onboardingReadError
            if (error == null) {
                CircularProgressIndicator(
                    Modifier.semantics { stateDescription = loadingLabel }
                )
            } else {
                SystemStatusStrip(
                    title = stringResource(R.string.startup_settings_error),
                    body = error,
                    tone = StatusTone.Error,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = {
                        onboardingReadError = null
                        onboardingReadAttempt++
                    }
                )
            }
        }
        return
    }
    val onboarded = legacyOnboarded || storedOnboarded == true
    val startDestination = if (onboarded) AppRoutes.HOME else AppRoutes.ONBOARDING
    // Saveable so a configuration change or process restore between "navigate" and "the target
    // screen consumed it" can't silently drop the scanned text, the shared file or the jump.
    var pendingStudyMaterialText by rememberSaveable { mutableStateOf<String?>(null) }
    // Targeted by chat ID so a share received while another chat is open cannot attach to the
    // old composer during the navigation frame.
    var pendingChatAttachment by rememberSaveable(stateSaver = PendingChatAttachmentSaver) { mutableStateOf<PendingChatAttachment?>(null) }
    var pendingMessageJump by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (legacyOnboarded) app.container.settingsRepository.setOnboarded(true)
        legacyPrefs.edit().remove("onboarded").apply()
    }

    // Launcher shortcuts — navigate to the relevant destination on launch.
    androidx.compose.runtime.LaunchedEffect(shortcut, intentVersion, onboarded) {
        if (shortcut == null || !onboarded) return@LaunchedEffect
        // "Open in Vervan" from the screen-assist overlay deep-links straight to the saved chat.
        if (shortcut.startsWith("open_chat:")) {
            navController.navigate(AppRoutes.chat(shortcut.removePrefix("open_chat:")))
            onShortcutConsumed()
            return@LaunchedEffect
        }
        when (shortcut) {
            "new_chat", "voice" -> {
                val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                app.container.db.chatDao().upsert(chat)
                navController.navigate(
                    if (shortcut == "voice") AppRoutes.chatStart(chat.id, "voice")
                    else AppRoutes.chat(chat.id)
                )
            }
            "capture" -> {
                // Blank title: the editor shows its own placeholder, and an untouched blank note
                // is cleaned up on leave instead of piling up as "Quick note" entries.
                val note = Note()
                app.container.db.noteDao().upsert(note)
                navController.navigate("note/${note.id}")
            }
            "search" -> navController.navigate(AppRoutes.SEARCH) { launchSingleTop = true }
            "settings" -> navController.navigatePrimaryRoot(AppRoutes.SETTINGS)
        }
        // Consumed only after the work is done: the shortcut is this effect's key, so clearing it
        // earlier would cancel the chat/note creation above mid-flight.
        onShortcutConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val focusManager = LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(currentRoute?.route) {
        focusManager.clearFocus(force = true)
    }
    androidx.compose.runtime.LaunchedEffect(incomingShare, intentVersion, currentRoute?.route) {
        val share = incomingShare ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
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
        navController.navigate(AppRoutes.chat(chat.id)) { launchSingleTop = true }
    }
    val allTabs = tabs + trailingTabs
    val showBottomBar = allTabs.any { currentRoute?.hierarchy?.any { d -> d.route == it.route } == true }
    // Tablet/foldable: a side rail instead of a bottom bar once the window is wider than a
    // phone (adaptive-layout gap) — same destinations, just repositioned.
    val useRail = windowSizeClass?.widthSizeClass != null && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val useTwoPane = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val activeJobs by app.container.db.jobDao().observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val modelLoadState by app.container.modelLoadCoordinator.state.collectAsStateWithLifecycle()
    val loadingModels = modelLoadState.values.count { it.phase == ModelLoadPhase.LOADING }
    val downloadStates by app.container.modelDownloadRepository.uiStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDownloads = downloadStates.count {
        it.status in setOf(
            ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.WAITING_FOR_NETWORK,
            ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE, ModelStatus.DOWNLOADING,
            ModelStatus.PAUSING, ModelStatus.VERIFYING, ModelStatus.IMPORTING,
        )
    }
    val createLabel = stringResource(R.string.action_create)
    val reducedMotion = rememberReducedMotion()

    androidx.compose.runtime.CompositionLocalProvider(
        com.vervan.chat.ui.common.LocalOpenModels provides { navController.navigate("models") { launchSingleTop = true } }
    ) {
    Row(Modifier.fillMaxSize()) {
        if (useRail && showBottomBar) {
            // Same calm treatment as the phone's bottom bar: page-colored, a hairline divider,
            // and a tonal (not bright) new-chat button. No brand tile: every tab's top bar
            // already carries the title, and a phone held sideways has little height to spare.
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                header = {
                    FloatingActionButton(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.padding(top = Space.sm, bottom = Space.sm),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = createLabel)
                    }
                },
            ) {
                tabs.forEach { tab -> RailTabItem(tab, currentRoute, navController) }
                trailingTabs.forEach { tab -> RailTabItem(tab, currentRoute, navController) }
            }
            androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            // This shell has no topBar of its own — every screen inside NavHost brings its
            // own Scaffold + TopAppBar, which already reserves the status-bar inset. Without
            // this override, Scaffold's default `contentWindowInsets` (WindowInsets.systemBars)
            // reserves that same top inset a second time in the padding handed to NavHost,
            // stacking two status-bar-height gaps above every screen's title.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Create used to be a fifth bottom tab that was really a button. It is now the
            // standard Material "new" button on the Chats tab, which keeps the bar to four real
            // destinations. Home has its own composer, and Library and Tools have their own "new"
            // actions, so the button only appears where it is the screen's main action.
            floatingActionButton = {
                val onChats = currentRoute?.hierarchy?.any { it.route == AppRoutes.CHATS } == true
                androidx.compose.animation.AnimatedVisibility(
                    visible = !useRail && onChats,
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                ) {
                    FloatingActionButton(
                        onClick = { showCreateSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = createLabel)
                    }
                }
            },
            bottomBar = {
                // Shown and hidden by animating its height on the same 300ms curve as the screen
                // transition. Dropping it instantly resized the screen mid-transition, so opening
                // a chat (or coming back) made the content jump by the bar's height.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !useRail && showBottomBar,
                    enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None
                    else androidx.compose.animation.expandVertically(
                        tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ),
                    exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None
                    else androidx.compose.animation.shrinkVertically(
                        tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    )
                ) {
                    VervanNavigationBar(
                        leading = tabs,
                        trailing = trailingTabs,
                        currentRoute = currentRoute,
                        navController = navController,
                    )
                }
            }
        ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        val axisOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.roundToPx() }
        fun isTab(destination: NavDestination) =
            allTabs.any { tab -> destination.hierarchy.any { it.route == tab.route } }
        fun isTabSwitch(from: NavDestination, to: NavDestination) = isTab(from) && isTab(to)
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            // Two Material motion patterns, the same ones Google's own apps use:
            //  - Shared axis X for going deeper and coming back: the new screen slides 30dp while
            //    fading in, the old one fades out early, so the pair never double-exposes and the
            //    direction of travel stays readable without a full-width shove.
            //  - Fade through between bottom-bar tabs: tabs are peers, not a stack, so they swap
            //    in place (quick fade out, then fade + slight scale in) rather than sliding.
            // Back runs the same motion in reverse; with predictive back enabled the system drives
            // it from the gesture. Reduced motion falls back to a short plain fade.
            enterTransition = {
                when {
                    reducedMotion -> fadeIn(tween(MotionSpec.REDUCED_MS))
                    isTabSwitch(initialState.destination, targetState.destination) -> MotionSpec.fadeThroughIn()
                    else -> MotionSpec.sharedAxisIn(axisOffsetPx)
                }
            },
            exitTransition = {
                when {
                    reducedMotion -> fadeOut(tween(MotionSpec.REDUCED_MS))
                    isTabSwitch(initialState.destination, targetState.destination) -> MotionSpec.fadeThroughOut()
                    else -> MotionSpec.sharedAxisOut(-axisOffsetPx)
                }
            },
            popEnterTransition = {
                when {
                    reducedMotion -> fadeIn(tween(MotionSpec.REDUCED_MS))
                    isTabSwitch(initialState.destination, targetState.destination) -> MotionSpec.fadeThroughIn()
                    else -> MotionSpec.sharedAxisIn(-axisOffsetPx)
                }
            },
            popExitTransition = {
                when {
                    reducedMotion -> fadeOut(tween(MotionSpec.REDUCED_MS))
                    isTabSwitch(initialState.destination, targetState.destination) -> MotionSpec.fadeThroughOut()
                    else -> MotionSpec.sharedAxisOut(axisOffsetPx)
                }
            },
            // Back *gesture*: the default here shrinks the page to ~90% as you swipe, which read
            // as the screen collapsing. Instead the page tracks the finger and slides away whole,
            // with the previous screen easing in from slightly behind — the swipe-back people
            // know from iOS, WhatsApp and Telegram. Button / 3-button back keeps the shared axis.
            predictivePopEnterTransition = { swipeEdge ->
                if (reducedMotion) fadeIn(tween(MotionSpec.REDUCED_MS))
                else MotionSpec.swipeBackIn(fromRightEdge = swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT)
            },
            predictivePopExitTransition = { swipeEdge ->
                if (reducedMotion) fadeOut(tween(MotionSpec.REDUCED_MS))
                else MotionSpec.swipeBackOut(fromRightEdge = swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT)
            }
        ) {
            composable(AppRoutes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        scope.launch {
                            app.container.settingsRepository.setOnboarded(true)
                            navController.navigate(AppRoutes.HOME) {
                                popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                            }
                        }
                    },
                    onImportModel = { navController.navigate("models") { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.HOME) {
                HomeScreen(
                    onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } },
                    onOpenChats = { navController.navigatePrimaryRoot(AppRoutes.CHATS) },
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },
                    onOpenProject = { projectId -> navController.navigate("project/$projectId") },
                    onOpenNote = { noteId -> navController.navigate("note/$noteId") },
                    onOpenToolRun = { runId -> navController.navigate("tools/runs?highlightId=$runId") },
                    onOpenKnowledge = { navController.navigate("knowledge") { launchSingleTop = true } },
                    onOpenSearch = { navController.navigate(AppRoutes.SEARCH) { launchSingleTop = true } },
                    onOpenPrivacy = { navController.navigate(AppRoutes.PRIVACY_DASHBOARD) { launchSingleTop = true } },
                    onOpenWorkspaces = { navController.navigate("workspaces") { launchSingleTop = true } },
                    onOpenProjects = { navController.navigate("projects") { launchSingleTop = true } },
                    onOpenFolders = { navController.navigate("folders") { launchSingleTop = true } },
                    onOpenDocScanner = { navController.navigate("tools/document-scanner") { launchSingleTop = true } },
                    onOpenVoiceChat = { navController.navigate("tools/voice-chat") { launchSingleTop = true } },
                    onOpenTranslate = { navController.navigate("tools/translate") { launchSingleTop = true } },
                    onOpenWritingAssistant = { navController.navigate("tools/writing-assistant") { launchSingleTop = true } },
                    onOpenAllTools = { navController.navigatePrimaryRoot(AppRoutes.TOOLS) }
                )
            }
            composable("tools/document-scanner") {
                com.vervan.chat.ui.tools.DocumentScannerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onProcessAsStudyMaterial = { text -> pendingStudyMaterialText = text; navController.navigate("tools/study-material") { launchSingleTop = true } }
                )
            }
            composable("tools/ocr-scanner") {
                com.vervan.chat.ui.tools.OcrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") }
                )
            }
            composable("tools/voice-chat") {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                    app.container.db.chatDao().upsert(chat)
                    navController.navigate(AppRoutes.chatStart(chat.id, "handsfree")) {
                        popUpTo("tools/voice-chat") { inclusive = true }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            composable("tools/transcribe") { com.vervan.chat.ui.tools.TranscriptionScreen(onBack = { navController.popBackStack() }, onOpenModels = { navController.navigate("models") { launchSingleTop = true } }) }
            composable("tools/text-to-speech") { com.vervan.chat.ui.tools.TextToSpeechScreen(onBack = { navController.popBackStack() }, onOpenModels = { navController.navigate("models") { launchSingleTop = true } }) }
            composable("tools/omni-studio") {
                com.vervan.chat.ui.omni.OmniStudioScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } }
                )
            }
            composable("tools/translate") { com.vervan.chat.ui.tools.TranslationScreen(onBack = { navController.popBackStack() }) }
            composable("tools/writing-assistant") { com.vervan.chat.ui.tools.WritingAssistantScreen(onBack = { navController.popBackStack() }) }
            composable("tools/smart-notes") { com.vervan.chat.ui.tools.SmartNotesScreen(onBack = { navController.popBackStack() }) }
            composable("tools/clipboard-assistant") { com.vervan.chat.ui.tools.ClipboardAssistantScreen(onBack = { navController.popBackStack() }) }
            composable("tools/explain-level") { com.vervan.chat.ui.tools.ExplainLikeImScreen(onBack = { navController.popBackStack() }) }
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
                            navController.navigate(AppRoutes.chat(chat.id))
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
            composable("tools/image-caption") { com.vervan.chat.ui.tools.ImageCaptionScreen(onBack = { navController.popBackStack() }, onOpenModels = { navController.navigate("models") { launchSingleTop = true } }) }
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
                            navController.navigate(AppRoutes.chat(chat.id)) {
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
            composable("tools/study-material") { entry ->
                val text = pendingStudyMaterialText
                if (text == null) {
                    // Never pop during composition: a recomposition would pop a second time and
                    // take the previous screen with it. Pop once, only while this entry is current.
                    androidx.compose.runtime.LaunchedEffect(entry) {
                        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                    }
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
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
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
                    onOpenSavedOutput = { _ -> navController.navigate(AppRoutes.LIBRARY) { launchSingleTop = true } },
                    onOpenTool = { route -> navController.navigate(route) },
                    onOpenToolRun = { id -> navController.navigate("tools/runs?highlightId=$id") },
                )
            }
            composable("graph") {
                com.vervan.chat.ui.graph.KnowledgeGraphScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEntity = { node ->
                        val route = when (node.type) {
                            com.vervan.chat.ui.graph.GraphNodeType.WORKSPACE -> "workspace/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.PROJECT -> "project/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.FOLDER -> "folder/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.CHAT -> "chat/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.NOTE -> "note/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.KNOWLEDGE_BASE -> "knowledge/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.DOCUMENT -> "document/${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.MEMORY -> "memory?highlightId=${node.id}"
                            com.vervan.chat.ui.graph.GraphNodeType.PERSONA -> "persona/${node.id}/edit"
                        }
                        navController.navigate(route)
                    }
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
            composable(AppRoutes.LIBRARY) {
                LibraryScreen(
                    onOpenPersona = { id -> navController.navigate("persona/$id/edit") },
                    onNewPersona = { navController.navigate("persona-new") { launchSingleTop = true } },
                    onOpenWorkflow = { id -> navController.navigate("workflow/$id") },
                    onNewWorkflow = { navController.navigate("workflow-new") { launchSingleTop = true } },
                    onEditWorkflow = { id -> navController.navigate("workflow/$id/edit") },
                    onOpenTemplate = { id -> navController.navigate("template/$id/edit") },
                    onNewTemplate = { navController.navigate("template-new") { launchSingleTop = true } },
                    onOpenNotes = { navController.navigate("notes") { launchSingleTop = true } }
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
                    onOpenSuggestions = { navController.navigate("memory-suggestions") { launchSingleTop = true } },
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
            composable(AppRoutes.CHATS) {
                if (useTwoPane) {
                    com.vervan.chat.ui.chats.ChatsTwoPaneScreen(
                        onOpenBranchTree = { chatId -> navController.navigate("chat/$chatId/tree") },
                        onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                        onOpenPdfPage = { documentId, page -> navController.navigate("document/$documentId/page/$page") },
                        onOpenChatInfo = { chatId -> navController.navigate("chat/$chatId/info") },
                        onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                        onOpenModels = { navController.navigate("models") { launchSingleTop = true } }
                    )
                } else {
                    ChatListScreen(
                        onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                        onNewChat = {
                            scope.launch {
                                val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                                app.container.db.chatDao().upsert(chat)
                                navController.navigate(AppRoutes.chat(chat.id))
                            }
                        }
                    )
                }
            }
            composable(AppRoutes.CHAT) { backStackEntry2 ->
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
                    onOpenChatInfo = { navController.navigate(AppRoutes.chatInfo(chatId)) },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenBranchTree = { navController.navigate(AppRoutes.chatTree(chatId)) },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenPdfPage = { documentId, page -> navController.navigate("document/$documentId/page/$page") },
                    onOpenFolders = { navController.navigate("folders") { launchSingleTop = true } },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } },
                    onOpenVoiceSettings = { navController.navigate("settings/voice") { launchSingleTop = true } },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") },
                    // Forking replaces this chat in the back stack instead of stacking on top of
                    // it — otherwise forking twice then pressing Back walks back through each
                    // fork instead of leaving the chat entirely (user ask).
                    onForkChat = { forkedChatId ->
                        navController.navigate(AppRoutes.chat(forkedChatId)) {
                            popUpTo(backStackEntry2.destination.id) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppRoutes.CHAT_START) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    initialAction = entry.arguments?.getString("startAction"),
                    onBack = { navController.popBackStack() },
                    onOpenChatInfo = { navController.navigate(AppRoutes.chatInfo(chatId)) },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") },
                    onOpenBranchTree = { navController.navigate(AppRoutes.chatTree(chatId)) },
                    onOpenPassage = { chunkId -> navController.navigate("passage/$chunkId") },
                    onOpenPdfPage = { documentId, page -> navController.navigate("document/$documentId/page/$page") },
                    onOpenFolders = { navController.navigate("folders") { launchSingleTop = true } },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } },
                    onOpenVoiceSettings = { navController.navigate("settings/voice") { launchSingleTop = true } },
                    onOpenWorkspace = { workspaceId -> navController.navigate("workspace/$workspaceId") },
                    onForkChat = { forkedChatId ->
                        navController.navigate(AppRoutes.chat(forkedChatId)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppRoutes.CHAT_TREE) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                BranchTreeScreen(chatId = chatId, onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.CHAT_INFO) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                ChatInfoScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId -> navController.navigate("document/$documentId") }
                )
            }
            composable("knowledge") {
                if (useTwoPane) {
                    KnowledgeTwoPaneScreen(
                        onOpenDocument = { documentId -> navController.navigate("document/$documentId") }
                    )
                } else {
                    KnowledgeScreen(onOpenKb = { kbId -> navController.navigate("knowledge/$kbId") })
                }
            }
            composable("knowledge/{kbId}") { entry ->
                val kbId = entry.arguments?.getString("kbId") ?: return@composable
                KnowledgeBaseDetailScreen(kbId = kbId, onBack = { navController.popBackStack() }, onOpenDocument = { docId -> navController.navigate("document/$docId") })
            }
            composable("models") {
                ModelManagerScreen(
                    onOpenStore = { navController.navigate("models/store") { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                    onOpenCalculator = { navController.navigate("models/calculator") { launchSingleTop = true } }
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
            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } },
                    onOpenMemory = { navController.navigate("memory") { launchSingleTop = true } },
                    onOpenProfile = { navController.navigate("profile") { launchSingleTop = true } },
                    onOpenMemorySuggestions = { navController.navigate("memory-suggestions") { launchSingleTop = true } },
                    onOpenAppearance = { navController.navigate("settings/appearance") { launchSingleTop = true } },
                    onOpenExperience = { navController.navigate("settings/experience") { launchSingleTop = true } },
                    onOpenAccessibility = { navController.navigate("settings/accessibility") { launchSingleTop = true } },
                    onOpenGeneration = { navController.navigate("settings/generation") { launchSingleTop = true } },
                    onOpenVoice = { navController.navigate("settings/voice") { launchSingleTop = true } },
                    onOpenStorage = { navController.navigate("settings/storage") { launchSingleTop = true } },
                    onOpenSecurity = { navController.navigate("settings/security") { launchSingleTop = true } },
                    onOpenTools = { navController.navigate("settings/tools") { launchSingleTop = true } },
                    onOpenHelp = { navController.navigate("settings/help") { launchSingleTop = true } }
                )
            }
            composable("settings/help") {
                com.vervan.chat.ui.settings.HelpSupportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } },
                    onOpenKnowledge = { navController.navigate("knowledge") { launchSingleTop = true } },
                    onOpenGeneration = { navController.navigate("settings/generation") { launchSingleTop = true } },
                    onOpenPermissions = { navController.navigate("settings/permissions") { launchSingleTop = true } },
                    onOpenJobs = { navController.navigate("jobs") { launchSingleTop = true } },
                    onOpenStorage = { navController.navigate("settings/storage") { launchSingleTop = true } },
                    onOpenDiagnostics = { navController.navigate("diagnostics") { launchSingleTop = true } }
                )
            }
            composable("settings/tools") { com.vervan.chat.ui.settings.ToolsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/appearance") { AppearanceSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/experience") {
                ExperienceControlsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGeneration = { navController.navigate("settings/generation") { launchSingleTop = true } },
                    onOpenModels = { navController.navigate("models") { launchSingleTop = true } }
                )
            }
            composable("settings/accessibility") { AccessibilitySettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/generation") { GenerationRetrievalSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/voice") {
                VoiceSettingsScreen(onBack = { navController.popBackStack() }, onOpenModelManager = { navController.navigate("models") { launchSingleTop = true } })
            }
            composable("settings/security") {
                com.vervan.chat.ui.settings.SecuritySettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate("settings/permissions") { launchSingleTop = true } },
                    onOpenApiServer = { navController.navigate("settings/api-server") { launchSingleTop = true } },
                    onOpenPrivacyDashboard = { navController.navigate(AppRoutes.PRIVACY_DASHBOARD) { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.PRIVACY_DASHBOARD) {
                com.vervan.chat.ui.settings.PrivacyDashboardScreen(
                    onBack = { navController.popBackStack() },
                    // Back only when we actually came from Security; from Home (or anywhere else)
                    // "Security" must open Security, not return to the previous screen.
                    onOpenSecurity = {
                        if (navController.previousBackStackEntry?.destination?.route == "settings/security") {
                            navController.popBackStack()
                        } else {
                            navController.navigate("settings/security") { launchSingleTop = true }
                        }
                    },
                    onOpenApiServer = { navController.navigate("settings/api-server") { launchSingleTop = true } },
                    onOpenDiagnostics = { navController.navigate("diagnostics") { launchSingleTop = true } }
                )
            }
            composable("settings/permissions") { com.vervan.chat.ui.settings.PermissionsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/api-server") { com.vervan.chat.ui.settings.ApiServerScreen(onBack = { navController.popBackStack() }) }
            composable("settings/storage") {
                StorageDataSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate("backup") { launchSingleTop = true } },
                    onOpenRecycleBin = { navController.navigate("recycle-bin") { launchSingleTop = true } },
                    onOpenDiagnostics = { navController.navigate("diagnostics") { launchSingleTop = true } },
                    onOpenJobs = { navController.navigate("jobs") { launchSingleTop = true } },
                    onOpenIndexMaintenance = { navController.navigate("index-maintenance") { launchSingleTop = true } },
                    onOpenModelCalculator = { navController.navigate("models/calculator") { launchSingleTop = true } }
                )
            }
            composable("backup") { BackupScreen(onBack = { navController.popBackStack() }) }
            composable("recycle-bin") { RecycleBinScreen(onBack = { navController.popBackStack() }) }
            composable("diagnostics") {
                DiagnosticsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate("settings/permissions") { launchSingleTop = true } }
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
                DocumentViewerScreen(
                    documentId = documentId,
                    onBack = { navController.popBackStack() },
                    onOpenPdfPage = { docId, page -> navController.navigate("document/$docId/page/$page") }
                )
            }
            composable("passage/{chunkId}") { entry ->
                val chunkId = entry.arguments?.getString("chunkId") ?: return@composable
                SourcePassageScreen(
                    chunkId = chunkId,
                    onBack = { navController.popBackStack() },
                    onOpenPdfPage = { documentId, page -> navController.navigate("document/$documentId/page/$page") }
                )
            }
            composable("document/{documentId}/page/{page}") { entry ->
                val documentId = entry.arguments?.getString("documentId") ?: return@composable
                val page = entry.arguments?.getString("page")?.toIntOrNull() ?: 1
                com.vervan.chat.ui.knowledge.PdfPageViewerScreen(documentId = documentId, initialPage = page, onBack = { navController.popBackStack() })
            }
        }
        // The chat screen already says "loading…" under its title, so a second indicator there is
        // noise; everywhere else the pill sits bottom-centre, clear of the bottom-end add buttons
        // and FABs it used to cover.
        // Chat screens are excluded: bottom-centre there is the composer and Send button, and the
        // chat already shows model state under its title. Elsewhere the pill can be dismissed; it
        // comes back only when the set of running activities changes.
        val onChatRoute = currentRoute?.route?.startsWith("chat/") == true
        val activityCount = activeJobs.size + loadingModels + activeDownloads
        val activitySignature = "${activeJobs.map { it.id }}|$loadingModels|$activeDownloads"
        var dismissedActivitySignature by rememberSaveable { mutableStateOf<String?>(null) }
        if (activityCount > 0 && !onChatRoute && dismissedActivitySignature != activitySignature) {
            val label = when {
                activeJobs.size == 1 && loadingModels == 0 && activeDownloads == 0 -> activeJobs.first().label
                loadingModels == 1 && activeJobs.isEmpty() && activeDownloads == 0 -> stringResource(R.string.activity_loading_model)
                activeDownloads == 1 && activeJobs.isEmpty() && loadingModels == 0 -> stringResource(R.string.activity_downloading_model)
                else -> androidx.compose.ui.res.pluralStringResource(R.plurals.activity_running_count, activityCount, activityCount)
            }
            Surface(
                onClick = { navController.navigate(if (activeJobs.isNotEmpty()) "jobs" else "models") { launchSingleTop = true } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(Space.md),
                shape = VervanExtraShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(start = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.sm))
                    androidx.compose.material3.IconButton(onClick = { dismissedActivitySignature = activitySignature }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.activity_hide),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        }
        }
    }
    }

    if (showCreateSheet) {
        val sheetState = com.vervan.chat.ui.common.rememberAdaptiveSheetState()
        fun go(route: String) { showCreateSheet = false; navController.navigate(route) }
        fun newChat(startAction: String? = null) {
            showCreateSheet = false
            scope.launch {
                val chat = Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
                app.container.db.chatDao().upsert(chat)
                navController.navigate(
                    startAction?.let { AppRoutes.chatStart(chat.id, it) } ?: AppRoutes.chat(chat.id)
                )
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
                // Start/Sources/Library create something right here; Browse only opens the list where
                // that kind of item is managed, and is labelled that way.
                CreateAction(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.create_new_chat), stringResource(R.string.create_new_chat_desc), stringResource(R.string.create_group_start), quick = true) { newChat() },
                CreateAction(Icons.Filled.Edit, stringResource(R.string.create_new_note), stringResource(R.string.create_new_note_desc), stringResource(R.string.create_group_start), quick = true) {
                    showCreateSheet = false
                    scope.launch {
                        val note = Note()
                        app.container.db.noteDao().upsert(note)
                        navController.navigate("note/${note.id}")
                    }
                },
                CreateAction(Icons.Filled.PhotoCamera, stringResource(R.string.create_photo_chat), stringResource(R.string.create_photo_chat_desc), stringResource(R.string.create_group_start), quick = true) { newChat("image") },
                CreateAction(Icons.Filled.Mic, stringResource(R.string.create_voice_message), stringResource(R.string.create_voice_message_desc), stringResource(R.string.create_group_start), quick = true) { newChat("voice") },
                CreateAction(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.create_add_source), stringResource(R.string.create_add_source_desc), stringResource(R.string.create_group_sources)) { go("knowledge") },
                CreateAction(Icons.Outlined.Person, stringResource(R.string.create_new_persona), stringResource(R.string.create_new_persona_desc), stringResource(R.string.create_group_library)) { go("persona-new") },
                CreateAction(Icons.Filled.Extension, stringResource(R.string.create_prompt_template), stringResource(R.string.create_prompt_template_desc), stringResource(R.string.create_group_library)) { go("template-new") },
                CreateAction(Icons.Filled.Widgets, stringResource(R.string.create_new_workflow), stringResource(R.string.create_new_workflow_desc), stringResource(R.string.create_group_library)) { go("workflow-new") },
                CreateAction(Icons.Filled.AutoAwesome, stringResource(R.string.create_import_model), stringResource(R.string.create_import_model_desc), stringResource(R.string.create_group_library)) { go("models") },
                CreateAction(Icons.Filled.Workspaces, stringResource(R.string.create_browse_projects), stringResource(R.string.create_browse_projects_desc), stringResource(R.string.create_group_browse)) { go("projects") },
                CreateAction(Icons.Filled.Dashboard, stringResource(R.string.create_browse_spaces), stringResource(R.string.create_browse_spaces_desc), stringResource(R.string.create_group_browse)) { go("workspaces") },
                CreateAction(Icons.Filled.Folder, stringResource(R.string.create_browse_folders), stringResource(R.string.create_browse_folders_desc), stringResource(R.string.create_group_browse)) { go("folders") },
                CreateAction(Icons.Filled.Collections, stringResource(R.string.create_browse_collections), stringResource(R.string.create_browse_collections_desc), stringResource(R.string.create_group_browse)) { go("collections") }
            ),
            onDismiss = { showCreateSheet = false }
        )
    }
}

@Composable
private fun VervanNavigationBar(
    leading: List<Tab>,
    trailing: List<Tab>,
    currentRoute: NavDestination?,
    navController: NavHostController,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The bar sits on the page colour with a hairline above it, so it reads as part of the
    // screen rather than a second, heavier panel.
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                leading.forEach { tab -> NavigationBarTab(tab, currentRoute, navController, itemColors) }
                trailing.forEach { tab -> NavigationBarTab(tab, currentRoute, navController, itemColors) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationBarTab(
    tab: Tab,
    currentRoute: NavDestination?,
    navController: NavHostController,
    colors: androidx.compose.material3.NavigationBarItemColors,
) {
    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
    NavigationBarItem(
        selected = selected,
        onClick = { navController.navigatePrimaryRoot(tab.route) },
        icon = {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null
            )
        },
        label = { Text(stringResource(tab.labelRes)) },
        alwaysShowLabel = true,
        colors = colors,
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
        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
        label = { Text(stringResource(tab.labelRes)) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
