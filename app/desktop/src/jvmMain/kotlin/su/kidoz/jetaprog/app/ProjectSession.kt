package su.kidoz.jetaprog.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.adapter.EditorServiceBridge
import su.kidoz.jetaprog.app.command.CommandPaletteIntent
import su.kidoz.jetaprog.app.command.CommandPaletteViewModel
import su.kidoz.jetaprog.app.gradle.GradleImportCoordinator
import su.kidoz.jetaprog.app.keymap.CommandActions
import su.kidoz.jetaprog.app.keymap.DefaultKeymap
import su.kidoz.jetaprog.app.keymap.NavigationActions
import su.kidoz.jetaprog.app.navigation.DefaultNavigationService
import su.kidoz.jetaprog.app.navigation.KotlinIndexNavigationService
import su.kidoz.jetaprog.app.project.ProjectFileActions
import su.kidoz.jetaprog.app.quickfix.KotlinQuickFixService
import su.kidoz.jetaprog.app.refactoring.KotlinRenameService
import su.kidoz.jetaprog.app.refactoring.RenameOutcome
import su.kidoz.jetaprog.app.refactoring.RenamePlan
import su.kidoz.jetaprog.app.refactoring.RenamePreparation
import su.kidoz.jetaprog.app.ui.navigation.NavigationIntent
import su.kidoz.jetaprog.app.ui.navigation.NavigationViewModel
import su.kidoz.jetaprog.app.ui.navigation.SearchMode
import su.kidoz.jetaprog.app.ui.navigation.handleNavigationKeyEvent
import su.kidoz.jetaprog.app.viewmodel.AgentSessionViewModel
import su.kidoz.jetaprog.app.viewmodel.ConfigurationViewModel
import su.kidoz.jetaprog.app.viewmodel.DebugViewModel
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.app.viewmodel.GradleViewModel
import su.kidoz.jetaprog.app.viewmodel.TerminalViewModel
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel
import su.kidoz.jetaprog.build.gradle.execution.JvmGradleExecutionService
import su.kidoz.jetaprog.build.gradle.importer.GradleClasspathResolver
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationManager
import su.kidoz.jetaprog.configuration.JvmConfigurationStorage
import su.kidoz.jetaprog.configuration.discovery.ConfigurationDiscovery
import su.kidoz.jetaprog.configuration.discovery.ProjectDetector
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.LineChangeMarker
import su.kidoz.jetaprog.lint.JvmLintConfigurationStorage
import su.kidoz.jetaprog.lint.engine.DefaultLintEngine
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.lsp.server.DefaultServerRegistry
import su.kidoz.jetaprog.lsp.server.EmbeddedServerConfig
import su.kidoz.jetaprog.lsp.server.EmbeddedServerRegistry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.plugins.cfamily.CPlugin
import su.kidoz.jetaprog.plugins.cfamily.CppPlugin
import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import su.kidoz.jetaprog.plugins.java.JavaPlugin
import su.kidoz.jetaprog.plugins.javascript.JavaScriptTypeScriptPlugin
import su.kidoz.jetaprog.plugins.kotlin.KotlinPlugin
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.server.KotlinEmbeddedServer
import su.kidoz.jetaprog.plugins.python.PythonPlugin
import su.kidoz.jetaprog.plugins.runtime.activation.ActivationEventServiceImpl
import su.kidoz.jetaprog.plugins.runtime.activation.ContributionRegistryImpl
import su.kidoz.jetaprog.plugins.runtime.activation.LazyPluginActivator
import su.kidoz.jetaprog.plugins.runtime.activation.PluginActivator
import su.kidoz.jetaprog.plugins.runtime.context.ServiceContainer
import su.kidoz.jetaprog.plugins.runtime.lifecycle.PluginState
import su.kidoz.jetaprog.plugins.runtime.manager.JvmPluginManager
import su.kidoz.jetaprog.plugins.runtime.services.CommandServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.EditorServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.LanguageServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.LintServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.NotificationServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.SettingsAccessServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.StorageServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.TerminalServiceImpl
import su.kidoz.jetaprog.plugins.runtime.services.WorkspaceServiceImpl
import su.kidoz.jetaprog.plugins.rust.RustPlugin
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.plugins.support.LanguageServerManager
import su.kidoz.jetaprog.plugins.vala.ValaPlugin
import su.kidoz.jetaprog.project.service.JvmFileOperations
import su.kidoz.jetaprog.project.service.ProjectDirectoryService
import su.kidoz.jetaprog.project.state.CursorState
import su.kidoz.jetaprog.project.state.TabState
import su.kidoz.jetaprog.project.state.WorkspaceState
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.vcs.GitLineChangeType

