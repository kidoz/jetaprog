package su.kidoz.jetaprog.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import su.kidoz.jetaprog.app.agent.AgentPrefsStore
import su.kidoz.jetaprog.app.keymap.KeymapManager
import su.kidoz.jetaprog.app.mcp.McpActionAuthorizer
import su.kidoz.jetaprog.app.mcp.McpApprovalRequests
import su.kidoz.jetaprog.app.mcp.McpSensitiveAction
import su.kidoz.jetaprog.app.mcp.registerIdeTools
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.plugin.PluginDialogRequests
import su.kidoz.jetaprog.app.ui.dialogs.filepicker.FilePickerViewModel
import su.kidoz.jetaprog.app.ui.welcome.WelcomeIntent
import su.kidoz.jetaprog.app.ui.welcome.WelcomeViewModel
import su.kidoz.jetaprog.app.viewmodel.CloneRepositoryViewModel
import su.kidoz.jetaprog.app.viewmodel.IdeMcpEndpoint
import su.kidoz.jetaprog.app.viewmodel.NewProjectViewModel
import su.kidoz.jetaprog.app.viewmodel.SettingsViewModel
import su.kidoz.jetaprog.database.JvmDatabaseProfileStore
import su.kidoz.jetaprog.database.SessionDatabaseCredentialStore
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.lint.engine.DefaultLintEngine
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.SecurityConfig
import su.kidoz.jetaprog.platform.JvmPlatform
import su.kidoz.jetaprog.platform.Platform
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.support.LanguageServerManager
import su.kidoz.jetaprog.settings.DefaultSettingsService
import su.kidoz.jetaprog.settings.SettingsScope
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.recent.RecentProjectsService
import su.kidoz.jetaprog.settings.storage.JvmSettingsStorage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

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
    public val mcpServer: EmbeddedMcpServer =
        EmbeddedMcpServer(
            McpServerConfig(
                // The endpoint is loopback-bound but still reachable by every local
                // process, so require a token minted fresh for this IDE run.
                security =
                    SecurityConfig(
                        authenticationEnabled = true,
                        authToken = UUID.randomUUID().toString(),
                    ),
            ),
        )

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
     * The settings storage.
     */
    private val settingsStorage: JvmSettingsStorage = JvmSettingsStorage()

    /** Password-free database profile persistence under the IDE configuration directory. */
    private val databaseProfileStore: JvmDatabaseProfileStore =
        JvmDatabaseProfileStore(
            File(settingsStorage.getPath(SettingsScope.IDE)).resolveSibling("database-connections.json"),
        )

    /** Process-only database passwords, deliberately never written to disk. */
    private val databaseCredentialStore: SessionDatabaseCredentialStore = SessionDatabaseCredentialStore()

    /**
     * The settings service.
     */
    private val settingsService: DefaultSettingsService = DefaultSettingsService(settingsStorage)

    /**
     * The application-wide keymap. Custom overrides are re-applied whenever
     * the user edits shortcuts in Settings → Keymap (or the settings file).
     */
    public val keymapManager: KeymapManager = KeymapManager()

    /** Long-lived scope for application-level observers (settings → keymap). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The appearance settings stream, used to drive the IDE color theme.
     */
    public val appearanceSettings: Flow<AppearanceSettings> = settingsService.appearance

    /**
     * The settings view model (global).
     */
    public val settingsViewModel: SettingsViewModel = SettingsViewModel(settingsService)

    /**
     * The clone repository view model (global).
     */
    public val cloneRepositoryViewModel: CloneRepositoryViewModel =
        CloneRepositoryViewModel(processExecutor)

    /**
     * The new project view model (global).
     */
    public val newProjectViewModel: NewProjectViewModel = NewProjectViewModel(fileSystem, processExecutor)

    /**
     * The IDE's own file picker, shared by every browse action (open project,
     * open file, save as, project location, clone destination).
     */
    public val filePickerViewModel: FilePickerViewModel = FilePickerViewModel(fileSystem)

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

    /**
     * Queue of pending plugin modal requests (input box, quick pick),
     * rendered by the dialog host in the main shell.
     */
    public val pluginDialogRequests: PluginDialogRequests = PluginDialogRequests()

    /**
     * Pending Allow / Deny prompts for MCP tools that write, build, or control the debugger.
     */
    public val mcpApprovals: McpApprovalRequests = McpApprovalRequests()

    // ========================================================================
    // Project Session
    // ========================================================================

    private val _session = MutableStateFlow<ProjectSession?>(null)

    /**
     * The current project session, or null if no project is open.
     * Observe this to react to project switches.
     */
    public val session: StateFlow<ProjectSession?> = _session.asStateFlow()

    private val projectTransitions =
        ProjectTransitionGuard(
            hasUnsavedChanges = {
                _session.value
                    ?.editorViewModel
                    ?.state
                    ?.value
                    ?.hasUnsavedChanges == true
            },
            saveAll = {
                val currentSession = _session.value ?: return@ProjectTransitionGuard false
                val savedState = currentSession.editorViewModel.perform(EditorIntent.SaveAll)
                _session.value === currentSession && !savedState.hasUnsavedChanges
            },
            openProjectAction = ::replaceProject,
            closeProjectAction = ::closeCurrentProject,
        )

    internal val pendingProjectTransition: StateFlow<PendingProjectTransition?> = projectTransitions.pending

    /**
     * Opens a project at the given path.
     *
     * Prompts before replacing a session with unsaved changes. Once the transition is
     * allowed, shuts down the current session (if any) and initializes the new path.
     *
     * @param projectPath The root path of the project to open.
     */
    public suspend fun openProject(projectPath: String) {
        projectTransitions.openProject(projectPath)
    }

    private suspend fun replaceProject(projectPath: String) {
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
                languageServerManager = languageServerManager,
                databaseProfileStore = databaseProfileStore,
                databaseCredentialStore = databaseCredentialStore,
                notificationCenter = notificationCenter,
                pluginDialogRequests = pluginDialogRequests,
                keymapManager = keymapManager,
                ideMcpEndpoint = {
                    mcpServer.endpoint?.let { IdeMcpEndpoint(url = it, authToken = mcpServer.authToken) }
                },
            )
        _session.value = newSession
        newSession.initialize()

        // Record the project in the recent list and refresh the Welcome Hub.
        recordRecentProject(projectPath)

        // Drop an MCP config so a terminal agent (claude/codex) can reach the IDE.
        writeMcpConfig(projectPath)
    }

    /**
     * Prompts for unsaved changes, then closes the project and returns to the Welcome Hub.
     */
    public suspend fun closeProject() {
        projectTransitions.closeProject()
    }

    private suspend fun closeCurrentProject() {
        _session.value?.shutdown()
        _session.value = null
        welcomeViewModel.dispatch(WelcomeIntent.Refresh)
    }

    internal fun requestExit(onExit: () -> Unit) {
        projectTransitions.requestExit(onExit)
    }

    internal fun cancelPendingProjectTransition() {
        projectTransitions.cancelPending()
    }

    internal suspend fun discardPendingProjectTransition(): PendingProjectTransition? =
        projectTransitions.discardPending()

    internal suspend fun saveAllAndResumePendingProjectTransition(): PendingProjectTransition? =
        projectTransitions.saveAllAndResumePending()

    /**
     * Initializes the application.
     *
     * Starts the MCP server. No project is opened automatically — the app launches
     * into the Welcome Hub ([session] is `null`) until the user opens a project.
     */
    public suspend fun initialize() {
        // Seed custom shortcuts from the persisted settings and keep them in
        // sync when the settings change at runtime.
        keymapManager.applyFromSettings(settingsService.getCurrentSettings().keymap)
        appScope.launch {
            settingsService.settings.collect { settings -> keymapManager.applyFromSettings(settings.keymap) }
        }

        registerIdeTools(
            server = mcpServer,
            fileSystem = fileSystem,
            currentSession = { _session.value },
            onDestructiveTool = { message ->
                notificationCenter.info(title = "MCP agent action", message = message)
            },
            approve = ::approveMcpAction,
        )
        mcpServer.start()
    }

    /**
     * Writes a project-level `.mcp.json` pointing a terminal MCP client (e.g. Claude
     * Code) at the IDE's embedded server, including the bearer token for this run.
     */
    private suspend fun approveMcpAction(
        action: McpSensitiveAction,
        description: String,
    ): Boolean {
        val session = _session.value ?: return false
        return McpActionAuthorizer(
            permissions = { AgentPrefsStore(fileSystem, session.projectPath).load().permissions },
            ask = { message -> mcpApprovals.confirm(title = "Allow MCP action?", message = message) },
        ).allow(action, description)
    }

    private fun writeMcpConfig(projectPath: String) {
        val endpoint = mcpServer.endpoint ?: return
        // The token changes every run, so refresh the file instead of skipping it.
        try {
            writeProjectMcpConfig(projectPath, endpoint, mcpServer.authToken)
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
        filePickerViewModel.dispose()
        settingsViewModel.dispose()
        databaseCredentialStore.dispose()
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

internal sealed interface PendingProjectTransition {
    public data class OpenProject(
        val projectPath: String,
    ) : PendingProjectTransition

    public data object CloseProject : PendingProjectTransition

    public data object Exit : PendingProjectTransition
}

internal class ProjectTransitionGuard(
    private val hasUnsavedChanges: () -> Boolean,
    private val saveAll: suspend () -> Boolean,
    private val openProjectAction: suspend (String) -> Unit,
    private val closeProjectAction: suspend () -> Unit,
) {
    private val _pending = MutableStateFlow<PendingProjectTransition?>(null)
    public val pending: StateFlow<PendingProjectTransition?> = _pending.asStateFlow()

    public suspend fun openProject(projectPath: String) {
        request(PendingProjectTransition.OpenProject(projectPath))
    }

    public suspend fun closeProject() {
        request(PendingProjectTransition.CloseProject)
    }

    public fun requestExit(onExit: () -> Unit) {
        if (_pending.value != null) return
        if (hasUnsavedChanges()) {
            _pending.compareAndSet(null, PendingProjectTransition.Exit)
        } else {
            onExit()
        }
    }

    public fun cancelPending() {
        _pending.value = null
    }

    public suspend fun discardPending(): PendingProjectTransition? {
        val transition = takePending() ?: return null
        return resume(transition)
    }

    public suspend fun saveAllAndResumePending(): PendingProjectTransition? {
        val transition = takePending() ?: return null
        if (!saveAll()) return null
        return resume(transition)
    }

    private fun takePending(): PendingProjectTransition? {
        while (true) {
            val pending = _pending.value ?: return null
            if (_pending.compareAndSet(pending, null)) return pending
        }
    }

    private suspend fun request(transition: PendingProjectTransition) {
        if (_pending.value != null) return
        if (hasUnsavedChanges()) {
            _pending.compareAndSet(null, transition)
        } else {
            resume(transition)
        }
    }

    private suspend fun resume(transition: PendingProjectTransition): PendingProjectTransition? =
        when (transition) {
            is PendingProjectTransition.OpenProject -> {
                openProjectAction(transition.projectPath)
                null
            }

            PendingProjectTransition.CloseProject -> {
                closeProjectAction()
                null
            }

            PendingProjectTransition.Exit -> {
                transition
            }
        }
}

/**
 * Renders the `.mcp.json` an external MCP client uses to reach the embedded server.
 */
internal fun mcpClientConfigJson(
    endpoint: String,
    authToken: String?,
): String {
    val document =
        buildJsonObject {
            putJsonObject("mcpServers") {
                putJsonObject("jetaprog") {
                    put("type", "http")
                    put("url", endpoint)
                    if (authToken != null) {
                        putJsonObject("headers") {
                            put("Authorization", "Bearer $authToken")
                        }
                    }
                }
            }
        }
    return MCP_CONFIG_JSON.encodeToString(JsonObject.serializer(), document) + "\n"
}

private val MCP_CONFIG_JSON = Json { prettyPrint = true }

private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

/**
 * Writes the project `.mcp.json` so only the owner can read the bearer token,
 * and adds the file to the repository's local git exclude when the project is a repo.
 */
internal fun writeProjectMcpConfig(
    projectPath: String,
    endpoint: String,
    authToken: String?,
) {
    val projectDir = File(projectPath)
    val configFile = File(projectDir, ".mcp.json")
    writeOwnerOnly(configFile.toPath(), mcpClientConfigJson(endpoint, authToken))
    excludeFromLocalGit(projectDir, ".mcp.json")
}

private fun writeOwnerOnly(
    path: Path,
    text: String,
) {
    path.parent?.let { Files.createDirectories(it) }
    Files.writeString(
        path,
        text,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    )
    runCatching { Files.setPosixFilePermissions(path, OWNER_ONLY) }
}

private fun excludeFromLocalGit(
    projectDir: File,
    relativePath: String,
) {
    val gitDir = resolveGitDir(projectDir) ?: return
    val info = File(gitDir, "info")
    if (!info.isDirectory && !info.mkdirs()) return
    val exclude = File(info, "exclude")
    val existing = if (exclude.isFile) exclude.readText() else ""
    if (existing.lineSequence().any { it.trim() == relativePath }) return
    val separator = if (existing.isNotEmpty() && !existing.endsWith("\n")) "\n" else ""
    exclude.appendText("$separator$relativePath\n")
}

private fun resolveGitDir(projectDir: File): File? {
    val dotGit = File(projectDir, ".git")
    if (dotGit.isDirectory) return dotGit
    if (!dotGit.isFile) return null
    val gitdir =
        dotGit
            .readLines()
            .firstOrNull { it.startsWith("gitdir:") }
            ?.removePrefix("gitdir:")
            ?.trim()
            ?: return null
    val resolved = File(gitdir).let { if (it.isAbsolute) it else File(projectDir, gitdir) }
    return resolved.takeIf { it.isDirectory }
}
