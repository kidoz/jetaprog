package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.editor.search.FileReplaceStatus
import su.kidoz.jetaprog.editor.search.FileTextMatches
import su.kidoz.jetaprog.editor.search.ProjectTextReplacer
import su.kidoz.jetaprog.editor.search.ProjectTextSearcher
import su.kidoz.jetaprog.editor.search.TextSearchQuery
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.project.state.WorkspaceState

/** State of the Find-in-Files panel. */
public data class TextSearchState(
    /** The current query string. */
    val query: String = "",
    /** Whether matching is case-sensitive. */
    val caseSensitive: Boolean = false,
    /** Whether the query is a regular expression. */
    val regex: Boolean = false,
    /** Whether to match whole words only. */
    val wholeWord: Boolean = false,
    /** Whether a search is in progress. */
    val isSearching: Boolean = false,
    /** Per-file matches from the last search. */
    val results: List<FileTextMatches> = emptyList(),
    /** Total number of matches across all files. */
    val totalMatches: Int = 0,
    /** Whether the last search completed with a query but found nothing. */
    val searched: Boolean = false,
    /** The replacement text for Replace in Files. */
    val replacement: String = "",
    /** Whether a replace-all confirmation is awaiting the user's decision. */
    val awaitingReplaceConfirmation: Boolean = false,
    /** Whether Replace in Files is writing files. */
    val isReplacing: Boolean = false,
    /** Summary of the last completed replace run, if any. */
    val replaceSummary: ReplaceInFilesSummary? = null,
    /** Executed searches, most recent first (persisted per project). */
    val searchHistory: List<String> = emptyList(),
    /** Applied replacements, most recent first (persisted per project). */
    val replaceHistory: List<String> = emptyList(),
)

/**
 * Outcome of a completed Replace-in-Files run.
 */
public data class ReplaceInFilesSummary(
    /** Paths of the files that were rewritten, in traversal order. */
    val replacedPaths: List<String> = emptyList(),
    /** Total occurrences replaced across all files. */
    val occurrences: Int = 0,
    /** Files left untouched because they are open with unsaved changes. */
    val skippedPaths: List<String> = emptyList(),
    /** Files that could not be written, keyed by path with the error message. */
    val failures: Map<String, String> = emptyMap(),
) {
    /** Whether the run changed anything at all. */
    public val isEmpty: Boolean
        get() = replacedPaths.isEmpty() && skippedPaths.isEmpty() && failures.isEmpty()
}

/**
 * Drives project-wide full-text search and replacement ("Find in Files").
 *
 * @param projectPath the workspace root searched.
 * @param fileSystem used to traverse, read, and write files.
 */
