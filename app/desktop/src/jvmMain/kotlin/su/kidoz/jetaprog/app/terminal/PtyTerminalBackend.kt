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
        withContext(Dispatchers.IO) {
            process.outputStream.write(bytes)
            process.outputStream.flush()
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
                        .setEnvironment(System.getenv())
                        .setDirectory(File(workingDirectory).absolutePath)
                        .setInitialColumns(columns)
                        .setInitialRows(rows)
                        .start()
                PtyTerminalBackend(process)
            }

        private fun defaultShellCommand(): List<String> =
            if (isWindows()) {
                listOf(System.getenv("COMSPEC") ?: "cmd.exe")
            } else {
                listOf(System.getenv("SHELL") ?: "/bin/sh")
            }

        private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)

        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 30
        private const val READ_BUFFER_SIZE = 8192
    }
}
