package su.kidoz.jetaprog.project.state

import kotlinx.serialization.Serializable

/**
 * Workspace state stored in `.jetaprog/workspace.json`.
 * This file should NOT be committed to version control (user-specific).
 */
@Serializable
public data class WorkspaceState(
    /**
     * Expanded paths in the project tree.
     */
    val expandedPaths: Set<String> = emptySet(),
    /**
     * Currently selected file path.
     */
    val selectedPath: String? = null,
    /**
     * Open editor tabs.
     */
    val openTabs: List<TabState> = emptyList(),
    /**
     * Index of the active tab.
     */
    val activeTabIndex: Int = -1,
    /**
     * Panel layout configuration.
     */
    val panelLayout: PanelLayout = PanelLayout(),
    /**
     * Recently opened files.
     */
    val recentFiles: List<String> = emptyList(),
    /**
     * Search history.
     */
    val searchHistory: List<String> = emptyList(),
    /**
     * Replace history.
     */
    val replaceHistory: List<String> = emptyList(),
    /**
     * Terminal command history.
     */
    val terminalHistory: List<String> = emptyList(),
    /**
     * Bookmarks.
     */
    val bookmarks: List<Bookmark> = emptyList(),
    /**
     * Breakpoints for debugging.
     */
    val breakpoints: List<Breakpoint> = emptyList(),
) {
    public companion object {
        /**
         * Maximum number of items to keep in history lists.
         */
        public const val MAX_HISTORY_SIZE: Int = 50

        /**
         * Maximum number of recent files to keep.
         */
        public const val MAX_RECENT_FILES: Int = 30
    }
}

/**
 * State of an open editor tab.
 */
@Serializable
public data class TabState(
    /**
     * File path (relative to project root).
     */
    val filePath: String,
    /**
     * Cursor position.
     */
    val cursor: CursorState = CursorState(),
    /**
     * Scroll position.
     */
    val scroll: ScrollState = ScrollState(),
    /**
     * Whether the tab has unsaved changes.
     */
    val isDirty: Boolean = false,
    /**
     * Whether the tab is pinned.
     */
    val isPinned: Boolean = false,
)

/**
 * Cursor position state.
 */
@Serializable
public data class CursorState(
    /**
     * Line number (0-based).
     */
    val line: Int = 0,
    /**
     * Column number (0-based).
     */
    val column: Int = 0,
    /**
     * Selection start (if any).
     */
    val selectionStart: Int? = null,
    /**
     * Selection end (if any).
     */
    val selectionEnd: Int? = null,
)

/**
 * Scroll position state.
 */
@Serializable
public data class ScrollState(
    /**
     * First visible line.
     */
    val firstVisibleLine: Int = 0,
    /**
     * Horizontal scroll offset.
     */
    val horizontalOffset: Int = 0,
)

/**
 * Panel layout configuration.
 */
@Serializable
public data class PanelLayout(
    /**
     * Project panel width.
     */
    val projectPanelWidth: Int = 250,
    /**
     * Whether project panel is visible.
     */
    val projectPanelVisible: Boolean = true,
    /**
     * Terminal panel height.
     */
    val terminalPanelHeight: Int = 200,
    /**
     * Whether terminal panel is visible.
     */
    val terminalPanelVisible: Boolean = false,
    /**
     * Problems panel height.
     */
    val problemsPanelHeight: Int = 150,
    /**
     * Whether problems panel is visible.
     */
    val problemsPanelVisible: Boolean = false,
    /**
     * Active bottom panel (terminal, problems, output, etc.).
     */
    val activeBottomPanel: String? = null,
)

/**
 * A bookmark in the code.
 */
@Serializable
public data class Bookmark(
    /**
     * File path.
     */
    val filePath: String,
    /**
     * Line number.
     */
    val line: Int,
    /**
     * Optional description.
     */
    val description: String? = null,
    /**
     * Mnemonic key (0-9, A-Z).
     */
    val mnemonic: Char? = null,
)

/**
 * A debug breakpoint.
 */
@Serializable
public data class Breakpoint(
    /**
     * File path.
     */
    val filePath: String,
    /**
     * Line number.
     */
    val line: Int,
    /**
     * Whether the breakpoint is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Condition expression (if conditional breakpoint).
     */
    val condition: String? = null,
    /**
     * Log message (if logpoint).
     */
    val logMessage: String? = null,
    /**
     * Hit count condition.
     */
    val hitCount: Int? = null,
)