public class TextSearchViewModel(
    private val projectPath: String,
    fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Disposable {
    private val searcher = ProjectTextSearcher(fileSystem)
    private val replacer = ProjectTextReplacer(fileSystem)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(TextSearchState())

    /** The observable panel state. */
    public val state: StateFlow<TextSearchState> = _state.asStateFlow()

    /** Updates the query string and schedules a debounced search. */
    public fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch()
    }

    /** Toggles case sensitivity and re-runs the search. */
    public fun toggleCaseSensitive() {
        _state.update { it.copy(caseSensitive = !it.caseSensitive) }
        scheduleSearch()
    }

    /** Toggles regular-expression mode and re-runs the search. */
    public fun toggleRegex() {
        _state.update { it.copy(regex = !it.regex) }
        scheduleSearch()
    }

    /** Toggles whole-word matching and re-runs the search. */
    public fun toggleWholeWord() {
        _state.update { it.copy(wholeWord = !it.wholeWord) }
        scheduleSearch()
    }

    /** Updates the replacement text used by Replace in Files. */
    public fun setReplacement(replacement: String) {
        _state.update { it.copy(replacement = replacement) }
    }

    /** Runs the search immediately with the current query and options. */
    public fun search() {
        recordSearch(_state.value.query)
        startSearch(debounced = false)
    }

    /**
     * Seeds the persisted histories after a project opens, most recent first.
     */
    public fun seedHistories(
        searches: List<String>,
        replacements: List<String>,
    ) {
        _state.update {
            it.copy(
                searchHistory = searches.take(MAX_HISTORY_SIZE),
                replaceHistory = replacements.take(MAX_HISTORY_SIZE),
            )
        }
    }

    /** Adds [query] to the search history, deduplicated and most recent first. */
    private fun recordSearch(query: String) {
        if (query.isEmpty()) return
        _state.update {
            it.copy(searchHistory = (listOf(query) + it.searchHistory).distinct().take(MAX_HISTORY_SIZE))
        }
    }

    /**
     * Opens the replace-all confirmation with the current results. Does
     * nothing while there is nothing to replace.
     */
    public fun requestReplaceAll() {
        val current = _state.value
        if (current.query.isEmpty() || current.results.isEmpty() || current.isReplacing) return
        _state.update { it.copy(awaitingReplaceConfirmation = true, replaceSummary = null) }
    }

    /** Closes the replace-all confirmation without replacing anything. */
    public fun cancelReplaceAll() {
        _state.update { it.copy(awaitingReplaceConfirmation = false) }
    }

    /**
     * Replaces every current match on disk with the replacement text,
     * re-reading each file so stale results never overwrite newer content.
     *
     * @param skipPaths files to leave untouched — open editors with unsaved
     *   changes whose buffers would otherwise clobber the replacement on save.
     */
    public fun confirmReplaceAll(skipPaths: Set<String> = emptySet()) {
        val current = _state.value
        if (current.query.isEmpty() || current.isReplacing) return
        val query =
            TextSearchQuery(
                query = current.query,
                caseSensitive = current.caseSensitive,
                regex = current.regex,
                wholeWord = current.wholeWord,
            )
        val replacement = current.replacement
        _state.update {
            it.copy(
                awaitingReplaceConfirmation = false,
                isReplacing = true,
                replaceSummary = null,
                searchHistory = (listOf(current.query) + it.searchHistory).distinct().take(MAX_HISTORY_SIZE),
                replaceHistory = (listOf(replacement) + it.replaceHistory).distinct().take(MAX_HISTORY_SIZE),
            )
        }
        scope.launch {
            val results =
                withContext(ioDispatcher) {
                    replacer.replaceAll(projectPath, query, replacement, skipPaths)
                }
            val summary =
                ReplaceInFilesSummary(
                    replacedPaths = results.filter { it.status == FileReplaceStatus.REPLACED }.map { it.filePath },
                    occurrences =
                        results
                            .filter { it.status == FileReplaceStatus.REPLACED }
                            .sumOf { it.replaced },
                    skippedPaths = results.filter { it.status == FileReplaceStatus.SKIPPED }.map { it.filePath },
                    failures =
                        results
                            .filter { it.status == FileReplaceStatus.FAILED }
                            .associate { it.filePath to (it.error ?: "unknown error") },
                )
            _state.update { it.copy(isReplacing = false, replaceSummary = summary) }
            // Refresh the match list so it reflects the post-replace content.
            startSearch(debounced = false, preserveSummary = true)
        }
    }

    // Search-as-you-type: every query/option change lands here; the debounce lets
    // a fast typist supersede a keystroke before the project scan starts, and
    // cancelling the previous job makes the newest input win.
    private fun scheduleSearch() {
        startSearch(debounced = true)
    }

    private fun startSearch(
        debounced: Boolean,
        preserveSummary: Boolean = false,
    ) {
        searchJob?.cancel()
        if (_state.value.query.isEmpty()) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    totalMatches = 0,
                    searched = false,
                    isSearching = false,
                    awaitingReplaceConfirmation = false,
                    replaceSummary = null,
                )
            }
            return
        }

        // New query/options invalidate the previous replace run's summary and
        // any confirmation built on the old results. The refresh triggered by
        // a finished replace keeps its summary — it only refreshes the list.
        _state.update {
            it.copy(
                isSearching = true,
                awaitingReplaceConfirmation = false,
                replaceSummary = if (preserveSummary) it.replaceSummary else null,
            )
        }
        searchJob =
            scope.launch {
                if (debounced) delay(SEARCH_DEBOUNCE_MS)
                val current = _state.value
                val query =
                    TextSearchQuery(
                        query = current.query,
                        caseSensitive = current.caseSensitive,
                        regex = current.regex,
                        wholeWord = current.wholeWord,
                    )
                val results = withContext(ioDispatcher) { searcher.search(projectPath, query) }
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = results,
                        totalMatches = results.sumOf { file -> file.matches.size },
                        searched = true,
                    )
                }
            }
    }

    override fun dispose() {
        scope.cancel()
    }

    private companion object {
        /** Delay between the last keystroke and the project scan it triggers. */
        const val SEARCH_DEBOUNCE_MS = 250L

        /** Capacity shared with the persisted workspace state. */
        const val MAX_HISTORY_SIZE = WorkspaceState.MAX_HISTORY_SIZE
    }
}
