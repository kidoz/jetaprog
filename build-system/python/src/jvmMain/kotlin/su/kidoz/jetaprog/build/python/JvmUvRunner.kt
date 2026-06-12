package su.kidoz.jetaprog.build.python

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

/**
 * JVM implementation of UvRunner using ProcessExecutor.
 */
public class JvmUvRunner(
    private val processExecutor: ProcessExecutor,
    private val uvCommand: String = "uv",
) : UvRunner {
    private var runningProcess: RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun pip(
        project: PythonProject,
        packages: List<String>,
        requirements: String?,
        upgrade: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("install")
            if (upgrade) add("--upgrade")
            requirements?.let {
                add("-r")
                add(it)
            }
            addAll(packages)
        }

    override suspend fun pipUninstall(
        project: PythonProject,
        packages: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("uninstall")
            addAll(packages)
        }

    override suspend fun pipList(project: PythonProject): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("list")
        }

    override suspend fun pipShow(
        project: PythonProject,
        packageName: String,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("show")
            add(packageName)
        }

    override suspend fun pipCompile(
        project: PythonProject,
        inputFile: String,
        outputFile: String,
        upgrade: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("compile")
            add(inputFile)
            add("-o")
            add(outputFile)
            if (upgrade) add("--upgrade")
        }

    override suspend fun pipSync(
        project: PythonProject,
        requirements: String,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("pip")
            add("sync")
            add(requirements)
        }

    override suspend fun venv(
        project: PythonProject,
        path: String,
        python: String?,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("venv")
            add(path)
            python?.let {
                add("--python")
                add(it)
            }
        }

    override suspend fun sync(
        project: PythonProject,
        frozen: Boolean,
        allExtras: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("sync")
            if (frozen) add("--frozen")
            if (allExtras) add("--all-extras")
        }

    override suspend fun lock(
        project: PythonProject,
        upgrade: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("lock")
            if (upgrade) add("--upgrade")
        }

    override suspend fun add(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("add")
            if (dev) add("--dev")
            addAll(packages)
        }

    override suspend fun remove(
        project: PythonProject,
        packages: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("remove")
            addAll(packages)
        }

    override suspend fun run(
        project: PythonProject,
        command: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("run")
            addAll(command)
        }

    override suspend fun runScript(
        project: PythonProject,
        script: String,
        args: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("run")
            add("python")
            add(script)
            addAll(args)
        }

    override suspend fun version(): Result<Flow<PythonOutput>> =
        runCatching {
            val config =
                ProcessConfig(
                    command = listOf(uvCommand, "--version"),
                    workingDirectory = null,
                )

            val process = processExecutor.start(config).getOrThrow()

            flow {
                emit(PythonOutput.CommandStarted(uvCommand, listOf("--version")))

                val startTime = System.currentTimeMillis()

                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            emit(PythonOutput.Stdout(output.line))
                        }

                        is ProcessOutput.Stderr -> {
                            emit(PythonOutput.Stderr(output.line))
                        }

                        is ProcessOutput.Exited -> {
                            val duration = System.currentTimeMillis() - startTime
                            emit(
                                PythonOutput.CommandCompleted(
                                    success = output.exitCode == 0,
                                    exitCode = output.exitCode,
                                    duration = duration,
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }

    private suspend fun runCommand(
        project: PythonProject,
        buildArgs: MutableList<String>.() -> Unit,
    ): Result<Flow<PythonOutput>> =
        runCatching {
            val args = mutableListOf(uvCommand).apply(buildArgs)

            val config =
                ProcessConfig(
                    command = args,
                    workingDirectory = project.rootPath,
                )

            val process = processExecutor.start(config).getOrThrow()
            runningProcess = process

            flow {
                emit(PythonOutput.CommandStarted(uvCommand, args.drop(1)))

                val startTime = System.currentTimeMillis()

                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            val line = output.line
                            emit(parseOutputLine(line))
                        }

                        is ProcessOutput.Stderr -> {
                            emit(PythonOutput.Stderr(output.line))
                        }

                        is ProcessOutput.Exited -> {
                            runningProcess = null
                            val duration = System.currentTimeMillis() - startTime
                            emit(
                                PythonOutput.CommandCompleted(
                                    success = output.exitCode == 0,
                                    exitCode = output.exitCode,
                                    duration = duration,
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun parseOutputLine(line: String): PythonOutput {
        // Parse uv's output for specific events
        val installedPattern = Regex("""^\s*\+\s+(\S+)==(\S+)""")
        val removedPattern = Regex("""^\s*-\s+(\S+)==\S+""")
        val resolvingPattern = Regex("""^Resolved \d+ packages?""")
        val createdVenvPattern = Regex("""^Using Python (\S+).*at:\s+(.+)""")
        val lockPattern = Regex("""^Resolved \d+ packages? in""")

        installedPattern.matchEntire(line.trim())?.let { match ->
            return PythonOutput.PackageInstalled(
                packageName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        removedPattern.matchEntire(line.trim())?.let { match ->
            return PythonOutput.PackageRemoved(packageName = match.groupValues[1])
        }

        if (resolvingPattern.containsMatchIn(line)) {
            return PythonOutput.ResolvingDependencies(message = line)
        }

        createdVenvPattern.find(line)?.let { match ->
            return PythonOutput.VenvCreated(
                path = match.groupValues[2].trim(),
                pythonVersion = match.groupValues[1],
            )
        }

        if (lockPattern.containsMatchIn(line)) {
            return PythonOutput.LockFileUpdated(path = PythonPaths.UV_LOCK)
        }

        return PythonOutput.Stdout(line)
    }
}
