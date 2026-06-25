package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.terminal.PtyTerminalBackend
import su.kidoz.jetaprog.app.terminal.TerminalBackend
import su.kidoz.jetaprog.app.terminal.TerminalBackendOutput
import su.kidoz.jetaprog.app.terminal.TerminalEmulator
import su.kidoz.jetaprog.app.terminal.TerminalScreenSnapshot
import su.kidoz.jetaprog.common.Disposable

private const val DEFAULT_TERMINAL_COLUMNS = 120
private const val DEFAULT_TERMINAL_ROWS = 30
private const val MIN_TERMINAL_COLUMNS = 1
private const val MIN_TERMINAL_ROWS = 5

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
    val columns: Int = DEFAULT_TERMINAL_COLUMNS,
    val rows: Int = DEFAULT_TERMINAL_ROWS,
    val applicationCursorKeys: Boolean = false,
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

    public data class ResizeTerminal(
        val columns: Int,
        val rows: Int,
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
 * @param defaultWorkingDirectory The default working directory for new terminals.
 * @param backendFactory Creates terminal backends.
 */
public class TerminalViewModel(
    private val defaultWorkingDirectory: String = System.getProperty("user.dir"),
    private val backendFactory: (String) -> Result<TerminalBackend> = { PtyTerminalBackend.start(it) },
) : Disposable {
    private var nextTabId = 1
    private val terminalBackends = mutableMapOf<Int, TerminalBackend>()
    private val backendJobs = mutableMapOf<Int, Job>()
    private val terminalEmulators = mutableMapOf<Int, TerminalEmulator>()

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
            is TerminalIntent.ResizeTerminal -> resizeTerminal(intent.columns, intent.rows)
        }
    }

    private suspend fun createTerminal(
        name: String,
        workingDirectory: String?,
    ) {
        val id = nextTabId++
        val cwd = workingDirectory ?: defaultWorkingDirectory
        val emulator = TerminalEmulator(columns = DEFAULT_TERMINAL_COLUMNS, rows = DEFAULT_TERMINAL_ROWS)
        terminalEmulators[id] = emulator
        val tab =
            TerminalTab(
                id = id,
                name = "$name $id",
                workingDirectory = cwd,
                output = emulator.snapshot().toTerminalLines(),
                isRunning = true,
            )

        _state.update { state ->
            val newTabs = state.tabs + tab
            state.copy(
                tabs = newTabs,
                activeTabIndex = newTabs.size - 1,
                isVisible = true,
            )
        }

        startTerminalBackend(id, cwd)
    }

    private fun closeTerminal(tabId: Int) {
        terminalBackends[tabId]?.kill()
        backendJobs[tabId]?.cancel()
        terminalBackends.remove(tabId)
        backendJobs.remove(tabId)
        terminalEmulators.remove(tabId)

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

        terminalBackends[tabId]?.write("$command\r".encodeToByteArray())
    }

    private suspend fun startTerminalBackend(
        tabId: Int,
        workingDirectory: String,
    ) {
        val backendResult =
            withContext(Dispatchers.IO) {
                backendFactory(workingDirectory)
            }
        if (backendResult.isFailure) {
            appendGridOutput(
                tabId = tabId,
                text = "Failed to start terminal: ${backendResult.exceptionOrNull()?.message}",
                isError = true,
            )
            updateTab(tabId) { it.copy(isRunning = false) }
            return
        }

        val backend = backendResult.getOrThrow()
        terminalBackends[tabId] = backend

        val job =
            viewModelScope.launch {
                backend.output.collect { output ->
                    when (output) {
                        is TerminalBackendOutput.Text -> {
                            applyTerminalOutput(tabId, output.value)
                        }

                        is TerminalBackendOutput.Error -> {
                            appendGridOutput(tabId, output.message, isError = true)
                        }

                        is TerminalBackendOutput.Exited -> {
                            updateTab(tabId) { it.copy(isRunning = false, exitCode = output.exitCode) }
                            terminalBackends.remove(tabId)
                            backendJobs.remove(tabId)
                        }
                    }
                }
            }
        backendJobs[tabId] = job
    }

    private suspend fun sendInput(text: String) {
        val activeTab = _state.value.activeTab ?: return
        val backend = terminalBackends[activeTab.id] ?: return
        backend.write(text.encodeToByteArray())
    }

    private fun clearOutput() {
        val activeTab = _state.value.activeTab ?: return
        val snapshot = terminalEmulators[activeTab.id]?.clear()
        updateTab(activeTab.id) { tab ->
            tab.copy(
                output = snapshot?.toTerminalLines() ?: emptyList(),
                applicationCursorKeys = snapshot?.inputMode?.applicationCursorKeys ?: tab.applicationCursorKeys,
            )
        }
    }

    private fun toggleVisibility() {
        _state.update { it.copy(isVisible = !it.isVisible) }
    }

    private fun killCurrentProcess() {
        val activeTab = _state.value.activeTab ?: return
        val backend = terminalBackends[activeTab.id]
        val job = backendJobs[activeTab.id]

        backend?.kill()
        job?.cancel()

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
        val panelHeight = height.coerceIn(100, 600)
        val rows = ((panelHeight - TERMINAL_CHROME_HEIGHT) / TERMINAL_ROW_HEIGHT).coerceAtLeast(MIN_TERMINAL_ROWS)
        val activeTab = _state.value.activeTab

        if (activeTab != null) {
            terminalBackends[activeTab.id]?.resize(activeTab.columns, rows)
            val snapshot = terminalEmulators[activeTab.id]?.resize(activeTab.columns, rows)
            if (snapshot != null) {
                updateTab(activeTab.id) {
                    it.copy(
                        output = snapshot.toTerminalLines(),
                        rows = rows,
                        applicationCursorKeys = snapshot.inputMode.applicationCursorKeys,
                    )
                }
            }
        }

        _state.update { it.copy(panelHeight = panelHeight) }
    }

    private fun resizeTerminal(
        columns: Int,
        rows: Int,
    ) {
        val activeTab = _state.value.activeTab ?: return
        val safeColumns = columns.coerceAtLeast(MIN_TERMINAL_COLUMNS)
        val safeRows = rows.coerceAtLeast(MIN_TERMINAL_ROWS)
        if (activeTab.columns == safeColumns && activeTab.rows == safeRows) return

        terminalBackends[activeTab.id]?.resize(safeColumns, safeRows)
        val snapshot = terminalEmulators[activeTab.id]?.resize(safeColumns, safeRows)
        updateTab(activeTab.id) { tab ->
            tab.copy(
                output = snapshot?.toTerminalLines() ?: tab.output,
                columns = safeColumns,
                rows = safeRows,
                applicationCursorKeys = snapshot?.inputMode?.applicationCursorKeys ?: tab.applicationCursorKeys,
            )
        }
    }

    private fun applyTerminalOutput(
        tabId: Int,
        text: String,
    ) {
        val snapshot = terminalEmulators[tabId]?.accept(text) ?: return
        updateTab(tabId) { tab ->
            tab.copy(
                output = snapshot.toTerminalLines(),
                applicationCursorKeys = snapshot.inputMode.applicationCursorKeys,
            )
        }
    }

    private fun appendGridOutput(
        tabId: Int,
        text: String,
        isError: Boolean = false,
    ) {
        val emulator = terminalEmulators[tabId]
        val snapshot =
            if (emulator != null) {
                emulator.accept("$text\n")
            } else {
                TerminalScreenSnapshot(
                    lines = listOf(text),
                    cursorRow = 0,
                    cursorColumn = 0,
                    cursorLineIndex = 0,
                )
            }
        updateTab(tabId) { tab ->
            tab.copy(
                output = snapshot.toTerminalLines(isError = isError),
                applicationCursorKeys = snapshot.inputMode.applicationCursorKeys,
            )
        }
    }

    private fun TerminalScreenSnapshot.toTerminalLines(isError: Boolean = false): List<TerminalLine> =
        lines.mapIndexed { index, line ->
            TerminalLine(
                text =
                    if (index == cursorLineIndex) {
                        line.withCursor(cursorColumn)
                    } else {
                        line
                    },
                isError = isError,
                timestamp = index.toLong(),
            )
        }

    private fun String.withCursor(column: Int): String {
        val safeColumn = column.coerceAtLeast(0)
        val text = StringBuilder(this)
        while (text.length < safeColumn) {
            text.append(' ')
        }
        if (safeColumn < text.length) {
            text.setCharAt(safeColumn, CURSOR_GLYPH)
        } else {
            text.append(CURSOR_GLYPH)
        }
        return text.toString()
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
        terminalBackends.values.forEach { it.kill() }
        backendJobs.values.forEach { it.cancel() }
        terminalBackends.clear()
        backendJobs.clear()
        terminalEmulators.clear()
        viewModelScope.cancel()
    }

    private companion object {
        private const val TERMINAL_CHROME_HEIGHT = 68
        private const val TERMINAL_ROW_HEIGHT = 18
        private const val CURSOR_GLYPH = '\u2588'
    }
}
