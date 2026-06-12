package su.kidoz.jetaprog.build.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.DeploymentSource
import su.kidoz.jetaprog.configuration.TomcatLocalSettings
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.File

/**
 * State of a Tomcat instance.
 */
public enum class TomcatState {
    /** Server is stopped. */
    STOPPED,

    /** Server is starting. */
    STARTING,

    /** Server is running. */
    RUNNING,

    /** Server is stopping. */
    STOPPING,

    /** Server encountered an error. */
    ERROR,
}

/**
 * Represents a running Tomcat instance.
 */
public class TomcatInstance(
    /** Unique instance ID. */
    public val id: String,
    /** The configuration used to start this instance. */
    public val settings: TomcatLocalSettings,
    private val process: RunningProcess,
    private val scope: CoroutineScope,
) : Disposable {
    private val _state = MutableStateFlow(TomcatState.STARTING)
    private val _logs = MutableStateFlow<List<String>>(emptyList())

    private var logCollectorJob: Job? = null

    /**
     * Current state of the instance.
     */
    public val state: StateFlow<TomcatState> = _state.asStateFlow()

    /**
     * Server logs.
     */
    public val logs: StateFlow<List<String>> = _logs.asStateFlow()

    /**
     * Flow of log output.
     */
    public val output: Flow<ProcessOutput> = process.output

    /**
     * Start collecting logs.
     */
    internal fun startLogCollection() {
        logCollectorJob =
            scope.launch {
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            _logs.value = _logs.value + output.line

                            // Detect startup
                            if (output.line.contains("Server startup in")) {
                                _state.value = TomcatState.RUNNING
                            }
                        }

                        is ProcessOutput.Stderr -> {
                            _logs.value = _logs.value + "[ERROR] ${output.line}"
                        }

                        is ProcessOutput.Exited -> {
                            _state.value =
                                if (output.exitCode == 0) {
                                    TomcatState.STOPPED
                                } else {
                                    TomcatState.ERROR
                                }
                        }
                    }
                }
            }
    }

    /**
     * Stop the Tomcat instance.
     */
    public fun stop() {
        _state.value = TomcatState.STOPPING
        process.kill()
    }

    /**
     * Check if the server is running.
     */
    public val isRunning: Boolean
        get() = process.isAlive && _state.value == TomcatState.RUNNING

    override fun dispose() {
        logCollectorJob?.cancel()
        if (process.isAlive) {
            process.kill()
        }
    }
}

/**
 * Manages local Tomcat server instances.
 */
