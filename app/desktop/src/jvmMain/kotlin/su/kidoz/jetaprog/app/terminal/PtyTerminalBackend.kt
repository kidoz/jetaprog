package su.kidoz.jetaprog.app.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal interface TerminalProcessHandle {
    val inputStream: InputStream
    val outputStream: OutputStream
    val errorStream: InputStream?
    val isAlive: Boolean

    fun waitFor(): Int

    fun destroyForcibly()

    fun setWinSize(
        columns: Int,
        rows: Int,
    )
}

private class Pty4JProcessHandle(
    private val process: PtyProcess,
) : TerminalProcessHandle {
    override val inputStream: InputStream
        get() = process.inputStream
    override val outputStream: OutputStream
        get() = process.outputStream
    override val errorStream: InputStream?
        get() = process.errorStream
    override val isAlive: Boolean
        get() = process.isAlive

    override fun waitFor(): Int = process.waitFor()

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun setWinSize(
        columns: Int,
        rows: Int,
    ) {
        process.setWinSize(WinSize(columns, rows))
    }
}

/**
 * Pty4J-backed interactive terminal backend.
 */
public class PtyTerminalBackend internal constructor(
    private val process: TerminalProcessHandle,
) : TerminalBackend {
    private val outputEvents = Channel<TerminalBackendOutput>(Channel.BUFFERED)
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val terminationRequested = AtomicBoolean()
    private val streamsClosed = AtomicBoolean()
    private val disposed = AtomicBoolean()

    override val output: Flow<TerminalBackendOutput> = outputEvents.receiveAsFlow()

    override val isAlive: Boolean
        get() = !terminationRequested.get() && process.isAlive

    init {
        val readJob = backendScope.launch { readOutput() }
        backendScope.launch {
            try {
                val exitCode =
                    try {
                        process.waitFor()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        readJob.cancel()
                        terminateProcess()
                        outputEvents.send(
                            TerminalBackendOutput.Error(errorMessage("Failed to wait for terminal process", error)),
                        )
                        return@launch
                    }
                readJob.join()
                closeStreams()
                outputEvents.send(TerminalBackendOutput.Exited(exitCode))
            } finally {
                closeStreams()
                outputEvents.close()
            }
        }
    }

    private suspend fun readOutput() {
        try {
            val buffer = CharArray(READ_BUFFER_SIZE)
            InputStreamReader(process.inputStream, Charsets.UTF_8).use { reader ->
                while (currentCoroutineContext().isActive) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        outputEvents.send(TerminalBackendOutput.Text(buffer.concatToString(endIndex = read)))
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (!terminationRequested.get() && !disposed.get()) {
                outputEvents.send(TerminalBackendOutput.Error(errorMessage("Failed to read terminal output", error)))
                terminateProcess()
            }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        if (!isAlive) return
        try {
            withContext(Dispatchers.IO) {
                process.outputStream.write(bytes)
                process.outputStream.flush()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            terminateProcess()
            throw IOException(errorMessage("Failed to write terminal input", error), error)
        }
    }

    override fun resize(
        columns: Int,
        rows: Int,
    ) {
        if (columns <= 0 || rows <= 0 || !isAlive) return
        process.setWinSize(columns, rows)
    }

    override fun kill() {
        terminateProcess()
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        try {
            terminateProcess()
        } finally {
            backendScope.cancel()
            outputEvents.close()
        }
    }

    private fun terminateProcess() {
        if (!terminationRequested.compareAndSet(false, true)) return
        try {
            if (process.isAlive) process.destroyForcibly()
        } finally {
            closeStreams()
        }
    }

    private fun closeStreams() {
        if (!streamsClosed.compareAndSet(false, true)) return
        closeQuietly(process.inputStream)
        closeQuietly(process.outputStream)
        closeQuietly(process.errorStream)
    }

    private fun closeQuietly(stream: Closeable?) {
        try {
            stream?.close()
        } catch (_: IOException) {
        }
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
            start(
                command = defaultShellCommand(),
                workingDirectory = workingDirectory,
                columns = columns,
                rows = rows,
            )

        internal fun start(
            command: List<String>,
            workingDirectory: String,
            columns: Int = DEFAULT_COLUMNS,
            rows: Int = DEFAULT_ROWS,
        ): Result<TerminalBackend> =
            runCatching {
                val process =
                    PtyProcessBuilder()
                        .setCommand(command.toTypedArray())
                        .setEnvironment(defaultTerminalEnvironment())
                        .setDirectory(File(workingDirectory).absolutePath)
                        .setInitialColumns(columns)
                        .setInitialRows(rows)
                        .start()
                PtyTerminalBackend(Pty4JProcessHandle(process))
            }

        private fun defaultShellCommand(): List<String> = TerminalProcessConfiguration.shellCommand()

        private fun defaultTerminalEnvironment(): Map<String, String> = TerminalProcessConfiguration.environment()

        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 30
        private const val READ_BUFFER_SIZE = 8192
    }
}

private fun errorMessage(
    action: String,
    error: Exception,
): String = "$action: ${error.message ?: error.javaClass.simpleName}"

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
            this[TERM_PROGRAM_ENV] = TERM_PROGRAM_VALUE
            this[COLORTERM_ENV] = TRUE_COLOR_VALUE
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
    private const val TERM_PROGRAM_ENV = "TERM_PROGRAM"
    private const val COLORTERM_ENV = "COLORTERM"
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
    private const val TERM_PROGRAM_VALUE = "JetaProg"
    private const val TRUE_COLOR_VALUE = "truecolor"

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
