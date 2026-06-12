package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Settings for external tools, build systems, and MCP servers.
 */
@Serializable
public data class ToolsSettings(
    /**
     * Build system configurations.
     */
    val buildSystems: BuildSystemsConfig = BuildSystemsConfig(),
    /**
     * MCP server configurations keyed by server ID.
     */
    val mcpServers: Map<String, McpServerConfig> = emptyMap(),
    /**
     * Custom external tool definitions.
     */
    val externalTools: List<ExternalToolConfig> = emptyList(),
) {
    public companion object {
        public val DEFAULT: ToolsSettings = ToolsSettings()
    }
}

/**
 * Build systems configuration.
 */
@Serializable
public data class BuildSystemsConfig(
    /**
     * Auto-detect build systems in project.
     */
    val autoDetect: Boolean = true,
    /**
     * Default build system to use.
     */
    val defaultBuildSystem: String? = null,
    /**
     * Gradle-specific configuration.
     */
    val gradle: GradleConfig = GradleConfig(),
    /**
     * Meson-specific configuration.
     */
    val meson: MesonConfig = MesonConfig(),
    /**
     * CMake-specific configuration.
     */
    val cmake: CMakeConfig = CMakeConfig(),
)

/**
 * Gradle build system configuration.
 */
@Serializable
public data class GradleConfig(
    /**
     * Enable Gradle integration.
     */
    val enabled: Boolean = true,
    /**
     * Path to Gradle installation (null = use wrapper or system).
     */
    val gradleHome: String? = null,
    /**
     * JDK to use for Gradle.
     */
    val jdk: String? = null,
    /**
     * Use Gradle Wrapper if available.
     */
    val useWrapper: Boolean = true,
    /**
     * Run Gradle in offline mode.
     */
    val offlineMode: Boolean = false,
    /**
     * Enable parallel project execution.
     */
    val parallelBuilds: Boolean = true,
    /**
     * JVM arguments for Gradle daemon.
     */
    val jvmArgs: String? = null,
)

/**
 * Meson build system configuration.
 */
@Serializable
public data class MesonConfig(
    /**
     * Enable Meson integration.
     */
    val enabled: Boolean = true,
    /**
     * Path to meson executable.
     */
    val executable: String = "meson",
    /**
     * Build directory name.
     */
    val buildDir: String = "build",
    /**
     * Build type.
     */
    val buildType: MesonBuildType = MesonBuildType.DEBUG,
    /**
     * Build backend.
     */
    val backend: MesonBackend = MesonBackend.NINJA,
)

/**
 * Meson build types.
 */
@Serializable
public enum class MesonBuildType(
    public val value: String,
) {
    DEBUG("debug"),
    DEBUG_OPTIMIZED("debugoptimized"),
    RELEASE("release"),
    MIN_SIZE("minsize"),
}

/**
 * Meson build backends.
 */
@Serializable
public enum class MesonBackend(
    public val value: String,
) {
    NINJA("ninja"),
    VS("vs"),
    XCODE("xcode"),
}

/**
 * CMake build system configuration.
 */
@Serializable
public data class CMakeConfig(
    /**
     * Enable CMake integration.
     */
    val enabled: Boolean = true,
    /**
     * Path to cmake executable.
     */
    val executable: String = "cmake",
    /**
     * CMake generator.
     */
    val generator: String? = null,
    /**
     * Build directory name.
     */
    val buildDir: String = "build",
    /**
     * Build type.
     */
    val buildType: CMakeBuildType = CMakeBuildType.DEBUG,
)

/**
 * CMake build types.
 */
@Serializable
public enum class CMakeBuildType(
    public val value: String,
) {
    DEBUG("Debug"),
    RELEASE("Release"),
    REL_WITH_DEB_INFO("RelWithDebInfo"),
    MIN_SIZE_REL("MinSizeRel"),
}

/**
 * MCP (Model Context Protocol) server configuration.
 */
@Serializable
public data class McpServerConfig(
    /**
     * Whether this MCP server is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Display name for the server.
     */
    val name: String,
    /**
     * Command to start the MCP server.
     */
    val command: String,
    /**
     * Command-line arguments.
     */
    val args: List<String> = emptyList(),
    /**
     * Environment variables.
     */
    val env: Map<String, String> = emptyMap(),
    /**
     * Working directory for the server.
     */
    val workingDirectory: String? = null,
    /**
     * Start server automatically with IDE.
     */
    val autoStart: Boolean = false,
    /**
     * Transport protocol.
     */
    val transport: McpTransport = McpTransport.STDIO,
    /**
     * Connection timeout in milliseconds.
     */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 30000L
    }
}

/**
 * MCP transport protocols.
 */
@Serializable
public enum class McpTransport {
    STDIO,
    SSE,
    WEBSOCKET,
}

/**
 * External tool configuration.
 */
@Serializable
public data class ExternalToolConfig(
    /**
     * Unique identifier for the tool.
     */
    val id: String,
    /**
     * Display name of the tool.
     */
    val name: String,
    /**
     * Tool description.
     */
    val description: String = "",
    /**
     * Command to execute.
     */
    val command: String,
    /**
     * Command-line arguments.
     */
    val args: List<String> = emptyList(),
    /**
     * Working directory (supports variables like $PROJECT_DIR).
     */
    val workingDirectory: String? = null,
    /**
     * Environment variables.
     */
    val env: Map<String, String> = emptyMap(),
    /**
     * Show in Tools menu.
     */
    val showInMenu: Boolean = true,
    /**
     * Show output in console.
     */
    val showOutput: Boolean = true,
    /**
     * File patterns this tool applies to.
     */
    val filePatterns: List<String> = emptyList(),
)