/**
 * Encapsulates all project-scoped services for a single open project.
 *
 * When the user switches projects, the current session is shut down and a new one is created.
 * This ensures all services see the correct project path and no stale state leaks between projects.
 *
 * @param projectPath The root path of the open project.
 * @param fileSystem Global file system instance.
 * @param processExecutor Global process executor instance.
 * @param settingsService Global settings service instance.
 * @param lintEngine Global lint engine instance.
 * @param lintProviderRegistry Global lint provider registry instance.
 * @param languageServerManager Global language server manager instance.
 */
public class ProjectSession(
    public val projectPath: String,
    private val fileSystem: FileSystem,
    private val processExecutor: ProcessExecutor,
    private val settingsService: SettingsService,
    private val lintEngine: DefaultLintEngine,
    private val lintProviderRegistry: LintProviderRegistry,
    private val languageServerManager: LanguageServerManager,
) : Disposable {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gradleExecutionService = JvmGradleExecutionService(processExecutor)

    // ========================================================================
    // LSP
    // ========================================================================

    private val embeddedServerConfig: EmbeddedServerConfig =
        EmbeddedServerConfig(
            rootUri = "file://$projectPath",
            workspaceFolders = listOf("file://$projectPath"),
        )

    /**
     * The embedded server registry for in-process language servers.
     */
    public val embeddedServerRegistry: EmbeddedServerRegistry =
        DefaultServerRegistry(embeddedServerConfig)

    // ========================================================================
    // Navigation
    // ========================================================================

    /**
     * Local Kotlin symbol index powering Go to Class/Symbol, file structure and
     * declaration navigation until a real language server is registered.
     */
    private val kotlinSymbolIndex: KotlinSymbolIndex = KotlinSymbolIndex()

    /**
     * Classpath-aware Kotlin semantic analyzer shared between the Kotlin plugin
     * (diagnostics, member completion) and the embedded language server
     * (semantic go-to-definition), so the expensive compiler environment is
     * built at most once per session. Owned and disposed by this session.
     */
    private val kotlinSemanticAnalyzer: KotlinSemanticAnalyzer =
        KotlinSemanticAnalyzer(classpathProvider = { filePath -> kotlinClasspath(filePath) })

    /**
     * The navigation service for code navigation features.
     */
    public val navigationService: NavigationService =
        KotlinIndexNavigationService(
            delegate =
                DefaultNavigationService(
                    lspClient = null,
                    fileSystem = fileSystem,
                    embeddedServerRegistry = embeddedServerRegistry,
                    workspacePath = projectPath,
                ),
            symbolIndex = kotlinSymbolIndex,
            fileSystem = fileSystem,
            workspacePath = projectPath,
            semanticAnalyzer = kotlinSemanticAnalyzer,
        )

    /**
     * The navigation view model.
     */
    public val navigationViewModel: NavigationViewModel = NavigationViewModel(navigationService)

    /**
     * Drives the command palette over the commands registered by the active plugins.
     */
    public val commandPaletteViewModel: CommandPaletteViewModel by lazy {
        CommandPaletteViewModel(
            listCommandIds = { commandService.getCommands() },
            listManifests = {
                pluginManager.installedPlugins.value.mapNotNull { pluginManager.getPlugin(it.id)?.manifest }
            },
            executeCommand = { id -> commandService.executeCommand(id) },
        )
    }

    // ========================================================================
    // Refactoring
    // ========================================================================

    /** Quick fixes and auto-import, both backed by the Kotlin symbol index. */
    private val kotlinQuickFixService: KotlinQuickFixService = KotlinQuickFixService(kotlinSymbolIndex)

    /** Create/rename/delete operations offered by the project tree. */
    public val projectFileActions: ProjectFileActions = ProjectFileActions(fileSystem)

    private val renameService: KotlinRenameService =
        KotlinRenameService(
            fileSystem = fileSystem,
            symbolIndex = kotlinSymbolIndex,
            semanticAnalyzer = kotlinSemanticAnalyzer,
            workspacePath = projectPath,
        )

    private val _renamePlan = MutableStateFlow<RenamePlan?>(null)

    /** The pending rename, shown in the rename dialog; null when no rename is in progress. */
    public val renamePlan: StateFlow<RenamePlan?> = _renamePlan.asStateFlow()

    private val _renameMessages = Channel<String>(Channel.BUFFERED)

    /** User-facing rename outcomes, surfaced as notifications by the UI layer. */
    public val renameMessages: Flow<String> = _renameMessages.receiveAsFlow()

    /**
     * Prepares a rename for the symbol under the caret, opening the dialog on
     * success and reporting why not otherwise.
     */
    public fun startRename() {
        sessionScope.launch {
            val state = editorViewModel.state.value
            val path = state.activeDocumentUri?.value?.removePrefix("file://")
            if (path == null) {
                notify("Open a file to rename a symbol in it.")
                return@launch
            }
            when (val preparation = renameService.prepare(path, state.cursor.position, state.content)) {
                is RenamePreparation.Ready -> {
                    val dirtyOthers =
                        preparation.plan.affectedFiles
                            .filter { it != path }
                            .filter { affected -> isDirty(affected) }
                    if (dirtyOthers.isNotEmpty()) {
                        // Rename rewrites files on disk; unsaved buffers elsewhere would be lost.
                        notify("Save ${dirtyOthers.joinToString { it.substringAfterLast('/') }} before renaming.")
                        return@launch
                    }
                    _renamePlan.value = preparation.plan
                }

                is RenamePreparation.Unavailable -> {
                    notify(preparation.reason)
                }
            }
        }
    }

    /** Applies the pending rename, rewriting every affected file. */
    public fun applyRename(newName: String) {
        val plan = _renamePlan.value ?: return
        _renamePlan.value = null
        sessionScope.launch {
            when (val outcome = renameService.apply(plan, newName)) {
                is RenameOutcome.Applied -> {
                    // Only the active document is held in memory; other tabs
                    // re-read from disk when switched to.
                    editorViewModel.dispatch(EditorIntent.UpdateContent(outcome.updatedOriginContent))
                    editorViewModel.dispatch(EditorIntent.Save)
                    notify(
                        "Renamed ${outcome.occurrencesReplaced} occurrences in ${outcome.filesChanged} files.",
                    )
                }

                is RenameOutcome.Failed -> {
                    notify(outcome.reason)
                }
            }
        }
    }

    /** Dismisses the rename dialog without changing anything. */
    public fun cancelRename() {
        _renamePlan.value = null
    }

    /**
     * Closes any editor tab for [path], used after the file is deleted or
     * renamed from the project tree so the editor cannot show a ghost buffer.
     */
    public fun closeTabFor(path: String) {
        val index =
            editorViewModel.state.value.tabs.indexOfFirst { tab ->
                tab.uri.value.removePrefix("file://") == path
            }
        if (index >= 0) editorViewModel.dispatch(EditorIntent.CloseTab(index))
    }

    private fun isDirty(path: String): Boolean =
        editorViewModel.state.value.tabs.any { tab ->
            tab.uri.value.removePrefix("file://") == path && tab.isDirty
        }

    private suspend fun notify(message: String) {
        _renameMessages.send(message)
    }

    // ========================================================================
    // Language
    // ========================================================================

    /**
     * The language registry for language features.
     */
    public val languageRegistry: LanguageRegistry by lazy {
        LanguageRegistry(languageServerManager, settingsService)
    }

    // ========================================================================
    // Plugin infrastructure
    // ========================================================================

    private val workspaceService: WorkspaceServiceImpl by lazy {
        WorkspaceServiceImpl(fileSystem, projectPath)
    }

    private val activationEventService: ActivationEventServiceImpl by lazy {
        ActivationEventServiceImpl(workspaceService)
    }

    private val commandService: CommandServiceImpl by lazy {
        CommandServiceImpl(activationEventService)
    }

    private val contributionRegistry: ContributionRegistryImpl by lazy {
        ContributionRegistryImpl(commandService, sessionScope)
    }

    private val lintService: LintServiceImpl by lazy {
        LintServiceImpl(lintEngine, lintProviderRegistry)
    }

    private val editorServiceBridge: EditorServiceBridge by lazy {
        EditorServiceBridge(editorViewModel)
    }

    private val editorService: EditorServiceImpl by lazy {
        EditorServiceImpl(
            openDocumentHandler = { uri, options -> editorServiceBridge.openDocument(uri, options) },
            showDocumentHandler = { document, options -> editorServiceBridge.showDocument(document, options) },
        )
    }

    private val serviceContainer: ServiceContainer by lazy {
        ServiceContainer(
            workspace = workspaceService,
            editor = editorService,
            languages = LanguageServiceImpl(languageRegistry, languageServerManager, projectPath),
            commands = commandService,
            notifications = NotificationServiceImpl(),
            terminal = TerminalServiceImpl(processExecutor, projectPath),
            lint = lintService,
            storageFactory = { pluginId -> StorageServiceImpl(pluginId, projectPath) },
            activationEvents = activationEventService,
            settingsAccess = SettingsAccessServiceImpl(settingsService),
        )
    }

    private val lazyActivator: LazyPluginActivator by lazy {
        LazyPluginActivator(
            activationEventService = activationEventService,
            contributionRegistry = contributionRegistry,
            pluginActivator = PluginActivator { pluginId -> pluginManager.activatePlugin(pluginId) },
            scope = sessionScope,
        )
    }

    private val pluginManager: JvmPluginManager by lazy {
        JvmPluginManager(serviceContainer, lazyActivator)
    }

    // ========================================================================
    // ViewModels
    // ========================================================================

    /**
     * The editor view model.
     */
    public val editorViewModel: EditorViewModel by lazy {
        EditorViewModel(
            fileSystem = fileSystem,
            settingsService = settingsService,
            navigationService = navigationService,
            languageRegistry = languageRegistry,
            activationEvents = activationEventService,
            lintService = lintService,
            quickFixProvider = kotlinQuickFixService,
            autoImportProvider = kotlinQuickFixService,
            pluginEditorService = editorService,
            workspacePath = projectPath,
        )
    }

    /**
     * The terminal view model.
     */
    public val terminalViewModel: TerminalViewModel =
        TerminalViewModel(defaultWorkingDirectory = projectPath)

    /**
     * The Gradle view model.
     */
    public val gradleViewModel: GradleViewModel =
        GradleViewModel(gradleExecutionService)

    /**
     * Imports the project structure from Gradle (Tooling API) and reconciles it
     * against `.jetaprog` metadata to surface stale or missing modules.
     */
    public val gradleImportCoordinator: GradleImportCoordinator =
        GradleImportCoordinator(
            projectPath = projectPath,
            fileSystem = fileSystem,
            executionService = gradleExecutionService,
        )

    /**
     * Persistence for the `.jetaprog` project directory (workspace state, config files).
     */
    private val projectDirectoryService: ProjectDirectoryService =
        ProjectDirectoryService(projectPath, JvmFileOperations())

    /**
     * The Kotlin compile classpath derived from the Gradle import, populated
     * asynchronously after the project opens. Fed to the Kotlin plugin for
     * classpath-aware analysis.
     */
    @Volatile
    private var kotlinClasspathResolver: GradleClasspathResolver? = null

    /**
     * The agent (ACP) session view model, driving an external coding agent.
     */
    public val agentSessionViewModel: AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = projectPath,
            fileSystem = fileSystem,
        )

    /**
     * The project-wide full-text search ("Find in Files") view model.
     */
    public val textSearchViewModel: TextSearchViewModel =
        TextSearchViewModel(projectPath = projectPath, fileSystem = fileSystem)

    /**
     * The Git workflow view model (status, diff, stage/unstage, commit).
     */
    public val gitViewModel: GitViewModel =
        GitViewModel(processExecutor = processExecutor, projectPath = projectPath)

    // ========================================================================
    // Configuration
    // ========================================================================

    private val configurationStorage: JvmConfigurationStorage = JvmConfigurationStorage()

    private val configurationManager: ConfigurationManager =
        ConfigurationManager(configurationStorage)

    private val projectDetector: ProjectDetector = ProjectDetector(fileSystem)

    private val configurationDiscovery: ConfigurationDiscovery =
        ConfigurationDiscovery(projectDetector)

    private val debugService: DebugService =
        DebugService(
            processExecutor = processExecutor,
            scope = sessionScope,
        )

    /**
     * The debugger view model, driving the Debug perspective from [debugService].
     */
    public val debugViewModel: DebugViewModel =
        DebugViewModel(
            debugService = debugService,
            projectPath = projectPath,
            fileSystem = fileSystem,
        )

    /**
     * The configuration view model.
     */
    public val configurationViewModel: ConfigurationViewModel =
        ConfigurationViewModel(
            configurationManager = configurationManager,
            processExecutor = processExecutor,
            gradleExecutionService = gradleExecutionService,
            configurationDiscovery = configurationDiscovery,
            debugService = debugService,
        )

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Initializes all project-scoped services.
     * Registers bundled plugins, activates eager ones, and starts listening for activation triggers.
     */
    public suspend fun initialize() {
        // Register the embedded Kotlin language server, backed by the shared
        // symbol index. Navigation prefers LSP answers, so features migrate to
        // the server automatically as it gains capabilities.
        embeddedServerRegistry.registerServerFactory("kotlin") {
            KotlinEmbeddedServer(kotlinSymbolIndex, kotlinSemanticAnalyzer)
        }

        // Load the project lint configuration (.jetaprog/lint.json)
        JvmLintConfigurationStorage()
            .load(projectPath)
            .onSuccess { lintService.setConfiguration(it) }

        // Initialize Gradle with current project
        gradleViewModel.dispatch(GradleIntent.Initialize(projectPath))

        // Initialize configuration manager
        configurationViewModel.dispatch(ConfigurationIntent.Initialize(projectPath))

        // Register bundled plugins (lazy activation handled by LazyPluginActivator)
        pluginManager.registerBundledPlugin(
            KotlinPlugin(
                classpathProvider = { kotlinClasspathResolver?.workspaceClasspath().orEmpty() },
                sharedSemanticAnalyzer = kotlinSemanticAnalyzer,
            ),
        )
        pluginManager.registerBundledPlugin(CPlugin())
        pluginManager.registerBundledPlugin(CppPlugin())
        pluginManager.registerBundledPlugin(DotNetPlugin())
        pluginManager.registerBundledPlugin(JavaPlugin())
        pluginManager.registerBundledPlugin(JavaScriptTypeScriptPlugin())
        pluginManager.registerBundledPlugin(PythonPlugin())
        pluginManager.registerBundledPlugin(RustPlugin())
        pluginManager.registerBundledPlugin(ValaPlugin())

        // Activate plugins that should start immediately (empty or * activation events)
        pluginManager.installedPlugins.value
            .filter { it.state == PluginState.Loaded }
            .forEach { pluginManager.activatePlugin(it.id) }

        // Start listening for activation triggers
        lazyActivator.start()

        // Fire startup finished event (activates plugins with onStartupFinished)
        activationEventService.fireStartupFinished()

        // Resolve the Kotlin classpath from Gradle in the background so semantic
        // analysis becomes available once import completes.
        sessionScope.launch { loadKotlinClasspath() }

        // Build the Kotlin symbol index in the background so Go to Class/Symbol,
        // file structure and declaration navigation work without a language server.
        sessionScope.launch { kotlinSymbolIndex.indexDirectory(projectPath) }

        // Keep editor gutter VCS markers in sync with the active document and git state
        sessionScope.launch { observeGitLineMarkers() }

        // Keep the plugin editor surface aligned with tab switches initiated by the UI.
        sessionScope.launch {
            editorViewModel.state
                .map { it.activeDocumentUri }
                .distinctUntilChanged()
                .collect { uri ->
                    val editor = uri?.let(editorServiceBridge::editorFor)
                    editorService.setActiveEditor(editor)
                    editorService.setVisibleEditors(editor?.let(::listOf).orEmpty())
                }
        }

        // Restore the previous editing session (open tabs, active tab, cursor)
        restoreWorkspaceState()
    }

    /**
     * Re-indexes a single file in the Kotlin symbol index.
     *
     * Call after a file is saved or opened so navigation stays in sync with edits;
     * non-Kotlin files are ignored by the index.
     */
    public fun reindexFile(path: String) {
        sessionScope.launch { kotlinSymbolIndex.indexFile(path) }
    }

    /**
     * Timestamp of the last plain Shift release, for double-Shift detection.
     */
    private var lastShiftReleaseAtMillis = 0L

    /**
     * Handles an IDE-wide key event for navigation shortcuts.
     *
     * Routed from the window's preview key handler. Recognizes the [su.kidoz.jetaprog.app.keymap.DefaultKeymap]
     * navigation chords (Go to Class/File/Symbol, declaration, usages, structure, back/forward)
     * plus double-Shift for Search Everywhere.
     *
     * @return true when the event triggered a navigation action and should not propagate.
     */
    public fun handleKeyEvent(event: KeyEvent): Boolean {
        // While a navigation popup is open it owns the keyboard.
        val navState = navigationViewModel.state.value
        val popupOpen =
            navState.isSearchPopupVisible ||
                navState.isFileStructureVisible ||
                navState.isQuickDefinitionVisible ||
                navState.isUsagesPopupVisible ||
                navState.isRecentFilesVisible ||
                commandPaletteViewModel.state.value.isVisible
        if (popupOpen) return false

        if (event.type == KeyEventType.KeyDown &&
            DefaultKeymap.findAction(event) == CommandActions.COMMAND_PALETTE
        ) {
            commandPaletteViewModel.dispatch(CommandPaletteIntent.Show)
            return true
        }

        if (handleDoubleShift(event)) return true

        if (event.type == KeyEventType.KeyDown &&
            DefaultKeymap.findAction(event) == NavigationActions.RENAME
        ) {
            startRename()
            return true
        }

        val editorState = editorViewModel.state.value
        val cursor = editorState.cursor.position
        return handleNavigationKeyEvent(
            event = event,
            viewModel = navigationViewModel,
            currentFilePath =
                editorState.activeTab
                    ?.uri
                    ?.value
                    ?.removePrefix("file://") ?: "",
            currentFileName = editorState.activeTab?.name ?: "",
            currentLine = cursor.line,
            currentColumn = cursor.column,
            scope = sessionScope,
        )
    }

    private fun handleDoubleShift(event: KeyEvent): Boolean {
        val isShift = event.key == Key.ShiftLeft || event.key == Key.ShiftRight
        val hasOtherModifiers = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        if (event.type == KeyEventType.KeyDown) {
            // Any non-Shift key press breaks a pending double-Shift sequence.
            if (!isShift) lastShiftReleaseAtMillis = 0L
            return false
        }
        if (event.type != KeyEventType.KeyUp || !isShift || hasOtherModifiers) return false

        val now = System.currentTimeMillis()
        val isDouble = now - lastShiftReleaseAtMillis <= DOUBLE_SHIFT_INTERVAL_MILLIS
        lastShiftReleaseAtMillis = if (isDouble) 0L else now
        if (isDouble) {
            sessionScope.launch {
                navigationViewModel.processIntent(NavigationIntent.ShowSearchPopup(SearchMode.ALL))
            }
        }
        return isDouble
    }

    private suspend fun observeGitLineMarkers() {
        val activeDocument =
            editorViewModel.state
                .map { state ->
                    val isDirty = state.tabs.getOrNull(state.activeTabIndex)?.isDirty ?: false
                    state.activeDocumentUri?.value to isDirty
                }.distinctUntilChanged()
        val gitChanges =
            gitViewModel.state
                .map { it.staged to it.unstaged }
                .distinctUntilChanged()

        combine(activeDocument, gitChanges) { document, _ -> document }
            .collect { (uri, isDirty) -> refreshGitLineMarkers(uri, isDirty) }
    }

    private suspend fun refreshGitLineMarkers(
        uri: String?,
        isDirty: Boolean,
    ) {
        if (uri == null || !uri.startsWith("file://")) {
            editorViewModel.dispatch(EditorIntent.SetLineChangeMarkers(emptyMap()))
            return
        }
        // While the document has unsaved edits the disk-based diff is stale;
        // keep the last markers until the next save.
        if (isDirty) return

        val path = uri.removePrefix("file://").removePrefix("$projectPath/")
        val markers =
            gitViewModel
                .lineChanges(path)
                .associate { change -> change.line to change.type.toLineChangeMarker() }
        editorViewModel.dispatch(EditorIntent.SetLineChangeMarkers(markers))
    }

    private fun GitLineChangeType.toLineChangeMarker(): LineChangeMarker =
        when (this) {
            GitLineChangeType.ADDED -> LineChangeMarker.ADDED
            GitLineChangeType.MODIFIED -> LineChangeMarker.MODIFIED
            GitLineChangeType.DELETED -> LineChangeMarker.DELETED
        }

    private suspend fun restoreWorkspaceState() {
        val state = projectDirectoryService.loadWorkspaceState().getOrNull() ?: return
        if (state.openTabs.isEmpty()) return

        val activeTab = state.openTabs.getOrNull(state.activeTabIndex)
        editorViewModel.dispatch(
            EditorIntent.RestoreSession(
                filePaths = state.openTabs.map { tab -> resolveWorkspacePath(tab.filePath) },
                activeTabIndex = state.activeTabIndex,
                cursor =
                    activeTab?.cursor?.let {
                        TextPosition(it.line.coerceAtLeast(0), it.column.coerceAtLeast(0))
                    },
            ),
        )
    }

    private fun resolveWorkspacePath(filePath: String): String =
        if (filePath.startsWith("/")) filePath else "$projectPath/$filePath"

    private suspend fun saveWorkspaceState() {
        val editorState = editorViewModel.state.value
        val existing =
            projectDirectoryService
                .loadWorkspaceState()
                .getOrDefault(WorkspaceState())
        val openTabs =
            editorState.tabs.mapIndexedNotNull { index, tab ->
                val uri = tab.uri.value
                if (!uri.startsWith("file://")) return@mapIndexedNotNull null
                val path = uri.removePrefix("file://")
                val cursor =
                    if (index == editorState.activeTabIndex) {
                        CursorState(
                            line = editorState.cursor.position.line,
                            column = editorState.cursor.position.column,
                        )
                    } else {
                        CursorState()
                    }
                TabState(
                    filePath = path.removePrefix("$projectPath/"),
                    cursor = cursor,
                    isDirty = tab.isDirty,
                )
            }
        projectDirectoryService.saveWorkspaceState(
            existing.copy(
                openTabs = openTabs,
                activeTabIndex = editorState.activeTabIndex,
            ),
        )
    }

    private suspend fun loadKotlinClasspath() {
        gradleImportCoordinator
            .importModel()
            .onSuccess { model ->
                kotlinClasspathResolver = GradleClasspathResolver(projectPath, model)
            }
    }

    /**
     * Reimports the Gradle project model and refreshes module-aware Kotlin classpaths.
     * The previous successful model remains active if synchronization fails.
     */
    public fun syncGradleProject() {
        sessionScope.launch { loadKotlinClasspath() }
    }

    private fun kotlinClasspath(filePath: String?): List<String> {
        val resolver = kotlinClasspathResolver ?: return emptyList()
        return filePath?.let(resolver::classpathFor) ?: resolver.workspaceClasspath()
    }

    /**
     * Shuts down all project-scoped services in order.
     */
    public suspend fun shutdown() {
        gradleExecutionService.cancel()
        saveWorkspaceState()
        pluginManager.shutdown()
        embeddedServerRegistry.shutdownAll()
        kotlinSemanticAnalyzer.dispose()
        languageRegistry.shutdown()
        editorViewModel.dispose()
        commandPaletteViewModel.dispose()
        terminalViewModel.dispose()
        gradleViewModel.dispose()
        agentSessionViewModel.dispose()
        textSearchViewModel.dispose()
        gitViewModel.dispose()
        configurationViewModel.dispose()
        debugViewModel.dispose()
        debugService.dispose()
        embeddedServerRegistry.dispose()
        sessionScope.cancel()
    }

    override fun dispose() {
        debugService.dispose()
        sessionScope.cancel()
    }

    private companion object {
        /** Two Shift releases within this window count as double-Shift (Search Everywhere). */
        const val DOUBLE_SHIFT_INTERVAL_MILLIS = 300L
    }
}
