package su.kidoz.jetaprog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.JetaProgApplication
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.ui.agent.AgentPerspective
import su.kidoz.jetaprog.app.ui.agent.AgentToolWindow
import su.kidoz.jetaprog.app.ui.components.ActivityBar
import su.kidoz.jetaprog.app.ui.components.ActivityBarItem
import su.kidoz.jetaprog.app.ui.components.Breadcrumbs
import su.kidoz.jetaprog.app.ui.components.BuildStatus
import su.kidoz.jetaprog.app.ui.components.FileMenu
import su.kidoz.jetaprog.app.ui.components.IntelliJEditorTabs
import su.kidoz.jetaprog.app.ui.components.IntelliJStatusBar
import su.kidoz.jetaprog.app.ui.components.NotificationOverlay
import su.kidoz.jetaprog.app.ui.components.VerticalDragHandle
import su.kidoz.jetaprog.app.ui.components.VerticalSplitter
import su.kidoz.jetaprog.app.ui.components.coerceInDp
import su.kidoz.jetaprog.app.ui.components.createBreadcrumbsFromPath
import su.kidoz.jetaprog.app.ui.debug.DebugBottomContent
import su.kidoz.jetaprog.app.ui.debug.DebugIntent
import su.kidoz.jetaprog.app.ui.debug.DebugSidePanel
import su.kidoz.jetaprog.app.ui.dialogs.configuration.RunConfigurationDialog
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectDialog
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectEffect
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsDialog
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsEffect
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.editor.CodeEditor
import su.kidoz.jetaprog.app.ui.editor.EditorDebugInfo
import su.kidoz.jetaprog.app.ui.editor.EmptyEditorPlaceholder
import su.kidoz.jetaprog.app.ui.editor.MarkdownEditor
import su.kidoz.jetaprog.app.ui.navigation.NavigationHost
import su.kidoz.jetaprog.app.ui.navigation.NavigationIntent
import su.kidoz.jetaprog.app.ui.navigation.SearchMode
import su.kidoz.jetaprog.app.ui.panels.BottomPanel
import su.kidoz.jetaprog.app.ui.panels.BottomTab
import su.kidoz.jetaprog.app.ui.panels.BuildOutputPanel
import su.kidoz.jetaprog.app.ui.panels.FindInFilesPanel
import su.kidoz.jetaprog.app.ui.panels.GitPanel
import su.kidoz.jetaprog.app.ui.panels.ProblemsContent
import su.kidoz.jetaprog.app.ui.panels.ProjectPanel
import su.kidoz.jetaprog.app.ui.panels.TerminalPanel
import su.kidoz.jetaprog.app.ui.panels.VcsMainArea
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.ui.toolbar.BranchSelector
import su.kidoz.jetaprog.app.ui.toolbar.RunConfigurationSelector
import su.kidoz.jetaprog.app.ui.welcome.WelcomeEffect
import su.kidoz.jetaprog.app.ui.welcome.WelcomeScreen
import su.kidoz.jetaprog.app.viewmodel.TerminalIntent
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.configuration.ConfigurationEffect
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.NotificationType
import java.io.File
import javax.swing.JFileChooser

/** Activity-bar items that render a panel in the left tool window. */
private val SIDEBAR_PANEL_ITEMS =
    setOf(
        ActivityBarItem.PROJECT,
        ActivityBarItem.SEARCH,
        ActivityBarItem.VCS,
        ActivityBarItem.AGENT,
        ActivityBarItem.DEBUG,
    )

/**
 * Main screen of the JetaProg IDE - IntelliJ IDEA style.
 */
