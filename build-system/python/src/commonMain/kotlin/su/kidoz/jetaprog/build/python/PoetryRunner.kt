package su.kidoz.jetaprog.build.python

import kotlinx.coroutines.flow.Flow

/**
 * Runs Poetry commands in a Python project.
 *
 * Poetry is a dependency manager and build tool for Python that provides:
 * - Dependency resolution and installation
 * - Virtual environment management
 * - Package building and publishing
 * - Script running
 *
 * Installation: `pip install poetry` or `pipx install poetry`
 *
 * @see <a href="https://python-poetry.org/">Poetry Documentation</a>
 */
public interface PoetryRunner {
    /**
     * Installs project dependencies from pyproject.toml and poetry.lock.
     *
     * Equivalent to: `poetry install`
     *
     * @param project The Python project to install dependencies for.
     * @param withDev Whether to include development dependencies.
     * @param extras Extra dependency groups to install.
     * @param noRoot Whether to skip installing the project itself.
     * @return A flow of output from the installation.
     */
    public suspend fun install(
        project: PythonProject,
        withDev: Boolean = true,
        extras: List<String> = emptyList(),
        noRoot: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Adds new dependencies to the project.
     *
     * Equivalent to: `poetry add <packages>`
     *
     * @param project The Python project.
     * @param packages Package specifications to add (e.g., "requests", "fastapi^0.100.0").
     * @param dev Whether to add as development dependencies.
     * @param group Dependency group to add to (Poetry 1.2+).
     * @return A flow of output from the operation.
     */
    public suspend fun add(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean = false,
        group: String? = null,
    ): Result<Flow<PythonOutput>>

    /**
     * Removes dependencies from the project.
     *
     * Equivalent to: `poetry remove <packages>`
     *
     * @param project The Python project.
     * @param packages Package names to remove.
     * @param dev Whether to remove from development dependencies.
     * @param group Dependency group to remove from.
     * @return A flow of output from the operation.
     */
    public suspend fun remove(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean = false,
        group: String? = null,
    ): Result<Flow<PythonOutput>>

    /**
     * Updates project dependencies.
     *
     * Equivalent to: `poetry update [packages]`
     *
     * @param project The Python project.
     * @param packages Specific packages to update (empty = all).
     * @return A flow of output from the update.
     */
    public suspend fun update(
        project: PythonProject,
        packages: List<String> = emptyList(),
    ): Result<Flow<PythonOutput>>

    /**
     * Shows information about installed packages.
     *
     * Equivalent to: `poetry show`
     *
     * @param project The Python project.
     * @param package Specific package to show info for (null = all).
     * @param tree Whether to show dependency tree.
     * @return A flow of output with package information.
     */
    public suspend fun show(
        project: PythonProject,
        packageName: String? = null,
        tree: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Builds the package (wheel and sdist).
     *
     * Equivalent to: `poetry build`
     *
     * @param project The Python project.
     * @param format Build format ("wheel", "sdist", or null for both).
     * @return A flow of output from the build.
     */
    public suspend fun build(
        project: PythonProject,
        format: String? = null,
    ): Result<Flow<PythonOutput>>

    /**
     * Runs a script defined in pyproject.toml.
     *
     * Equivalent to: `poetry run <script>`
     *
     * @param project The Python project.
     * @param script The script name or command to run.
     * @param args Additional arguments to pass to the script.
     * @return A flow of output from the script execution.
     */
    public suspend fun run(
        project: PythonProject,
        script: String,
        args: List<String> = emptyList(),
    ): Result<Flow<PythonOutput>>

    /**
     * Opens a shell within the virtual environment.
     *
     * Equivalent to: `poetry shell`
     *
     * @param project The Python project.
     * @return A flow of output.
     */
    public suspend fun shell(project: PythonProject): Result<Flow<PythonOutput>>

    /**
     * Updates the poetry.lock file without installing.
     *
     * Equivalent to: `poetry lock`
     *
     * @param project The Python project.
     * @param noUpdate Whether to skip updating dependencies (just regenerate lock).
     * @return A flow of output from the lock operation.
     */
    public suspend fun lock(
        project: PythonProject,
        noUpdate: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Exports dependencies to requirements.txt format.
     *
     * Equivalent to: `poetry export`
     *
     * @param project The Python project.
     * @param outputPath Path for the output file.
     * @param withDev Whether to include development dependencies.
     * @param format Export format (default: requirements.txt).
     * @return A flow of output from the export.
     */
    public suspend fun export(
        project: PythonProject,
        outputPath: String,
        withDev: Boolean = false,
        format: String = "requirements.txt",
    ): Result<Flow<PythonOutput>>

    /**
     * Gets information about the project's virtual environment.
     *
     * Equivalent to: `poetry env info`
     *
     * @param project The Python project.
     * @return A flow of output with environment information.
     */
    public suspend fun envInfo(project: PythonProject): Result<Flow<PythonOutput>>

    /**
     * Cancels the currently running command.
     */
    public fun cancel()

    /**
     * Whether a command is currently running.
     */
    public val isRunning: Boolean
}
