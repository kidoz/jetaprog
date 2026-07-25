package su.kidoz.jetaprog.plugins.cfamily

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import su.kidoz.jetaprog.build.cmake.CMakeCacheKeys
import su.kidoz.jetaprog.build.cmake.CMakeOutput
import su.kidoz.jetaprog.build.cmake.CMakeProject
import su.kidoz.jetaprog.build.cmake.CMakeRunner
import su.kidoz.jetaprog.build.cmake.CTestOutcome
import su.kidoz.jetaprog.build.cmake.DiagnosticSeverity
import su.kidoz.jetaprog.build.cmake.JvmCMakeRunner
import su.kidoz.jetaprog.build.meson.JvmMesonRunner
import su.kidoz.jetaprog.build.meson.MesonOptions
import su.kidoz.jetaprog.build.meson.MesonOutput
import su.kidoz.jetaprog.build.meson.MesonProject
import su.kidoz.jetaprog.build.meson.MesonRunner
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService
import java.io.File

/**
 * Build system integration shared by the C and C++ plugins.
 *
 * Detects whether the workspace is driven by CMake or Meson and exposes a single set of
 * configure/build/test/clean/install operations over whichever is present. Both back ends
 * emit `compile_commands.json`, which is what makes clangd useful.
 */
public class CFamilyBuildSupport private constructor(
    private val workspacePath: String,
    private val cmake: CMakePair?,
    private val meson: MesonPair?,
) {
    private data class CMakePair(
        val runner: CMakeRunner,
        val project: CMakeProject,
    )

    private data class MesonPair(
        val runner: MesonRunner,
        val project: MesonProject,
    )

    /**
     * The build system detected for this workspace.
     */
    public val kind: Kind
        get() =
            when {
                cmake != null -> Kind.CMAKE
                meson != null -> Kind.MESON
                else -> Kind.NONE
            }

    /**
     * Which build system drives the workspace.
     */
    public enum class Kind {
        CMAKE,
        MESON,
        NONE,
    }

    /**
     * Absolute path of the directory containing `compile_commands.json`, or null when the
     * compilation database has not been generated yet.
     */
    public fun compileCommandsDirectory(): String? {
        cmake?.let { pair ->
            pair.runner.compileCommandsDirectory(pair.project)?.let { return it }
        }
        meson?.let { pair ->
            val buildDir =
                File(pair.project.buildDir).takeIf { it.isAbsolute }
                    ?: File(pair.project.rootPath, pair.project.buildDir)
            if (File(buildDir, CFamilyFiles.COMPILE_COMMANDS).isFile) return buildDir.absolutePath
        }
        // A database committed to, or generated at, the workspace root.
        return workspacePath.takeIf { File(it, CFamilyFiles.COMPILE_COMMANDS).isFile }
    }

    /**
     * Configures the build directory, generating the compilation database.
     *
     * @return Human-readable output from the build system.
     */
    public suspend fun configure(): String =
        when {
            cmake != null -> collectCMake { cmake.runner.configure(cmake.project) }
            meson != null -> collectMeson { meson.runner.setup(meson.project, reconfigure = mesonBuildDirExists()) }
            else -> NO_BUILD_SYSTEM
        }

    /**
     * Builds the project.
     *
     * @param targets Targets to build; empty builds everything.
     */
    public suspend fun build(targets: List<String> = emptyList()): String =
        when {
            cmake != null -> collectCMake { cmake.runner.build(cmake.project, targets = targets) }
            meson != null -> collectMeson { meson.runner.compile(meson.project, targets = targets) }
            else -> NO_BUILD_SYSTEM
        }

    /**
     * Runs the project's tests.
     *
     * @param filter Test name filter, or null to run everything.
     */
    public suspend fun test(filter: String? = null): String =
        when {
            cmake != null -> {
                collectCMake { cmake.runner.test(cmake.project, filter = filter) }
            }

            meson != null -> {
                // `--suite` selects a whole suite, so a name filter goes through as the
                // positional test-name argument that `meson test` expects instead.
                collectMeson { meson.runner.test(meson.project, args = listOfNotNull(filter)) }
            }

            else -> {
                NO_BUILD_SYSTEM
            }
        }

    /**
     * Removes build outputs.
     */
    public suspend fun clean(): String =
        when {
            cmake != null -> collectCMake { cmake.runner.clean(cmake.project) }
            meson != null -> collectMeson { meson.runner.clean(meson.project) }
            else -> NO_BUILD_SYSTEM
        }

    /**
     * Installs the project.
     *
     * @param prefix Installation prefix, or null for the build system default.
     */
    public suspend fun install(prefix: String? = null): String =
        when {
            cmake != null -> collectCMake { cmake.runner.install(cmake.project, prefix = prefix) }
            meson != null -> collectMeson { meson.runner.install(meson.project, destDir = prefix) }
            else -> NO_BUILD_SYSTEM
        }

    /**
     * Cancels any running build command.
     */
    public fun cancel() {
        cmake?.runner?.cancel()
        meson?.runner?.cancel()
    }

    private fun mesonBuildDirExists(): Boolean {
        val project = meson?.project ?: return false
        return File(project.rootPath, project.buildDir).isDirectory
    }

    private suspend fun collectCMake(command: suspend () -> Result<Flow<CMakeOutput>>): String =
        command().fold(
            onSuccess = { flow -> flow.toList().joinToString("\n") { format(it) } },
            onFailure = { error -> "Command failed: ${error.message}" },
        )

    private suspend fun collectMeson(command: suspend () -> Result<Flow<MesonOutput>>): String =
        command().fold(
            onSuccess = { flow -> flow.toList().joinToString("\n") { format(it) } },
            onFailure = { error -> "Command failed: ${error.message}" },
        )

    private fun format(output: CMakeOutput): String =
        when (output) {
            is CMakeOutput.Stdout -> {
                output.line
            }

            is CMakeOutput.Stderr -> {
                "[stderr] ${output.line}"
            }

            is CMakeOutput.CommandStarted -> {
                "Running: ${output.command} ${output.args.joinToString(" ")}"
            }

            is CMakeOutput.ConfigureProgress -> {
                "-- ${output.message}"
            }

            is CMakeOutput.BuildProgress -> {
                "[${output.percent}%] ${output.message}"
            }

            is CMakeOutput.Compiling -> {
                "Compiling ${output.target}"
            }

            is CMakeOutput.Linking -> {
                "Linking ${output.target}"
            }

            is CMakeOutput.Diagnostic -> {
                formatDiagnostic(output)
            }

            is CMakeOutput.TestStarted -> {
                "Running test: ${output.testName}"
            }

            is CMakeOutput.TestCompleted -> {
                formatTestResult(output)
            }

            is CMakeOutput.TestSummary -> {
                "test result: ${output.passed} passed; ${output.failed} failed; ${output.total} total"
            }

            is CMakeOutput.CommandFinished -> {
                "Command ${if (output.success) "SUCCESS" else "FAILED"} (exit code: ${output.exitCode})"
            }
        }

    private fun formatDiagnostic(diagnostic: CMakeOutput.Diagnostic): String =
        buildString {
            append(
                when (diagnostic.severity) {
                    DiagnosticSeverity.ERROR -> "[error] "
                    DiagnosticSeverity.WARNING -> "[warning] "
                    DiagnosticSeverity.NOTE -> "[note] "
                },
            )
            diagnostic.file?.let { file ->
                append(file)
                diagnostic.line?.let { append(":$it") }
                diagnostic.column?.let { append(":$it") }
                append(": ")
            }
            append(diagnostic.message)
        }

    private fun formatTestResult(result: CMakeOutput.TestCompleted): String =
        buildString {
            append("test ${result.testName} ... ")
            append(
                when (result.outcome) {
                    CTestOutcome.PASSED -> "ok"
                    CTestOutcome.FAILED -> "FAILED"
                    CTestOutcome.TIMEOUT -> "TIMEOUT"
                    CTestOutcome.SKIPPED -> "skipped"
                    CTestOutcome.NOT_RUN -> "not run"
                },
            )
            result.durationSeconds?.let { append(" (${it}s)") }
        }

    private fun format(output: MesonOutput): String =
        when (output) {
            is MesonOutput.Stdout -> {
                output.line
            }

            is MesonOutput.Stderr -> {
                "[stderr] ${output.line}"
            }

            is MesonOutput.CompileStarted -> {
                "Compiling ${output.target}..."
            }

            is MesonOutput.CompileProgress -> {
                "[${output.current}/${output.total}] ${output.target}"
            }

            is MesonOutput.CompileCompleted -> {
                "Compiled ${output.target}: ${if (output.success) "OK" else "FAILED"}"
            }

            is MesonOutput.TestStarted -> {
                "Running test: ${output.testName}"
            }

            is MesonOutput.TestCompleted -> {
                "test ${output.testName} ... ${output.outcome.name.lowercase()}" +
                    (output.duration?.let { " (${it}ms)" } ?: "")
            }

            is MesonOutput.BuildFinished -> {
                "Build ${if (output.success) "SUCCESS" else "FAILED"} (exit code: ${output.exitCode})"
            }
        }

    public companion object {
        private const val NO_BUILD_SYSTEM =
            "No CMake or Meson project found. Add a CMakeLists.txt or meson.build to the workspace root."

        /** Build directory Meson projects are configured into. */
        public const val MESON_BUILD_DIR: String = "builddir"

        /**
         * Detects the build system in [workspacePath].
         *
         * CMake wins when both marker files are present, since a workspace carrying both
         * is normally a CMake project with a Meson subproject rather than the reverse.
         *
         * @param workspace Workspace service used to probe for marker files.
         * @param workspacePath Absolute path of the workspace root.
         * @param processExecutor Executor used to run the build tools.
         * @param cStandard C standard applied to whichever build system is detected.
         * @param cppStandard C++ standard applied to whichever build system is detected.
         */
        public suspend fun detect(
            workspace: WorkspaceService,
            workspacePath: String,
            processExecutor: ProcessExecutor,
            cStandard: CStandard = CStandard.LATEST,
            cppStandard: CppStandard = CppStandard.LATEST,
        ): CFamilyBuildSupport {
            val hasCMake = workspace.exists("$workspacePath/${CFamilyFiles.CMAKE_LISTS}")
            val hasMeson = workspace.exists("$workspacePath/${CFamilyFiles.MESON_BUILD}")

            val cmake =
                if (hasCMake) {
                    CMakePair(
                        runner = JvmCMakeRunner(processExecutor),
                        project =
                            CMakeProject(
                                rootPath = workspacePath,
                                cacheEntries = cmakeCacheEntries(cStandard, cppStandard),
                            ),
                    )
                } else {
                    null
                }

            val meson =
                if (!hasCMake && hasMeson) {
                    MesonPair(
                        runner = JvmMesonRunner(processExecutor),
                        project =
                            MesonProject(
                                rootPath = workspacePath,
                                buildDir = MESON_BUILD_DIR,
                                options = mesonOptions(cStandard, cppStandard),
                            ),
                    )
                } else {
                    null
                }

            return CFamilyBuildSupport(workspacePath, cmake, meson)
        }

        /**
         * CMake cache entries carrying the language standards and the compilation
         * database clangd needs.
         */
        internal fun cmakeCacheEntries(
            cStandard: CStandard,
            cppStandard: CppStandard,
        ): Map<String, String> =
            mapOf(
                CMakeCacheKeys.EXPORT_COMPILE_COMMANDS to "ON",
                CMakeCacheKeys.C_STANDARD to cStandard.cmakeValue,
                CMakeCacheKeys.CXX_STANDARD to cppStandard.cmakeValue,
            )

        /**
         * Meson `-D` options carrying the language standards.
         *
         * Meson writes `compile_commands.json` into the build directory unconditionally,
         * so unlike CMake there is no option to request it.
         */
        internal fun mesonOptions(
            cStandard: CStandard,
            cppStandard: CppStandard,
        ): Map<String, String> =
            mapOf(
                MesonOptions.C_STD to cStandard.flag,
                MesonOptions.CPP_STD to cppStandard.flag,
            )
    }
}