@Composable
public fun MainScreen(app: JetaProgApplication) {
    val session by app.session.collectAsState()
    val settingsState by app.settingsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedActivityItem by remember { mutableStateOf<ActivityBarItem?>(ActivityBarItem.PROJECT) }

    // Handle New Project effects
    LaunchedEffect(app) {
        app.newProjectViewModel.effects.collect { effect ->
            when (effect) {
                is NewProjectEffect.ProjectCreated -> {
                    app.openProject(effect.path)
                    app.notificationCenter.success(
                        title = "Project created",
                        message = effect.path,
                    )
                }

                is NewProjectEffect.ShowError -> {
                    app.notificationCenter.error(
                        title = "Could not create project",
                        message = effect.message,
                    )
                }

                is NewProjectEffect.OpenDirectoryPicker -> {
                    // Handled via onBrowseLocation callback
                }
            }
        }
    }

    // Global Settings effects
    LaunchedEffect(app) {
        app.settingsViewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowError -> {
                    app.notificationCenter.error(title = "Settings", message = effect.message)
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    // Directory picker for new project location
    val browseLocation: () -> Unit = {
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Project Location"
                currentDirectory = File(app.newProjectViewModel.state.value.projectLocation)
            }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            app.newProjectViewModel.dispatch(
                NewProjectIntent.LocationSelected(chooser.selectedFile.absolutePath),
            )
        }
    }

    // Open an existing project from the Welcome Hub via a directory picker.
    val openProjectFromWelcome: () -> Unit = {
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Open Project"
                currentDirectory = File(System.getProperty("user.home"))
            }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            coroutineScope.launch { app.openProject(chooser.selectedFile.absolutePath) }
        }
    }

    // Handle Welcome Hub effects
    LaunchedEffect(app) {
        app.welcomeViewModel.effects.collect { effect ->
            when (effect) {
                is WelcomeEffect.OpenProject -> {
                    app.openProject(effect.path)
                }

                is WelcomeEffect.ShowNewProject -> {
                    app.newProjectViewModel.dispatch(NewProjectIntent.Show)
                }

                is WelcomeEffect.BrowseToOpen -> {
                    openProjectFromWelcome()
                }

                is WelcomeEffect.StartClone -> {
                    app.notificationCenter.info(
                        title = "Clone Repository",
                        message = "Repository cloning is coming soon.",
                    )
                }

                is WelcomeEffect.OpenInNewWindow -> {
                    app.notificationCenter.info(
                        title = "Open in New Window",
                        message = "Multiple windows are coming soon.",
                    )
                }
            }
        }
    }

    val currentSession = session
    if (currentSession == null) {
        // No project open — show the Welcome Hub.
        Box(modifier = Modifier.fillMaxSize().background(IntelliJColors.background)) {
            WelcomeScreen(
                viewModel = app.welcomeViewModel,
                nowEpochMillis = System.currentTimeMillis(),
                modifier = Modifier.fillMaxSize(),
            )
            NotificationOverlay(center = app.notificationCenter)
        }
        return
    }

    MainScreenContent(
        app = app,
        session = currentSession,
        settingsState = settingsState,
        selectedActivityItem = selectedActivityItem,
        onSelectedActivityItemChange = { selectedActivityItem = it },
        browseLocation = browseLocation,
        coroutineScope = coroutineScope,
    )
}

/**
 * Main screen content rendered when a project session is active.
 */
