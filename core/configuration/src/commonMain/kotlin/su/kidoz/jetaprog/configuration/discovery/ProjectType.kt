package su.kidoz.jetaprog.configuration.discovery

/**
 * Detected project type based on project files.
 */
public enum class ProjectType {
    /** Gradle-based project (build.gradle, build.gradle.kts). */
    GRADLE,

    /** Rust/Cargo project (Cargo.toml). */
    CARGO,

    /** Meson build system project (meson.build). */
    MESON,

    /** Python project with pyproject.toml. */
    PYTHON_PYPROJECT,

    /** Python project with setup.py. */
    PYTHON_SETUP,

    /** Poetry-managed Python project. */
    POETRY,

    /** UV-managed Python project. */
    UV,

    /** .NET solution or project. */
    DOTNET,

    /** CMake project (CMakeLists.txt). */
    CMAKE,

    /** Node.js project (package.json). */
    NODEJS,

    /** Go project (go.mod). */
    GO,

    /** Unknown or no build system detected. */
    UNKNOWN,
}

/**
 * Information about a detected project.
 */
public data class DetectedProject(
    /** The type of project detected. */
    val type: ProjectType,
    /** The root path of the project. */
    val rootPath: String,
    /** The main file that triggered detection. */
    val detectionFile: String,
    /** Optional project name extracted from project files. */
    val projectName: String? = null,
    /** Optional main entry point (main file, executable, etc.). */
    val mainEntry: String? = null,
    /** Additional metadata about the project. */
    val metadata: Map<String, String> = emptyMap(),
)