public class TomcatManager(
    private val processExecutor: ProcessExecutor,
    private val scope: CoroutineScope,
) : Disposable {
    private val instances = mutableMapOf<String, TomcatInstance>()
    private var disposed = false

    /**
     * Start a Tomcat instance.
     *
     * @param settings The Tomcat configuration.
     * @param workspacePath The workspace path.
     * @return The started Tomcat instance.
     */
    public suspend fun start(
        settings: TomcatLocalSettings,
        workspacePath: String,
    ): Result<TomcatInstance> {
        check(!disposed) { "TomcatManager has been disposed" }

        val tomcatHome = File(settings.tomcatHome)
        if (!tomcatHome.exists()) {
            return Result.failure(
                IllegalArgumentException("Tomcat home does not exist: ${settings.tomcatHome}"),
            )
        }

        val catalinaScript = File(tomcatHome, "bin/catalina.sh")
        if (!catalinaScript.exists()) {
            return Result.failure(
                IllegalArgumentException("catalina.sh not found in Tomcat home"),
            )
        }

        // Build environment variables
        val environment =
            buildMap {
                put("CATALINA_HOME", settings.tomcatHome)
                put("CATALINA_BASE", settings.tomcatHome)

                if (settings.jvmOptions.isNotBlank()) {
                    put("CATALINA_OPTS", settings.jvmOptions)
                }

                // Set ports via system properties
                val javaOpts =
                    buildList {
                        add("-Dhttp.port=${settings.httpPort}")
                        if (settings.httpsPort > 0) add("-Dhttps.port=${settings.httpsPort}")
                        if (settings.ajpPort > 0) add("-Dajp.port=${settings.ajpPort}")
                        if (settings.jmxPort > 0) {
                            add("-Dcom.sun.management.jmxremote.port=${settings.jmxPort}")
                            add("-Dcom.sun.management.jmxremote.authenticate=false")
                            add("-Dcom.sun.management.jmxremote.ssl=false")
                        }
                    }.joinToString(" ")

                if (javaOpts.isNotBlank()) {
                    put("JAVA_OPTS", javaOpts)
                }

                putAll(settings.environment)
            }

        val config =
            ProcessConfig(
                command = listOf(catalinaScript.absolutePath, "run"),
                workingDirectory = settings.tomcatHome,
                environment = environment,
            )

        return processExecutor.start(config).mapCatching { process ->
            val instance =
                TomcatInstance(
                    id = generateInstanceId(),
                    settings = settings,
                    process = process,
                    scope = scope,
                )

            instance.startLogCollection()
            instances[instance.id] = instance

            // Deploy application if needed
            if (settings.deployOnStartup) {
                scope.launch {
                    // Wait for server to be ready
                    waitForServerReady(instance)

                    // Deploy the application
                    deploy(instance, settings.deploymentSource)
                }
            }

            instance
        }
    }

    /**
     * Stop a Tomcat instance.
     */
    public suspend fun stop(instance: TomcatInstance) {
        instance.stop()
        instances.remove(instance.id)
    }

    /**
     * Deploy an application to a running Tomcat instance.
     */
    public suspend fun deploy(
        instance: TomcatInstance,
        source: DeploymentSource,
    ): Result<Unit> {
        val webappsDir = File(instance.settings.tomcatHome, "webapps")
        if (!webappsDir.exists()) {
            return Result.failure(
                IllegalStateException("webapps directory not found"),
            )
        }

        return when (source) {
            is DeploymentSource.WarFile -> deployWarFile(webappsDir, source)
            is DeploymentSource.ExplodedWar -> deployExplodedWar(webappsDir, source, instance)
            is DeploymentSource.GradleArtifact -> deployGradleArtifact(webappsDir, source)
            is DeploymentSource.MavenArtifact -> deployMavenArtifact(webappsDir, source)
        }
    }

    /**
     * Undeploy an application from a Tomcat instance.
     */
    public suspend fun undeploy(
        instance: TomcatInstance,
        contextPath: String,
    ): Result<Unit> {
        val webappsDir = File(instance.settings.tomcatHome, "webapps")
        val appName = contextPath.removePrefix("/").ifEmpty { "ROOT" }
        val warFile = File(webappsDir, "$appName.war")
        val appDir = File(webappsDir, appName)

        return runCatching {
            if (warFile.exists()) warFile.delete()
            if (appDir.exists()) appDir.deleteRecursively()
        }
    }

    /**
     * Get all running instances.
     */
    public fun getInstances(): List<TomcatInstance> = instances.values.toList()

    private suspend fun waitForServerReady(
        instance: TomcatInstance,
        timeoutMs: Long = 60_000,
    ) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (instance.state.value == TomcatState.RUNNING) {
                return
            }
            if (instance.state.value == TomcatState.ERROR) {
                return
            }
            delay(500)
        }
    }

    private fun deployWarFile(
        webappsDir: File,
        source: DeploymentSource.WarFile,
    ): Result<Unit> =
        runCatching {
            val sourceFile = File(source.path)
            if (!sourceFile.exists()) {
                throw IllegalArgumentException("WAR file not found: ${source.path}")
            }
            sourceFile.copyTo(File(webappsDir, sourceFile.name), overwrite = true)
        }

    private fun deployExplodedWar(
        webappsDir: File,
        source: DeploymentSource.ExplodedWar,
        instance: TomcatInstance,
    ): Result<Unit> =
        runCatching {
            val sourceDir = File(source.path)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                throw IllegalArgumentException("Exploded WAR directory not found: ${source.path}")
            }

            val contextPath = instance.settings.contextPath
            val appName = contextPath.removePrefix("/").ifEmpty { "ROOT" }
            val targetDir = File(webappsDir, appName)

            sourceDir.copyRecursively(targetDir, overwrite = true)
        }

    private fun deployGradleArtifact(
        webappsDir: File,
        source: DeploymentSource.GradleArtifact,
    ): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Gradle artifact deployment requires running Gradle task first"),
        )

    private fun deployMavenArtifact(
        webappsDir: File,
        source: DeploymentSource.MavenArtifact,
    ): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Maven artifact deployment requires running Maven first"),
        )

    private fun generateInstanceId(): String {
        val chars = "0123456789abcdef"
        return "tomcat-" + (1..8).map { chars.random() }.joinToString("")
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        instances.values.forEach { it.dispose() }
        instances.clear()
    }
}
