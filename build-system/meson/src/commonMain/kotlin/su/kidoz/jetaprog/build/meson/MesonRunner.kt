package su.kidoz.jetaprog.build.meson

import kotlinx.coroutines.flow.Flow

/**
 * Runs Meson build commands in a project.
 */
public interface MesonRunner {
    /**
     * Sets up the Meson build directory.
     *
     * @param project The Meson project to set up.
     * @param reconfigure Whether to reconfigure an existing build directory.
     * @param args Additional arguments to pass to meson setup.
     * @return A flow of output from the setup execution.
     */
    public suspend fun setup(
        project: MesonProject,
        reconfigure: Boolean = false,
        args: List<String> = emptyList(),
    ): Result<Flow<MesonOutput>>

    /**
     * Compiles the Meson project.
     *
     * @param project The Meson project to compile.
     * @param targets Specific targets to build (empty = all).
     * @param args Additional arguments to pass to meson compile.
     * @return A flow of output from the compilation.
     */
    public suspend fun compile(
        project: MesonProject,
        targets: List<String> = emptyList(),
        args: List<String> = emptyList(),
    ): Result<Flow<MesonOutput>>

    /**
     * Runs tests in the Meson project.
     *
     * @param project The Meson project to test.
     * @param suites Test suites to run (empty = all).
     * @param args Additional arguments to pass to meson test.
     * @return A flow of output from the test execution.
     */
    public suspend fun test(
        project: MesonProject,
        suites: List<String> = emptyList(),
        args: List<String> = emptyList(),
    ): Result<Flow<MesonOutput>>

    /**
     * Cleans the Meson build directory.
     *
     * @param project The Meson project to clean.
     * @return A flow of output from the clean execution.
     */
    public suspend fun clean(project: MesonProject): Result<Flow<MesonOutput>>

    /**
     * Installs the Meson project.
     *
     * @param project The Meson project to install.
     * @param destDir Optional destination directory.
     * @return A flow of output from the install execution.
     */
    public suspend fun install(
        project: MesonProject,
        destDir: String? = null,
    ): Result<Flow<MesonOutput>>

    /**
     * Cancels the currently running command.
     */
    public fun cancel()

    /**
     * Whether a command is currently running.
     */
    public val isRunning: Boolean

    /**
     * Introspects the Meson project to discover targets and information.
     *
     * @param project The Meson project to introspect.
     * @return The project with discovered information.
     */
    public suspend fun introspect(project: MesonProject): Result<MesonProject>
}
