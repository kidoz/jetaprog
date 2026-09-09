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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.JetaProgApplication
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.database.DatabaseEffect
import su.kidoz.jetaprog.app.gradle.GradleSyncState
import su.kidoz.jetaprog.app.keymap.NavigationActions
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.ui.agent.AgentPerspective
import su.kidoz.jetaprog.app.ui.agent.AgentToolWindow
import su.kidoz.jetaprog.app.ui.command.CommandPaletteHost
import su.kidoz.jetaprog.app.ui.components.ActivityBar
import su.kidoz.jetaprog.app.ui.components.ActivityBarItem
import su.kidoz.jetaprog.app.ui.components.BreadcrumbSegment
import su.kidoz.jetaprog.app.ui.components.BreadcrumbType
import su.kidoz.jetaprog.app.ui.components.Breadcrumbs
import su.kidoz.jetaprog.app.ui.components.BuildStatus
import su.kidoz.jetaprog.app.ui.components.FileMenu
import su.kidoz.jetaprog.app.ui.components.IntelliJEditorTabs
import su.kidoz.jetaprog.app.ui.components.IntelliJStatusBar
import su.kidoz.jetaprog.app.ui.components.NotificationOverlay
import su.kidoz.jetaprog.app.ui.components.PopupChromeMenu
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.VerticalDragHandle
import su.kidoz.jetaprog.app.ui.components.VerticalSplitter
import su.kidoz.jetaprog.app.ui.components.coerceInDp
import su.kidoz.jetaprog.app.ui.components.createBreadcrumbsFromPath
import su.kidoz.jetaprog.app.ui.database.DatabaseQueryResultsPanel
import su.kidoz.jetaprog.app.ui.database.DatabaseToolWindow
import su.kidoz.jetaprog.app.ui.debug.BreakpointsDialog
import su.kidoz.jetaprog.app.ui.debug.DebugBottomContent
import su.kidoz.jetaprog.app.ui.debug.DebugIntent
import su.kidoz.jetaprog.app.ui.debug.DebugSidePanel
import su.kidoz.jetaprog.app.ui.dialogs.ConfirmationDialog
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryDialog
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryEffect
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryIntent
import su.kidoz.jetaprog.app.ui.dialogs.configuration.RunConfigurationDialog
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerEffect
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerHost
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerIntent
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerPurpose
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerRequest
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectDialog
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectEffect
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectIntent
import su.kidoz.jetaprog.app.ui.dialogs.rename.RenameDialog
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
import su.kidoz.jetaprog.app.ui.panels.BreadcrumbDirectoryPopup
import su.kidoz.jetaprog.app.ui.panels.BuildOutputPanel
import su.kidoz.jetaprog.app.ui.panels.FindInFilesPanel
import su.kidoz.jetaprog.app.ui.panels.GitPanel
import su.kidoz.jetaprog.app.ui.panels.ProblemsContent
import su.kidoz.jetaprog.app.ui.panels.ProjectPanel
import su.kidoz.jetaprog.app.ui.panels.RunOutputPanel
import su.kidoz.jetaprog.app.ui.panels.TerminalPanel
import su.kidoz.jetaprog.app.ui.panels.TestResultsPanel
import su.kidoz.jetaprog.app.ui.panels.VcsMainArea
import su.kidoz.jetaprog.app.ui.plugin.PluginDialogHost
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.ui.toolbar.BranchSelector
import su.kidoz.jetaprog.app.ui.toolbar.RunConfigurationSelector
import su.kidoz.jetaprog.app.ui.welcome.WelcomeEffect
import su.kidoz.jetaprog.app.ui.welcome.WelcomeScreen
import su.kidoz.jetaprog.app.viewmodel.GitState
import su.kidoz.jetaprog.app.viewmodel.TerminalIntent
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.configuration.ConfigurationEffect
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.state.NotificationType
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

