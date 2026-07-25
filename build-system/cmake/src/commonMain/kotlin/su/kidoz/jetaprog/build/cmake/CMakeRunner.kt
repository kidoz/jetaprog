package su.kidoz.jetaprog.build.cmake

import kotlinx.coroutines.flow.Flow

/**
 * Runs CMake and CTest commands for a project.
 */
public interface CMakeRunner {
    /**
     * Configures (or reconfigures) the build directory.
     *
     * Implementations always enable `CMAKE_EXPORT_COMPILE_COMMANDS` so that clangd
     * can pick up the compilation database.
     *
     * @param project The project to configure.
     * @param args Additional arguments appended to the `cmake` invocation.
     * @return A flow of output from the configure step.
     */
    public suspend fun configure(
        project: CMakeProject,
        args: List<String> = emptyList(),
    ): Result<Flow<CMakeOutput>>

    /**
     * Builds the project.
     *
     * @param project The project to build.
     * @param targets Targets to build (empty builds the default target).
     * @param parallel Number of parallel jobs, or null for the generator default.
     * @param args Additional arguments appended after `--`.
     * @return A flow of output from the build.
     */
    public suspend fun build(
        project: CMakeProject,
        targets: List<String> = emptyList(),
        parallel: Int? = null,
        args: List<String> = emptyList(),
    ): Result<Flow<CMakeOutput>>

    /**
     * Runs the project's tests through CTest.
     *
     * @param project The project to test.
     * @param filter Regular expression matched against test names, or null for all tests.
     * @param args Additional arguments appended to the `ctest` invocation.
     * @return A flow of output from the test run.
     */
    public suspend fun test(
        project: CMakeProject,
        filter: String? = null,
        args: List<String> = emptyList(),
    ): Result<Flow<CMakeOutput>>

    /**
     * Removes the build outputs via the generator's clean target.
     *
     * @param project The project to clean.
     * @return A flow of output from the clean step.
     */
    public suspend fun clean(project: CMakeProject): Result<Flow<CMakeOutput>>

    /**
     * Installs the project.
     *
     * @param project The project to install.
     * @param prefix Optional installation prefix overriding `CMAKE_INSTALL_PREFIX`.
     * @return A flow of output from the install step.
     */
    public suspend fun install(
        project: CMakeProject,
        prefix: String? = null,
    ): Result<Flow<CMakeOutput>>

    /**
     * Reads the project name and targets from the CMake file API.
     *
     * Returns the project unchanged when the build directory has not been configured yet.
     *
     * @param project The project to introspect.
     * @return The project enriched with discovered metadata.
     */
    public suspend fun introspect(project: CMakeProject): Result<CMakeProject>

    /**
     * Returns the absolute path of the directory holding `compile_commands.json`,
     * or null when the compilation database has not been generated yet.
     *
     * @param project The project to inspect.
     */
    public fun compileCommandsDirectory(project: CMakeProject): String?

    /**
     * Cancels the currently running command.
     */
    public fun cancel()

    /**
     * Whether a command is currently running.
     */
    public val isRunning: Boolean
}
