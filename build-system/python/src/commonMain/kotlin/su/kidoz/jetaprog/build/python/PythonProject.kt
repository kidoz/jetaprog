package su.kidoz.jetaprog.build.python

/**
 * Represents a Python project.
 */
public data class PythonProject(
    /** Root path of the Python project. */
    val rootPath: String,
    /** Name of the project (from pyproject.toml or setup.py). */
    val name: String = "",
    /** Project version. */
    val version: String = "",
    /** Python version constraint. */
    val pythonVersion: String? = null,
    /** Dependencies of the project. */
    val dependencies: List<PythonDependency> = emptyList(),
    /** Development dependencies. */
    val devDependencies: List<PythonDependency> = emptyList(),
    /** Available scripts defined in pyproject.toml. */
    val scripts: List<PythonScript> = emptyList(),
    /** Path to the virtual environment. */
    val venvPath: String? = null,
    /** The build tool used (Poetry, uv, pip, etc.). */
    val buildTool: PythonBuildTool = PythonBuildTool.UNKNOWN,
)

/**
 * A Python project dependency.
 */
public data class PythonDependency(
    /** Package name. */
    val name: String,
    /** Version constraint (e.g., "^1.0.0", ">=2.0,<3.0"). */
    val version: String? = null,
    /** Extra features to install (e.g., "dev", "test"). */
    val extras: List<String> = emptyList(),
    /** Whether this is an optional dependency. */
    val optional: Boolean = false,
    /** Dependency group (for Poetry groups). */
    val group: String? = null,
)

/**
 * A script defined in the Python project.
 */
public data class PythonScript(
    /** Script name (e.g., "test", "lint", "serve"). */
    val name: String,
    /** Script entry point (e.g., "mypackage:main"). */
    val entryPoint: String? = null,
    /** Script command (for task runner style scripts). */
    val command: String? = null,
)

/**
 * Build tool used for the Python project.
 */
public enum class PythonBuildTool {
    /** Poetry dependency manager. */
    POETRY,

    /** uv package manager. */
    UV,

    /** Standard pip with requirements.txt. */
    PIP,

    /** Pipenv (Pipfile). */
    PIPENV,

    /** PDM (Python Dependency Manager). */
    PDM,

    /** Flit for simple packages. */
    FLIT,

    /** Hatch build system. */
    HATCH,

    /** Unknown or not detected. */
    UNKNOWN,
}

/**
 * Common Python commands and paths.
 */
public object PythonPaths {
    /** Common virtual environment directory name. */
    public const val VENV_DIR: String = ".venv"

    /** Alternative virtual environment directory name. */
    public const val VENV_ALT_DIR: String = "venv"

    /** Poetry virtual environment in project directory. */
    public const val POETRY_VENV: String = ".venv"

    /** pyproject.toml file name. */
    public const val PYPROJECT_TOML: String = "pyproject.toml"

    /** requirements.txt file name. */
    public const val REQUIREMENTS_TXT: String = "requirements.txt"

    /** Poetry lock file. */
    public const val POETRY_LOCK: String = "poetry.lock"

    /** uv lock file. */
    public const val UV_LOCK: String = "uv.lock"

    /** Pipenv lock file. */
    public const val PIPFILE_LOCK: String = "Pipfile.lock"
}
