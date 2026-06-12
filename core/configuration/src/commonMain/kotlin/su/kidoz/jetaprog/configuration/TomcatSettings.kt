package su.kidoz.jetaprog.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tomcat server configuration settings.
 */
@Serializable
@SerialName("tomcat_local")
public data class TomcatLocalSettings(
    /** Tomcat installation directory. */
    val tomcatHome: String,
    /** Deployment source for the application. */
    val deploymentSource: DeploymentSource,
    /** HTTP port for the server. */
    val httpPort: Int = 8080,
    /** HTTPS port (0 to disable). */
    val httpsPort: Int = 0,
    /** AJP port (0 to disable). */
    val ajpPort: Int = 0,
    /** JMX port (0 to disable). */
    val jmxPort: Int = 0,
    /** JVM options for the server. */
    val jvmOptions: String = "",
    /** Environment variables. */
    val environment: Map<String, String> = emptyMap(),
    /** Context path for the application. */
    val contextPath: String = "/",
    /** Whether to deploy on startup. */
    val deployOnStartup: Boolean = true,
    /** Whether to open browser after deployment. */
    val openBrowserOnStart: Boolean = false,
    /** Browser URL to open (if openBrowserOnStart is true). */
    val browserUrl: String? = null,
    /** Whether to preserve sessions on redeploy. */
    val preserveSessionsOnRedeploy: Boolean = true,
) : ConfigurationSettings

/**
 * Remote Tomcat server configuration.
 */
@Serializable
@SerialName("tomcat_remote")
public data class TomcatRemoteSettings(
    /** Remote server host. */
    val host: String,
    /** HTTP port. */
    val httpPort: Int = 8080,
    /** Manager application path. */
    val managerPath: String = "/manager/text",
    /** Username for manager application. */
    val username: String,
    /** Password for manager application. */
    val password: String,
    /** Deployment source. */
    val deploymentSource: DeploymentSource,
    /** Context path for the application. */
    val contextPath: String = "/",
    /** Whether to use HTTPS for manager connection. */
    val useHttps: Boolean = false,
) : ConfigurationSettings

/**
 * Source for deployment artifacts.
 */
@Serializable
public sealed interface DeploymentSource {
    /**
     * Deploy a WAR file.
     */
    @Serializable
    @SerialName("war_file")
    public data class WarFile(
        /** Path to the WAR file. */
        val path: String,
    ) : DeploymentSource

    /**
     * Deploy an exploded WAR directory.
     */
    @Serializable
    @SerialName("exploded_war")
    public data class ExplodedWar(
        /** Path to the exploded WAR directory. */
        val path: String,
    ) : DeploymentSource

    /**
     * Deploy artifact produced by a Gradle task.
     */
    @Serializable
    @SerialName("gradle_artifact")
    public data class GradleArtifact(
        /** Gradle task path that produces the artifact. */
        val taskPath: String,
        /** Path to the artifact relative to the build directory. */
        val artifactPath: String = "libs",
    ) : DeploymentSource

    /**
     * Deploy artifact produced by a Maven goal.
     */
    @Serializable
    @SerialName("maven_artifact")
    public data class MavenArtifact(
        /** Maven goal to execute. */
        val goal: String = "package",
        /** Path to the artifact relative to target directory. */
        val artifactPath: String = "",
    ) : DeploymentSource
}
