package su.kidoz.jetaprog.build.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.DockerComposeSettings
import su.kidoz.jetaprog.configuration.DockerRunSettings
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

/**
 * Result of a Docker build operation.
 */
public data class DockerBuildResult(
    /** Whether the build succeeded. */
    val success: Boolean,
    /** The image ID if successful. */
    val imageId: String? = null,
    /** Build output logs. */
    val logs: List<String> = emptyList(),
    /** Error message if failed. */
    val errorMessage: String? = null,
)

/**
 * State of a Docker container.
 */
public enum class ContainerState {
    CREATED,
    RUNNING,
    PAUSED,
    RESTARTING,
    REMOVING,
    EXITED,
    DEAD,
}

/**
 * Represents a running Docker container.
 */
public class DockerContainer(
    /** Container ID. */
    public val containerId: String,
    /** Container name. */
    public val containerName: String,
    /** Image used. */
    public val imageName: String,
    /** The configuration used to create this container. */
    public val settings: DockerRunSettings,
    private val process: RunningProcess?,
    private val scope: CoroutineScope,
) : Disposable {
    private val _state = MutableStateFlow(ContainerState.RUNNING)
    private val _logs = MutableSharedFlow<String>(replay = 100)

    private var logCollectorJob: Job? = null

    /**
     * Current state of the container.
     */
    public val state: StateFlow<ContainerState> = _state.asStateFlow()

    /**
     * Container logs.
     */
    public val logs: Flow<String> = _logs.asSharedFlow()

    /**
     * Start collecting logs from the process.
     */
    internal fun startLogCollection() {
        process?.let { p ->
            logCollectorJob =
                scope.launch {
                    p.output.collect { output ->
                        when (output) {
                            is ProcessOutput.Stdout -> {
                                _logs.emit(output.line)
                            }

                            is ProcessOutput.Stderr -> {
                                _logs.emit("[STDERR] ${output.line}")
                            }

                            is ProcessOutput.Exited -> {
                                _state.value = ContainerState.EXITED
                            }
                        }
                    }
                }
        }
    }

    /**
     * Update the container state.
     */
    internal fun updateState(newState: ContainerState) {
        _state.value = newState
    }

    override fun dispose() {
        logCollectorJob?.cancel()
    }
}

/**
 * Manages Docker operations (build, run, etc.).
 */
