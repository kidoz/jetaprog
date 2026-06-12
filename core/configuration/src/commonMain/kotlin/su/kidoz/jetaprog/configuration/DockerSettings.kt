package su.kidoz.jetaprog.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Docker build configuration settings.
 */
@Serializable
@SerialName("docker_build")
public data class DockerBuildSettings(
    /** Path to the Dockerfile. */
    val dockerfile: String = "Dockerfile",
    /** Build context path (directory containing files to build). */
    val contextPath: String = ".",
    /** Image name. */
    val imageName: String,
    /** Image tag. */
    val imageTag: String = "latest",
    /** Build arguments. */
    val buildArgs: Map<String, String> = emptyMap(),
    /** Target build stage (for multi-stage builds). */
    val target: String? = null,
    /** Whether to use no-cache. */
    val noCache: Boolean = false,
    /** Whether to pull latest base images. */
    val pull: Boolean = false,
    /** Platform to build for (e.g., "linux/amd64"). */
    val platform: String? = null,
    /** Additional labels. */
    val labels: Map<String, String> = emptyMap(),
    /** Build secrets. */
    val secrets: List<DockerBuildSecret> = emptyList(),
    /** SSH agents to forward. */
    val sshAgents: List<String> = emptyList(),
    /** Network mode for RUN instructions. */
    val network: String? = null,
    /** Output format (docker, tar, etc.). */
    val output: String? = null,
) : ConfigurationSettings {
    /**
     * Get the full image reference.
     */
    public val imageReference: String get() = "$imageName:$imageTag"
}

/**
 * A build secret for Docker build.
 */
@Serializable
public data class DockerBuildSecret(
    /** Secret ID. */
    val id: String,
    /** Source file path. */
    val src: String? = null,
    /** Environment variable name. */
    val env: String? = null,
)

/**
 * Docker run configuration settings.
 */
@Serializable
@SerialName("docker_run")
public data class DockerRunSettings(
    /** Image name (with optional tag). */
    val imageName: String,
    /** Container name (optional). */
    val containerName: String? = null,
    /** Port mappings. */
    val portMappings: List<PortMapping> = emptyList(),
    /** Volume mappings. */
    val volumeMappings: List<VolumeMapping> = emptyList(),
    /** Environment variables. */
    val environment: Map<String, String> = emptyMap(),
    /** Environment file path. */
    val envFile: String? = null,
    /** Command to run (overrides image CMD). */
    val command: String? = null,
    /** Entrypoint override. */
    val entrypoint: String? = null,
    /** Working directory inside container. */
    val workdir: String? = null,
    /** User to run as. */
    val user: String? = null,
    /** Network mode. */
    val network: String? = null,
    /** Networks to connect to. */
    val networks: List<String> = emptyList(),
    /** Whether to remove container on exit. */
    val rm: Boolean = true,
    /** Whether to run in detached mode. */
    val detach: Boolean = false,
    /** Whether to allocate a TTY. */
    val tty: Boolean = true,
    /** Whether to run interactively. */
    val interactive: Boolean = true,
    /** Whether to run privileged. */
    val privileged: Boolean = false,
    /** Capabilities to add. */
    val capAdd: List<String> = emptyList(),
    /** Capabilities to drop. */
    val capDrop: List<String> = emptyList(),
    /** Memory limit (e.g., "512m"). */
    val memory: String? = null,
    /** CPU limit (e.g., "0.5"). */
    val cpus: String? = null,
    /** Restart policy. */
    val restart: RestartPolicy = RestartPolicy.NO,
    /** Labels. */
    val labels: Map<String, String> = emptyMap(),
    /** Hostname. */
    val hostname: String? = null,
    /** Extra hosts entries. */
    val extraHosts: Map<String, String> = emptyMap(),
    /** DNS servers. */
    val dns: List<String> = emptyList(),
    /** Security options. */
    val securityOpt: List<String> = emptyList(),
) : ConfigurationSettings

/**
 * Port mapping for Docker containers.
 */
@Serializable
public data class PortMapping(
    /** Host port. */
    val hostPort: Int,
    /** Container port. */
    val containerPort: Int,
    /** Protocol (tcp or udp). */
    val protocol: String = "tcp",
    /** Host IP to bind to (optional). */
    val hostIp: String? = null,
) {
    /**
     * Format as Docker port mapping string.
     */
    public fun toDockerFormat(): String {
        val host = if (hostIp != null) "$hostIp:$hostPort" else hostPort.toString()
        return "$host:$containerPort/$protocol"
    }
}

/**
 * Volume mapping for Docker containers.
 */
@Serializable
public data class VolumeMapping(
    /** Host path or volume name. */
    val hostPath: String,
    /** Container path. */
    val containerPath: String,
    /** Whether the volume is read-only. */
    val readOnly: Boolean = false,
    /** Volume options. */
    val options: String? = null,
) {
    /**
     * Format as Docker volume mapping string.
     */
    public fun toDockerFormat(): String =
        buildString {
            append(hostPath)
            append(':')
            append(containerPath)
            if (readOnly) append(":ro")
            options?.let { append(",$it") }
        }
}

/**
 * Docker container restart policy.
 */
@Serializable
public enum class RestartPolicy {
    @SerialName("no")
    NO,

    @SerialName("always")
    ALWAYS,

    @SerialName("on-failure")
    ON_FAILURE,

    @SerialName("unless-stopped")
    UNLESS_STOPPED,
}

/**
 * Docker Compose configuration settings.
 */
@Serializable
@SerialName("docker_compose")
public data class DockerComposeSettings(
    /** Path to the docker-compose.yml file. */
    val composeFile: String = "docker-compose.yml",
    /** Additional compose files to include. */
    val additionalFiles: List<String> = emptyList(),
    /** Services to start (empty = all). */
    val services: List<String> = emptyList(),
    /** Project name. */
    val projectName: String? = null,
    /** Environment file. */
    val envFile: String? = null,
    /** Whether to build images before starting. */
    val build: Boolean = false,
    /** Whether to force recreate containers. */
    val forceRecreate: Boolean = false,
    /** Whether to remove orphan containers. */
    val removeOrphans: Boolean = true,
    /** Operation to perform. */
    val operation: ComposeOperation = ComposeOperation.UP,
    /** Scale settings for services. */
    val scale: Map<String, Int> = emptyMap(),
    /** Profiles to enable. */
    val profiles: List<String> = emptyList(),
) : ConfigurationSettings

/**
 * Docker Compose operation.
 */
@Serializable
public enum class ComposeOperation {
    @SerialName("up")
    UP,

    @SerialName("down")
    DOWN,

    @SerialName("start")
    START,

    @SerialName("stop")
    STOP,

    @SerialName("restart")
    RESTART,

    @SerialName("build")
    BUILD,

    @SerialName("pull")
    PULL,

    @SerialName("logs")
    LOGS,

    @SerialName("ps")
    PS,

    @SerialName("exec")
    EXEC,
}
