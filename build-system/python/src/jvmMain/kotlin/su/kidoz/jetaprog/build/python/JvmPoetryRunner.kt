package su.kidoz.jetaprog.build.python

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

/**
 * JVM implementation of PoetryRunner using ProcessExecutor.
 */
public class JvmPoetryRunner(
    private val processExecutor: ProcessExecutor,
    private val poetryCommand: String = "poetry",
) : PoetryRunner {
    private var runningProcess: RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun install(
        project: PythonProject,
        withDev: Boolean,
        extras: List<String>,
        noRoot: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("install")
            if (!withDev) add("--no-dev")
            extras.forEach { add("--extras=$it") }
            if (noRoot) add("--no-root")
        }

    override suspend fun add(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean,
        group: String?,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("add")
            if (dev) add("--dev")
            group?.let { add("--group=$it") }
            addAll(packages)
        }

    override suspend fun remove(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean,
        group: String?,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("remove")
            if (dev) add("--dev")
            group?.let { add("--group=$it") }
            addAll(packages)
        }

    override suspend fun update(
        project: PythonProject,
        packages: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("update")
            addAll(packages)
        }

    override suspend fun show(
        project: PythonProject,
        packageName: String?,
        tree: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("show")
            if (tree) add("--tree")
            packageName?.let { add(it) }
        }

    override suspend fun build(
        project: PythonProject,
        format: String?,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("build")
            format?.let { add("--format=$it") }
        }

    override suspend fun run(
        project: PythonProject,
        script: String,
        args: List<String>,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("run")
            add(script)
            addAll(args)
        }

    override suspend fun shell(project: PythonProject): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("shell")
        }

    override suspend fun lock(
        project: PythonProject,
        noUpdate: Boolean,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("lock")
            if (noUpdate) add("--no-update")
        }

    override suspend fun export(
        project: PythonProject,
        outputPath: String,
        withDev: Boolean,
        format: String,
    ): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("export")
            add("-f")
            add(format)
            add("-o")
            add(outputPath)
            if (withDev) add("--dev")
        }

    override suspend fun envInfo(project: PythonProject): Result<Flow<PythonOutput>> =
        runCommand(project) {
            add("env")
            add("info")
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
            val args = mutableListOf(poetryCommand).apply(buildArgs)

            val config =
                ProcessConfig(
                    command = args,
                    workingDirectory = project.rootPath,
                )

            val process = processExecutor.start(config).getOrThrow()
            runningProcess = process

            flow {
                emit(PythonOutput.CommandStarted(poetryCommand, args.drop(1)))

                val startTime = System.currentTimeMillis()
                var exitCode = 0

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
                            exitCode = output.exitCode
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
        // Parse Poetry's output for specific events
        val installingPattern = Regex("""^\s*•\s+Installing\s+(\S+)\s+\(([^)]+)\)""")
        val removingPattern = Regex("""^\s*•\s+Removing\s+(\S+)""")
        val updatingPattern = Regex("""^\s*•\s+Updating\s+(\S+)\s+\(([^)]+)\s+→\s+([^)]+)\)""")
        val lockingPattern = Regex("""^Writing lock file""")
        val resolvingPattern = Regex("""^Resolving dependencies""")

        installingPattern.matchEntire(line)?.let { match ->
            return PythonOutput.PackageInstalled(
                packageName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        removingPattern.matchEntire(line)?.let { match ->
            return PythonOutput.PackageRemoved(packageName = match.groupValues[1])
        }

        updatingPattern.matchEntire(line)?.let { match ->
            return PythonOutput.PackageInstalled(
                packageName = match.groupValues[1],
                version = match.groupValues[3],
            )
        }

        if (lockingPattern.containsMatchIn(line)) {
            return PythonOutput.LockFileUpdated(path = PythonPaths.POETRY_LOCK)
        }

        if (resolvingPattern.containsMatchIn(line)) {
            return PythonOutput.ResolvingDependencies(message = line)
        }

        return PythonOutput.Stdout(line)
    }
}
