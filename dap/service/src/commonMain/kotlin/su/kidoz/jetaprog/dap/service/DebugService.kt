package su.kidoz.jetaprog.dap.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.dap.client.DapClient
import su.kidoz.jetaprog.dap.protocol.InitializeRequestArguments
import su.kidoz.jetaprog.dap.protocol.LaunchRequestArguments
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor

/**
 * Debug adapter configuration.
 */
public data class DebugAdapterConfig(
    /** The command to start the debug adapter. */
    val command: List<String>,
    /** Working directory for the adapter. */
    val workingDirectory: String? = null,
    /** Environment variables. */
    val environment: Map<String, String> = emptyMap(),
    /** Adapter-specific launch arguments. */
    val launchArgs: Map<String, Any> = emptyMap(),
)

/**
 * Service for managing debug sessions.
 */
public class DebugService(
    private val processExecutor: ProcessExecutor,
    private val scope: CoroutineScope,
) : Disposable {
    private val _sessions = MutableStateFlow<Map<String, DebugSession>>(emptyMap())
    private var disposed = false

    /**
     * All active debug sessions.
     */
    public val sessions: StateFlow<Map<String, DebugSession>> = _sessions.asStateFlow()

    /**
     * Start a debug session for a configuration.
     *
     * @param configuration The configuration to debug.
     * @param workspacePath The workspace root path.
     * @return The created debug session.
     */
    public suspend fun startDebugSession(
        configuration: RunConfiguration,
        workspacePath: String,
    ): Result<DebugSession> {
        check(!disposed) { "DebugService has been disposed" }

        val adapterConfig =
            getDebugAdapterConfig(configuration, workspacePath)
                ?: return Result.failure(
                    IllegalArgumentException("No debug adapter available for this configuration type"),
                )

        // Start the debug adapter process
        val processConfig =
            ProcessConfig(
                command = adapterConfig.command,
                workingDirectory = adapterConfig.workingDirectory ?: workspacePath,
                environment = adapterConfig.environment,
            )

        return processExecutor.start(processConfig).mapCatching { process ->
            // Create the DAP client
            // Note: This is a simplified implementation that assumes the process
            // provides stdin/stdout streams. In a real implementation, this would
            // need to properly access the process streams.
            val inputStream = ProcessInputStream(process)
            val outputStream = ProcessOutputStream(process)

            val client = DapClient(inputStream, outputStream, scope)
            client.start()

            val session =
                DebugSession(
                    id = DebugSession.generateId(),
                    configuration = configuration,
                    client = client,
                    scope = scope,
                )

            // Initialize the debug adapter
            val initParams =
                InitializeRequestArguments(
                    clientID = "jetaprog",
                    clientName = "JetaProg IDE",
                    adapterID = getAdapterId(configuration),
                    supportsRunInTerminalRequest = false,
                    supportsProgressReporting = true,
                )

            client.initialize(initParams).getOrThrow()

            // Start event listener
            session.startEventListener()

            // Launch or attach based on configuration
            val launchArgs = buildLaunchArgs(configuration, workspacePath, adapterConfig)
            client.launch(launchArgs).getOrThrow()

            session.setRunning()

            // Store the session
            _sessions.value = _sessions.value + (session.id to session)

            session
        }
    }

    /**
     * Get an active session by ID.
     */
    public fun getSession(sessionId: String): DebugSession? = _sessions.value[sessionId]

    /**
     * Stop a debug session.
     */
    public suspend fun stopSession(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        session.stop()
        session.dispose()
        _sessions.value = _sessions.value - sessionId
    }

    /**
     * Stop all debug sessions.
     */
    public suspend fun stopAllSessions() {
        _sessions.value.keys
            .toList()
            .forEach { stopSession(it) }
    }

    private fun getDebugAdapterConfig(
        configuration: RunConfiguration,
        workspacePath: String,
    ): DebugAdapterConfig? =
        when (configuration.settings) {
            is ConfigurationSettings.Python -> getPythonDebugAdapter(workspacePath)

            is ConfigurationSettings.CargoRun,
            is ConfigurationSettings.CargoBuild,
            is ConfigurationSettings.CargoTest,
            -> getRustDebugAdapter(workspacePath)

            is ConfigurationSettings.Application -> getGenericDebugAdapter(workspacePath)

            // Add more configuration types as needed
            else -> null
        }

    private fun getPythonDebugAdapter(workspacePath: String): DebugAdapterConfig =
        DebugAdapterConfig(
            command = listOf("python", "-m", "debugpy.adapter"),
            workingDirectory = workspacePath,
        )

    private fun getRustDebugAdapter(workspacePath: String): DebugAdapterConfig =
        DebugAdapterConfig(
            command = listOf("codelldb", "--port", "0"),
            workingDirectory = workspacePath,
        )

    private fun getGenericDebugAdapter(workspacePath: String): DebugAdapterConfig =
        // Use LLDB for native applications
        DebugAdapterConfig(
            command = listOf("lldb-vscode"),
            workingDirectory = workspacePath,
        )

    private fun getAdapterId(configuration: RunConfiguration): String =
        when (configuration.settings) {
            is ConfigurationSettings.Python -> "debugpy"

            is ConfigurationSettings.CargoRun,
            is ConfigurationSettings.CargoBuild,
            is ConfigurationSettings.CargoTest,
            -> "codelldb"

            else -> "generic"
        }

    private fun buildLaunchArgs(
        configuration: RunConfiguration,
        workspacePath: String,
        adapterConfig: DebugAdapterConfig,
    ): LaunchRequestArguments {
        val settings = configuration.settings

        return when (settings) {
            is ConfigurationSettings.Python -> {
                LaunchRequestArguments(
                    program = settings.scriptPath,
                    args = settings.scriptArguments,
                    cwd = settings.workingDirectory ?: workspacePath,
                    env = settings.environment,
                    stopOnEntry = false,
                )
            }

            is ConfigurationSettings.CargoRun -> {
                LaunchRequestArguments(
                    program = "$workspacePath/target/debug/${configuration.name}",
                    args = settings.programArguments,
                    cwd = settings.workingDirectory ?: workspacePath,
                    env = settings.environment,
                    stopOnEntry = false,
                )
            }

            is ConfigurationSettings.Application -> {
                LaunchRequestArguments(
                    program = settings.executablePath,
                    args = settings.programArguments,
                    cwd = settings.workingDirectory ?: workspacePath,
                    env = settings.environment,
                    stopOnEntry = false,
                )
            }

            else -> {
                LaunchRequestArguments(
                    stopOnEntry = false,
                )
            }
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        _sessions.value.values.forEach { it.dispose() }
        _sessions.value = emptyMap()
    }
}

/**
 * Wrapper to provide InputStream from a running process.
 */
private class ProcessInputStream(
    private val process: su.kidoz.jetaprog.platform.process.RunningProcess,
) : java.io.InputStream() {
    // This is a placeholder - actual implementation would need to properly
    // access the process stdout stream
    override fun read(): Int = -1
}

/**
 * Wrapper to provide OutputStream to a running process.
 */
private class ProcessOutputStream(
    private val process: su.kidoz.jetaprog.platform.process.RunningProcess,
) : java.io.OutputStream() {
    // This is a placeholder - actual implementation would need to properly
    // access the process stdin stream
    override fun write(b: Int) {}
}
