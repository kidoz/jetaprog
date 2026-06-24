package su.kidoz.jetaprog.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.gradle.GradleImportCoordinator
import su.kidoz.jetaprog.app.navigation.DefaultNavigationService
import su.kidoz.jetaprog.app.ui.navigation.NavigationViewModel
import su.kidoz.jetaprog.app.viewmodel.AgentSessionViewModel
import su.kidoz.jetaprog.app.viewmodel.ConfigurationViewModel
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.app.viewmodel.GradleViewModel
import su.kidoz.jetaprog.app.viewmodel.TerminalViewModel
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationManager
import su.kidoz.jetaprog.configuration.JvmConfigurationStorage
import su.kidoz.jetaprog.configuration.discovery.ConfigurationDiscovery
import su.kidoz.jetaprog.configuration.discovery.ProjectDetector
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.lint.engine.DefaultLintEngine
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.lsp.server.DefaultServerRegistry
import su.kidoz.jetaprog.lsp.server.EmbeddedServerConfig
import su.kidoz.jetaprog.lsp.server.EmbeddedServerRegistry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import su.kidoz.jetaprog.plugins.kotlin.KotlinPlugin
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
import su.kidoz.jetaprog.settings.SettingsService

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
     * The navigation service for code navigation features.
     */
    public val navigationService: NavigationService =
        DefaultNavigationService(
            lspClient = null,
            fileSystem = fileSystem,
            embeddedServerRegistry = embeddedServerRegistry,
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

    private val serviceContainer: ServiceContainer by lazy {
        ServiceContainer(
            workspace = workspaceService,
            editor = EditorServiceImpl(),
            languages = LanguageServiceImpl(languageRegistry, languageServerManager, projectPath),
            commands = commandService,
            notifications = NotificationServiceImpl(),
            terminal = TerminalServiceImpl(processExecutor, projectPath),
            lint = LintServiceImpl(lintEngine, lintProviderRegistry),
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
            defaultAgentCommand = AgentSessionViewModel.DEFAULT_CLAUDE_CODE_COMMAND,
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
        debugService.dispose()
        embeddedServerRegistry.dispose()
        sessionScope.cancel()
    }

    override fun dispose() {
        debugService.dispose()
        sessionScope.cancel()
    }
}
