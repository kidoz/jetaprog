package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

/**
 * Represents a single terminal tab.
 */
public data class TerminalTab(
    val id: Int,
    val name: String,
    val workingDirectory: String,
    val output: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = false,
    val exitCode: Int? = null,
    val commandHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
)

/**
 * A line of terminal output.
 */
public data class TerminalLine(
    val text: String,
    val isError: Boolean = false,
    val isCommand: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * State for the terminal panel.
 */
public data class TerminalState(
    val tabs: List<TerminalTab> = emptyList(),
    val activeTabIndex: Int = -1,
    val isVisible: Boolean = false,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val panelHeight: Int = 250,
) {
    public val activeTab: TerminalTab?
        get() = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null

    public val filteredOutput: List<TerminalLine>
        get() {
            val tab = activeTab ?: return emptyList()
            return if (searchQuery.isEmpty()) {
                tab.output
            } else {
                tab.output.filter { it.text.contains(searchQuery, ignoreCase = true) }
            }
        }
}

/**
 * Intents for terminal actions.
 */
public sealed interface TerminalIntent {
    public data class CreateTerminal(
        val name: String = "Terminal",
        val workingDirectory: String? = null,
    ) : TerminalIntent

    public data class CloseTerminal(
        val tabId: Int,
    ) : TerminalIntent

    public data class SwitchTerminal(
        val tabIndex: Int,
    ) : TerminalIntent

    public data class RenameTerminal(
        val tabId: Int,
        val newName: String,
    ) : TerminalIntent

    public data class ExecuteCommand(
        val command: String,
    ) : TerminalIntent

    public data class SendInput(
        val text: String,
    ) : TerminalIntent

    public data object ClearOutput : TerminalIntent

    public data object ToggleVisibility : TerminalIntent

    public data object KillProcess : TerminalIntent

    // Command history navigation
    public data object HistoryUp : TerminalIntent

    public data object HistoryDown : TerminalIntent

    // Search
    public data object ToggleSearch : TerminalIntent

    public data class SetSearchQuery(
        val query: String,
    ) : TerminalIntent

    // Resize
    public data class ResizePanel(
        val height: Int,
    ) : TerminalIntent
}

/**
 * Effects for terminal side effects.
 */
public sealed interface TerminalEffect {
    public data class CommandCompleted(
        val exitCode: Int,
    ) : TerminalEffect

    public data class CommandFailed(
        val error: String,
    ) : TerminalEffect

    public data class HistoryCommand(
        val command: String,
    ) : TerminalEffect
}

/**
 * ViewModel for the terminal panel.
 *
 * @param processExecutor The executor for running terminal processes.
 * @param defaultWorkingDirectory The default working directory for new terminals.
 */
public class TerminalViewModel(
    private val processExecutor: ProcessExecutor,
    private val defaultWorkingDirectory: String = System.getProperty("user.dir"),
) : Disposable {
    private var nextTabId = 1
    private val runningProcesses = mutableMapOf<Int, RunningProcess>()
    private val processJobs = mutableMapOf<Int, Job>()

    private val _state = MutableStateFlow(TerminalState())
    public val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _effects = MutableStateFlow<TerminalEffect?>(null)
    public val effects: StateFlow<TerminalEffect?> = _effects.asStateFlow()

    private val viewModelScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main,
        )

    public fun dispatch(intent: TerminalIntent) {
        viewModelScope.launch {
            handleIntent(intent)
        }
    }

    private suspend fun handleIntent(intent: TerminalIntent) {
        when (intent) {
            is TerminalIntent.CreateTerminal -> createTerminal(intent.name, intent.workingDirectory)
            is TerminalIntent.CloseTerminal -> closeTerminal(intent.tabId)
            is TerminalIntent.SwitchTerminal -> switchTerminal(intent.tabIndex)
            is TerminalIntent.RenameTerminal -> renameTerminal(intent.tabId, intent.newName)
            is TerminalIntent.ExecuteCommand -> executeCommand(intent.command)
            is TerminalIntent.SendInput -> sendInput(intent.text)
            is TerminalIntent.ClearOutput -> clearOutput()
            is TerminalIntent.ToggleVisibility -> toggleVisibility()
            is TerminalIntent.KillProcess -> killCurrentProcess()
            is TerminalIntent.HistoryUp -> navigateHistoryUp()
            is TerminalIntent.HistoryDown -> navigateHistoryDown()
            is TerminalIntent.ToggleSearch -> toggleSearch()
            is TerminalIntent.SetSearchQuery -> setSearchQuery(intent.query)
            is TerminalIntent.ResizePanel -> resizePanel(intent.height)
        }
    }

    private fun createTerminal(
        name: String,
        workingDirectory: String?,
    ) {
        val id = nextTabId++
        val cwd = workingDirectory ?: defaultWorkingDirectory
        val tab =
            TerminalTab(
                id = id,
                name = "$name $id",
                workingDirectory = cwd,
            )

        _state.update { state ->
            val newTabs = state.tabs + tab
            state.copy(
                tabs = newTabs,
                activeTabIndex = newTabs.size - 1,
                isVisible = true,
            )
        }
    }

    private fun closeTerminal(tabId: Int) {
        runningProcesses[tabId]?.kill()
        processJobs[tabId]?.cancel()
        runningProcesses.remove(tabId)
        processJobs.remove(tabId)

        _state.update { state ->
            val tabIndex = state.tabs.indexOfFirst { it.id == tabId }
            if (tabIndex < 0) return@update state

            val newTabs = state.tabs.filterNot { it.id == tabId }
            val newActiveIndex =
                when {
                    newTabs.isEmpty() -> -1
                    tabIndex >= newTabs.size -> newTabs.size - 1
                    else -> tabIndex
                }

            state.copy(tabs = newTabs, activeTabIndex = newActiveIndex)
        }
    }

    private fun switchTerminal(tabIndex: Int) {
        if (tabIndex !in _state.value.tabs.indices) return
        _state.update { it.copy(activeTabIndex = tabIndex) }
    }

    private fun renameTerminal(
        tabId: Int,
        newName: String,
    ) {
        updateTab(tabId) { it.copy(name = newName) }
    }

    private suspend fun executeCommand(command: String) {
        val activeTab = _state.value.activeTab ?: return
        val tabId = activeTab.id

        // Add to command history
        val newHistory = (activeTab.commandHistory + command).takeLast(100)
        updateTab(tabId) {
            it.copy(
                commandHistory = newHistory,
                historyIndex = -1,
            )
        }

        // Kill existing process if any
        runningProcesses[tabId]?.kill()
        processJobs[tabId]?.cancel()

        // Add command to output
        appendOutput(tabId, "$ $command", isError = false, isCommand = true)

        // Update state to show running
        updateTab(tabId) { it.copy(isRunning = true, exitCode = null) }

        // Start the process
        val shell =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                listOf("cmd", "/c", command)
            } else {
                listOf("/bin/sh", "-c", command)
            }

        val config =
            ProcessConfig(
                command = shell,
                workingDirectory = activeTab.workingDirectory,
            )

        val processResult = processExecutor.start(config)
        if (processResult.isFailure) {
            appendOutput(tabId, "Failed to start process: ${processResult.exceptionOrNull()?.message}", isError = true)
            updateTab(tabId) { it.copy(isRunning = false) }
            return
        }

        val process = processResult.getOrThrow()
        runningProcesses[tabId] = process

        val job =
            viewModelScope.launch {
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            appendOutput(tabId, output.line, isError = false)
                        }

                        is ProcessOutput.Stderr -> {
                            appendOutput(tabId, output.line, isError = true)
                        }

                        is ProcessOutput.Exited -> {
                            updateTab(tabId) { it.copy(isRunning = false, exitCode = output.exitCode) }
                            runningProcesses.remove(tabId)
                        }
                    }
                }
            }
        processJobs[tabId] = job
    }

    private suspend fun sendInput(text: String) {
        val activeTab = _state.value.activeTab ?: return
        val process = runningProcesses[activeTab.id] ?: return
        process.writeStdin(text + "\n")
    }

    private fun clearOutput() {
        val activeTab = _state.value.activeTab ?: return
        updateTab(activeTab.id) { it.copy(output = emptyList()) }
    }

    private fun toggleVisibility() {
        _state.update { it.copy(isVisible = !it.isVisible) }
    }

    private fun killCurrentProcess() {
        val activeTab = _state.value.activeTab ?: return
        val process = runningProcesses[activeTab.id]
        val job = processJobs[activeTab.id]

        process?.kill()
        job?.cancel()

        appendOutput(activeTab.id, "^C Process terminated", isError = true)
        updateTab(activeTab.id) { it.copy(isRunning = false) }
    }

    private fun navigateHistoryUp() {
        val activeTab = _state.value.activeTab ?: return
        val history = activeTab.commandHistory
        if (history.isEmpty()) return

        val newIndex =
            if (activeTab.historyIndex < 0) {
                history.size - 1
            } else {
                (activeTab.historyIndex - 1).coerceAtLeast(0)
            }

        updateTab(activeTab.id) { it.copy(historyIndex = newIndex) }
        _effects.value = TerminalEffect.HistoryCommand(history[newIndex])
    }

    private fun navigateHistoryDown() {
        val activeTab = _state.value.activeTab ?: return
        val history = activeTab.commandHistory
        if (history.isEmpty() || activeTab.historyIndex < 0) return

        val newIndex = activeTab.historyIndex + 1
        if (newIndex >= history.size) {
            updateTab(activeTab.id) { it.copy(historyIndex = -1) }
            _effects.value = TerminalEffect.HistoryCommand("")
        } else {
            updateTab(activeTab.id) { it.copy(historyIndex = newIndex) }
            _effects.value = TerminalEffect.HistoryCommand(history[newIndex])
        }
    }

    private fun toggleSearch() {
        _state.update {
            it.copy(
                isSearchVisible = !it.isSearchVisible,
                searchQuery = if (it.isSearchVisible) "" else it.searchQuery,
            )
        }
    }

    private fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun resizePanel(height: Int) {
        _state.update { it.copy(panelHeight = height.coerceIn(100, 600)) }
    }

    private fun appendOutput(
        tabId: Int,
        text: String,
        isError: Boolean = false,
        isCommand: Boolean = false,
    ) {
        updateTab(tabId) { tab ->
            val newOutput =
                tab.output +
                    TerminalLine(
                        text = text,
                        isError = isError,
                        isCommand = isCommand,
                    )
            val trimmedOutput =
                if (newOutput.size > 10000) {
                    newOutput.takeLast(10000)
                } else {
                    newOutput
                }
            tab.copy(output = trimmedOutput)
        }
    }

    private fun updateTab(
        tabId: Int,
        update: (TerminalTab) -> TerminalTab,
    ) {
        _state.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) update(tab) else tab
                    },
            )
        }
    }

    override fun dispose() {
        runningProcesses.values.forEach { it.kill() }
        processJobs.values.forEach { it.cancel() }
        runningProcesses.clear()
        processJobs.clear()
        viewModelScope.launch { }.cancel()
    }
}
