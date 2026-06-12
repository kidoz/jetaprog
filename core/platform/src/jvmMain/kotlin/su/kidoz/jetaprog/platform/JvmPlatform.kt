package su.kidoz.jetaprog.platform

import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessExecutor

/**
 * JVM implementation of the Platform interface.
 */
public class JvmPlatform : Platform {
    override val type: PlatformType = detectPlatformType()

    override val info: PlatformInfo =
        PlatformInfo(
            type = type,
            osName = System.getProperty("os.name") ?: "Unknown",
            osVersion = System.getProperty("os.version") ?: "Unknown",
            architecture = System.getProperty("os.arch") ?: "Unknown",
            userHome = System.getProperty("user.home") ?: "",
            workingDirectory = System.getProperty("user.dir") ?: "",
            pathSeparator = java.io.File.separator,
            lineSeparator = System.lineSeparator(),
        )

    override val fileSystem: FileSystem = JvmFileSystem()

    override val processExecutor: ProcessExecutor = JvmProcessExecutor()

    private fun detectPlatformType(): PlatformType {
        val osName = System.getProperty("os.name")?.lowercase() ?: return PlatformType.UNKNOWN
        return when {
            osName.contains("win") -> PlatformType.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> PlatformType.MACOS
            osName.contains("linux") || osName.contains("nix") || osName.contains("nux") -> PlatformType.LINUX
            else -> PlatformType.UNKNOWN
        }
    }
}

private val platformInstance: Platform by lazy { JvmPlatform() }

public actual fun currentPlatform(): Platform = platformInstance
