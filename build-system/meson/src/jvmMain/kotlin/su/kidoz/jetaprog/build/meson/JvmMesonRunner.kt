package su.kidoz.jetaprog.build.meson

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.File

/**
 * JVM implementation of MesonRunner using ProcessExecutor.
 */
public class JvmMesonRunner(
    private val processExecutor: ProcessExecutor,
) : MesonRunner {
    private var runningProcess: RunningProcess? = null

    private val json = Json { ignoreUnknownKeys = true }

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun setup(
        project: MesonProject,
        reconfigure: Boolean,
        args: List<String>,
    ): Result<Flow<MesonOutput>> =
        runCatching {
            val command =
                buildList {
                    add("meson")
                    add("setup")
                    add(project.buildDir)
                    if (reconfigure) add("--reconfigure")
                    add("--buildtype=${project.buildType.name.lowercase()}")
                    addAll(args)
                }

            runCommand(project, command)
        }

    override suspend fun compile(
        project: MesonProject,
        targets: List<String>,
        args: List<String>,
    ): Result<Flow<MesonOutput>> =
        runCatching {
            val command =
                buildList {
                    add("meson")
                    add("compile")
                    add("-C")
                    add(project.buildDir)
                    targets.forEach { add(it) }
                    addAll(args)
                }

            runCommand(project, command)
        }

    override suspend fun test(
        project: MesonProject,
        suites: List<String>,
        args: List<String>,
    ): Result<Flow<MesonOutput>> =
        runCatching {
            val command =
                buildList {
                    add("meson")
                    add("test")
                    add("-C")
                    add(project.buildDir)
                    suites.forEach {
                        add("--suite")
                        add(it)
                    }
                    addAll(args)
                }

            runCommand(project, command)
        }

    override suspend fun clean(project: MesonProject): Result<Flow<MesonOutput>> =
        runCatching {
            val command =
                listOf(
                    "meson",
                    "compile",
                    "-C",
                    project.buildDir,
                    "--clean",
                )

            runCommand(project, command)
        }

    override suspend fun install(
        project: MesonProject,
        destDir: String?,
    ): Result<Flow<MesonOutput>> =
        runCatching {
            val command =
                buildList {
                    add("meson")
                    add("install")
                    add("-C")
                    add(project.buildDir)
                    if (destDir != null) {
                        add("--destdir")
                        add(destDir)
                    }
                }

            runCommand(project, command)
        }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }

    override suspend fun introspect(project: MesonProject): Result<MesonProject> =
        runCatching {
            val buildDirPath = File(project.rootPath, project.buildDir)

            // Check if build directory exists
            if (!buildDirPath.exists()) {
                return@runCatching project
            }

            // Get project info
            val projectInfoResult =
                processExecutor
                    .execute(
                        command = listOf("meson", "introspect", "--projectinfo", project.buildDir),
                        workingDirectory = project.rootPath,
                        timeoutMillis = 30_000,
                    ).getOrThrow()

            val projectInfo = json.parseToJsonElement(projectInfoResult.stdout).jsonObject
            val projectName = projectInfo["descriptive_name"]?.jsonPrimitive?.contentOrNull ?: ""
            val projectVersion = projectInfo["version"]?.jsonPrimitive?.contentOrNull ?: ""

            // Get targets
            val targetsResult =
                processExecutor
                    .execute(
                        command = listOf("meson", "introspect", "--targets", project.buildDir),
                        workingDirectory = project.rootPath,
                        timeoutMillis = 30_000,
                    ).getOrThrow()

            val targetsJson = json.parseToJsonElement(targetsResult.stdout).jsonArray
            val targets = parseTargets(targetsJson)

            project.copy(
                name = projectName,
                version = projectVersion,
                targets = targets,
            )
        }

    private suspend fun runCommand(
        project: MesonProject,
        command: List<String>,
    ): Flow<MesonOutput> {
        val config =
            ProcessConfig(
                command = command,
                workingDirectory = project.rootPath,
            )

        val process = processExecutor.start(config).getOrThrow()
        runningProcess = process

        return flow {
            process.output.collect { output ->
                when (output) {
                    is ProcessOutput.Stdout -> {
                        val line = output.line
                        emit(parseOutputLine(line))
                    }

                    is ProcessOutput.Stderr -> {
                        emit(MesonOutput.Stderr(output.line))
                    }

                    is ProcessOutput.Exited -> {
                        runningProcess = null
                        emit(
                            MesonOutput.BuildFinished(
                                success = output.exitCode == 0,
                                exitCode = output.exitCode,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun parseOutputLine(line: String): MesonOutput {
        // Parse compilation progress: [123/456] Compiling C object foo.c.o
        val progressPattern = Regex("""^\[(\d+)/(\d+)\]\s+(.+)$""")
        progressPattern.matchEntire(line.trim())?.let { match ->
            val current = match.groupValues[1].toInt()
            val total = match.groupValues[2].toInt()
            val target = match.groupValues[3]
            return MesonOutput.CompileProgress(current, total, target)
        }

        // Parse test output
        val testPattern = Regex("""^(\d+)/(\d+)\s+(\S+)\s+(OK|FAIL|SKIP|TIMEOUT|ERROR)\s*(.*)$""")
        testPattern.matchEntire(line.trim())?.let { match ->
            val testName = match.groupValues[3]
            val outcomeStr = match.groupValues[4]
            val outcome =
                when (outcomeStr) {
                    "OK" -> TestOutcome.OK
                    "FAIL" -> TestOutcome.FAIL
                    "SKIP" -> TestOutcome.SKIP
                    "TIMEOUT" -> TestOutcome.TIMEOUT
                    "ERROR" -> TestOutcome.ERROR
                    else -> TestOutcome.OK
                }
            return MesonOutput.TestCompleted(testName, outcome)
        }

        return MesonOutput.Stdout(line)
    }

    private fun parseTargets(targetsJson: JsonArray): List<MesonTarget> =
        targetsJson.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: "custom"
            val type =
                when (typeStr) {
                    "executable" -> MesonTargetType.EXECUTABLE
                    "static library" -> MesonTargetType.STATIC_LIBRARY
                    "shared library" -> MesonTargetType.SHARED_LIBRARY
                    "both libraries" -> MesonTargetType.BOTH_LIBRARIES
                    else -> MesonTargetType.CUSTOM
                }
            val outputPath =
                (obj["filename"] as? JsonArray)
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.contentOrNull

            MesonTarget(
                name = name,
                type = type,
                outputPath = outputPath,
            )
        }
}
