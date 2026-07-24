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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.gradle.GradleImportCoordinator
import su.kidoz.jetaprog.app.navigation.DefaultNavigationService
import su.kidoz.jetaprog.app.navigation.KotlinIndexNavigationService
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
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
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
import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import su.kidoz.jetaprog.plugins.kotlin.KotlinPlugin
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
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
 * @param gradleTaskRunner Global Gradle task runner instance.
 * @param languageServerManager Global language server manager instance.
 */
public class ProjectSession(
    public val projectPath: String,
    private val fileSystem: FileSystem,
    private val processExecutor: ProcessExecutor,
    private val settingsService: SettingsService,
    private val lintEngine: DefaultLintEngine,
    private val lintProviderRegistry: LintProviderRegistry,
    private val gradleTaskRunner: GradleTaskRunner,
    private val languageServerManager: LanguageServerManager,
) : Disposable {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        )

    /**
     * The navigation view model.
     */
    public val navigationViewModel: NavigationViewModel = NavigationViewModel(navigationService)

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

    private val serviceContainer: ServiceContainer by lazy {
        ServiceContainer(
            workspace = workspaceService,
            editor = EditorServiceImpl(),
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
    public val gradleViewModel: GradleViewModel = GradleViewModel(gradleTaskRunner)

    /**
     * Imports the project structure from Gradle (Tooling API) and reconciles it
     * against `.jetaprog` metadata to surface stale or missing modules.
     */
    public val gradleImportCoordinator: GradleImportCoordinator =
        GradleImportCoordinator(projectPath = projectPath, fileSystem = fileSystem)

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
    private var kotlinClasspath: List<String> = emptyList()

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
            gradleTaskRunner = gradleTaskRunner,
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
        // Load the project lint configuration (.jetaprog/lint.json)
        JvmLintConfigurationStorage()
            .load(projectPath)
            .onSuccess { lintService.setConfiguration(it) }

        // Initialize Gradle with current project
        gradleViewModel.dispatch(GradleIntent.Initialize(projectPath))

        // Initialize configuration manager
        configurationViewModel.dispatch(ConfigurationIntent.Initialize(projectPath))

        // Register bundled plugins (lazy activation handled by LazyPluginActivator)
        pluginManager.registerBundledPlugin(KotlinPlugin(classpathProvider = { kotlinClasspath }))
        pluginManager.registerBundledPlugin(DotNetPlugin())
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
                navState.isUsagesPopupVisible
        if (popupOpen) return false

        if (handleDoubleShift(event)) return true

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
                kotlinClasspath = model.modules.flatMap { it.classpath }.distinct()
            }
    }

    /**
     * Shuts down all project-scoped services in order.
     */
    public suspend fun shutdown() {
        saveWorkspaceState()
        pluginManager.shutdown()
        embeddedServerRegistry.shutdownAll()
        languageRegistry.shutdown()
        editorViewModel.dispose()
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
