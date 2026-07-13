package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
private const val MIN_PANEL_HEIGHT = 100
private const val MAX_PANEL_HEIGHT = 600

/**
 * Represents a single terminal tab.
 *
 * [lines] holds the raw grid rows produced by the emulator; the UI renders them
 * directly and applies the cursor overlay lazily so no per-row wrapper objects are
 * allocated on the output hot path. [revision] increments on every content change so
 * observers can detect updates cheaply without comparing the whole [lines] list.
 */
public data class TerminalTab(
    val id: Int,
    val name: String,
    val workingDirectory: String,
    val lines: List<String> = emptyList(),
    val cursorLineIndex: Int = 0,
    val cursorColumn: Int = 0,
    val isCursorVisible: Boolean = true,
    val errorMessages: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val exitCode: Int? = null,
    val columns: Int = DEFAULT_TERMINAL_COLUMNS,
    val rows: Int = DEFAULT_TERMINAL_ROWS,
    val applicationCursorKeys: Boolean = false,
    val revision: Long = 0,
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

    /**
     * Visible grid lines for the active tab, filtered by [searchQuery] when a search is active.
     */
    public val filteredLines: List<String>
        get() {
            val tab = activeTab ?: return emptyList()
            return if (searchQuery.isEmpty()) {
                tab.lines
            } else {
                tab.lines.filter { it.contains(searchQuery, ignoreCase = true) }
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

    public data class SendInput(
        val text: String,
    ) : TerminalIntent

    public data object ClearOutput : TerminalIntent

    public data object ToggleVisibility : TerminalIntent

    public data object KillProcess : TerminalIntent

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

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
            is TerminalIntent.SendInput -> sendInput(intent.text)
            is TerminalIntent.ClearOutput -> clearOutput()
            is TerminalIntent.ToggleVisibility -> toggleVisibility()
            is TerminalIntent.KillProcess -> killCurrentProcess()
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
                isRunning = true,
            ).applySnapshot(emulator.snapshot())

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

    private suspend fun startTerminalBackend(
        tabId: Int,
        workingDirectory: String,
    ) {
        val backendResult =
            withContext(Dispatchers.IO) {
                backendFactory(workingDirectory)
            }
        if (backendResult.isFailure) {
            appendError(
                tabId = tabId,
                message = "Failed to start terminal: ${backendResult.exceptionOrNull()?.message}",
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
                            appendError(tabId, output.message)
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
        if (!backend.isAlive) return
        runCatching { backend.write(text.encodeToByteArray()) }
    }

    private fun clearOutput() {
        val activeTab = _state.value.activeTab ?: return
        val snapshot = terminalEmulators[activeTab.id]?.clear() ?: return
        updateTab(activeTab.id) { tab -> tab.copy(errorMessages = emptyList()).applySnapshot(snapshot) }
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
        // The panel only owns its height; the grid is resized from the measured viewport
        // (ResizeTerminal) so there is a single authoritative sizing path.
        _state.update { it.copy(panelHeight = height.coerceIn(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT)) }
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
            val resized = tab.copy(columns = safeColumns, rows = safeRows)
            if (snapshot != null) resized.applySnapshot(snapshot) else resized
        }
    }

    private fun applyTerminalOutput(
        tabId: Int,
        text: String,
    ) {
        val snapshot = terminalEmulators[tabId]?.accept(text) ?: return
        updateTab(tabId) { tab -> tab.applySnapshot(snapshot) }
    }

    private fun appendError(
        tabId: Int,
        message: String,
    ) {
        updateTab(tabId) { tab ->
            tab.copy(
                errorMessages = tab.errorMessages + message,
                revision = tab.revision + 1,
            )
        }
    }

    private fun TerminalTab.applySnapshot(snapshot: TerminalScreenSnapshot): TerminalTab =
        copy(
            lines = snapshot.lines,
            cursorLineIndex = snapshot.cursorLineIndex,
            cursorColumn = snapshot.cursorColumn,
            isCursorVisible = snapshot.isCursorVisible,
            applicationCursorKeys = snapshot.inputMode.applicationCursorKeys,
            revision = revision + 1,
        )

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
}
