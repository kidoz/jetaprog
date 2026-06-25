package su.kidoz.jetaprog.app.terminal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalProcessConfigurationTest {
    @Test
    fun zshStartsAsLoginShellOnUnix() {
        val command =
            TerminalProcessConfiguration.shellCommand(
                osName = "Mac OS X",
                shell = "/bin/zsh",
                comspec = null,
            )

        assertEquals(listOf("/bin/zsh", "-l"), command)
    }

    @Test
    fun fishStartsAsLoginShellOnUnix() {
        val command =
            TerminalProcessConfiguration.shellCommand(
                osName = "Linux",
                shell = "/usr/local/bin/fish",
                comspec = null,
            )

        assertEquals(listOf("/usr/local/bin/fish", "--login"), command)
    }

    @Test
    fun windowsUsesComspecWithoutLoginFlag() {
        val command =
            TerminalProcessConfiguration.shellCommand(
                osName = "Windows 11",
                shell = "/bin/zsh",
                comspec = "C:\\Windows\\System32\\cmd.exe",
            )

        assertEquals(listOf("C:\\Windows\\System32\\cmd.exe"), command)
    }

    @Test
    fun environmentSetsTerminalTypeAndAugmentsPath() {
        val environment =
            TerminalProcessConfiguration.environment(
                source =
                    mapOf(
                        "PATH" to "/custom/bin",
                        "TERM" to "dumb",
                    ),
            )
        val pathEntries = environment.getValue("PATH").split(File.pathSeparatorChar)

        assertEquals("xterm-256color", environment["TERM"])
        assertEquals("/custom/bin", pathEntries.first())
        assertTrue("/opt/homebrew/bin" in pathEntries)
        assertTrue("/usr/local/bin" in pathEntries)
    }
}
