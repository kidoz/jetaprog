package su.kidoz.jetaprog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.JetaProgApplication
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.ui.components.ActivityBar
import su.kidoz.jetaprog.app.ui.components.ActivityBarItem
import su.kidoz.jetaprog.app.ui.components.Breadcrumbs
import su.kidoz.jetaprog.app.ui.components.BuildStatus
import su.kidoz.jetaprog.app.ui.components.FileMenu
import su.kidoz.jetaprog.app.ui.components.IntelliJEditorTabs
import su.kidoz.jetaprog.app.ui.components.IntelliJStatusBar
import su.kidoz.jetaprog.app.ui.components.NotificationOverlay
import su.kidoz.jetaprog.app.ui.components.SessionSkeleton
import su.kidoz.jetaprog.app.ui.components.VerticalDragHandle
import su.kidoz.jetaprog.app.ui.components.VerticalSplitter
import su.kidoz.jetaprog.app.ui.components.coerceInDp
import su.kidoz.jetaprog.app.ui.components.createBreadcrumbsFromPath
import su.kidoz.jetaprog.app.ui.dialogs.configuration.RunConfigurationDialog
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectDialog
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectEffect
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsDialog
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsEffect
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.editor.CodeEditor
import su.kidoz.jetaprog.app.ui.editor.EmptyEditorPlaceholder
import su.kidoz.jetaprog.app.ui.editor.MarkdownEditor
import su.kidoz.jetaprog.app.ui.navigation.NavigationHost
import su.kidoz.jetaprog.app.ui.panels.BuildOutputPanel
import su.kidoz.jetaprog.app.ui.panels.ProjectPanel
import su.kidoz.jetaprog.app.ui.panels.TerminalPanel
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.toolbar.RunConfigurationSelector
import su.kidoz.jetaprog.app.viewmodel.TerminalIntent
import su.kidoz.jetaprog.configuration.ConfigurationEffect
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.NotificationType
import java.io.File
import javax.swing.JFileChooser

/**
 * Main screen of the JetaProg IDE - IntelliJ IDEA style.
 */
@Composable
public fun MainScreen(app: JetaProgApplication) {
    val session by app.session.collectAsState()
    val settingsState by app.settingsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedActivityItem by remember { mutableStateOf<ActivityBarItem?>(ActivityBarItem.PROJECT) }
    var showProjectPanel by remember { mutableStateOf(true) }

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

    val currentSession = session
    if (currentSession == null) {
        // No project session yet — show skeleton in the shape of the shell.
        Box(modifier = Modifier.fillMaxSize().background(IntelliJColors.background)) {
            SessionSkeleton(modifier = Modifier.fillMaxSize())
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
        showProjectPanel = showProjectPanel,
        onShowProjectPanelChange = { showProjectPanel = it },
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
    showProjectPanel: Boolean,
    onShowProjectPanelChange: (Boolean) -> Unit,
    browseLocation: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val editorState by session.editorViewModel.state.collectAsState()
    val editorSettings by session.editorViewModel.settings.collectAsState()
    val terminalState by session.terminalViewModel.state.collectAsState()
    val gradleState by session.gradleViewModel.state.collectAsState()
    val configurationState by session.configurationViewModel.state.collectAsState()

    val currentProjectPath = session.projectPath
    val notificationCenter = app.notificationCenter

    // Session-scoped effect collectors: surface ShowError/ShowNotification toasts.
    LaunchedEffect(session) {
        session.editorViewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.ShowError -> {
                    notificationCenter.error(title = "Editor", message = effect.message)
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
                    coroutineScope.launch { app.openProject(System.getProperty("user.dir")) }
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

            // Main toolbar (run configuration + future VCS/search-everywhere)
            MainToolbar(
                configurationState = configurationState,
                onSelectConfiguration = { id ->
                    session.configurationViewModel.dispatch(ConfigurationIntent.SelectConfiguration(id))
                },
                onRunConfiguration = {
                    session.configurationViewModel.dispatch(ConfigurationIntent.RunActive)
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
                            ActivityBarItem.PROJECT -> {
                                val newShow = !showProjectPanel
                                onShowProjectPanelChange(newShow)
                                onSelectedActivityItemChange(if (newShow) item else null)
                            }

                            ActivityBarItem.TERMINAL -> {
                                session.terminalViewModel.dispatch(TerminalIntent.ToggleVisibility)
                                if (!terminalState.isVisible) {
                                    session.terminalViewModel.dispatch(TerminalIntent.CreateTerminal())
                                }
                            }

                            ActivityBarItem.BUILD -> {
                                session.gradleViewModel.dispatch(
                                    su.kidoz.jetaprog.build.gradle.state.GradleIntent.ToggleVisibility,
                                )
                            }

                            else -> {
                                onSelectedActivityItemChange(item)
                            }
                        }
                    },
                )

                // Vertical divider between activity bar and project panel
                VerticalSplitter(modifier = Modifier.fillMaxHeight())

                // Project panel (left sidebar) — resizable
                if (showProjectPanel) {
                    val minWidth = Dimensions.toolWindowMinWidth.dp
                    val maxWidth = Dimensions.toolWindowMaxWidth.dp
                    var projectPanelWidth by remember { mutableStateOf(Dimensions.toolWindowDefaultWidth.dp) }
                    ProjectPanel(
                        projectPath = currentProjectPath,
                        onFileOpen = { path -> session.editorViewModel.dispatch(EditorIntent.OpenFile(path)) },
                        modifier = Modifier.width(projectPanelWidth),
                    )
                    VerticalDragHandle(
                        onDelta = { delta ->
                            projectPanelWidth = (projectPanelWidth + delta).coerceInDp(minWidth, maxWidth)
                        },
                    )
                }

                // Editor area
                Column(modifier = Modifier.weight(1f)) {
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
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            EmptyEditorPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // Bottom panels
            Column {
                // Build output panel
                BuildOutputPanel(
                    state = gradleState,
                    onIntent = { intent -> session.gradleViewModel.dispatch(intent) },
                )

                // Terminal panel
                TerminalPanel(
                    state = terminalState,
                    effects = session.terminalViewModel.effects,
                    onIntent = { intent -> session.terminalViewModel.dispatch(intent) },
                )
            }

            // Status bar
            IntelliJStatusBar(
                gitBranch = "master",
                lineInfo =
                    if (editorState.activeTab != null) {
                        "${editorState.currentLine}:${editorState.currentColumn}"
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
            onNavigate = { filePath, line, _ ->
                session.editorViewModel.dispatch(EditorIntent.OpenFile(filePath))
                session.editorViewModel.dispatch(EditorIntent.GoToLine(line + 1))
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
 * Currently hosts the run-configuration selector. VCS controls and the
 * search-everywhere trigger will land here in follow-up work — this row is
 * the canonical home for high-frequency global actions, per the UI/UX guide.
 */
@Composable
private fun MainToolbar(
    configurationState: su.kidoz.jetaprog.configuration.ConfigurationState,
    onSelectConfiguration: (su.kidoz.jetaprog.configuration.ConfigurationId) -> Unit,
    onRunConfiguration: () -> Unit,
    onStopConfiguration: () -> Unit,
    onEditConfigurations: () -> Unit,
    onCreateRecommended: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.mainToolbarHeight.dp)
                    .background(IntelliJColors.background)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster reserved for project switcher / future controls.
            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.weight(1f))
            RunConfigurationSelector(
                state = configurationState,
                onSelect = onSelectConfiguration,
                onRun = onRunConfiguration,
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
