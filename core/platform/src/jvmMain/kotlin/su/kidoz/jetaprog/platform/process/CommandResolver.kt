package su.kidoz.jetaprog.platform.process

import java.io.File

/**
 * Resolves bare command names to executables using the Windows `PATH` and `PATHEXT`.
 *
 * `ProcessBuilder` reaches `CreateProcess` directly on Windows, which does not apply
 * `PATHEXT`. Tools installed as `npm.cmd`, `gradle.bat`, or `kotlin-language-server.bat`
 * therefore fail to start unless the resolved path is supplied.
 */
public object CommandResolver {
    /**
     * Resolves [command] against [environment] when running on Windows.
     *
     * @param command executable name or path.
     * @param environment environment variables used for the lookup; defaults to the process environment.
     * @return the absolute executable path when found, otherwise [command] unchanged.
     */
    public fun resolve(
        command: String,
        environment: Map<String, String> = System.getenv(),
    ): String =
        resolve(
            command = command,
            environment = environment,
            isWindows = isWindows(),
            fileExists = { path -> File(path).isFile },
        )

    internal fun resolve(
        command: String,
        environment: Map<String, String>,
        isWindows: Boolean,
        fileExists: (String) -> Boolean,
    ): String {
        if (!isWindows || command.isBlank() || command.hasPathComponent()) return command

        val path =
            environment.valueIgnoreCase(PATH_ENV)?.takeIf { it.isNotBlank() }
                ?: System.getenv(PATH_ENV).orEmpty()
        if (path.isBlank()) return command

        val directories = path.split(';').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        val extensions =
            environment
                .valueIgnoreCase(PATHEXT_ENV)
                ?.split(';')
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_PATHEXT

        findExecutable(command, directories, extensions, fileExists)?.let { return it }

        // Python 3 installs as `python.exe` on Windows, so older configurations keep working.
        for (fallback in WINDOWS_FALLBACK_EXECUTABLES[command.lowercase()].orEmpty()) {
            findExecutable(fallback, directories, extensions, fileExists)?.let { return it }
        }

        return command
    }

    private fun findExecutable(
        command: String,
        directories: List<String>,
        extensions: List<String>,
        fileExists: (String) -> Boolean,
    ): String? {
        val names = executableNames(command, extensions)
        for (directory in directories) {
            for (name in names) {
                val candidate = File(directory, name).absolutePath
                if (fileExists(candidate)) return candidate
            }
        }
        return null
    }

    /**
     * Builds the names to probe for [command]. PATHEXT wrappers come first so a Windows
     * install that ships both a POSIX script and `npm.cmd` launches the executable one.
     */
    private fun executableNames(
        command: String,
        extensions: List<String>,
    ): List<String> {
        val hasExtension = command.substringAfterLast('.', missingDelimiterValue = "").isNotEmpty()
        if (hasExtension) return listOf(command)
        val suffixed =
            extensions.map { extension ->
                if (extension.startsWith('.')) "$command$extension" else "$command.$extension"
            }
        return suffixed + command
    }

    private fun String.hasPathComponent(): Boolean = contains('/') || contains('\\') || contains(':')

    private fun Map<String, String>.valueIgnoreCase(key: String): String? =
        entries.firstOrNull { entry -> entry.key.equals(key, ignoreCase = true) }?.value

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("win", ignoreCase = true)

    private val DEFAULT_PATHEXT: List<String> = listOf(".COM", ".EXE", ".BAT", ".CMD")
    private val WINDOWS_FALLBACK_EXECUTABLES: Map<String, List<String>> = mapOf("python3" to listOf("python", "py"))
    private const val PATH_ENV: String = "PATH"
    private const val PATHEXT_ENV: String = "PATHEXT"
}

/**
 * Returns this command list with its executable resolved for the host platform.
 *
 * @param environment environment overrides used for the lookup.
 */
public fun List<String>.withResolvedExecutable(environment: Map<String, String> = emptyMap()): List<String> =
    if (isEmpty()) this else listOf(CommandResolver.resolve(first(), environment)) + drop(1)
