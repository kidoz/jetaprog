package su.kidoz.jetaprog.app.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

/**
 * Pty4J-backed interactive terminal backend.
 */
public class PtyTerminalBackend private constructor(
    private val process: PtyProcess,
) : TerminalBackend {
    override val isAlive: Boolean
        get() = process.isAlive

    override val output: Flow<TerminalBackendOutput> =
        callbackFlow {
            val readJob =
                launch(Dispatchers.IO) {
                    val buffer = CharArray(READ_BUFFER_SIZE)
                    InputStreamReader(process.inputStream, Charsets.UTF_8).use { reader ->
                        while (isActive && process.isAlive) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                val text = buffer.concatToString(endIndex = read)
                                trySend(TerminalBackendOutput.Text(text))
                            }
                        }
                    }
                }

            val waitJob =
                launch(Dispatchers.IO) {
                    val exitCode = process.waitFor()
                    trySend(TerminalBackendOutput.Exited(exitCode))
                    close()
                }

            awaitClose {
                readJob.cancel()
                waitJob.cancel()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }

    override suspend fun write(bytes: ByteArray) {
        if (!process.isAlive) return
        withContext(Dispatchers.IO) {
            runCatching {
                process.outputStream.write(bytes)
                process.outputStream.flush()
            }
        }
    }

    override fun resize(
        columns: Int,
        rows: Int,
    ) {
        if (columns <= 0 || rows <= 0) return
        process.setWinSize(WinSize(columns, rows))
    }

    override fun kill() {
        process.destroyForcibly()
    }

    override fun dispose() {
        kill()
    }

    public companion object {
        /**
         * Start an interactive shell attached to a PTY.
         */
        public fun start(
            workingDirectory: String,
            columns: Int = DEFAULT_COLUMNS,
            rows: Int = DEFAULT_ROWS,
        ): Result<TerminalBackend> =
            runCatching {
                val process =
                    PtyProcessBuilder()
                        .setCommand(defaultShellCommand().toTypedArray())
                        .setEnvironment(defaultTerminalEnvironment())
                        .setDirectory(File(workingDirectory).absolutePath)
                        .setInitialColumns(columns)
                        .setInitialRows(rows)
                        .start()
                PtyTerminalBackend(process)
            }

        private fun defaultShellCommand(): List<String> = TerminalProcessConfiguration.shellCommand()

        private fun defaultTerminalEnvironment(): Map<String, String> = TerminalProcessConfiguration.environment()

        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 30
        private const val READ_BUFFER_SIZE = 8192
    }
}

internal object TerminalProcessConfiguration {
    fun shellCommand(
        osName: String = System.getProperty(OS_NAME_PROPERTY),
        shell: String? = System.getenv(SHELL_ENV),
        comspec: String? = System.getenv(COMSPEC_ENV),
    ): List<String> =
        if (isWindows(osName)) {
            listOf(comspec?.takeIf { it.isNotBlank() } ?: WINDOWS_DEFAULT_SHELL)
        } else {
            loginShellCommand(shell?.takeIf { it.isNotBlank() } ?: UNIX_DEFAULT_SHELL)
        }

    fun environment(source: Map<String, String> = System.getenv()): Map<String, String> =
        source.toMutableMap().apply {
            val term = this[TERM_ENV]
            if (term.isNullOrBlank() || term == DUMB_TERM) {
                this[TERM_ENV] = DEFAULT_TERM
            }
            this[PATH_ENV] = augmentedPath(this[PATH_ENV])
        }

    private fun loginShellCommand(shell: String): List<String> =
        when (File(shell).name) {
            ZSH_SHELL, BASH_SHELL -> listOf(shell, LOGIN_FLAG)
            FISH_SHELL -> listOf(shell, FISH_LOGIN_FLAG)
            else -> listOf(shell)
        }

    private fun augmentedPath(path: String?): String {
        val existingEntries =
            path
                ?.split(File.pathSeparatorChar)
                ?.filter { it.isNotBlank() }
                .orEmpty()
        return (existingEntries + DEVELOPER_PATH_ENTRIES)
            .distinct()
            .joinToString(File.pathSeparator)
    }

    private fun isWindows(osName: String): Boolean = osName.lowercase().startsWith(WINDOWS_OS_PREFIX)

    private const val OS_NAME_PROPERTY = "os.name"
    private const val SHELL_ENV = "SHELL"
    private const val COMSPEC_ENV = "COMSPEC"
    private const val TERM_ENV = "TERM"
    private const val PATH_ENV = "PATH"
    private const val DEFAULT_TERM = "xterm-256color"
    private const val DUMB_TERM = "dumb"
    private const val WINDOWS_OS_PREFIX = "win"
    private const val WINDOWS_DEFAULT_SHELL = "cmd.exe"
    private const val UNIX_DEFAULT_SHELL = "/bin/sh"
    private const val ZSH_SHELL = "zsh"
    private const val BASH_SHELL = "bash"
    private const val FISH_SHELL = "fish"
    private const val LOGIN_FLAG = "-l"
    private const val FISH_LOGIN_FLAG = "--login"

    private val DEVELOPER_PATH_ENTRIES =
        listOf(
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            "/usr/local/bin",
            "/usr/local/sbin",
            "/usr/bin",
            "/bin",
            "/usr/sbin",
            "/sbin",
        )
}
