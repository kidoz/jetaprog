package su.kidoz.jetaprog.build.python

import kotlinx.coroutines.flow.Flow

/**
 * Runs uv commands for Python package management.
 *
 * uv is an extremely fast Python package installer and resolver written in Rust.
 * It's designed to be a drop-in replacement for pip, pip-tools, and virtualenv.
 *
 * Features:
 * - 10-100x faster than pip
 * - Compatible with pip's CLI
 * - Built-in virtual environment management
 * - Lockfile support (uv.lock)
 *
 * Installation: `pip install uv` or `curl -LsSf https://astral.sh/uv/install.sh | sh`
 *
 * @see <a href="https://github.com/astral-sh/uv">uv on GitHub</a>
 */
public interface UvRunner {
    /**
     * Installs packages using uv pip.
     *
     * Equivalent to: `uv pip install <packages>`
     *
     * @param project The Python project context.
     * @param packages Package specifications to install.
     * @param requirements Path to requirements file to install from.
     * @param upgrade Whether to upgrade packages to latest versions.
     * @return A flow of output from the installation.
     */
    public suspend fun pip(
        project: PythonProject,
        packages: List<String> = emptyList(),
        requirements: String? = null,
        upgrade: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Uninstalls packages.
     *
     * Equivalent to: `uv pip uninstall <packages>`
     *
     * @param project The Python project context.
     * @param packages Package names to uninstall.
     * @return A flow of output from the uninstallation.
     */
    public suspend fun pipUninstall(
        project: PythonProject,
        packages: List<String>,
    ): Result<Flow<PythonOutput>>

    /**
     * Lists installed packages.
     *
     * Equivalent to: `uv pip list`
     *
     * @param project The Python project context.
     * @return A flow of output with package list.
     */
    public suspend fun pipList(project: PythonProject): Result<Flow<PythonOutput>>

    /**
     * Shows information about an installed package.
     *
     * Equivalent to: `uv pip show <package>`
     *
     * @param project The Python project context.
     * @param packageName The package to show info for.
     * @return A flow of output with package information.
     */
    public suspend fun pipShow(
        project: PythonProject,
        packageName: String,
    ): Result<Flow<PythonOutput>>

    /**
     * Compiles requirements.in to requirements.txt.
     *
     * Equivalent to: `uv pip compile requirements.in -o requirements.txt`
     *
     * @param project The Python project context.
     * @param inputFile Path to input requirements file.
     * @param outputFile Path for compiled output file.
     * @param upgrade Whether to upgrade all packages.
     * @return A flow of output from compilation.
     */
    public suspend fun pipCompile(
        project: PythonProject,
        inputFile: String,
        outputFile: String,
        upgrade: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Syncs installed packages with requirements.txt.
     *
     * Equivalent to: `uv pip sync requirements.txt`
     *
     * @param project The Python project context.
     * @param requirements Path to requirements file.
     * @return A flow of output from sync operation.
     */
    public suspend fun pipSync(
        project: PythonProject,
        requirements: String,
    ): Result<Flow<PythonOutput>>

    /**
     * Creates a virtual environment.
     *
     * Equivalent to: `uv venv <path>`
     *
     * @param project The Python project context.
     * @param path Path for the virtual environment.
     * @param python Python version or path to interpreter.
     * @return A flow of output from venv creation.
     */
    public suspend fun venv(
        project: PythonProject,
        path: String = ".venv",
        python: String? = null,
    ): Result<Flow<PythonOutput>>

    /**
     * Syncs project dependencies from pyproject.toml/uv.lock.
     *
     * Equivalent to: `uv sync`
     *
     * @param project The Python project.
     * @param frozen Whether to require exact versions from lock file.
     * @param allExtras Whether to install all optional dependencies.
     * @return A flow of output from sync operation.
     */
    public suspend fun sync(
        project: PythonProject,
        frozen: Boolean = false,
        allExtras: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Updates the uv.lock file.
     *
     * Equivalent to: `uv lock`
     *
     * @param project The Python project.
     * @param upgrade Whether to upgrade packages.
     * @return A flow of output from lock operation.
     */
    public suspend fun lock(
        project: PythonProject,
        upgrade: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Adds a dependency to the project.
     *
     * Equivalent to: `uv add <package>`
     *
     * @param project The Python project.
     * @param packages Package specifications to add.
     * @param dev Whether to add as development dependency.
     * @return A flow of output from the operation.
     */
    public suspend fun add(
        project: PythonProject,
        packages: List<String>,
        dev: Boolean = false,
    ): Result<Flow<PythonOutput>>

    /**
     * Removes a dependency from the project.
     *
     * Equivalent to: `uv remove <package>`
     *
     * @param project The Python project.
     * @param packages Package names to remove.
     * @return A flow of output from the operation.
     */
    public suspend fun remove(
        project: PythonProject,
        packages: List<String>,
    ): Result<Flow<PythonOutput>>

    /**
     * Runs a command in the project environment.
     *
     * Equivalent to: `uv run <command>`
     *
     * @param project The Python project.
     * @param command The command to run.
     * @param args Additional arguments.
     * @return A flow of output from the command.
     */
    public suspend fun run(
        project: PythonProject,
        command: List<String>,
    ): Result<Flow<PythonOutput>>

    /**
     * Runs a Python script.
     *
     * Equivalent to: `uv run python <script>`
     *
     * @param project The Python project.
     * @param script Path to the Python script.
     * @param args Arguments to pass to the script.
     * @return A flow of output from the script.
     */
    public suspend fun runScript(
        project: PythonProject,
        script: String,
        args: List<String> = emptyList(),
    ): Result<Flow<PythonOutput>>

    /**
     * Shows information about the uv installation.
     *
     * @return A flow of output with version info.
     */
    public suspend fun version(): Result<Flow<PythonOutput>>

    /**
     * Cancels the currently running command.
     */
    public fun cancel()

    /**
     * Whether a command is currently running.
     */
    public val isRunning: Boolean
}
