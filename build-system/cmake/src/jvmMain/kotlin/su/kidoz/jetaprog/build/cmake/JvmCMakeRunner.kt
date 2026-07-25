package su.kidoz.jetaprog.build.cmake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
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
 * JVM implementation of [CMakeRunner] backed by the `cmake` and `ctest` executables.
 *
 * @property cmakePath Path or name of the `cmake` executable.
 * @property ctestPath Path or name of the `ctest` executable.
 */
public class JvmCMakeRunner(
    private val processExecutor: ProcessExecutor,
    private val cmakePath: String = "cmake",
    private val ctestPath: String = "ctest",
) : CMakeRunner {
    private var runningProcess: RunningProcess? = null

    private val json = Json { ignoreUnknownKeys = true }

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun configure(
        project: CMakeProject,
        args: List<String>,
    ): Result<Flow<CMakeOutput>> =
        runCatching {
            // Ask CMake for a codemodel reply so introspect() can read the targets later.
            writeFileApiQuery(project)

            val command =
                buildList {
                    add(cmakePath)
                    add("-S")
                    add(project.rootPath)
                    add("-B")
                    add(buildDirectory(project).absolutePath)
                    project.generator?.let {
                        add("-G")
                        add(it.generatorName)
                    }
                    add("-D${CMakeCacheKeys.EXPORT_COMPILE_COMMANDS}=ON")
                    add("-D${CMakeCacheKeys.BUILD_TYPE}=${project.buildType.cmakeName}")
                    project.cacheEntries.forEach { (key, value) -> add("-D$key=$value") }
                    addAll(args)
                }

            runCommand(project, command)
        }

    override suspend fun build(
        project: CMakeProject,
        targets: List<String>,
        parallel: Int?,
        args: List<String>,
    ): Result<Flow<CMakeOutput>> =
        runCatching {
            val command =
                buildList {
                    add(cmakePath)
                    add("--build")
                    add(buildDirectory(project).absolutePath)
                    add("--config")
                    add(project.buildType.cmakeName)
                    targets.forEach {
                        add("--target")
                        add(it)
                    }
                    parallel?.let {
                        add("--parallel")
                        add(it.toString())
                    }
                    if (args.isNotEmpty()) {
                        add("--")
                        addAll(args)
                    }
                }

            runCommand(project, command)
        }

    override suspend fun test(
        project: CMakeProject,
        filter: String?,
        args: List<String>,
    ): Result<Flow<CMakeOutput>> =
        runCatching {
            val command =
                buildList {
                    add(ctestPath)
                    add("--test-dir")
                    add(buildDirectory(project).absolutePath)
                    add("--build-config")
                    add(project.buildType.cmakeName)
                    add("--output-on-failure")
                    filter?.let {
                        add("-R")
                        add(it)
                    }
                    addAll(args)
                }

            runCommand(project, command)
        }

    override suspend fun clean(project: CMakeProject): Result<Flow<CMakeOutput>> =
        runCatching {
            val command =
                listOf(
                    cmakePath,
                    "--build",
                    buildDirectory(project).absolutePath,
                    "--config",
                    project.buildType.cmakeName,
                    "--target",
                    "clean",
                )

            runCommand(project, command)
        }

    override suspend fun install(
        project: CMakeProject,
        prefix: String?,
    ): Result<Flow<CMakeOutput>> =
        runCatching {
            val command =
                buildList {
                    add(cmakePath)
                    add("--install")
                    add(buildDirectory(project).absolutePath)
                    add("--config")
                    add(project.buildType.cmakeName)
                    prefix?.let {
                        add("--prefix")
                        add(it)
                    }
                }

            runCommand(project, command)
        }

    override suspend fun introspect(project: CMakeProject): Result<CMakeProject> =
        runCatching {
            val replyDir = File(buildDirectory(project), "$FILE_API_DIR/reply")
            if (!replyDir.isDirectory) return@runCatching project

            val codemodel =
                replyDir
                    .listFiles { file -> file.name.startsWith("codemodel-v2") && file.name.endsWith(".json") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: return@runCatching project

            val root = json.parseToJsonElement(codemodel.readText()).jsonObject
            val configuration =
                root["configurations"]
                    ?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { config ->
                        config["name"]?.jsonPrimitive?.contentOrNull == project.buildType.cmakeName
                    }
                    ?: root["configurations"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@runCatching project

            val projectName =
                configuration["projects"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: project.name

            val targets =
                configuration["targets"]?.jsonArray.orEmpty().mapNotNull { element ->
                    val jsonFile =
                        element.jsonObject["jsonFile"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                    parseTarget(File(replyDir, jsonFile))
                }

            project.copy(name = projectName, targets = targets)
        }

    override fun compileCommandsDirectory(project: CMakeProject): String? {
        val buildDir = buildDirectory(project)
        return if (File(buildDir, COMPILE_COMMANDS).isFile) buildDir.absolutePath else null
    }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }

    /**
     * Resolves the build directory, treating a relative [CMakeProject.buildDir] as
     * relative to the project root.
     */
    private fun buildDirectory(project: CMakeProject): File {
        val configured = File(project.buildDir)
        return if (configured.isAbsolute) configured else File(project.rootPath, project.buildDir)
    }

    /**
     * Creates the shared stateless file API query so the next configure emits a codemodel reply.
     */
    private fun writeFileApiQuery(project: CMakeProject) {
        val queryDir = File(buildDirectory(project), "$FILE_API_DIR/query")
        if (!queryDir.isDirectory && !queryDir.mkdirs()) return
        val query = File(queryDir, "codemodel-v2")
        if (!query.exists()) {
            query.writeText("")
        }
    }

    private fun parseTarget(file: File): CMakeTarget? {
        if (!file.isFile) return null
        val target = json.parseToJsonElement(file.readText()).jsonObject
        val name = target["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val type =
            when (target["type"]?.jsonPrimitive?.contentOrNull) {
                "EXECUTABLE" -> CMakeTargetType.EXECUTABLE
                "STATIC_LIBRARY" -> CMakeTargetType.STATIC_LIBRARY
                "SHARED_LIBRARY" -> CMakeTargetType.SHARED_LIBRARY
                "MODULE_LIBRARY" -> CMakeTargetType.MODULE_LIBRARY
                "OBJECT_LIBRARY" -> CMakeTargetType.OBJECT_LIBRARY
                "INTERFACE_LIBRARY" -> CMakeTargetType.INTERFACE_LIBRARY
                else -> CMakeTargetType.UTILITY
            }
        val artifact =
            target["artifacts"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("path")
                ?.jsonPrimitive
                ?.contentOrNull

        return CMakeTarget(name = name, type = type, artifactPath = artifact)
    }

    private suspend fun runCommand(
        project: CMakeProject,
        command: List<String>,
    ): Flow<CMakeOutput> {
        val config =
            ProcessConfig(
                command = command,
                workingDirectory = project.rootPath,
            )

        val process = processExecutor.start(config).getOrThrow()
        runningProcess = process

        return flow {
            emit(CMakeOutput.CommandStarted(command.first(), command.drop(1)))
            process.output.collect { output ->
                when (output) {
                    is ProcessOutput.Stdout -> {
                        emit(CMakeOutputParser.parseStdout(output.line))
                    }

                    is ProcessOutput.Stderr -> {
                        emit(CMakeOutputParser.parseStderr(output.line))
                    }

                    is ProcessOutput.Exited -> {
                        runningProcess = null
                        emit(
                            CMakeOutput.CommandFinished(
                                success = output.exitCode == 0,
                                exitCode = output.exitCode,
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val FILE_API_DIR = ".cmake/api/v1"
        const val COMPILE_COMMANDS = "compile_commands.json"
    }
}