@Composable
@Suppress("LongParameterList")
private fun MainScreenContent(
    app: JetaProgApplication,
    session: ProjectSession,
    settingsState: su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsState,
    selectedActivityItem: ActivityBarItem?,
    onSelectedActivityItemChange: (ActivityBarItem?) -> Unit,
    browseLocation: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val editorState by session.editorViewModel.state.collectAsState()
    val editorSettings by session.editorViewModel.settings.collectAsState()
    val terminalState by session.terminalViewModel.state.collectAsState()
    val gradleState by session.gradleViewModel.state.collectAsState()
    val configurationState by session.configurationViewModel.state.collectAsState()
    val gitState by session.gitViewModel.state.collectAsState()
    val debugState by session.debugViewModel.state.collectAsState()

    val currentProjectPath = session.projectPath
    val notificationCenter = app.notificationCenter

    // Unified bottom tool window (Terminal / Build / Problems). null = hidden.
    var selectedBottomTab by remember { mutableStateOf<BottomTab?>(null) }
    val openTerminalTab: () -> Unit = {
        if (!terminalState.isVisible) {
            session.terminalViewModel.dispatch(TerminalIntent.ToggleVisibility)
        }
        if (terminalState.tabs.isEmpty()) {
            session.terminalViewModel.dispatch(TerminalIntent.CreateTerminal())
        }
        selectedBottomTab = BottomTab.TERMINAL
    }
    val openBuildTab: () -> Unit = {
        if (!gradleState.isVisible) {
            session.gradleViewModel.dispatch(su.kidoz.jetaprog.build.gradle.state.GradleIntent.ToggleVisibility)
        }
        selectedBottomTab = BottomTab.BUILD
    }
    val openDebuggerTab: () -> Unit = {
        selectedBottomTab = BottomTab.DEBUGGER
    }
    val closeBottomPanel: () -> Unit = {
        if (terminalState.isVisible) {
            session.terminalViewModel.dispatch(TerminalIntent.ToggleVisibility)
        }
        if (gradleState.isVisible) {
            session.gradleViewModel.dispatch(su.kidoz.jetaprog.build.gradle.state.GradleIntent.ToggleVisibility)
        }
        selectedBottomTab = null
    }

    // Session-scoped effect collectors: toasts plus navigation effects
    // (go-to-definition jumps, usages popup, symbol search, index refresh).
    LaunchedEffect(session) {
        session.editorViewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.ShowError -> {
                    notificationCenter.error(title = "Editor", message = effect.message)
                }

                is EditorEffect.NavigateTo -> {
                    session.editorViewModel.dispatch(
                        EditorIntent.NavigateTo(path = effect.path, position = effect.position),
                    )
                }

                is EditorEffect.ShowUsages -> {
                    session.navigationViewModel.processIntent(
                        NavigationIntent.ShowUsagesResult(effect.result),
                    )
                }

                is EditorEffect.ShowSymbolSearch -> {
                    session.navigationViewModel.processIntent(
                        NavigationIntent.ShowSearchPopup(SearchMode.SYMBOLS),
                    )
                }

                is EditorEffect.FileSaved -> {
                    session.reindexFile(effect.path)
                }

                is EditorEffect.FileOpened -> {
                    session.reindexFile(effect.path)
                }

                is EditorEffect.ShowNotification -> {
                    when (effect.type) {
                        NotificationType.ERROR -> {
                            notificationCenter.error(title = "Editor", message = effect.message)
                        }

                        NotificationType.WARNING -> {
                            notificationCenter.warning(title = "Editor", message = effect.message)
                        }

                        NotificationType.SUCCESS -> {
                            notificationCenter.success(title = "Editor", message = effect.message)
                        }

                        NotificationType.INFO -> {
                            notificationCenter.info(title = "Editor", message = effect.message)
                        }
                    }
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }
    LaunchedEffect(session) {
        session.configurationViewModel.effects.collect { effect ->
            when (effect) {
                is ConfigurationEffect.ShowError -> {
                    notificationCenter.error(title = "Run configuration", message = effect.message)
                }

                is ConfigurationEffect.ShowSuccess -> {
                    notificationCenter.success(title = "Run configuration", message = effect.message)
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    // Open existing project
    val openProject: () -> Unit = {
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Open Project"
                currentDirectory = File(currentProjectPath).parentFile
            }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            coroutineScope.launch { app.openProject(chooser.selectedFile.absolutePath) }
        }
    }

    // Open single file
    val openFile: () -> Unit = {
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                dialogTitle = "Open File"
                currentDirectory = File(currentProjectPath)
            }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            session.editorViewModel.dispatch(EditorIntent.OpenFile(chooser.selectedFile.absolutePath))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(IntelliJColors.background),
        ) {
            // Top menu bar
            IntelliJMenuBar(
                onNewProject = { app.newProjectViewModel.dispatch(NewProjectIntent.Show) },
                onOpenProject = openProject,
                onOpenFile = openFile,
                onSave = { session.editorViewModel.dispatch(EditorIntent.Save) },
                onCloseProject = {
                    coroutineScope.launch { app.closeProject() }
                },
                onSettings = { app.settingsViewModel.dispatch(SettingsIntent.Show) },
                onToggleBuild = {
                    session.gradleViewModel.dispatch(su.kidoz.jetaprog.build.gradle.state.GradleIntent.ToggleVisibility)
                },
                onBuild = {
                    session.gradleViewModel.dispatch(
                        su.kidoz.jetaprog.build.gradle.state.GradleIntent
                            .RunTask("build"),
                    )
                },
            )

            // Main toolbar (project chip + branch + search-everywhere + run configuration)
            MainToolbar(
                projectName = currentProjectPath.substringAfterLast('/'),
                onNavigateBack = {
                    coroutineScope.launch {
                        session.navigationViewModel.processIntent(NavigationIntent.GoBack)
                    }
                },
                onNavigateForward = {
                    coroutineScope.launch {
                        session.navigationViewModel.processIntent(NavigationIntent.GoForward)
                    }
                },
                onSearchEverywhere = {
                    coroutineScope.launch {
                        session.navigationViewModel.processIntent(
                            NavigationIntent.ShowSearchPopup(SearchMode.ALL),
                        )
                    }
                },
                branchName = gitState.branch,
                branches = gitState.branches,
                ahead = gitState.ahead,
                onUpdate = { session.gitViewModel.pull() },
                onPush = { session.gitViewModel.push() },
                onCheckoutBranch = { name -> session.gitViewModel.checkoutBranch(name) },
                onCreateBranch = { name -> session.gitViewModel.createBranch(name) },
                configurationState = configurationState,
                onSelectConfiguration = { id ->
                    session.configurationViewModel.dispatch(ConfigurationIntent.SelectConfiguration(id))
                },
                onRunConfiguration = {
                    val gradleSettings =
                        configurationState.activeConfiguration?.settings as? ConfigurationSettings.Gradle
                    if (gradleSettings != null) {
                        // Route Gradle runs through the Build panel so output streams live
                        session.gradleViewModel.dispatch(
                            su.kidoz.jetaprog.build.gradle.state.GradleIntent.RunTask(
                                taskPath = gradleSettings.taskPath,
                                args =
                                    gradleSettings.arguments +
                                        gradleSettings.jvmArguments.map { "-D$it" },
                            ),
                        )
                        openBuildTab()
                    } else {
                        session.configurationViewModel.dispatch(ConfigurationIntent.RunActive)
                    }
                },
                onDebugConfiguration = {
                    session.configurationViewModel.dispatch(ConfigurationIntent.DebugActive)
                },
                onStopConfiguration = {
                    session.configurationViewModel.dispatch(ConfigurationIntent.Stop)
                },
                onEditConfigurations = {
                    session.configurationViewModel.dispatch(ConfigurationIntent.OpenDialog)
                },
                onCreateRecommended = {
                    session.configurationViewModel.dispatch(ConfigurationIntent.CreateRecommended)
                },
            )

            // Main content area
            Row(modifier = Modifier.weight(1f)) {
                // Activity bar (far left)
                ActivityBar(
                    selectedItem = selectedActivityItem,
                    onItemClick = { item ->
                        when (item) {
                            ActivityBarItem.PROJECT,
                            ActivityBarItem.SEARCH,
                            ActivityBarItem.VCS,
                            ActivityBarItem.AGENT,
                            -> {
                                onSelectedActivityItemChange(if (selectedActivityItem == item) null else item)
                            }

                            ActivityBarItem.DEBUG -> {
                                val selecting = selectedActivityItem != ActivityBarItem.DEBUG
                                onSelectedActivityItemChange(if (selecting) ActivityBarItem.DEBUG else null)
                                if (selecting) openDebuggerTab()
                            }

                            ActivityBarItem.TERMINAL -> {
                                if (selectedBottomTab == BottomTab.TERMINAL) closeBottomPanel() else openTerminalTab()
                            }

                            ActivityBarItem.BUILD -> {
                                if (selectedBottomTab == BottomTab.BUILD) closeBottomPanel() else openBuildTab()
                            }

                            else -> {
                                onSelectedActivityItemChange(item)
                            }
                        }
                    },
                )

                // Vertical divider between activity bar and project panel
                VerticalSplitter(modifier = Modifier.fillMaxHeight())

                // Left tool window (resizable) — content switches with the activity bar
                val sidebarItem = selectedActivityItem
                val agentState by session.agentSessionViewModel.state.collectAsState()
                val agentInPerspective = sidebarItem == ActivityBarItem.AGENT && !agentState.docked
                if (sidebarItem in SIDEBAR_PANEL_ITEMS && !agentInPerspective) {
                    val minWidth = Dimensions.toolWindowMinWidth.dp
                    val maxWidth = Dimensions.toolWindowMaxWidth.dp
                    var leftPanelWidth by remember { mutableStateOf(Dimensions.projectPanelWidth.dp) }
                    val panelModifier = Modifier.width(leftPanelWidth).fillMaxHeight()
                    when (sidebarItem) {
                        ActivityBarItem.SEARCH -> {
                            FindInFilesPanel(
                                viewModel = session.textSearchViewModel,
                                onOpenMatch = { path, line, column ->
                                    session.editorViewModel.dispatch(
                                        EditorIntent.NavigateTo(path = path, position = TextPosition(line, column)),
                                    )
                                },
                                modifier = panelModifier,
                            )
                        }

                        ActivityBarItem.VCS -> {
                            GitPanel(viewModel = session.gitViewModel, modifier = panelModifier)
                        }

                        ActivityBarItem.AGENT -> {
                            AgentToolWindow(viewModel = session.agentSessionViewModel, modifier = panelModifier)
                        }

                        ActivityBarItem.DEBUG -> {
                            DebugSidePanel(
                                state = debugState,
                                dispatch = { intent -> session.debugViewModel.dispatch(intent) },
                                modifier = panelModifier,
                            )
                        }

                        else -> {
                            ProjectPanel(
                                projectPath = currentProjectPath,
                                onFileOpen = { path -> session.editorViewModel.dispatch(EditorIntent.OpenFile(path)) },
                                modifier = panelModifier,
                            )
                        }
                    }
                    VerticalDragHandle(
                        onDelta = { delta ->
                            leftPanelWidth = (leftPanelWidth + delta).coerceInDp(minWidth, maxWidth)
                        },
                    )
                }

                // Editor area — replaced by a full perspective when VCS or the Agent is active
                Column(modifier = Modifier.weight(1f)) {
                    if (agentInPerspective) {
                        AgentPerspective(
                            viewModel = session.agentSessionViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (selectedActivityItem == ActivityBarItem.VCS) {
                        VcsMainArea(
                            viewModel = session.gitViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Editor tabs
                        if (editorState.tabs.isNotEmpty()) {
                            IntelliJEditorTabs(
                                tabs = editorState.tabs,
                                activeTabIndex = editorState.activeTabIndex,
                                onTabClick = { index ->
                                    session.editorViewModel.dispatch(EditorIntent.SwitchTab(index))
                                },
                                onTabClose = { index ->
                                    session.editorViewModel.dispatch(EditorIntent.CloseTab(index))
                                },
                            )
                        }

                        // Breadcrumbs navigation
                        val activeTab = editorState.activeTab
                        if (activeTab != null) {
                            val projectName = currentProjectPath.substringAfterLast('/')
                            val filePath = activeTab.uri.value.removePrefix("file://")
                            val breadcrumbs =
                                remember(filePath, currentProjectPath) {
                                    createBreadcrumbsFromPath(currentProjectPath, projectName, filePath)
                                }
                            Breadcrumbs(
                                segments = breadcrumbs,
                                onSegmentClick = { _ ->
                                    // Navigate to directory or file
                                },
                            )
                        }

                        // Editor content
                        Box(modifier = Modifier.weight(1f)) {
                            @Suppress("NAME_SHADOWING")
                            val activeTab = editorState.activeTab
                            if (activeTab != null) {
                                val isMarkdown =
                                    editorState.activeDocumentUri?.value?.let { path ->
                                        path.endsWith(".md") || path.endsWith(".markdown")
                                    } ?: false

                                if (isMarkdown) {
                                    MarkdownEditor(
                                        state = editorState,
                                        onContentChange = { content ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.UpdateContent(content),
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CodeEditor(
                                        state = editorState,
                                        onContentChange = { content ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.UpdateContent(content),
                                            )
                                        },
                                        onCompletionRequest = { triggerKind, triggerChar, filterText ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.RequestCompletion(
                                                    triggerKind = triggerKind,
                                                    triggerCharacter = triggerChar,
                                                    filterText = filterText,
                                                ),
                                            )
                                        },
                                        onCompletionSelect = { item ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.ApplyCompletion(item),
                                            )
                                        },
                                        onCompletionMoveUp = {
                                            session.editorViewModel.dispatch(
                                                EditorIntent.CompletionMoveUp,
                                            )
                                        },
                                        onCompletionMoveDown = {
                                            session.editorViewModel.dispatch(
                                                EditorIntent.CompletionMoveDown,
                                            )
                                        },
                                        onCompletionDismiss = {
                                            session.editorViewModel.dispatch(
                                                EditorIntent.DismissCompletion,
                                            )
                                        },
                                        onCompletionFilterChange = { filterText ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.UpdateCompletionFilter(filterText),
                                            )
                                        },
                                        onCursorMove = { position ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.MoveCursor(position),
                                            )
                                        },
                                        onHoverRequest = { position ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.RequestHover(position),
                                            )
                                        },
                                        onHoverDismiss = {
                                            session.editorViewModel.dispatch(EditorIntent.DismissHover)
                                        },
                                        onSignatureHelpRequest = { triggerChar ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.RequestSignatureHelp(
                                                    triggerCharacter = triggerChar,
                                                    isRetrigger = false,
                                                ),
                                            )
                                        },
                                        onSignatureHelpNextSignature = {
                                            session.editorViewModel.dispatch(EditorIntent.NextSignature)
                                        },
                                        onSignatureHelpPreviousSignature = {
                                            session.editorViewModel.dispatch(EditorIntent.PreviousSignature)
                                        },
                                        onSignatureHelpDismiss = {
                                            session.editorViewModel.dispatch(
                                                EditorIntent.DismissSignatureHelp,
                                            )
                                        },
                                        onFormatDocument = {
                                            session.editorViewModel.dispatch(EditorIntent.FormatDocument)
                                        },
                                        onIntent = { intent ->
                                            session.editorViewModel.dispatch(intent)
                                        },
                                        indentUnit =
                                            if (editorSettings.editor.useTabs) {
                                                "\t"
                                            } else {
                                                " ".repeat(editorSettings.editor.tabSize)
                                            },
                                        debug =
                                            editorState.activeDocumentUri
                                                ?.value
                                                ?.removePrefix("file://")
                                                ?.let { path ->
                                                    EditorDebugInfo(
                                                        breakpointLines =
                                                            debugState.breakpoints
                                                                .filter { it.file == path && it.enabled }
                                                                .map { it.line }
                                                                .toSet(),
                                                        executionLine =
                                                            debugState.stoppedAt
                                                                ?.takeIf { it.path == path }
                                                                ?.line,
                                                        variableValues = debugState.variableValues,
                                                        showInlineValues = debugState.showInlineValues,
                                                        onToggleBreakpoint = { line ->
                                                            session.debugViewModel.dispatch(
                                                                DebugIntent.ToggleBreakpoint(path, line),
                                                            )
                                                        },
                                                    )
                                                },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                EmptyEditorPlaceholder(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            // Unified bottom tool window (Terminal / Build / Problems)
            selectedBottomTab?.let { tab ->
                BottomPanel(
                    selectedTab = tab,
                    problemsCount = editorState.diagnostics.size,
                    onSelectTab = { newTab ->
                        when (newTab) {
                            BottomTab.TERMINAL -> openTerminalTab()
                            BottomTab.BUILD -> openBuildTab()
                            BottomTab.PROBLEMS -> selectedBottomTab = BottomTab.PROBLEMS
                            BottomTab.DEBUGGER -> openDebuggerTab()
                        }
                    },
                    onClose = closeBottomPanel,
                ) { currentTab ->
                    when (currentTab) {
                        BottomTab.TERMINAL -> {
                            TerminalPanel(
                                state = terminalState,
                                onIntent = { intent -> session.terminalViewModel.dispatch(intent) },
                                embedded = true,
                            )
                        }

                        BottomTab.BUILD -> {
                            BuildOutputPanel(
                                state = gradleState,
                                onIntent = { intent -> session.gradleViewModel.dispatch(intent) },
                                onOpenDiagnostic = { diagnostic ->
                                    session.editorViewModel.dispatch(
                                        EditorIntent.NavigateTo(
                                            path = diagnostic.filePath,
                                            position =
                                                TextPosition(
                                                    diagnostic.position.line,
                                                    diagnostic.position.column,
                                                ),
                                        ),
                                    )
                                },
                                embedded = true,
                            )
                        }

                        BottomTab.PROBLEMS -> {
                            ProblemsContent(diagnostics = editorState.diagnostics)
                        }

                        BottomTab.DEBUGGER -> {
                            DebugBottomContent(
                                state = debugState,
                                dispatch = { intent -> session.debugViewModel.dispatch(intent) },
                            )
                        }
                    }
                }
            }

            // Status bar
            IntelliJStatusBar(
                gitBranch = gitState.branch,
                isDirty = gitState.staged.isNotEmpty() || gitState.unstaged.isNotEmpty(),
                errorCount = editorState.diagnostics.count { it.severity == DiagnosticSeverity.ERROR },
                warningCount = editorState.diagnostics.count { it.severity == DiagnosticSeverity.WARNING },
                lineInfo =
                    if (editorState.activeTab != null) {
                        "${editorState.currentLine}:${editorState.currentColumn}"
                    } else {
                        null
                    },
                indentInfo =
                    if (editorState.activeTab != null) {
                        if (editorSettings.editor.useTabs) {
                            "Tab"
                        } else {
                            "${editorSettings.editor.tabSize} spaces"
                        }
                    } else {
                        null
                    },
                languageInfo =
                    if (editorState.activeTab != null) {
                        editorState.languageId.displayName
                    } else {
                        null
                    },
                isBuilding = gradleState.isRunning,
                buildStatus =
                    gradleState.lastBuildResult?.let { result ->
                        BuildStatus(
                            success = result.success,
                            message = if (result.success) "Build successful" else "Build failed",
                        )
                    },
            )
        }

        // New Project Dialog
        NewProjectDialog(
            viewModel = app.newProjectViewModel,
            onBrowseLocation = browseLocation,
        )

        // Run Configuration Dialog
        RunConfigurationDialog(
            state = configurationState,
            onSave = { config ->
                session.configurationViewModel.dispatch(ConfigurationIntent.SaveFromDialog(config))
            },
            onDelete = { id ->
                session.configurationViewModel.dispatch(ConfigurationIntent.Delete(id))
            },
            onDuplicate = { id ->
                session.configurationViewModel.dispatch(ConfigurationIntent.Duplicate(id))
            },
            onSelect = { id ->
                session.configurationViewModel.dispatch(ConfigurationIntent.SelectConfiguration(id))
            },
            onCreateNew = { type ->
                session.configurationViewModel.dispatch(ConfigurationIntent.CreateNew(type))
            },
            onCreateRecommended = {
                session.configurationViewModel.dispatch(ConfigurationIntent.CreateRecommended)
            },
            onClose = {
                session.configurationViewModel.dispatch(ConfigurationIntent.CloseDialog)
            },
        )

        // Settings Dialog
        SettingsDialog(
            state = settingsState,
            onIntent = { intent -> app.settingsViewModel.dispatch(intent) },
        )

        // Navigation popups (search, file structure, usages, quick definition)
        val activeTab = editorState.activeTab
        NavigationHost(
            viewModel = session.navigationViewModel,
            currentFilePath = activeTab?.uri?.value?.removePrefix("file://") ?: "",
            currentFileName = activeTab?.name ?: "",
            currentLine = editorState.currentLine,
            currentColumn = editorState.currentColumn,
            onNavigate = { filePath, line, column ->
                session.editorViewModel.dispatch(
                    EditorIntent.NavigateTo(path = filePath, position = TextPosition(line, column)),
                )
            },
            notificationCenter = notificationCenter,
        )

        // Toast overlay — last child so it sits above everything except modals.
        NotificationOverlay(center = notificationCenter)
    }
}

/**
 * IntelliJ-style menu bar.
 *
 * Holds top-level menus only. The run-configuration selector and other
 * toolbar actions live in [MainToolbar] beneath.
 */
@Composable
@Suppress("LongParameterList")
private fun IntelliJMenuBar(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onCloseProject: () -> Unit,
    onSettings: () -> Unit,
    onToggleBuild: () -> Unit,
    onBuild: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.menuBarHeight.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileMenu(
            onNewProject = onNewProject,
            onOpenProject = onOpenProject,
            onOpenFile = onOpenFile,
            onSave = onSave,
            onSaveAs = { /* TODO: Implement Save As */ },
            onCloseProject = onCloseProject,
            onSettings = onSettings,
        )
        IntelliJMenuItem("Edit")
        IntelliJMenuItem("View")
        IntelliJMenuItem("Navigate")
        IntelliJMenuItem("Code")
        IntelliJMenuItem("Refactor")
        IntelliJMenuItem("Build", onClick = onToggleBuild)
        IntelliJMenuItem("Run", onClick = onBuild)
        IntelliJMenuItem("Tools")
        IntelliJMenuItem("VCS")
        IntelliJMenuItem("Window")
        IntelliJMenuItem("Help")
    }
}

/**
 * Main toolbar row beneath the menu bar.
 *
 * Hosts the high-frequency global actions per the UI/UX guide: back/forward
 * navigation, the project chip, VCS controls, the Search Everywhere trigger
 * and the run-configuration selector.
 */
@Composable
@Suppress("LongParameterList")
private fun MainToolbar(
    projectName: String,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onSearchEverywhere: () -> Unit,
    branchName: String?,
    branches: List<su.kidoz.jetaprog.vcs.GitBranch>,
    ahead: Int,
    onUpdate: () -> Unit,
    onPush: () -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    configurationState: su.kidoz.jetaprog.configuration.ConfigurationState,
    onSelectConfiguration: (su.kidoz.jetaprog.configuration.ConfigurationId) -> Unit,
    onRunConfiguration: () -> Unit,
    onDebugConfiguration: () -> Unit,
    onStopConfiguration: () -> Unit,
    onEditConfigurations: () -> Unit,
    onCreateRecommended: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.mainToolbarHeightFilled.dp)
                    .background(IntelliJColors.background)
                    .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Navigate back",
                tint = IntelliJColors.iconDefault,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .clickable(onClick = onNavigateBack)
                        .size(Dimensions.iconLg.dp),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Navigate forward",
                tint = IntelliJColors.iconDefault,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .clickable(onClick = onNavigateForward)
                        .size(Dimensions.iconLg.dp),
            )
            ToolbarDivider()
            ToolbarChip(icon = Icons.Filled.Folder, iconTint = IntelliJColors.iconFolder, label = projectName)
            if (!branchName.isNullOrBlank()) {
                BranchSelector(
                    branchName = branchName,
                    branches = branches,
                    onCheckoutBranch = onCheckoutBranch,
                    onCreateBranch = onCreateBranch,
                )
                ToolbarDivider()
                ToolbarAction(icon = Icons.Filled.South, label = "Update", onClick = onUpdate)
                ToolbarAction(icon = Icons.Filled.North, label = "Push", badge = ahead, onClick = onPush)
            }
            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.weight(1f))
            SearchEverywhereField(onClick = onSearchEverywhere)
            ToolbarDivider()
            RunConfigurationSelector(
                state = configurationState,
                onSelect = onSelectConfiguration,
                onRun = onRunConfiguration,
                onDebug = onDebugConfiguration,
                onStop = onStopConfiguration,
                onEditConfigurations = onEditConfigurations,
                onCreateRecommended = onCreateRecommended,
            )
        }
        // 1 dp bottom border to separate the toolbar from the editor area.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider),
        )
    }
}

