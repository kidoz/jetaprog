package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.plugins.api.services.ExecuteInTerminalOptions
import su.kidoz.jetaprog.plugins.api.services.Terminal
import su.kidoz.jetaprog.plugins.api.services.TerminalOptions
import su.kidoz.jetaprog.plugins.api.services.TerminalService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of TerminalService for terminal operations.
 */
public class TerminalServiceImpl(
    private val processExecutor: ProcessExecutor,
    private val workspacePath: String,
) : TerminalService {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val terminalIdCounter = AtomicLong(0)
    private val terminalMap = ConcurrentHashMap<Long, TerminalImpl>()

    private val _activeTerminal = MutableStateFlow<Terminal?>(null)

    private val openHandlers = mutableListOf<suspend (Terminal) -> Unit>()
    private val closeHandlers = mutableListOf<suspend (Terminal) -> Unit>()
    private val changeHandlers = mutableListOf<suspend (Terminal?) -> Unit>()

    override val activeTerminal: Terminal? get() = _activeTerminal.value

    override val terminals: List<Terminal> get() = terminalMap.values.toList()

    override suspend fun createTerminal(options: TerminalOptions): Terminal {
        val id = terminalIdCounter.incrementAndGet()
        val terminalName = options.name ?: "Terminal $id"
        val terminalCwd = options.cwd ?: workspacePath
        val terminalEnv = options.env

        val terminal =
            TerminalImpl(
                id = id,
                name = terminalName,
                cwd = terminalCwd,
                processExecutor = processExecutor,
                env = terminalEnv,
                onDispose = { closedTerminal ->
                    terminalMap.remove(id)
                    scope.launch {
                        closeHandlers.forEach { it(closedTerminal) }
                        if (_activeTerminal.value == closedTerminal) {
                            val newActive = terminalMap.values.firstOrNull()
                            _activeTerminal.value = newActive
                            changeHandlers.forEach { it(newActive) }
                        }
                    }
                },
            )

        terminalMap[id] = terminal

        if (_activeTerminal.value == null) {
            _activeTerminal.value = terminal
            changeHandlers.forEach { it(terminal) }
        }

        openHandlers.forEach { it(terminal) }

        return terminal
    }

    override suspend fun executeCommand(
        command: String,
        options: ExecuteInTerminalOptions,
    ): Terminal {
        val terminal =
            if (options.newTerminal || terminalMap.isEmpty()) {
                createTerminal(
                    TerminalOptions(
                        name = options.name,
                        cwd = options.cwd ?: workspacePath,
                        env = options.env,
                    ),
                )
            } else {
                _activeTerminal.value ?: createTerminal(TerminalOptions())
            }

        terminal.sendText(command, addNewLine = true)

        if (options.focus) {
            terminal.show()
            _activeTerminal.value = terminal
            changeHandlers.forEach { it(terminal) }
        }

        return terminal
    }

    override fun onDidOpenTerminal(handler: suspend (Terminal) -> Unit): Disposable {
        openHandlers.add(handler)
        return Disposable { openHandlers.remove(handler) }
    }

    override fun onDidCloseTerminal(handler: suspend (Terminal) -> Unit): Disposable {
        closeHandlers.add(handler)
        return Disposable { closeHandlers.remove(handler) }
    }

    override fun onDidChangeActiveTerminal(handler: suspend (Terminal?) -> Unit): Disposable {
        changeHandlers.add(handler)
        return Disposable { changeHandlers.remove(handler) }
    }
}

/**
 * Implementation of Terminal with streaming output support.
 */
private class TerminalImpl(
    private val id: Long,
    override val name: String,
    private val cwd: String,
    private val processExecutor: ProcessExecutor,
    private val env: Map<String, String> = emptyMap(),
    private val onDispose: (Terminal) -> Unit,
) : Terminal {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var _processId: Long? = null
    private var _exitStatus: Int? = null
    private var disposed = false
    private var currentProcess: Process? = null

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1024)

    override val processId: Long? get() = _processId

    override val exitStatus: Int? get() = _exitStatus

    override val output: Flow<String> = _output.asSharedFlow()

    override suspend fun sendText(
        text: String,
        addNewLine: Boolean,
    ) {
        val command = text.trim()

        withContext(Dispatchers.IO) {
            try {
                // Build process with proper command parsing
                val processBuilder =
                    ProcessBuilder().apply {
                        // Use shell for command parsing
                        val shell = System.getenv("SHELL") ?: "/bin/sh"
                        command(shell, "-c", command)
                        directory(java.io.File(cwd))
                        environment().putAll(env)
                        redirectErrorStream(false)
                    }

                val process = processBuilder.start()
                currentProcess = process
                _processId = process.pid()

                // Stream stdout
                scope.launch {
                    try {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                _output.emit(line!! + "\n")
                            }
                        }
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }

                // Stream stderr
                scope.launch {
                    try {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                _output.emit("[stderr] " + line!! + "\n")
                            }
                        }
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }

                // Wait for process completion
                _exitStatus = process.waitFor()
                currentProcess = null
            } catch (e: Exception) {
                _output.emit("[error] ${e.message}\n")
                _exitStatus = -1
            }
        }
    }

    override fun show(preserveFocus: Boolean) {
        // In a full implementation, this would show the terminal panel
    }

    override fun hide() {
        // In a full implementation, this would hide the terminal panel
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        currentProcess?.destroyForcibly()
        onDispose(this)
    }
}