/** Activity-bar items that render a panel in the left tool window. */
private val SIDEBAR_PANEL_ITEMS =
    setOf(
        ActivityBarItem.PROJECT,
        ActivityBarItem.SEARCH,
        ActivityBarItem.VCS,
        ActivityBarItem.AGENT,
        ActivityBarItem.DEBUG,
        ActivityBarItem.DATABASE,
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

    // Every browse action goes through the IDE's own file picker; the picked
    // path comes back as a FilePickerEffect, routed by purpose below.
    val showFilePicker: (FilePickerRequest) -> Unit = { request ->
        app.filePickerViewModel.dispatch(FilePickerIntent.Show(request))
    }

    // Directory picker for new project location
    val browseLocation: () -> Unit = {
        showFilePicker(FilePickerRequest.newProjectLocation(app.newProjectViewModel.state.value.projectLocation))
    }

    // Open an existing project from the Welcome Hub via a directory picker.
    val openProjectFromWelcome: () -> Unit = {
        showFilePicker(FilePickerRequest.openProject(System.getProperty("user.home").orEmpty()))
    }

    // Route the file picker's result to whoever asked for it. This is the only
    // collector: effects are delivered once, so a second one would steal them.
    LaunchedEffect(app) {
        app.filePickerViewModel.effects.collect { effect ->
            when (effect) {
                is FilePickerEffect.Picked -> handlePickedPath(app, effect)
            }
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
                    app.cloneRepositoryViewModel.dispatch(CloneRepositoryIntent.Show)
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
        val welcomeSettingsState by app.settingsViewModel.state.collectAsState()
        Box(modifier = Modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
            WelcomeScreen(
                viewModel = app.welcomeViewModel,
                settingsViewModel = app.settingsViewModel,
                nowEpochMillis = System.currentTimeMillis(),
                onOpenSettings = { app.settingsViewModel.dispatch(SettingsIntent.Show) },
                modifier = Modifier.fillMaxSize(),
            )
            NotificationOverlay(center = app.notificationCenter)
            SettingsDialog(
                state = welcomeSettingsState,
                onIntent = { intent -> app.settingsViewModel.dispatch(intent) },
                effectiveShortcuts = app.keymapManager.getAllShortcuts(),
            )
            CloneRepositoryDialog(
                viewModel = app.cloneRepositoryViewModel,
                onBrowseDestination = {
                    showFilePicker(
                        FilePickerRequest.cloneDestination(
                            app.cloneRepositoryViewModel.state.value.destinationDirectory,
                        ),
                    )
                },
            )
            // New Project is reachable from the Welcome Hub, so its dialog lives here too.
            NewProjectDialog(
                viewModel = app.newProjectViewModel,
                onBrowseLocation = browseLocation,
            )
            // Above the dialogs that open it.
            FilePickerHost(viewModel = app.filePickerViewModel)
            LaunchedEffect(app.cloneRepositoryViewModel) {
                app.cloneRepositoryViewModel.effects.collect { effect ->
                    when (effect) {
                        is CloneRepositoryEffect.Cloned -> {
                            app.notificationCenter.info(
                                title = "Repository cloned",
                                message = "Opened ${effect.projectPath.substringAfterLast('/')}.",
                            )
                            app.openProject(effect.projectPath)
                        }
                    }
                }
            }
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

/** Hands a path chosen in the file picker to the feature that asked for it. */
private suspend fun handlePickedPath(
    app: JetaProgApplication,
    picked: FilePickerEffect.Picked,
) {
    when (picked.purpose) {
        FilePickerPurpose.OPEN_PROJECT -> {
            app.openProject(picked.path)
        }

        FilePickerPurpose.NEW_PROJECT_LOCATION -> {
            app.newProjectViewModel.dispatch(NewProjectIntent.LocationSelected(picked.path))
        }

        FilePickerPurpose.CLONE_DESTINATION -> {
            app.cloneRepositoryViewModel.dispatch(CloneRepositoryIntent.SetDestinationDirectory(picked.path))
        }

        FilePickerPurpose.OPEN_FILE -> {
            app.session.value
                ?.editorViewModel
                ?.dispatch(EditorIntent.OpenFile(picked.path))
        }

        FilePickerPurpose.SAVE_FILE_AS -> {
            app.session.value
                ?.editorViewModel
                ?.dispatch(EditorIntent.SaveAs(picked.path))
        }
    }
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
    // Layout persisted in the workspace state: seed the initial UI values once
    // per session and write changes back so they survive a restart.
    val restoredLayout = remember(session) { session.panelLayout.value }
    LaunchedEffect(session) {
        restoredLayout.sidebarItem?.let { name ->
            ActivityBarItem.entries.firstOrNull { it.name == name }?.let(onSelectedActivityItemChange)
        }
    }

    val editorState by session.editorViewModel.state.collectAsState()
    val editorSettings by session.editorViewModel.settings.collectAsState()
    // Ctrl/Cmd+Click in the editor: same path as the Go to Declaration shortcut.
    val goToDeclaration: (String, TextPosition) -> Unit = { uri, position ->
        coroutineScope.launch {
            session.navigationViewModel.processIntent(
                NavigationIntent.GoToDeclaration(uri.removePrefix("file://"), position.line, position.column),
            )
        }
    }
    val terminalState by session.terminalViewModel.state.collectAsState()
    val gradleState by session.gradleViewModel.state.collectAsState()
    val gradleSyncState by session.gradleImportCoordinator.state.collectAsState()
    val configurationState by session.configurationViewModel.state.collectAsState()
    val gitState by session.gitViewModel.state.collectAsState()
    val debugState by session.debugViewModel.state.collectAsState()
    var showBreakpointsDialog by remember { mutableStateOf(false) }
    val databaseState by session.databaseViewModel.state.collectAsState()

    val currentProjectPath = session.projectPath
    val notificationCenter = app.notificationCenter
    var editorConfirmation by remember(session) { mutableStateOf<EditorEffect.ShowConfirmation?>(null) }
    var showCloseProjectConfirmation by remember(session) { mutableStateOf(false) }

    LaunchedEffect(gradleSyncState) {
        val failure = gradleSyncState as? GradleSyncState.Failed ?: return@LaunchedEffect
        notificationCenter.error(title = "Gradle sync failed", message = failure.message)
    }

    LaunchedEffect(selectedActivityItem) {
        session.panelLayout.update { it.copy(sidebarItem = selectedActivityItem?.name) }
    }

    // Unified bottom tool window (Terminal / Build / Problems). null = hidden.
    var selectedBottomTab by remember(session) {
        mutableStateOf<BottomTab?>(
            restoredLayout.activeBottomPanel?.let { name ->
                BottomTab.entries.firstOrNull { it.name == name }
            },
        )
    }
    LaunchedEffect(selectedBottomTab) {
        session.panelLayout.update { it.copy(activeBottomPanel = selectedBottomTab?.name) }
    }
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
    val openTestsTab: () -> Unit = {
        selectedBottomTab = BottomTab.TESTS
    }
    val openDebuggerTab: () -> Unit = {
        selectedBottomTab = BottomTab.DEBUGGER
    }
    val openDatabaseTab: () -> Unit = {
        selectedBottomTab = BottomTab.DATABASE
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

    LaunchedEffect(gradleState.testRun) {
        if (gradleState.testRun != null) openTestsTab()
    }

    LaunchedEffect(session) {
        session.databaseViewModel.effects.collect { effect ->
            when (effect) {
                DatabaseEffect.OpenQueryResults -> {
                    openDatabaseTab()
                }

                is DatabaseEffect.ShowMessage -> {
                    notificationCenter.info(title = "Database", message = effect.message)
                }
            }
        }
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

                is EditorEffect.ShowConfirmation -> {
                    editorConfirmation = effect
                }

                is EditorEffect.CopyToClipboard -> {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(effect.text),
                        null,
                    )
                }

                is EditorEffect.RequestPaste -> {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val text =
                        runCatching {
                            clipboard.getData(DataFlavor.stringFlavor) as? String
                        }.getOrNull()
                    if (text != null) {
                        session.editorViewModel.dispatch(EditorIntent.Paste(text))
                    }
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }
    LaunchedEffect(session) {
        session.renameMessages.collect { message ->
            notificationCenter.info(title = "Rename", message = message)
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

                is ConfigurationEffect.ConfigurationStarted -> {
                    val settings = effect.configuration.settings
                    if (
                        settings is ConfigurationSettings.Node ||
                        settings is ConfigurationSettings.Java ||
                        settings is ConfigurationSettings.DotNetBuild ||
                        settings is ConfigurationSettings.DotNetRun ||
                        settings is ConfigurationSettings.DotNetTest
                    ) {
                        selectedBottomTab = BottomTab.BUILD
                    }
                }

                is ConfigurationEffect.DebugConfigurationStarted -> {
                    selectedBottomTab = BottomTab.DEBUGGER
                }

                is ConfigurationEffect.GoTestsFinished -> {
                    val message =
                        buildString {
                            append("${effect.passed} passed, ${effect.failed} failed, ${effect.skipped} skipped")
                            if (effect.failedPackages.isNotEmpty()) {
                                append("; package failures: ${effect.failedPackages.joinToString()}")
                            }
                        }
                    if (effect.failed > 0 || effect.failedPackages.isNotEmpty()) {
                        notificationCenter.error(title = "Go tests", message = message)
                    } else {
                        notificationCenter.success(title = "Go tests", message = message)
                    }
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    val showFilePicker: (FilePickerRequest) -> Unit = { request ->
        app.filePickerViewModel.dispatch(FilePickerIntent.Show(request))
    }

    // Open existing project
    val openProject: () -> Unit = {
        showFilePicker(FilePickerRequest.openProject(File(currentProjectPath).parent.orEmpty()))
    }

    // Open single file
    val openFile: () -> Unit = {
        showFilePicker(FilePickerRequest.openFile(currentProjectPath))
    }
    val saveFileAs: () -> Unit = {
        val activeFile =
            editorState.activeDocumentUri
                ?.value
                ?.removePrefix("file://")
                ?.let(::File)
        showFilePicker(
            FilePickerRequest.saveFileAs(
                initialDirectory = activeFile?.parent ?: currentProjectPath,
                suggestedFileName = activeFile?.name.orEmpty(),
            ),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(LocalIntelliJColors.current.background),
        ) {
            // Top menu bar
            IntelliJMenuBar(
                session = session,
                app = app,
                editorState = editorState,
                gitState = gitState,
                configurationState = configurationState,
                selectedActivityItem = selectedActivityItem,
                onSelectedActivityItemChange = onSelectedActivityItemChange,
                onSelectBottomTab = { selectedBottomTab = it },
                onOpenTerminalTab = openTerminalTab,
                onOpenBuildTab = openBuildTab,
                onOpenTestsTab = openTestsTab,
                onOpenDebuggerTab = openDebuggerTab,
                onNewProject = { app.newProjectViewModel.dispatch(NewProjectIntent.Show) },
                onOpenProject = openProject,
                onOpenFile = openFile,
                onSaveAs = saveFileAs,
                onCloseProject = {
                    if (editorState.hasUnsavedChanges) {
                        showCloseProjectConfirmation = true
                    } else {
                        coroutineScope.launch { app.closeProject() }
                    }
                },
                onSettings = { app.settingsViewModel.dispatch(SettingsIntent.Show) },
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
                onDeleteBranch = { name -> session.gitViewModel.deleteBranch(name) },
                onRenameBranch = { oldName, newName -> session.gitViewModel.renameBranch(oldName, newName) },
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
                    openDebuggerTab()
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
                            ActivityBarItem.DATABASE,
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
                    var leftPanelWidth by remember(session) { mutableStateOf(restoredLayout.projectPanelWidth.dp) }
                    LaunchedEffect(leftPanelWidth) {
                        session.panelLayout.update { it.copy(projectPanelWidth = leftPanelWidth.value.toInt()) }
                    }
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
                                dirtyOpenPaths =
                                    editorState.tabs
                                        .filter { it.isDirty }
                                        .map { tab -> tab.uri.value.removePrefix("file://") }
                                        .toSet(),
                                onFilesReplaced = { paths ->
                                    paths.forEach { path ->
                                        session.editorViewModel.dispatch(EditorIntent.ReloadFile(path))
                                    }
                                },
                                modifier = panelModifier,
                            )
                        }

                        ActivityBarItem.VCS -> {
                            GitPanel(
                                viewModel = session.gitViewModel,
                                modifier = panelModifier,
                                onSaveOpenDocuments = {
                                    session.editorViewModel.dispatch(EditorIntent.SaveAll)
                                },
                            )
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

                        ActivityBarItem.DATABASE -> {
                            DatabaseToolWindow(
                                state = databaseState,
                                dispatch = { intent -> session.databaseViewModel.dispatch(intent) },
                                onClose = { onSelectedActivityItemChange(null) },
                                modifier = panelModifier,
                            )
                        }

                        else -> {
                            val expandedDirs =
                                remember(session) {
                                    mutableStateMapOf<String, Boolean>().apply {
                                        session.treeExpansion.value.forEach { path -> put(path, true) }
                                    }
                                }
                            LaunchedEffect(session) {
                                snapshotFlow { expandedDirs.filterValues { expanded -> expanded }.keys.toSet() }
                                    .collect { expanded -> session.treeExpansion.value = expanded }
                            }
                            ProjectPanel(
                                projectPath = currentProjectPath,
                                onFileOpen = { path -> session.editorViewModel.dispatch(EditorIntent.OpenFile(path)) },
                                modifier = panelModifier,
                                fileSystem = app.fileSystem,
                                fileActions = session.projectFileActions,
                                onMessage = { message ->
                                    notificationCenter.warning(title = "Project", message = message)
                                },
                                onPathRemoved = { path -> session.closeTabFor(path) },
                                expandedDirs = expandedDirs,
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

                        // Breadcrumbs navigation: file path plus the symbols containing the cursor
                        var breadcrumbPopupDirectory by remember { mutableStateOf<String?>(null) }
                        val activeTab = editorState.activeTab
                        if (activeTab != null) {
                            val projectName = currentProjectPath.substringAfterLast('/')
                            val filePath = activeTab.uri.value.removePrefix("file://")
                            val pathBreadcrumbs =
                                remember(filePath, currentProjectPath) {
                                    createBreadcrumbsFromPath(currentProjectPath, projectName, filePath)
                                }
                            val cursorLine = editorState.cursor.position.line
                            var symbolBreadcrumbs by remember(filePath) {
                                mutableStateOf<List<BreadcrumbSegment>>(emptyList())
                            }
                            LaunchedEffect(filePath, cursorLine) {
                                symbolBreadcrumbs =
                                    session.navigationService
                                        .getBreadcrumbs(filePath, TextPosition(cursorLine, 0))
                                        .filter { it.kind != NavigationSymbolKind.FILE }
                                        .map { crumb ->
                                            BreadcrumbSegment(
                                                name = crumb.name,
                                                path = crumb.target.filePath,
                                                type = BreadcrumbType.SYMBOL,
                                                position = crumb.target.position,
                                            )
                                        }
                            }
                            Breadcrumbs(
                                segments = pathBreadcrumbs + symbolBreadcrumbs,
                                onSegmentClick = { segment ->
                                    val position = segment.position
                                    when {
                                        segment.type == BreadcrumbType.SYMBOL && position != null -> {
                                            session.editorViewModel.dispatch(
                                                EditorIntent.NavigateTo(path = segment.path, position = position),
                                            )
                                        }

                                        segment.type == BreadcrumbType.FILE -> {
                                            session.editorViewModel.dispatch(EditorIntent.OpenFile(segment.path))
                                        }

                                        segment.type == BreadcrumbType.DIRECTORY ||
                                            segment.type == BreadcrumbType.PROJECT -> {
                                            breadcrumbPopupDirectory = segment.path
                                        }
                                    }
                                },
                            )
                            breadcrumbPopupDirectory?.let { directory ->
                                BreadcrumbDirectoryPopup(
                                    directory = directory,
                                    fileSystem = app.fileSystem,
                                    onOpenFile = { path ->
                                        breadcrumbPopupDirectory = null
                                        session.editorViewModel.dispatch(EditorIntent.OpenFile(path))
                                    },
                                    onOpenDirectory = { breadcrumbPopupDirectory = it },
                                    onDismiss = { breadcrumbPopupDirectory = null },
                                )
                            }
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
                                        onIntent = { session.editorViewModel.dispatch(it) },
                                        onGoToDefinition = { position ->
                                            goToDeclaration(activeTab.uri.value, position)
                                        },
                                        indentUnit =
                                            if (editorSettings.editor.useTabs) {
                                                "\t"
                                            } else {
                                                " ".repeat(editorSettings.editor.tabSize)
                                            },
                                        settings = editorSettings.editor,
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
                                        onGoToDefinition = { position ->
                                            goToDeclaration(activeTab.uri.value, position)
                                        },
                                        onHoverRequest = { position ->
                                            session.editorViewModel.dispatch(
                                                EditorIntent.RequestHover(position),
                                            )
                                        },
                                        onHoverDismiss = {
                                            session.editorViewModel.dispatch(EditorIntent.DismissHover)
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
                                        settings = editorSettings.editor,
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
                    problemsCount = editorState.workspaceDiagnostics.size,
                    failedTestsCount = gradleState.testRun?.failedCount ?: 0,
                    onSelectTab = { newTab ->
                        when (newTab) {
                            BottomTab.TERMINAL -> openTerminalTab()
                            BottomTab.BUILD -> openBuildTab()
                            BottomTab.TESTS -> openTestsTab()
                            BottomTab.PROBLEMS -> selectedBottomTab = BottomTab.PROBLEMS
                            BottomTab.DEBUGGER -> openDebuggerTab()
                            BottomTab.DATABASE -> openDatabaseTab()
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
                            val outputConfiguration =
                                configurationState.outputConfigurationId?.let { id ->
                                    configurationState.configurations.find { it.id == id }
                                }
                            if (outputConfiguration != null) {
                                RunOutputPanel(
                                    configurationName = outputConfiguration.name,
                                    output = configurationState.executionOutput,
                                    isRunning =
                                        configurationState.isRunning &&
                                            configurationState.runningConfigurationId == outputConfiguration.id,
                                    exitCode = configurationState.lastExecutionExitCode,
                                    onStop = { session.configurationViewModel.dispatch(ConfigurationIntent.Stop) },
                                    onClear = {
                                        session.configurationViewModel.dispatch(
                                            ConfigurationIntent.ClearExecutionOutput,
                                        )
                                    },
                                )
                            } else {
                                BuildOutputPanel(
                                    state = gradleState,
                                    onIntent = { intent ->
                                        if (intent is su.kidoz.jetaprog.build.gradle.state.GradleIntent.RefreshTasks) {
                                            session.syncGradleProject()
                                        }
                                        session.gradleViewModel.dispatch(intent)
                                    },
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
                        }

                        BottomTab.TESTS -> {
                            TestResultsPanel(
                                testRun = gradleState.testRun,
                                onRerunTest = { taskPath, pattern ->
                                    session.gradleViewModel.dispatch(
                                        su.kidoz.jetaprog.build.gradle.state.GradleIntent.RunTask(
                                            taskPath = taskPath,
                                            args = listOf("--tests", pattern),
                                        ),
                                    )
                                },
                            )
                        }

                        BottomTab.PROBLEMS -> {
                            ProblemsContent(
                                diagnostics = editorState.workspaceDiagnostics,
                                onOpenDiagnostic = { workspaceDiagnostic ->
                                    session.editorViewModel.dispatch(
                                        EditorIntent.NavigateTo(
                                            path = workspaceDiagnostic.uri.value.removePrefix("file://"),
                                            position = workspaceDiagnostic.diagnostic.range.start,
                                        ),
                                    )
                                },
                            )
                        }

                        BottomTab.DEBUGGER -> {
                            DebugBottomContent(
                                state = debugState,
                                dispatch = { intent -> session.debugViewModel.dispatch(intent) },
                                onRestart = {
                                    session.configurationViewModel.dispatch(ConfigurationIntent.Restart)
                                },
                                onShowBreakpoints = { showBreakpointsDialog = true },
                            )
                        }

                        BottomTab.DATABASE -> {
                            DatabaseQueryResultsPanel(
                                state = databaseState,
                                dispatch = { intent -> session.databaseViewModel.dispatch(intent) },
                            )
                        }
                    }
                }
            }

            // Status bar
            IntelliJStatusBar(
                gitBranch = gitState.branch,
                isDirty = gitState.staged.isNotEmpty() || gitState.unstaged.isNotEmpty(),
                errorCount =
                    editorState.workspaceDiagnostics.count {
                        it.diagnostic.severity == DiagnosticSeverity.ERROR
                    },
                warningCount =
                    editorState.workspaceDiagnostics.count {
                        it.diagnostic.severity == DiagnosticSeverity.WARNING
                    },
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
                gradleSyncStatus =
                    when (val syncState = gradleSyncState) {
                        GradleSyncState.Idle -> null
                        GradleSyncState.Syncing -> "Syncing Gradle…"
                        is GradleSyncState.Synchronized -> "${syncState.moduleCount} Gradle modules"
                        is GradleSyncState.Failed -> "Gradle sync failed"
                    },
                isGradleSyncing = gradleSyncState == GradleSyncState.Syncing,
                hasGradleSyncError = gradleSyncState is GradleSyncState.Failed,
                onBranchClick = { onSelectedActivityItemChange(ActivityBarItem.VCS) },
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
            effectiveShortcuts = app.keymapManager.getAllShortcuts(),
        )

        if (showBreakpointsDialog) {
            BreakpointsDialog(
                state = debugState,
                dispatch = { intent -> session.debugViewModel.dispatch(intent) },
                onDismiss = { showBreakpointsDialog = false },
            )
        }

        editorConfirmation?.let { confirmation ->
            ConfirmationDialog(
                title = "Unsaved changes",
                message = confirmation.message,
                confirmLabel = "Discard",
                onConfirm = {
                    editorConfirmation = null
                    confirmation.onConfirm()
                },
                onDismiss = {
                    editorConfirmation = null
                    confirmation.onCancel()
                },
                alternateLabel = confirmation.onSave?.let { "Save" },
                onAlternate =
                    confirmation.onSave?.let { save ->
                        {
                            editorConfirmation = null
                            save()
                        }
                    },
            )
        }

        if (showCloseProjectConfirmation) {
            ConfirmationDialog(
                title = "Close project?",
                message = "The project has unsaved files. Closing it will discard those changes.",
                confirmLabel = "Close Project",
                onConfirm = {
                    showCloseProjectConfirmation = false
                    coroutineScope.launch { app.closeProject() }
                },
                onDismiss = { showCloseProjectConfirmation = false },
                alternateLabel = "Save All",
                onAlternate = {
                    showCloseProjectConfirmation = false
                    session.editorViewModel.dispatch(EditorIntent.SaveAll)
                },
            )
        }

        // Rename refactoring (Shift+F6)
        val renamePlan by session.renamePlan.collectAsState()
        RenameDialog(
            plan = renamePlan,
            onRename = { newName -> session.applyRename(newName) },
            onDismiss = { session.cancelRename() },
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
            projectPath = currentProjectPath,
        )

        // Command palette over everything the active plugins registered.
        CommandPaletteHost(
            viewModel = session.commandPaletteViewModel,
            notificationCenter = notificationCenter,
        )

        // Plugin modal requests (input box, quick pick) and the toast overlay —
        // last children so they sit above everything except modals.
        PluginDialogHost(requestQueue = app.pluginDialogRequests)
        // Above the dialogs that open it (New Project, Clone Repository).
        FilePickerHost(viewModel = app.filePickerViewModel)
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
@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun IntelliJMenuBar(
    session: ProjectSession,
    app: JetaProgApplication,
    editorState: EditorState,
    gitState: GitState,
    configurationState: ConfigurationState,
    selectedActivityItem: ActivityBarItem?,
    onSelectedActivityItemChange: (ActivityBarItem?) -> Unit,
    onSelectBottomTab: (BottomTab?) -> Unit,
    onOpenTerminalTab: () -> Unit,
    onOpenBuildTab: () -> Unit,
    onOpenTestsTab: () -> Unit,
    onOpenDebuggerTab: () -> Unit,
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenFile: () -> Unit,
    onSaveAs: () -> Unit,
    onCloseProject: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gradleState by session.gradleViewModel.state.collectAsState()
    val palette = LocalIntelliJColors.current

    fun toggleActivityItem(item: ActivityBarItem) {
        onSelectedActivityItemChange(if (selectedActivityItem == item) null else item)
    }

    fun navigate(intent: NavigationIntent) {
        scope.launch { session.navigationViewModel.processIntent(intent) }
    }

    fun keymapShortcut(action: String): String? = app.keymapManager.getShortcut(action)?.toDisplayString()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.menuBarHeight.dp)
                .background(palette.toolWindowHeader)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileMenu(
            onNewProject = onNewProject,
            onOpenProject = onOpenProject,
            onOpenFile = onOpenFile,
            onSave = { session.editorViewModel.dispatch(EditorIntent.Save) },
            onSaveAs = onSaveAs,
            onCloseProject = onCloseProject,
            onSettings = onSettings,
        )
        MenuBarItem(
            text = "Edit",
            entries =
                listOf(
                    MenuEntry("Undo", "Ctrl+Z") { session.editorViewModel.dispatch(EditorIntent.Undo) },
                    MenuEntry("Redo", "Ctrl+Shift+Z") { session.editorViewModel.dispatch(EditorIntent.Redo) },
                    null,
                    MenuEntry("Find", "Ctrl+F") {
                        session.editorViewModel.dispatch(EditorIntent.OpenFindBar(withReplace = false))
                    },
                    MenuEntry("Replace", "Ctrl+R") {
                        session.editorViewModel.dispatch(EditorIntent.OpenFindBar(withReplace = true))
                    },
                    MenuEntry("Find Next", "F3") { session.editorViewModel.dispatch(EditorIntent.FindNext) },
                    MenuEntry("Find Previous", "Shift+F3") {
                        session.editorViewModel.dispatch(EditorIntent.FindPrevious)
                    },
                ),
        )
        MenuBarItem(
            text = "View",
            entries =
                listOf(
                    MenuEntry(
                        "Project",
                        ActivityBarItem.PROJECT.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.PROJECT) },
                    MenuEntry(
                        "Find in Files",
                        ActivityBarItem.SEARCH.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.SEARCH) },
                    MenuEntry(
                        "Git",
                        ActivityBarItem.VCS.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.VCS) },
                    MenuEntry(
                        "Agent",
                        ActivityBarItem.AGENT.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.AGENT) },
                    MenuEntry(
                        "Debug",
                        ActivityBarItem.DEBUG.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.DEBUG) },
                    MenuEntry(
                        "Database",
                        ActivityBarItem.DATABASE.shortcut,
                    ) { toggleActivityItem(ActivityBarItem.DATABASE) },
                    null,
                    MenuEntry("Terminal", onClick = onOpenTerminalTab),
                    MenuEntry("Build", onClick = onOpenBuildTab),
                    MenuEntry("Tests", onClick = onOpenTestsTab),
                    MenuEntry("Debugger", onClick = onOpenDebuggerTab),
                    MenuEntry("Problems", null) { onSelectBottomTab(BottomTab.PROBLEMS) },
                ),
        )
        MenuBarItem(
            text = "Navigate",
            entries =
                listOf(
                    MenuEntry("Search Everywhere", keymapShortcut(NavigationActions.SEARCH_EVERYWHERE)) {
                        navigate(NavigationIntent.ShowSearchPopup(SearchMode.ALL))
                    },
                    MenuEntry("Go to Class", keymapShortcut(NavigationActions.GOTO_CLASS)) {
                        navigate(NavigationIntent.ShowSearchPopup(SearchMode.CLASSES))
                    },
                    MenuEntry("Go to File", keymapShortcut(NavigationActions.GOTO_FILE)) {
                        navigate(NavigationIntent.ShowSearchPopup(SearchMode.FILES))
                    },
                    MenuEntry("Go to Symbol", keymapShortcut(NavigationActions.GOTO_SYMBOL)) {
                        navigate(NavigationIntent.ShowSearchPopup(SearchMode.SYMBOLS))
                    },
                    null,
                    MenuEntry("Recent Files", keymapShortcut(NavigationActions.RECENT_FILES)) {
                        navigate(NavigationIntent.ShowRecentFiles)
                    },
                    MenuEntry("Recent Locations", keymapShortcut(NavigationActions.RECENT_LOCATIONS)) {
                        navigate(NavigationIntent.ShowRecentLocations)
                    },
                    null,
                    MenuEntry("Back", keymapShortcut(NavigationActions.BACK)) {
                        navigate(NavigationIntent.GoBack)
                    },
                    MenuEntry("Forward", keymapShortcut(NavigationActions.FORWARD)) {
                        navigate(NavigationIntent.GoForward)
                    },
                ),
        )
        MenuBarItem(
            text = "Code",
            entries =
                listOf(
                    MenuEntry("Format Document") {
                        session.editorViewModel.dispatch(EditorIntent.FormatDocument)
                    },
                    null,
                    MenuEntry("Delete Line") { session.editorViewModel.dispatch(EditorIntent.DeleteLine) },
                    MenuEntry("Duplicate Line") {
                        session.editorViewModel.dispatch(EditorIntent.DuplicateLine)
                    },
                ),
        )
        MenuBarItem(
            text = "Refactor",
            entries =
                listOf(
                    MenuEntry("Rename...", keymapShortcut(NavigationActions.RENAME)) { session.startRename() },
                ),
        )
        MenuBarItem(
            text = "Build",
            entries =
                listOf(
                    MenuEntry("Toggle Build Tool Window") {
                        session.gradleViewModel.dispatch(GradleIntent.ToggleVisibility)
                    },
                    null,
                    MenuEntry("Build Project") {
                        session.gradleViewModel.dispatch(GradleIntent.RunTask("build"))
                        onOpenBuildTab()
                    },
                    MenuEntry(
                        "Cancel Build",
                        enabled = gradleState.isRunning,
                    ) { session.gradleViewModel.dispatch(GradleIntent.CancelTask) },
                    MenuEntry("Reload Gradle Project") { session.syncGradleProject() },
                ),
        )
        MenuBarItem(
            text = "Run",
            entries =
                listOf(
                    MenuEntry("Run", "Shift+F10") {
                        val gradleSettings =
                            configurationState.activeConfiguration?.settings as? ConfigurationSettings.Gradle
                        if (gradleSettings != null) {
                            session.gradleViewModel.dispatch(
                                GradleIntent.RunTask(
                                    gradleSettings.taskPath,
                                    args = gradleSettings.arguments + gradleSettings.jvmArguments.map { "-D$it" },
                                ),
                            )
                            onOpenBuildTab()
                        } else {
                            session.configurationViewModel.dispatch(ConfigurationIntent.RunActive)
                        }
                    },
                    MenuEntry("Debug", "Shift+F9") {
                        session.configurationViewModel.dispatch(ConfigurationIntent.DebugActive)
                        onOpenDebuggerTab()
                    },
                    MenuEntry("Stop", "Ctrl+F2") {
                        session.configurationViewModel.dispatch(ConfigurationIntent.Stop)
                    },
                ),
        )
        MenuBarItem(
            text = "VCS",
            entries =
                listOf(
                    MenuEntry("Show Git Tool Window") { onSelectedActivityItemChange(ActivityBarItem.VCS) },
                    null,
                    MenuEntry("Commit...") { onSelectedActivityItemChange(ActivityBarItem.VCS) },
                    MenuEntry("Update Project") { session.gitViewModel.pull() },
                    MenuEntry("Push") { session.gitViewModel.push() },
                    MenuEntry("Refresh", enabled = gitState.isBusy) { session.gitViewModel.refresh() },
                ),
        )
        MenuBarItem(
            text = "Window",
            entries =
                listOf(
                    MenuEntry(
                        "Close Editor Tab",
                        enabled = editorState.activeTab != null,
                    ) { session.editorViewModel.dispatch(EditorIntent.CloseTab(editorState.activeTabIndex)) },
                    null,
                    MenuEntry("Close Project") { onCloseProject() },
                ),
        )
        MenuBarItem(
            text = "Help",
            entries =
                listOf(
                    MenuEntry("About JetaProg") {
                        app.notificationCenter.info(
                            title = "JetaProg IDE",
                            message = "Cross-platform IDE built with Kotlin and Compose Multiplatform.",
                        )
                    },
                ),
        )
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
    onDeleteBranch: (String) -> Unit,
    onRenameBranch: (oldName: String, newName: String) -> Unit,
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
                    .background(LocalIntelliJColors.current.background)
                    .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Navigate back",
                tint = LocalIntelliJColors.current.iconDefault,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .clickable(onClick = onNavigateBack)
                        .size(Dimensions.iconLg.dp),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Navigate forward",
                tint = LocalIntelliJColors.current.iconDefault,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .clickable(onClick = onNavigateForward)
                        .size(Dimensions.iconLg.dp),
            )
            ToolbarDivider()
            ToolbarChip(
                icon = Icons.Filled.Folder,
                iconTint = LocalIntelliJColors.current.iconFolder,
                label = projectName,
            )
            if (!branchName.isNullOrBlank()) {
                BranchSelector(
                    branchName = branchName,
                    branches = branches,
                    onCheckoutBranch = onCheckoutBranch,
                    onCreateBranch = onCreateBranch,
                    onDeleteBranch = onDeleteBranch,
                    onRenameBranch = onRenameBranch,
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
                    .background(LocalIntelliJColors.current.divider),
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
                .height(Dimensions.toolbarIcon.dp)
                .background(LocalIntelliJColors.current.divider),
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
                .height(Dimensions.chipHeight.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(LocalIntelliJColors.current.surfaceElevated)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(text = label, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp, maxLines = 1)
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
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
                .height(Dimensions.chipHeight.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalIntelliJColors.current.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
        if (badge > 0) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalIntelliJColors.current.accent)
                        .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.toString(),
                    color = LocalIntelliJColors.current.background,
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
                .height(Dimensions.chipHeight.dp)
                .widthIn(min = Dimensions.searchEverywhereWidth.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clickable(onClick = onClick)
                .background(LocalIntelliJColors.current.inputBackground)
                .border(
                    width = 1.dp,
                    color = LocalIntelliJColors.current.divider,
                    shape = RoundedCornerShape(Dimensions.cornerRadius.dp),
                ).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = LocalIntelliJColors.current.textMuted,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(
            text = "Search Everywhere",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
        )
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(LocalIntelliJColors.current.surfaceContainer)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(text = "⇧⇧", color = LocalIntelliJColors.current.iconDefault, fontSize = 10.sp)
        }
    }
}

/** One actionable row in a menu-bar dropdown; a `null` entry renders a divider. */
private data class MenuEntry(
    val label: String,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/**
 * IntelliJ menu bar item: a hover-highlighted title that opens an on-contract
 * dropdown of [MenuEntry] rows.
 */
@Composable
private fun MenuBarItem(
    text: String,
    entries: List<MenuEntry?>,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }
    var triggerHeightPx by remember { mutableStateOf(0) }

    Box {
        Box(
            modifier =
                Modifier
                    .onSizeChanged { triggerHeightPx = it.height }
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isHovered || expanded) {
                            LocalIntelliJColors.current.buttonBackgroundHover
                        } else {
                            Color.Transparent
                        },
                    ).hoverable(interactionSource)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 12.sp,
            )
        }
        PopupChromeMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offsetY = triggerHeightPx,
        ) {
            entries.forEachIndexed { index, entry ->
                if (entry == null) {
                    if (index != 0 && index != entries.lastIndex) {
                        HorizontalDivider(color = LocalIntelliJColors.current.border)
                    }
                } else {
                    PopupListRow(
                        selected = false,
                        onClick = {
                            if (entry.enabled) {
                                expanded = false
                                entry.onClick()
                            }
                        },
                    ) {
                        Text(
                            text = entry.label,
                            color =
                                if (entry.enabled) {
                                    LocalIntelliJColors.current.textPrimary
                                } else {
                                    LocalIntelliJColors.current.textDisabled
                                },
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.shortcut != null) {
                            Text(
                                text = entry.shortcut,
                                color = LocalIntelliJColors.current.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
