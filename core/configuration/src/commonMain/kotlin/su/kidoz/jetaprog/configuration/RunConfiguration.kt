package su.kidoz.jetaprog.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unique identifier for a run configuration.
 */
@JvmInline
@Serializable
public value class ConfigurationId(
    public val value: String,
) {
    public companion object {
        /**
         * Generate a new unique configuration ID.
         */
        public fun generate(): ConfigurationId = ConfigurationId(generateUuid())

        private fun generateUuid(): String {
            val chars = "0123456789abcdef"
            val sections = listOf(8, 4, 4, 4, 12)
            return sections.joinToString("-") { length ->
                (1..length).map { chars.random() }.joinToString("")
            }
        }
    }
}

/**
 * Run configuration type identifier.
 */
@Serializable
public enum class ConfigurationType(
    public val displayName: String,
    public val icon: String,
) {
    /** Gradle build task. */
    @SerialName("gradle")
    GRADLE("Gradle", "gradle"),

    /** Meson build. */
    @SerialName("meson_build")
    MESON_BUILD("Meson Build", "meson"),

    /** Meson run executable. */
    @SerialName("meson_run")
    MESON_RUN("Meson Run", "meson"),

    /** Python script. */
    @SerialName("python")
    PYTHON("Python", "python"),

    /** Poetry command. */
    @SerialName("poetry")
    POETRY("Poetry", "poetry"),

    /** uv command. */
    @SerialName("uv")
    UV("uv", "uv"),

    /** Cargo build. */
    @SerialName("cargo_build")
    CARGO_BUILD("Cargo Build", "rust"),

    /** Cargo run. */
    @SerialName("cargo_run")
    CARGO_RUN("Cargo Run", "rust"),

    /** Cargo test. */
    @SerialName("cargo_test")
    CARGO_TEST("Cargo Test", "rust"),

    /** Cargo clippy. */
    @SerialName("cargo_clippy")
    CARGO_CLIPPY("Cargo Clippy", "rust"),

    /** .NET build. */
    @SerialName("dotnet_build")
    DOTNET_BUILD(".NET Build", "dotnet"),

    /** .NET run. */
    @SerialName("dotnet_run")
    DOTNET_RUN(".NET Run", "dotnet"),

    /** .NET test. */
    @SerialName("dotnet_test")
    DOTNET_TEST(".NET Test", "dotnet"),

    /** Direct application/executable. */
    @SerialName("application")
    APPLICATION("Application", "application"),

    /** Shell script. */
    @SerialName("shell_script")
    SHELL_SCRIPT("Shell Script", "terminal"),

    /** Compound configuration (runs multiple). */
    @SerialName("compound")
    COMPOUND("Compound", "compound"),

    /** Local Tomcat server. */
    @SerialName("tomcat_local")
    TOMCAT_LOCAL("Tomcat Local", "tomcat"),

    /** Remote Tomcat server. */
    @SerialName("tomcat_remote")
    TOMCAT_REMOTE("Tomcat Remote", "tomcat"),

    /** Spring Boot application. */
    @SerialName("spring_boot")
    SPRING_BOOT("Spring Boot", "spring"),

    /** Docker build. */
    @SerialName("docker_build")
    DOCKER_BUILD("Docker Build", "docker"),

    /** Docker run. */
    @SerialName("docker_run")
    DOCKER_RUN("Docker Run", "docker"),

    /** Docker Compose. */
    @SerialName("docker_compose")
    DOCKER_COMPOSE("Docker Compose", "docker"),
}

/**
 * A run/debug configuration.
 *
 * Similar to IntelliJ IDEA's run configurations, this defines how to
 * build, run, or test a project or specific target.
 */
@Serializable
public data class RunConfiguration(
    /** Unique identifier. */
    val id: ConfigurationId,
    /** Display name. */
    val name: String,
    /** Configuration type. */
    val type: ConfigurationType,
    /** Whether this is a temporary (auto-generated) configuration. */
    val isTemporary: Boolean = false,
    /** Type-specific settings. */
    val settings: ConfigurationSettings,
    /** Tasks to run before this configuration. */
    val beforeLaunch: List<BeforeLaunchTask> = emptyList(),
    /** Whether to allow running multiple instances in parallel. */
    val allowParallelRun: Boolean = false,
    /** Whether to store as project file (for VCS sharing). */
    val storeAsProjectFile: Boolean = true,
    /** Optional folder for grouping configurations. */
    val folderName: String? = null,
)

