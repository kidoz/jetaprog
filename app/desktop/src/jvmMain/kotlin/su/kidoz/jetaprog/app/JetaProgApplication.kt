package su.kidoz.jetaprog.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.app.mcp.registerIdeTools
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.ui.welcome.WelcomeIntent
import su.kidoz.jetaprog.app.ui.welcome.WelcomeViewModel
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
import su.kidoz.jetaprog.settings.recent.RecentProjectsService
import su.kidoz.jetaprog.settings.storage.JvmSettingsStorage
import java.io.File
import java.io.IOException

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
     * IDE-scoped persistence for the Welcome Hub's recent-projects list.
     */
    private val recentProjectsService: RecentProjectsService = RecentProjectsService()

    /**
     * The Welcome Hub view model (global). Shown when no project is open.
     */
    public val welcomeViewModel: WelcomeViewModel = WelcomeViewModel(recentProjectsService)

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

        // Record the project in the recent list and refresh the Welcome Hub.
        recordRecentProject(projectPath)

        // Drop an MCP config so a terminal agent (claude/codex) can reach the IDE.
        writeMcpConfig(projectPath)
    }

    /**
     * Closes the current project (if any) and returns to the Welcome Hub.
     */
    public suspend fun closeProject() {
        _session.value?.shutdown()
        _session.value = null
        welcomeViewModel.dispatch(WelcomeIntent.Refresh)
    }

    /**
     * Initializes the application.
     *
     * Starts the MCP server. No project is opened automatically — the app launches
     * into the Welcome Hub ([session] is `null`) until the user opens a project.
     */
    public suspend fun initialize() {
        registerIdeTools(mcpServer, fileSystem) { _session.value }
        mcpServer.start()
    }

    /**
     * Writes a project-level `.mcp.json` pointing a terminal MCP client (e.g. Claude
     * Code) at the IDE's embedded server. Skips if the project already has one.
     */
    private fun writeMcpConfig(projectPath: String) {
        val endpoint = mcpServer.endpoint ?: return
        val configFile = File(projectPath, ".mcp.json")
        if (configFile.exists()) return
        try {
            configFile.writeText(
                """
                {
                  "mcpServers": {
                    "jetaprog": {
                      "type": "http",
                      "url": "$endpoint"
                    }
                  }
                }
                """.trimIndent() + "\n",
            )
        } catch (exception: IOException) {
            notificationCenter.warning(title = "MCP config not written", message = exception.message)
        }
    }

    /**
     * Shuts down the application.
     */
    public suspend fun shutdown() {
        _session.value?.shutdown()
        mcpServer.stop()
        welcomeViewModel.dispose()
        newProjectViewModel.dispose()
        settingsViewModel.dispose()
    }

    private suspend fun recordRecentProject(projectPath: String) {
        try {
            recentProjectsService.push(projectPath, System.currentTimeMillis())
            welcomeViewModel.dispatch(WelcomeIntent.Refresh)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            warnRecentProjectNotUpdated(exception)
        } catch (exception: RuntimeException) {
            warnRecentProjectNotUpdated(exception)
        }
    }

    private fun warnRecentProjectNotUpdated(exception: Exception) {
        notificationCenter.warning(
            title = "Recent projects were not updated",
            message = exception.message,
        )
    }
}
