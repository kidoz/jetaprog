package su.kidoz.jetaprog.build.dotnet

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

private val logger = KotlinLogging.logger {}

/**
 * JVM implementation of [DotNetRunner] using [ProcessExecutor].
 */
public class JvmDotNetRunner(
    private val processExecutor: ProcessExecutor,
    private val dotNetCommand: String = "dotnet",
) : DotNetRunner {
    private var runningProcess: RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun restore(
        project: DotNetProject,
        sources: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("restore")
            project.targetPath?.let { add(it) }
            sources.forEach {
                add("--source")
                add(it)
            }
        }

    override suspend fun build(
        project: DotNetProject,
        configuration: DotNetConfiguration,
        noRestore: Boolean,
        arguments: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("build")
            project.targetPath?.let { add(it) }
            addConfiguration(configuration)
            if (noRestore) add("--no-restore")
            addAll(arguments)
        }

    override suspend fun run(
        project: DotNetProject,
        configuration: DotNetConfiguration,
        projectPath: String?,
        arguments: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("run")
            val selectedProjectPath = projectPath ?: project.projectPath
            selectedProjectPath?.let {
                add("--project")
                add(it)
            }
            addConfiguration(configuration)
            if (arguments.isNotEmpty()) {
                add("--")
                addAll(arguments)
            }
        }

    override suspend fun test(
        project: DotNetProject,
        configuration: DotNetConfiguration,
        filter: String?,
        noBuild: Boolean,
        arguments: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("test")
            project.targetPath?.let { add(it) }
            addConfiguration(configuration)
            filter?.let {
                add("--filter")
                add(it)
            }
            if (noBuild) add("--no-build")
            addAll(arguments)
        }

    override suspend fun publish(
        project: DotNetProject,
        configuration: DotNetConfiguration,
        outputDirectory: String?,
        arguments: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("publish")
            project.targetPath?.let { add(it) }
            addConfiguration(configuration)
            outputDirectory?.let {
                add("--output")
                add(it)
            }
            addAll(arguments)
        }

    override suspend fun pack(
        project: DotNetProject,
        configuration: DotNetConfiguration,
        outputDirectory: String?,
        arguments: List<String>,
    ): Result<Flow<DotNetOutput>> =
        runCommand(project.rootPath) {
            add("pack")
            project.targetPath?.let { add(it) }
            addConfiguration(configuration)
            outputDirectory?.let {
                add("--output")
                add(it)
            }
            addAll(arguments)
        }

    override suspend fun new(
        path: String,
        template: DotNetTemplate,
        name: String?,
        framework: String?,
    ): Result<Flow<DotNetOutput>> =
        runCommand(path) {
            add("new")
            add(template.cliValue)
            name?.let {
                add("--name")
                add(it)
            }
            framework?.let {
                add("--framework")
                add(it)
            }
        }

    override suspend fun info(): Result<String> =
        processExecutor
            .execute(listOf(dotNetCommand, "--info"))
            .map { it.stdout.ifBlank { it.stderr } }

    override suspend fun listSdks(): Result<List<String>> =
        processExecutor
            .execute(listOf(dotNetCommand, "--list-sdks"))
            .map { result ->
                result.stdout
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }

    private suspend fun runCommand(
        workingDirectory: String,
        buildArgs: MutableList<String>.() -> Unit,
    ): Result<Flow<DotNetOutput>> =
        runCatching {
            val args = mutableListOf(dotNetCommand).apply(buildArgs)
            logger.debug { "Running: ${args.joinToString(" ")}" }

            val config =
                ProcessConfig(
                    command = args,
                    workingDirectory = workingDirectory,
                    environment = mapOf("DOTNET_CLI_UI_LANGUAGE" to "en"),
                )

            val process = processExecutor.start(config).getOrThrow()
            runningProcess = process

            flow {
                emit(DotNetOutput.CommandStarted(dotNetCommand, args.drop(1)))
                emitOperationStarted(args)

                val startTime = System.currentTimeMillis()

                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            emit(DotNetOutput.Stdout(output.line))
                        }

                        is ProcessOutput.Stderr -> {
                            emit(DotNetOutput.Stderr(output.line))
                        }

                        is ProcessOutput.Exited -> {
                            runningProcess = null
                            emit(
                                DotNetOutput.CommandCompleted(
                                    success = output.exitCode == 0,
                                    exitCode = output.exitCode,
                                    duration = System.currentTimeMillis() - startTime,
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun MutableList<String>.addConfiguration(configuration: DotNetConfiguration) {
        add("--configuration")
        add(configuration.cliValue)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<DotNetOutput>.emitOperationStarted(args: List<String>) {
        val target = args.drop(2).firstOrNull { !it.startsWith("-") }
        when (args.getOrNull(1)) {
            "restore" -> emit(DotNetOutput.Restoring)
            "build" -> emit(DotNetOutput.Building(target))
            "test" -> emit(DotNetOutput.Testing(target))
            "publish" -> emit(DotNetOutput.Publishing(target))
            "pack" -> emit(DotNetOutput.Packing(target))
        }
    }
}
