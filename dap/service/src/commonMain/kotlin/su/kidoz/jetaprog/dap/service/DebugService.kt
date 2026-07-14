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
import java.io.File

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
            val inputStream =
                process.inputStream
                    ?: error("Debug adapter process does not expose stdout stream")
            val outputStream =
                process.outputStream
                    ?: error("Debug adapter process does not expose stdin stream")

            val client = DapClient(inputStream, outputStream, scope)
            client.start()

            val session =
                DebugSession(
                    id = DebugSession.generateId(),
                    configuration = configuration,
                    client = client,
                    scope = scope,
                    adapterProcess = process,
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
            val launchArgs = buildLaunchArgs(configuration, workspacePath).getOrThrow()
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
        when (val settings = configuration.settings) {
            is ConfigurationSettings.Python -> getPythonDebugAdapter(workspacePath)

            is ConfigurationSettings.CargoRun,
            is ConfigurationSettings.CargoBuild,
            is ConfigurationSettings.CargoTest,
            -> getRustDebugAdapter(workspacePath)

            is ConfigurationSettings.Gradle -> getJvmDebugAdapter(workspacePath)

            is ConfigurationSettings.Application -> getGenericDebugAdapter(workspacePath)

            is ConfigurationSettings.DotNetDebug -> getDotNetDebugAdapter(settings, workspacePath)

            else -> null
        }

    /**
     * The bundled JDI-based adapter, launched as a child JVM reusing this
     * process's runtime and classpath.
     */
    private fun getJvmDebugAdapter(workspacePath: String): DebugAdapterConfig {
        val javaBinary = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        return DebugAdapterConfig(
            command =
                listOf(
                    javaBinary,
                    "-cp",
                    System.getProperty("java.class.path"),
                    JVM_ADAPTER_MAIN_CLASS,
                ),
            workingDirectory = workspacePath,
        )
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

    private fun getDotNetDebugAdapter(
        settings: ConfigurationSettings.DotNetDebug,
        workspacePath: String,
    ): DebugAdapterConfig =
        DebugAdapterConfig(
            command = listOf(settings.adapterCommand) + settings.adapterArguments,
            workingDirectory = settings.workingDirectory ?: workspacePath,
        )

    private fun getAdapterId(configuration: RunConfiguration): String =
        when (configuration.settings) {
            is ConfigurationSettings.Gradle -> "jetaprog-jvm"

            is ConfigurationSettings.Python -> "debugpy"

            is ConfigurationSettings.CargoRun,
            is ConfigurationSettings.CargoBuild,
            is ConfigurationSettings.CargoTest,
            -> "codelldb"

            is ConfigurationSettings.DotNetDebug -> "coreclr"

            else -> "generic"
        }

    private fun buildLaunchArgs(
        configuration: RunConfiguration,
        workspacePath: String,
    ): Result<LaunchRequestArguments> {
        val settings = configuration.settings

        return when (settings) {
            is ConfigurationSettings.Gradle -> {
                buildGradleLaunchArgs(settings, workspacePath)
            }

            is ConfigurationSettings.Python -> {
                Result.success(
                    LaunchRequestArguments(
                        program = settings.scriptPath,
                        args = settings.scriptArguments,
                        cwd = settings.workingDirectory ?: workspacePath,
                        env = settings.environment,
                        stopOnEntry = false,
                    ),
                )
            }

            is ConfigurationSettings.CargoRun -> {
                Result.success(
                    LaunchRequestArguments(
                        program = "$workspacePath/target/debug/${configuration.name}",
                        args = settings.programArguments,
                        cwd = settings.workingDirectory ?: workspacePath,
                        env = settings.environment,
                        stopOnEntry = false,
                    ),
                )
            }

            is ConfigurationSettings.Application -> {
                Result.success(
                    LaunchRequestArguments(
                        program = settings.executablePath,
                        args = settings.programArguments,
                        cwd = settings.workingDirectory ?: workspacePath,
                        env = settings.environment,
                        stopOnEntry = false,
                    ),
                )
            }

            is ConfigurationSettings.DotNetDebug -> {
                buildDotNetLaunchArgs(settings, workspacePath)
            }

            else -> {
                Result.success(
                    LaunchRequestArguments(
                        stopOnEntry = false,
                    ),
                )
            }
        }
    }

    /**
     * Launches the Gradle task with `--debug-jvm` so the forked JVM waits on
     * the default JDWP port, then lets the adapter attach to it.
     */
    private fun buildGradleLaunchArgs(
        settings: ConfigurationSettings.Gradle,
        workspacePath: String,
    ): Result<LaunchRequestArguments> {
        val wrapper = File(workspacePath, "gradlew")
        val gradleCommand = if (wrapper.canExecute()) wrapper.absolutePath else "gradle"
        return Result.success(
            LaunchRequestArguments(
                program = gradleCommand,
                args =
                    listOf(settings.taskPath, "--debug-jvm") +
                        settings.arguments +
                        settings.jvmArguments.map { "-D$it" },
                cwd = workspacePath,
                attachPort = GRADLE_JDWP_PORT,
                attachHost = "127.0.0.1",
                attachTimeoutMs = GRADLE_ATTACH_TIMEOUT_MS,
                sourceRoots = discoverSourceRoots(workspacePath),
            ),
        )
    }

    /**
     * Collects conventional JVM source roots (`src/<sourceSet>/kotlin|java`)
     * so the adapter can map compiled locations back to files.
     */
    private fun discoverSourceRoots(workspacePath: String): List<String> {
        val root = File(workspacePath)
        return root
            .walkTopDown()
            .onEnter { directory ->
                directory == root ||
                    (directory.name !in EXCLUDED_DIRECTORIES && !directory.name.startsWith("."))
            }.maxDepth(SOURCE_ROOT_SCAN_DEPTH)
            .filter { it.isDirectory && it.name in SOURCE_ROOT_NAMES && it.parentFile?.parentFile?.name == "src" }
            .map { it.absolutePath }
            .toList()
    }

    private fun buildDotNetLaunchArgs(
        settings: ConfigurationSettings.DotNetDebug,
        workspacePath: String,
    ): Result<LaunchRequestArguments> {
        val cwd = settings.workingDirectory ?: workspacePath
        val program =
            settings.programPath?.takeIf { it.isNotBlank() }
                ?: inferDotNetProgramPath(settings)
                ?: return Result.failure(
                    IllegalArgumentException(
                        ".NET debug requires a program path or a project path with a target framework",
                    ),
                )

        return Result.success(
            LaunchRequestArguments(
                program = program,
                args = settings.programArguments,
                cwd = cwd,
                env = settings.environment,
                stopOnEntry = settings.stopAtEntry,
                stopAtEntry = settings.stopAtEntry,
                console = "internalConsole",
            ),
        )
    }

    private fun inferDotNetProgramPath(settings: ConfigurationSettings.DotNetDebug): String? {
        val projectPath = settings.projectPath?.takeIf { it.isNotBlank() } ?: return null
        val projectFile = File(projectPath)
        val targetFramework =
            settings.targetFramework?.takeIf { it.isNotBlank() }
                ?: readTargetFramework(projectFile)
                ?: return null
        val outputDirectory =
            projectFile.parentFile
                ?: return null

        return File(
            outputDirectory,
            "bin/${settings.configuration.value}/$targetFramework/${projectFile.nameWithoutExtension}.dll",
        ).path
    }

    private fun readTargetFramework(projectFile: File): String? =
        runCatching {
            val content = projectFile.readText()
            targetFrameworkRegex.find(content)?.groupValues?.get(1)
                ?: targetFrameworksRegex
                    .find(content)
                    ?.groupValues
                    ?.get(1)
                    ?.substringBefore(";")
        }.getOrNull()

    override fun dispose() {
        if (disposed) return
        disposed = true

        _sessions.value.values.forEach { it.dispose() }
        _sessions.value = emptyMap()
    }

    private companion object {
        private val targetFrameworkRegex = Regex("<TargetFramework>\\s*([^<\\s]+)\\s*</TargetFramework>")
        private val targetFrameworksRegex = Regex("<TargetFrameworks>\\s*([^<\\s]+)\\s*</TargetFrameworks>")

        private const val JVM_ADAPTER_MAIN_CLASS = "su.kidoz.jetaprog.dap.jvm.JvmDebugAdapterMainKt"

        /** Default port used by Gradle's `--debug-jvm`. */
        private const val GRADLE_JDWP_PORT = 5005

        /** Gradle may compile before the debugged JVM starts listening. */
        private const val GRADLE_ATTACH_TIMEOUT_MS = 300_000L

        private const val SOURCE_ROOT_SCAN_DEPTH = 5
        private val SOURCE_ROOT_NAMES = setOf("kotlin", "java")
        private val EXCLUDED_DIRECTORIES = setOf("build", "node_modules", "out", "target")
    }
}
