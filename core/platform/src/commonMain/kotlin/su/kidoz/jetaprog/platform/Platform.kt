package su.kidoz.jetaprog.platform

import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor

/**
 * Represents the type of operating system platform.
 */
public enum class PlatformType {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN,
}

/**
 * Information about the current platform.
 */
public data class PlatformInfo(
    /**
     * The type of platform.
     */
    val type: PlatformType,
    /**
     * The operating system name.
     */
    val osName: String,
    /**
     * The operating system version.
     */
    val osVersion: String,
    /**
     * The system architecture (e.g., "amd64", "aarch64").
     */
    val architecture: String,
    /**
     * The user's home directory path.
     */
    val userHome: String,
    /**
     * The current working directory.
     */
    val workingDirectory: String,
    /**
     * The path separator for this platform ("/" or "\\").
     */
    val pathSeparator: String,
    /**
     * The line separator for this platform.
     */
    val lineSeparator: String,
)

/**
 * Platform abstraction providing access to platform-specific services.
 */
public interface Platform {
    /**
     * The platform type.
     */
    public val type: PlatformType

    /**
     * Detailed platform information.
     */
    public val info: PlatformInfo

    /**
     * The file system service.
     */
    public val fileSystem: FileSystem

    /**
     * The process executor service.
     */
    public val processExecutor: ProcessExecutor
}

/**
 * Returns the current platform instance.
 */
public expect fun currentPlatform(): Platform
