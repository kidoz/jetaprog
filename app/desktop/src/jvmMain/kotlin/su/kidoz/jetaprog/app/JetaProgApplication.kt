package su.kidoz.jetaprog.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.viewmodel.NewProjectViewModel
import su.kidoz.jetaprog.app.viewmodel.SettingsViewModel
import su.kidoz.jetaprog.build.gradle.JvmGradleTaskRunner
import su.kidoz.jetaprog.lint.engine.DefaultLintEngine
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.platform.JvmPlatform
import su.kidoz.jetaprog.platform.Platform
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.support.LanguageServerManager
import su.kidoz.jetaprog.settings.DefaultSettingsService
import su.kidoz.jetaprog.settings.storage.JvmSettingsStorage

/**
 * Main application class for JetaProg IDE.
 *
 * Holds global (application-scoped) services and manages the current [ProjectSession].
 * When the user opens a different project, the old session is shut down and a new one
 * is created via [openProject].
 */
public class JetaProgApplication {
    /**
     * The platform instance.
     */
    public val platform: Platform = JvmPlatform()

    /**
     * The file system instance.
     */
    public val fileSystem: JvmFileSystem = JvmFileSystem()

    /**
     * The process executor instance.
     */
    public val processExecutor: JvmProcessExecutor = JvmProcessExecutor()

    /**
     * The embedded MCP server.
     */
    public val mcpServer: EmbeddedMcpServer = EmbeddedMcpServer(McpServerConfig())

    /**
     * The language server manager for LSP servers.
     */
    private val languageServerManager: LanguageServerManager = LanguageServerManager()

    /**
     * The lint engine.
     */
    private val lintEngine: DefaultLintEngine = DefaultLintEngine()

    /**
     * The lint provider registry.
     */
    private val lintProviderRegistry: LintProviderRegistry = LintProviderRegistry(lintEngine)

    /**
     * The Gradle task runner.
     */
    private val gradleTaskRunner: JvmGradleTaskRunner = JvmGradleTaskRunner(processExecutor)

    /**
     * The settings storage.
     */
    private val settingsStorage: JvmSettingsStorage = JvmSettingsStorage()

    /**
     * The settings service.
     */
    private val settingsService: DefaultSettingsService = DefaultSettingsService(settingsStorage)

    /**
     * The settings view model (global).
     */
    public val settingsViewModel: SettingsViewModel = SettingsViewModel(settingsService)

    /**
     * The new project view model (global).
     */
    public val newProjectViewModel: NewProjectViewModel = NewProjectViewModel(fileSystem)

    /**
     * Process-wide notification hub. ViewModels push toasts here; the UI
     * overlay subscribes for rendering.
     */
    public val notificationCenter: NotificationCenter = NotificationCenter()

    // ========================================================================
    // Project Session
    // ========================================================================

    private val _session = MutableStateFlow<ProjectSession?>(null)

    /**
     * The current project session, or null if no project is open.
     * Observe this to react to project switches.
     */
    public val session: StateFlow<ProjectSession?> = _session.asStateFlow()

    /**
     * Opens a project at the given path.
     *
     * Shuts down the current session (if any) and creates a new [ProjectSession]
     * with all project-scoped services initialized for the new path.
     *
     * @param projectPath The root path of the project to open.
     */
    public suspend fun openProject(projectPath: String) {
        // Shut down the old session
        _session.value?.shutdown()

        // Create and initialize a new session
        val newSession =
            ProjectSession(
                projectPath = projectPath,
                fileSystem = fileSystem,
                processExecutor = processExecutor,
                settingsService = settingsService,
                lintEngine = lintEngine,
                lintProviderRegistry = lintProviderRegistry,
                gradleTaskRunner = gradleTaskRunner,
                languageServerManager = languageServerManager,
            )
        _session.value = newSession
        newSession.initialize()
    }

    /**
     * Initializes the application.
     * Starts the MCP server and opens the default project.
     */
    public suspend fun initialize() {
        mcpServer.start()
        openProject(System.getProperty("user.dir"))
    }

    /**
     * Shuts down the application.
     */
    public suspend fun shutdown() {
        _session.value?.shutdown()
        mcpServer.stop()
        newProjectViewModel.dispose()
        settingsViewModel.dispose()
    }
}
