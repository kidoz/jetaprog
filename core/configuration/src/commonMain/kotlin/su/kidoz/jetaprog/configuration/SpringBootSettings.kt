package su.kidoz.jetaprog.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spring Boot application configuration settings.
 */
@Serializable
@SerialName("spring_boot")
public data class SpringBootSettings(
    /** Fully qualified main class name. */
    val mainClass: String,
    /** Path to the module (for multi-module projects). */
    val modulePath: String = "",
    /** Active Spring profiles. */
    val activeProfiles: List<String> = emptyList(),
    /** JVM options (e.g., "-Xmx512m"). */
    val vmOptions: String = "",
    /** Program arguments. */
    val programArguments: String = "",
    /** Environment variables. */
    val environment: Map<String, String> = emptyMap(),
    /** Working directory override. */
    val workingDirectory: String? = null,
    /** Whether to enable debug mode. */
    val enableDebug: Boolean = false,
    /** Debug port (if enableDebug is true). */
    val debugPort: Int = 5005,
    /** Whether to suspend on start when debugging. */
    val debugSuspend: Boolean = true,
    /** Spring Boot configuration file path. */
    val configFile: String? = null,
    /** Additional Spring Boot properties. */
    val springProperties: Map<String, String> = emptyMap(),
    /** Override server port (0 = use application.properties). */
    val serverPort: Int = 0,
    /** Whether to enable Spring DevTools. */
    val enableDevTools: Boolean = true,
    /** Whether to enable live reload. */
    val enableLiveReload: Boolean = true,
    /** Use Gradle task to run instead of direct launch. */
    val useGradleTask: Boolean = false,
    /** Gradle task to use if useGradleTask is true. */
    val gradleTask: String = "bootRun",
) : ConfigurationSettings {
    /**
     * Build Spring profiles argument.
     */
    public fun buildProfilesArg(): String? =
        if (activeProfiles.isNotEmpty()) {
            "--spring.profiles.active=${activeProfiles.joinToString(",")}"
        } else {
            null
        }

    /**
     * Build JVM arguments for debugging.
     */
    public fun buildDebugArgs(): String =
        if (enableDebug) {
            val suspend = if (debugSuspend) "y" else "n"
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=*:$debugPort"
        } else {
            ""
        }

    /**
     * Get all JVM arguments combined.
     */
    public fun getAllVmOptions(): String =
        buildList {
            if (vmOptions.isNotBlank()) add(vmOptions)
            if (enableDebug) add(buildDebugArgs())
        }.joinToString(" ")
}

/**
 * Spring Boot development server settings.
 */
@Serializable
@SerialName("spring_boot_dev")
public data class SpringBootDevServerSettings(
    /** Base Spring Boot settings. */
    val baseSettings: SpringBootSettings,
    /** Enable automatic restart on file changes. */
    val autoRestart: Boolean = true,
    /** Trigger file path for manual restart. */
    val triggerFile: String? = null,
    /** Excluded paths from restart monitoring. */
    val excludePaths: List<String> = listOf("static/**", "public/**"),
    /** Additional watch paths. */
    val additionalWatchPaths: List<String> = emptyList(),
) : ConfigurationSettings