/** A 1dp × 16dp vertical separator used between toolbar clusters. */
@Composable
private fun ToolbarDivider() {
    Box(
        modifier =
            Modifier
                .padding(horizontal = Spacing.xs.dp)
                .width(Dimensions.splitterThickness.dp)
                .height(16.dp)
                .background(IntelliJColors.divider),
    )
}

/** A rounded toolbar chip: leading icon + label, optional trailing chevron. */
@Composable
private fun ToolbarChip(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier =
            Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 3.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        Text(text = label, color = IntelliJColors.textPrimary, fontSize = 12.sp, maxLines = 1)
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = IntelliJColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A toolbar text action (icon + label), optionally with a count badge. */
@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    Row(
        modifier =
            Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, color = IntelliJColors.textPrimary, fontSize = 12.sp)
        if (badge > 0) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IntelliJColors.accent)
                        .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.toString(),
                    color = IntelliJColors.background,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** The "Search Everywhere" entry point shown on the right of the toolbar. */
@Composable
private fun SearchEverywhereField(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .height(26.dp)
                .widthIn(min = 190.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clickable(onClick = onClick)
                .background(IntelliJColors.inputBackground)
                .border(
                    width = 1.dp,
                    color = IntelliJColors.divider,
                    shape = RoundedCornerShape(Dimensions.cornerRadius.dp),
                ).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = IntelliJColors.textMuted,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "Search Everywhere",
            color = IntelliJColors.textMuted,
            fontSize = 12.sp,
        )
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(IntelliJColors.surfaceContainer)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(text = "⇧⇧", color = IntelliJColors.iconDefault, fontSize = 10.sp)
        }
    }
}

@Composable
private fun IntelliJMenuItem(
    text: String,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isHovered) IntelliJColors.buttonBackgroundHover else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )
    }
}