public class DockerManager(
    private val processExecutor: ProcessExecutor,
    private val scope: CoroutineScope,
) : Disposable {
    private val containers = mutableMapOf<String, DockerContainer>()
    private var disposed = false

    /**
     * Build a Docker image.
     *
     * @param settings The build configuration.
     * @param workspacePath The workspace path.
     * @return The build result.
     */
    public suspend fun build(
        settings: DockerBuildSettings,
        workspacePath: String,
    ): DockerBuildResult {
        check(!disposed) { "DockerManager has been disposed" }

        val command = buildBuildCommand(settings)
        val logs = mutableListOf<String>()
        var imageId: String? = null

        val config =
            ProcessConfig(
                command = command,
                workingDirectory = workspacePath,
            )

        val result = processExecutor.start(config)

        return result.fold(
            onSuccess = { process ->
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            logs.add(output.line)
                            // Parse image ID from build output
                            if (output.line.startsWith("sha256:")) {
                                imageId = output.line.substringAfter("sha256:").take(12)
                            }
                        }

                        is ProcessOutput.Stderr -> {
                            logs.add("[BUILD] ${output.line}")
                        }

                        is ProcessOutput.Exited -> { /* handled after collection */ }
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    DockerBuildResult(
                        success = true,
                        imageId = imageId ?: settings.imageReference,
                        logs = logs,
                    )
                } else {
                    DockerBuildResult(
                        success = false,
                        logs = logs,
                        errorMessage = "Build failed with exit code $exitCode",
                    )
                }
            },
            onFailure = { error ->
                DockerBuildResult(
                    success = false,
                    logs = logs,
                    errorMessage = error.message,
                )
            },
        )
    }

    /**
     * Run a Docker container.
     *
     * @param settings The run configuration.
     * @param workspacePath The workspace path.
     * @return The running container.
     */
    public suspend fun run(
        settings: DockerRunSettings,
        workspacePath: String,
    ): Result<DockerContainer> {
        check(!disposed) { "DockerManager has been disposed" }

        val command = buildRunCommand(settings)

        val config =
            ProcessConfig(
                command = command,
                workingDirectory = workspacePath,
            )

        return processExecutor.start(config).mapCatching { process ->
            val containerId = generateContainerId()
            val containerName = settings.containerName ?: "container-$containerId"

            val container =
                DockerContainer(
                    containerId = containerId,
                    containerName = containerName,
                    imageName = settings.imageName,
                    settings = settings,
                    process = process,
                    scope = scope,
                )

            container.startLogCollection()
            containers[containerId] = container

            container
        }
    }

    /**
     * Stop a running container.
     */
    public suspend fun stop(container: DockerContainer) {
        container.updateState(ContainerState.REMOVING)

        processExecutor.execute(
            command = listOf("docker", "stop", container.containerId),
        )

        containers.remove(container.containerId)
        container.dispose()
    }

    /**
     * Get logs from a container.
     *
     * @param containerId The container ID.
     * @param tail Number of lines to return (0 = all).
     * @param follow Whether to follow logs.
     * @return Flow of log lines.
     */
    public suspend fun logs(
        containerId: String,
        tail: Int = 0,
        follow: Boolean = false,
    ): Result<Flow<String>> {
        val command =
            buildList {
                add("docker")
                add("logs")
                if (tail > 0) {
                    add("--tail")
                    add(tail.toString())
                }
                if (follow) add("-f")
                add(containerId)
            }

        val config = ProcessConfig(command = command)

        return processExecutor.start(config).mapCatching { process ->
            val logFlow = MutableSharedFlow<String>(replay = 100)

            scope.launch {
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            logFlow.emit(output.line)
                        }

                        is ProcessOutput.Stderr -> {
                            logFlow.emit(output.line)
                        }

                        is ProcessOutput.Exited -> { /* done */ }
                    }
                }
            }

            logFlow.asSharedFlow()
        }
    }

    /**
     * Execute a command in a running container.
     */
    public suspend fun exec(
        containerId: String,
        command: List<String>,
        interactive: Boolean = false,
        tty: Boolean = false,
        workdir: String? = null,
        user: String? = null,
        environment: Map<String, String> = emptyMap(),
    ): Result<RunningProcess> {
        val execCommand =
            buildList {
                add("docker")
                add("exec")
                if (interactive) add("-i")
                if (tty) add("-t")
                workdir?.let {
                    add("-w")
                    add(it)
                }
                user?.let {
                    add("-u")
                    add(it)
                }
                environment.forEach { (key, value) ->
                    add("-e")
                    add("$key=$value")
                }
                add(containerId)
                addAll(command)
            }

        val config = ProcessConfig(command = execCommand)
        return processExecutor.start(config)
    }

    /**
     * Run a Docker Compose operation.
     */
    public suspend fun compose(
        settings: DockerComposeSettings,
        workspacePath: String,
    ): Result<RunningProcess> {
        val command = buildComposeCommand(settings)

        val config =
            ProcessConfig(
                command = command,
                workingDirectory = workspacePath,
            )

        return processExecutor.start(config)
    }

    /**
     * Pull an image.
     */
    public suspend fun pull(imageName: String): Result<Unit> =
        processExecutor
            .execute(
                command = listOf("docker", "pull", imageName),
            ).map { }

    /**
     * Remove an image.
     */
    public suspend fun removeImage(
        imageName: String,
        force: Boolean = false,
    ): Result<Unit> {
        val command =
            buildList {
                add("docker")
                add("rmi")
                if (force) add("-f")
                add(imageName)
            }
        return processExecutor.execute(command).map { }
    }

    /**
     * List running containers.
     */
    public fun getRunningContainers(): List<DockerContainer> = containers.values.toList()

    private fun buildBuildCommand(settings: DockerBuildSettings): List<String> =
        buildList {
            add("docker")
            add("build")

            add("-t")
            add(settings.imageReference)

            add("-f")
            add(settings.dockerfile)

            settings.buildArgs.forEach { (key, value) ->
                add("--build-arg")
                add("$key=$value")
            }

            settings.target?.let {
                add("--target")
                add(it)
            }

            if (settings.noCache) add("--no-cache")
            if (settings.pull) add("--pull")

            settings.platform?.let {
                add("--platform")
                add(it)
            }

            settings.labels.forEach { (key, value) ->
                add("--label")
                add("$key=$value")
            }

            settings.network?.let {
                add("--network")
                add(it)
            }

            add(settings.contextPath)
        }

    private fun buildRunCommand(settings: DockerRunSettings): List<String> =
        buildList {
            add("docker")
            add("run")

            settings.containerName?.let {
                add("--name")
                add(it)
            }

            settings.portMappings.forEach { pm ->
                add("-p")
                add(pm.toDockerFormat())
            }

            settings.volumeMappings.forEach { vm ->
                add("-v")
                add(vm.toDockerFormat())
            }

            settings.environment.forEach { (key, value) ->
                add("-e")
                add("$key=$value")
            }

            settings.envFile?.let {
                add("--env-file")
                add(it)
            }

            settings.workdir?.let {
                add("-w")
                add(it)
            }

            settings.user?.let {
                add("-u")
                add(it)
            }

            settings.network?.let {
                add("--network")
                add(it)
            }

            settings.networks.forEach { net ->
                add("--network")
                add(net)
            }

            if (settings.rm) add("--rm")
            if (settings.detach) add("-d")
            if (settings.tty) add("-t")
            if (settings.interactive) add("-i")
            if (settings.privileged) add("--privileged")

            settings.capAdd.forEach { cap ->
                add("--cap-add")
                add(cap)
            }

            settings.capDrop.forEach { cap ->
                add("--cap-drop")
                add(cap)
            }

            settings.memory?.let {
                add("-m")
                add(it)
            }

            settings.cpus?.let {
                add("--cpus")
                add(it)
            }

            if (settings.restart != su.kidoz.jetaprog.configuration.RestartPolicy.NO) {
                add("--restart")
                add(
                    settings.restart.name
                        .lowercase()
                        .replace('_', '-'),
                )
            }

            settings.labels.forEach { (key, value) ->
                add("-l")
                add("$key=$value")
            }

            settings.hostname?.let {
                add("-h")
                add(it)
            }

            settings.extraHosts.forEach { (host, ip) ->
                add("--add-host")
                add("$host:$ip")
            }

            settings.dns.forEach { dns ->
                add("--dns")
                add(dns)
            }

            settings.securityOpt.forEach { opt ->
                add("--security-opt")
                add(opt)
            }

            settings.entrypoint?.let {
                add("--entrypoint")
                add(it)
            }

            add(settings.imageName)

            settings.command?.let { cmd ->
                // Split command into arguments
                cmd.split(" ").forEach { add(it) }
            }
        }

    private fun buildComposeCommand(settings: DockerComposeSettings): List<String> =
        buildList {
            add("docker")
            add("compose")

            add("-f")
            add(settings.composeFile)

            settings.additionalFiles.forEach { file ->
                add("-f")
                add(file)
            }

            settings.projectName?.let {
                add("-p")
                add(it)
            }

            settings.envFile?.let {
                add("--env-file")
                add(it)
            }

            settings.profiles.forEach { profile ->
                add("--profile")
                add(profile)
            }

            // Add the operation
            add(settings.operation.name.lowercase())

            // Operation-specific flags
            when (settings.operation) {
                su.kidoz.jetaprog.configuration.ComposeOperation.UP -> {
                    add("-d") // Detached by default
                    if (settings.build) add("--build")
                    if (settings.forceRecreate) add("--force-recreate")
                    if (settings.removeOrphans) add("--remove-orphans")
                    settings.scale.forEach { (service, count) ->
                        add("--scale")
                        add("$service=$count")
                    }
                }

                su.kidoz.jetaprog.configuration.ComposeOperation.DOWN -> {
                    if (settings.removeOrphans) add("--remove-orphans")
                }

                su.kidoz.jetaprog.configuration.ComposeOperation.BUILD -> {
                    if (settings.build) add("--no-cache")
                }

                su.kidoz.jetaprog.configuration.ComposeOperation.LOGS -> {
                    add("-f") // Follow logs
                }

                else -> { /* No special flags */ }
            }

            // Add services
            settings.services.forEach { service ->
                add(service)
            }
        }

    private fun generateContainerId(): String {
        val chars = "0123456789abcdef"
        return (1..12).map { chars.random() }.joinToString("")
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        containers.values.forEach { it.dispose() }
        containers.clear()
    }
}
