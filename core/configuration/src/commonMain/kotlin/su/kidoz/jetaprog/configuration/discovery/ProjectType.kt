package su.kidoz.jetaprog.configuration.discovery

/**
 * Detected project type based on project files.
 */
public enum class ProjectType {
    /** Gradle-based project (build.gradle, build.gradle.kts). */
    GRADLE,

    /** Maven-based project (pom.xml). */
    MAVEN,

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

internal const val NODE_PACKAGE_MANAGER_METADATA_KEY: String = "node.packageManager"
internal const val NODE_SCRIPT_METADATA_PREFIX: String = "node.script."
internal const val JAVA_PROJECT_METADATA_KEY: String = "java.project"
internal const val JAVA_RUNNABLE_METADATA_KEY: String = "java.runnable"
internal const val JAVA_MAIN_CLASS_METADATA_KEY: String = "java.mainClass"
internal const val JAVA_EXECUTABLE_METADATA_KEY: String = "java.executable"
internal const val DOTNET_TARGET_PATH_METADATA_KEY: String = "dotnet.targetPath"
internal const val DOTNET_PROJECT_PATH_METADATA_KEY: String = "dotnet.projectPath"
internal const val DOTNET_TEST_TARGET_PATH_METADATA_KEY: String = "dotnet.testTargetPath"
internal const val DOTNET_RUNNABLE_METADATA_KEY: String = "dotnet.runnable"
internal const val DOTNET_TARGET_FRAMEWORK_METADATA_KEY: String = "dotnet.targetFramework"
internal const val DOTNET_ASSEMBLY_NAME_METADATA_KEY: String = "dotnet.assemblyName"
internal const val DOTNET_SDK_METADATA_KEY: String = "dotnet.sdk"
internal const val DOTNET_LAUNCH_PROFILES_METADATA_KEY: String = "dotnet.launchProfiles"