/**
 * Type-specific configuration settings.
 */
@Serializable
public sealed interface ConfigurationSettings {
    /**
     * Gradle task configuration.
     */
    @Serializable
    @SerialName("gradle")
    public data class Gradle(
        /** Task path (e.g., ":app:desktop:run"). */
        val taskPath: String,
        /** Additional Gradle arguments. */
        val arguments: List<String> = emptyList(),
        /** JVM arguments for Gradle. */
        val jvmArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory override. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Meson build configuration.
     */
    @Serializable
    @SerialName("meson_build")
    public data class MesonBuild(
        /** Build directory path. */
        val buildDirectory: String = "builddir",
        /** Specific target to build (null = all). */
        val target: String? = null,
        /** Build type. */
        val buildType: MesonBuildType = MesonBuildType.DEBUG,
        /** Additional meson compile arguments. */
        val arguments: List<String> = emptyList(),
    ) : ConfigurationSettings

    /**
     * Meson run executable configuration.
     */
    @Serializable
    @SerialName("meson_run")
    public data class MesonRun(
        /** Build directory path. */
        val buildDirectory: String = "builddir",
        /** Executable target name. */
        val executable: String,
        /** Program arguments. */
        val programArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory override. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Python script configuration.
     */
    @Serializable
    @SerialName("python")
    public data class Python(
        /** Path to the Python script to run. */
        val scriptPath: String,
        /** Python interpreter path (e.g., "python3", "/usr/bin/python"). */
        val pythonInterpreter: String = "python3",
        /** Script arguments. */
        val scriptArguments: List<String> = emptyList(),
        /** Python interpreter arguments (before script). */
        val interpreterArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
        /** PYTHONPATH additions. */
        val pythonPath: List<String> = emptyList(),
        /** Module run mode (-m module). */
        val module: String? = null,
    ) : ConfigurationSettings

    /**
     * Poetry command configuration.
     */
    @Serializable
    @SerialName("poetry")
    public data class Poetry(
        /** Poetry command (e.g., "run", "install", "build"). */
        val command: PoetryCommand,
        /** Command arguments. */
        val arguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * uv command configuration.
     */
    @Serializable
    @SerialName("uv")
    public data class Uv(
        /** uv command (e.g., "run", "sync", "pip install"). */
        val command: UvCommand,
        /** Command arguments. */
        val arguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Cargo build configuration.
     */
    @Serializable
    @SerialName("cargo_build")
    public data class CargoBuild(
        /** Build profile (debug, release). */
        val profile: CargoProfileType = CargoProfileType.DEBUG,
        /** Target triple for cross-compilation. */
        val target: String? = null,
        /** Features to enable. */
        val features: List<String> = emptyList(),
        /** Enable all features. */
        val allFeatures: Boolean = false,
        /** Disable default features. */
        val noDefaultFeatures: Boolean = false,
        /** Specific package in workspace. */
        val package_: String? = null,
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Cargo run configuration.
     */
    @Serializable
    @SerialName("cargo_run")
    public data class CargoRun(
        /** Build profile (debug, release). */
        val profile: CargoProfileType = CargoProfileType.DEBUG,
        /** Binary to run (for multi-binary projects). */
        val bin: String? = null,
        /** Example to run. */
        val example: String? = null,
        /** Program arguments (passed after --). */
        val programArguments: List<String> = emptyList(),
        /** Features to enable. */
        val features: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Cargo test configuration.
     */
    @Serializable
    @SerialName("cargo_test")
    public data class CargoTest(
        /** Specific test name or pattern. */
        val testName: String? = null,
        /** Build profile. */
        val profile: CargoProfileType = CargoProfileType.DEBUG,
        /** Specific package in workspace. */
        val package_: String? = null,
        /** Only run library tests. */
        val lib: Boolean = false,
        /** Only run doc tests. */
        val doc: Boolean = false,
        /** Don't capture stdout/stderr. */
        val nocapture: Boolean = false,
        /** Additional test arguments. */
        val testArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Cargo clippy configuration.
     */
    @Serializable
    @SerialName("cargo_clippy")
    public data class CargoClippy(
        /** Automatically apply suggestions. */
        val fix: Boolean = false,
        /** Specific package in workspace. */
        val package_: String? = null,
        /** Check all targets. */
        val allTargets: Boolean = true,
        /** Treat warnings as errors. */
        val denyWarnings: Boolean = false,
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * .NET build configuration.
     */
    @Serializable
    @SerialName("dotnet_build")
    public data class DotNetBuild(
        /** Solution, project, or directory target. */
        val targetPath: String? = null,
        /** Build configuration. */
        val configuration: DotNetConfigurationType = DotNetConfigurationType.DEBUG,
        /** Skip package restore. */
        val noRestore: Boolean = false,
        /** Additional dotnet build arguments. */
        val arguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * .NET run configuration.
     */
    @Serializable
    @SerialName("dotnet_run")
    public data class DotNetRun(
        /** Project file to run. */
        val projectPath: String? = null,
        /** Build configuration. */
        val configuration: DotNetConfigurationType = DotNetConfigurationType.DEBUG,
        /** Skip package restore. */
        val noRestore: Boolean = false,
        /** Program arguments passed after --. */
        val programArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * .NET test configuration.
     */
    @Serializable
    @SerialName("dotnet_test")
    public data class DotNetTest(
        /** Solution, project, or directory target. */
        val targetPath: String? = null,
        /** Build configuration. */
        val configuration: DotNetConfigurationType = DotNetConfigurationType.DEBUG,
        /** Test filter expression. */
        val filter: String? = null,
        /** Skip build before testing. */
        val noBuild: Boolean = false,
        /** Additional dotnet test arguments. */
        val arguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Direct application/executable configuration.
     */
    @Serializable
    @SerialName("application")
    public data class Application(
        /** Path to executable. */
        val executablePath: String,
        /** Program arguments. */
        val programArguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Shell script configuration.
     */
    @Serializable
    @SerialName("shell_script")
    public data class ShellScript(
        /** Script path or inline script. */
        val script: String,
        /** Whether script is a file path (true) or inline content (false). */
        val isFile: Boolean = true,
        /** Script interpreter (e.g., "/bin/bash", "/bin/sh"). */
        val interpreter: String? = null,
        /** Script arguments. */
        val arguments: List<String> = emptyList(),
        /** Environment variables. */
        val environment: Map<String, String> = emptyMap(),
        /** Working directory. */
        val workingDirectory: String? = null,
    ) : ConfigurationSettings

    /**
     * Compound configuration that runs multiple configurations.
     */
    @Serializable
    @SerialName("compound")
    public data class Compound(
        /** IDs of configurations to run. */
        val configurationIds: List<ConfigurationId>,
        /** Whether to run in parallel or sequentially. */
        val parallel: Boolean = false,
    ) : ConfigurationSettings
}

/**
 * .NET build configuration type.
 */
@Serializable
public enum class DotNetConfigurationType(
    public val value: String,
    public val displayName: String,
) {
    /** Debug configuration. */
    @SerialName("debug")
    DEBUG("Debug", "Debug"),

    /** Release configuration. */
    @SerialName("release")
    RELEASE("Release", "Release"),
}

/**
 * Meson build type.
 */
@Serializable
public enum class MesonBuildType(
    public val value: String,
) {
    @SerialName("debug")
    DEBUG("debug"),

    @SerialName("release")
    RELEASE("release"),

    @SerialName("debugoptimized")
    DEBUG_OPTIMIZED("debugoptimized"),

    @SerialName("minsize")
    MIN_SIZE("minsize"),
}

/**
 * Poetry command type.
 */
@Serializable
public enum class PoetryCommand(
    public val value: String,
    public val displayName: String,
) {
    /** Run a script or command in the virtual environment. */
    @SerialName("run")
    RUN("run", "Run"),

    /** Install dependencies. */
    @SerialName("install")
    INSTALL("install", "Install"),

    /** Update dependencies. */
    @SerialName("update")
    UPDATE("update", "Update"),

    /** Add a dependency. */
    @SerialName("add")
    ADD("add", "Add"),

    /** Remove a dependency. */
    @SerialName("remove")
    REMOVE("remove", "Remove"),

    /** Build the package. */
    @SerialName("build")
    BUILD("build", "Build"),

    /** Show dependency tree. */
    @SerialName("show")
    SHOW("show", "Show"),

    /** Update lock file. */
    @SerialName("lock")
    LOCK("lock", "Lock"),

    /** Export to requirements.txt. */
    @SerialName("export")
    EXPORT("export", "Export"),

    /** Open shell in virtual environment. */
    @SerialName("shell")
    SHELL("shell", "Shell"),
}

/**
 * uv command type.
 */
@Serializable
public enum class UvCommand(
    public val value: String,
    public val displayName: String,
) {
    /** Run a command in the project environment. */
    @SerialName("run")
    RUN("run", "Run"),

    /** Sync dependencies from lock file. */
    @SerialName("sync")
    SYNC("sync", "Sync"),

    /** Update lock file. */
    @SerialName("lock")
    LOCK("lock", "Lock"),

    /** Add a dependency. */
    @SerialName("add")
    ADD("add", "Add"),

    /** Remove a dependency. */
    @SerialName("remove")
    REMOVE("remove", "Remove"),

    /** Install packages (pip). */
    @SerialName("pip_install")
    PIP_INSTALL("pip install", "Pip Install"),

    /** Uninstall packages (pip). */
    @SerialName("pip_uninstall")
    PIP_UNINSTALL("pip uninstall", "Pip Uninstall"),

    /** Compile requirements. */
    @SerialName("pip_compile")
    PIP_COMPILE("pip compile", "Pip Compile"),

    /** Sync with requirements. */
    @SerialName("pip_sync")
    PIP_SYNC("pip sync", "Pip Sync"),

    /** Create virtual environment. */
    @SerialName("venv")
    VENV("venv", "Create Venv"),
}

/**
 * Task to run before launching a configuration.
 */
@Serializable
public sealed interface BeforeLaunchTask {
    /**
     * Run another configuration first.
     */
    @Serializable
    @SerialName("run_configuration")
    public data class RunConfiguration(
        val configurationId: ConfigurationId,
    ) : BeforeLaunchTask

    /**
     * Run a Gradle task.
     */
    @Serializable
    @SerialName("gradle_task")
    public data class GradleTask(
        val taskPath: String,
    ) : BeforeLaunchTask

    /**
     * Run a Poetry command.
     */
    @Serializable
    @SerialName("poetry_command")
    public data class PoetryTask(
        val command: PoetryCommand,
        val arguments: List<String> = emptyList(),
    ) : BeforeLaunchTask

    /**
     * Run a uv command.
     */
    @Serializable
    @SerialName("uv_command")
    public data class UvTask(
        val command: UvCommand,
        val arguments: List<String> = emptyList(),
    ) : BeforeLaunchTask

    /**
     * Build the project.
     */
    @Serializable
    @SerialName("build")
    public data object Build : BeforeLaunchTask

    /**
     * Run a shell command.
     */
    @Serializable
    @SerialName("shell_command")
    public data class ShellCommand(
        val command: String,
    ) : BeforeLaunchTask

    /**
     * Run cargo build.
     */
    @Serializable
    @SerialName("cargo_build")
    public data class CargoBuild(
        val profile: CargoProfileType = CargoProfileType.DEBUG,
    ) : BeforeLaunchTask

    /**
     * Run cargo clippy.
     */
    @Serializable
    @SerialName("cargo_clippy")
    public data object CargoClippy : BeforeLaunchTask
}

/**
 * Cargo build profile.
 */
@Serializable
public enum class CargoProfileType(
    public val value: String,
    public val displayName: String,
) {
    /** Debug build (default, fast compile, no optimizations). */
    @SerialName("debug")
    DEBUG("debug", "Debug"),

    /** Release build (slow compile, optimized). */
    @SerialName("release")
    RELEASE("release", "Release"),
}
