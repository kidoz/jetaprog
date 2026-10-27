package su.kidoz.jetaprog.configuration

import java.io.File

/**
 * Resolves the Gradle launcher for [workingDirectory].
 *
 * Uses the project wrapper when it exists, with the platform-specific name, so builds
 * work on Windows without a shell. Falls back to the `gradle` command, which
 * [CommandResolver][su.kidoz.jetaprog.platform.process.CommandResolver] resolves
 * through `PATHEXT` on Windows.
 */
internal fun gradleCommand(
    workingDirectory: String,
    executable: String? = null,
    osName: String = System.getProperty("os.name").orEmpty(),
): String {
    if (!executable.isNullOrBlank()) return executable
    val wrapperName = if (isWindowsOs(osName)) GRADLE_WINDOWS_WRAPPER else GRADLE_WRAPPER
    val wrapper = File(workingDirectory, wrapperName)
    return if (wrapper.isFile) wrapper.absolutePath else GRADLE_COMMAND
}

/**
 * Resolves the Maven launcher for [workingDirectory].
 *
 * Prefers the project wrapper, with `mvnw.cmd` on Windows, then the `mvn` command.
 */
internal fun mavenCommand(
    workingDirectory: String,
    executable: String? = null,
    osName: String = System.getProperty("os.name").orEmpty(),
): String {
    if (!executable.isNullOrBlank()) return executable
    val wrapperName = if (isWindowsOs(osName)) MAVEN_WINDOWS_WRAPPER else MAVEN_WRAPPER
    val wrapper = File(workingDirectory, wrapperName)
    return if (wrapper.isFile) wrapper.absolutePath else MAVEN_COMMAND
}

/** Default Python interpreter for the host platform. */
internal fun defaultPythonInterpreter(osName: String = System.getProperty("os.name").orEmpty()): String =
    if (isWindowsOs(osName)) WINDOWS_PYTHON_INTERPRETER else UNIX_PYTHON_INTERPRETER

/** True when [osName] names Windows (and never the macOS `Darwin` kernel). */
internal fun isWindowsOs(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
    osName.startsWith("win", ignoreCase = true)

private const val GRADLE_WRAPPER: String = "gradlew"
private const val GRADLE_WINDOWS_WRAPPER: String = "gradlew.bat"
private const val GRADLE_COMMAND: String = "gradle"
private const val MAVEN_WRAPPER: String = "mvnw"
private const val MAVEN_WINDOWS_WRAPPER: String = "mvnw.cmd"
private const val MAVEN_COMMAND: String = "mvn"
private const val WINDOWS_PYTHON_INTERPRETER: String = "python"
private const val UNIX_PYTHON_INTERPRETER: String = "python3"
