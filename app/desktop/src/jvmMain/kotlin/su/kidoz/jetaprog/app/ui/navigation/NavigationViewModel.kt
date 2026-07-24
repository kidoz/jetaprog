package su.kidoz.jetaprog.app.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.NavigationSearchResult
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.StructureItem
import su.kidoz.jetaprog.editor.navigation.UsageInfo

/**
 * ViewModel for navigation features.
 */
public class NavigationViewModel(
    private val navigationService: NavigationService? = null,
) {
    private val _state = MutableStateFlow(NavigationState())
    public val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _effects = Channel<NavigationEffect>(Channel.BUFFERED)
    public val effects: kotlinx.coroutines.flow.Flow<NavigationEffect> = _effects.receiveAsFlow()

    /**
     * Process a navigation intent.
     */
    public suspend fun processIntent(intent: NavigationIntent) {
        when (intent) {
            is NavigationIntent.ShowSearchPopup -> {
                showSearchPopup(intent.mode)
            }

            is NavigationIntent.HideSearchPopup -> {
                hideSearchPopup()
            }

            is NavigationIntent.Search -> {
                search(intent.query)
            }

            is NavigationIntent.ChangeSearchMode -> {
                changeSearchMode(intent.mode)
            }

            is NavigationIntent.SelectSearchResult -> {
                selectSearchResult(intent.result)
            }

            is NavigationIntent.ShowFileStructure -> {
                showFileStructure(intent.fileName, intent.filePath)
            }

            is NavigationIntent.HideFileStructure -> {
                hideFileStructure()
            }

            is NavigationIntent.SelectStructureItem -> {
                selectStructureItem(intent.item)
            }

            is NavigationIntent.ShowQuickDefinition -> {
                showQuickDefinition(
                    intent.filePath,
                    intent.line,
                    intent.column,
                )
            }

            is NavigationIntent.HideQuickDefinition -> {
                hideQuickDefinition()
            }

            is NavigationIntent.NavigateToDefinition -> {
                navigateToDefinition()
            }

            is NavigationIntent.ShowUsages -> {
                showUsages(intent.filePath, intent.line, intent.column)
            }

            is NavigationIntent.ShowUsagesResult -> {
                showUsagesResult(intent.result)
            }

            is NavigationIntent.HideUsages -> {
                hideUsages()
            }

            is NavigationIntent.SelectUsage -> {
                selectUsage(intent.usage)
            }

            is NavigationIntent.GoToDeclaration -> {
                goToDeclaration(intent.filePath, intent.line, intent.column)
            }

            is NavigationIntent.UpdateBreadcrumbs -> {
                updateBreadcrumbs(
                    intent.filePath,
                    intent.line,
                    intent.column,
                )
            }

            is NavigationIntent.NavigateToBreadcrumb -> {
                navigateToBreadcrumb(intent.item)
            }

            is NavigationIntent.GoBack -> {
                goBack()
            }

            is NavigationIntent.GoForward -> {
                goForward()
            }

            is NavigationIntent.GoToLastEditLocation -> {
                goToLastEditLocation()
            }
        }
    }

    // Search popup
    private fun showSearchPopup(mode: SearchMode) {
        _state.update {
            it.copy(
                isSearchPopupVisible = true,
                searchMode = mode,
                searchResults = emptyList(),
            )
        }
    }

    private fun hideSearchPopup() {
        _state.update {
            it.copy(
                isSearchPopupVisible = false,
                searchResults = emptyList(),
            )
        }
    }

    private suspend fun search(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        _state.update { it.copy(isSearching = true) }

        val results =
            navigationService?.let { service ->
                when (_state.value.searchMode) {
                    SearchMode.ALL -> {
                        val allResults = service.searchEverywhere(query)
                        allResults.values.flatten()
                    }

                    SearchMode.CLASSES -> {
                        service.searchClasses(query)
                    }

                    SearchMode.FILES -> {
                        service.searchFiles(query)
                    }

                    SearchMode.SYMBOLS -> {
                        service.searchSymbols(query)
                    }
                }
            } ?: emptyList()

        _state.update {
            it.copy(
                searchResults = results,
                isSearching = false,
            )
        }
    }

    private fun changeSearchMode(mode: SearchMode) {
        _state.update { it.copy(searchMode = mode) }
    }

    private suspend fun selectSearchResult(result: NavigationSearchResult) {
        hideSearchPopup()
        navigateTo(
            result.target.filePath,
            result.target.position.line,
            result.target.position.column,
        )
    }

    // File structure
    private suspend fun showFileStructure(
        fileName: String,
        filePath: String,
    ) {
        val items = navigationService?.getFileStructure(filePath) ?: emptyList()
        _state.update {
            it.copy(
                isFileStructureVisible = true,
                fileStructureFileName = fileName,
                fileStructureItems = items,
            )
        }
    }

    private fun hideFileStructure() {
        _state.update {
            it.copy(
                isFileStructureVisible = false,
                fileStructureItems = emptyList(),
            )
        }
    }

    private suspend fun selectStructureItem(item: StructureItem) {
        hideFileStructure()
        navigateTo(
            item.target.filePath,
            item.target.position.line,
            item.target.position.column,
        )
    }

    // Quick definition
    private suspend fun showQuickDefinition(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        val info = navigationService?.getQuickInfo(filePath, TextPosition(line, column))
        if (info != null) {
            _state.update {
                it.copy(
                    isQuickDefinitionVisible = true,
                    quickInfo = info,
                )
            }
        } else {
            _effects.send(NavigationEffect.ShowNotification("No definition found"))
        }
    }

    private fun hideQuickDefinition() {
        _state.update {
            it.copy(
                isQuickDefinitionVisible = false,
                quickInfo = null,
            )
        }
    }

    private suspend fun navigateToDefinition() {
        val info = _state.value.quickInfo
        if (info != null) {
            hideQuickDefinition()
            navigateTo(
                info.symbol.filePath,
                info.symbol.position.line,
                info.symbol.position.column,
            )
        }
    }

    // Usages
    private suspend fun showUsages(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        val result = navigationService?.findUsages(filePath, TextPosition(line, column))
        if (result != null && result.totalCount > 0) {
            _state.update {
                it.copy(
                    isUsagesPopupVisible = true,
                    usagesResult = result,
                )
            }
        } else {
            _effects.send(NavigationEffect.ShowNotification("No usages found"))
        }
    }

    private fun showUsagesResult(result: FindUsagesResult) {
        _state.update {
            it.copy(
                isUsagesPopupVisible = true,
                usagesResult = result,
            )
        }
    }

    private fun hideUsages() {
        _state.update {
            it.copy(
                isUsagesPopupVisible = false,
                usagesResult = null,
            )
        }
    }

    private suspend fun selectUsage(usage: UsageInfo) {
        hideUsages()
        navigateTo(
            usage.target.filePath,
            usage.target.position.line,
            usage.target.position.column,
        )
    }

    // Go to declaration
    private suspend fun goToDeclaration(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        val target = navigationService?.getDefinition(filePath, TextPosition(line, column))
        if (target != null) {
            navigateTo(
                target.filePath,
                target.position.line,
                target.position.column,
            )
        } else {
            _effects.send(NavigationEffect.ShowNotification("No declaration found"))
        }
    }

    // Breadcrumbs
    private suspend fun updateBreadcrumbs(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        val items = navigationService?.getBreadcrumbs(filePath, TextPosition(line, column)) ?: emptyList()
        _state.update { it.copy(breadcrumbs = items) }
    }

    private suspend fun navigateToBreadcrumb(item: su.kidoz.jetaprog.editor.navigation.BreadcrumbItem) {
        navigateTo(
            item.target.filePath,
            item.target.position.line,
            item.target.position.column,
        )
    }

    // History navigation
    private suspend fun goBack() {
        val entry = navigationService?.goBack()
        if (entry != null) {
            navigateTo(entry.filePath, entry.position.line, entry.position.column)
        }
    }

    private suspend fun goForward() {
        val entry = navigationService?.goForward()
        if (entry != null) {
            navigateTo(entry.filePath, entry.position.line, entry.position.column)
        }
    }

    private suspend fun goToLastEditLocation() {
        val entry = navigationService?.getLastEditLocation()
        if (entry != null) {
            navigateTo(entry.filePath, entry.position.line, entry.position.column)
        } else {
            _effects.send(NavigationEffect.ShowNotification("No edit history"))
        }
    }

    // Helper
    private suspend fun navigateTo(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        _effects.send(NavigationEffect.NavigateTo(filePath, line, column))
    }

    /**
     * Dismiss all popups.
     */
    public fun dismissAll() {
        _state.update {
            it.copy(
                isSearchPopupVisible = false,
                isFileStructureVisible = false,
                isQuickDefinitionVisible = false,
                isUsagesPopupVisible = false,
            )
        }
    }
}
