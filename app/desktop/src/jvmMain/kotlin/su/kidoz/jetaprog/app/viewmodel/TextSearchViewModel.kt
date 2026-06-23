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
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.editor.search.FileTextMatches
import su.kidoz.jetaprog.editor.search.ProjectTextSearcher
import su.kidoz.jetaprog.editor.search.TextSearchQuery
import su.kidoz.jetaprog.platform.filesystem.FileSystem

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
)

/**
 * Drives project-wide full-text search ("Find in Files").
 *
 * @param projectPath the workspace root searched.
 * @param fileSystem used to traverse and read files.
 */
public class TextSearchViewModel(
    private val projectPath: String,
    fileSystem: FileSystem,
) : Disposable {
    private val searcher = ProjectTextSearcher(fileSystem)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(TextSearchState())

    /** The observable panel state. */
    public val state: StateFlow<TextSearchState> = _state.asStateFlow()

    /** Updates the query string. */
    public fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Toggles case sensitivity. */
    public fun toggleCaseSensitive() {
        _state.update { it.copy(caseSensitive = !it.caseSensitive) }
    }

    /** Toggles regular-expression mode. */
    public fun toggleRegex() {
        _state.update { it.copy(regex = !it.regex) }
    }

    /** Toggles whole-word matching. */
    public fun toggleWholeWord() {
        _state.update { it.copy(wholeWord = !it.wholeWord) }
    }

    /** Runs the search with the current query and options. */
    public fun search() {
        val current = _state.value
        if (current.query.isEmpty()) {
            _state.update { it.copy(results = emptyList(), totalMatches = 0, searched = false) }
            return
        }

        searchJob?.cancel()
        _state.update { it.copy(isSearching = true) }
        searchJob =
            scope.launch {
                val query =
                    TextSearchQuery(
                        query = current.query,
                        caseSensitive = current.caseSensitive,
                        regex = current.regex,
                        wholeWord = current.wholeWord,
                    )
                val results = withContext(Dispatchers.IO) { searcher.search(projectPath, query) }
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
}
