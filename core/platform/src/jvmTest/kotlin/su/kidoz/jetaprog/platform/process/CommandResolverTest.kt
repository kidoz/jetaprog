package su.kidoz.jetaprog.platform.process

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandResolverTest {
    private val windowsEnvironment =
        mapOf(
            "PATH" to "C:\\tools",
            "PATHEXT" to ".COM;.EXE;.BAT;.CMD",
        )

    @Test
    fun resolvesCmdWrapperBeforeExtensionlessScript() {
        val files =
            setOf(
                executable("C:\\tools", "npm"),
                executable("C:\\tools", "npm.CMD"),
            )

        assertEquals(
            executable("C:\\tools", "npm.CMD"),
            CommandResolver.resolve(
                command = "npm",
                environment = windowsEnvironment,
                isWindows = true,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun followsPathextOrder() {
        val files =
            setOf(
                executable("C:\\tools", "gradle.BAT"),
                executable("C:\\tools", "gradle.CMD"),
            )

        assertEquals(
            executable("C:\\tools", "gradle.BAT"),
            CommandResolver.resolve(
                command = "gradle",
                environment = windowsEnvironment,
                isWindows = true,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun fallsBackFromPython3ToPythonOnWindows() {
        val files = setOf(executable("C:\\Python", "python.EXE"))

        assertEquals(
            executable("C:\\Python", "python.EXE"),
            CommandResolver.resolve(
                command = "python3",
                environment = mapOf("PATH" to "C:\\Python", "PATHEXT" to ".EXE"),
                isWindows = true,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun searchesQuotedPathEntries() {
        val files = setOf(executable("C:\\Program Files\\nodejs", "npx.CMD"))

        assertEquals(
            executable("C:\\Program Files\\nodejs", "npx.CMD"),
            CommandResolver.resolve(
                command = "npx",
                environment =
                    mapOf(
                        "PATH" to "\"C:\\Program Files\\nodejs\";C:\\tools",
                        "PATHEXT" to ".CMD",
                    ),
                isWindows = true,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun readsPathAndPathextRegardlessOfCase() {
        val files = setOf(executable("C:\\tools", "npm.CMD"))

        assertEquals(
            executable("C:\\tools", "npm.CMD"),
            CommandResolver.resolve(
                command = "npm",
                environment = mapOf("Path" to "C:\\tools", "Pathext" to ".CMD"),
                isWindows = true,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun leavesCommandsWithPathComponentsUnchanged() {
        val files = emptySet<String>()
        val commands = listOf("C:\\tools\\npm.cmd", "/usr/bin/npm", "./gradlew", "..\\gradlew.bat")

        for (command in commands) {
            assertEquals(
                command,
                CommandResolver.resolve(
                    command = command,
                    environment = windowsEnvironment,
                    isWindows = true,
                    fileExists = files::contains,
                ),
            )
        }
    }

    @Test
    fun leavesCommandsUnchangedOnNonWindowsPlatforms() {
        val files = setOf(executable("/tools", "npm.CMD"))

        assertEquals(
            "npm",
            CommandResolver.resolve(
                command = "npm",
                environment = mapOf("PATH" to "/tools", "PATHEXT" to ".CMD"),
                isWindows = false,
                fileExists = files::contains,
            ),
        )
    }

    @Test
    fun returnsTheOriginalCommandWhenNothingMatches() {
        assertEquals(
            "npm",
            CommandResolver.resolve(
                command = "npm",
                environment = windowsEnvironment,
                isWindows = true,
                fileExists = { false },
            ),
        )
    }

    private fun executable(
        directory: String,
        name: String,
    ): String = File(directory, name).absolutePath
}
