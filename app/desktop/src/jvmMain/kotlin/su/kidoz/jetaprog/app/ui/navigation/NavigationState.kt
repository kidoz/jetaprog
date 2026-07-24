package su.kidoz.jetaprog.app.ui.navigation

import androidx.compose.runtime.Immutable
import su.kidoz.jetaprog.editor.navigation.BreadcrumbItem
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.NavigationHistoryEntry
import su.kidoz.jetaprog.editor.navigation.NavigationSearchResult
import su.kidoz.jetaprog.editor.navigation.QuickInfo
import su.kidoz.jetaprog.editor.navigation.StructureItem

/**
 * State for navigation popups and features.
 */
@Immutable
public data class NavigationState(
    // Search popup
    val isSearchPopupVisible: Boolean = false,
    val searchMode: SearchMode = SearchMode.ALL,
    val searchResults: List<NavigationSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    // File structure popup
    val isFileStructureVisible: Boolean = false,
    val fileStructureFileName: String = "",
    val fileStructureItems: List<StructureItem> = emptyList(),
    // Quick definition popup
    val isQuickDefinitionVisible: Boolean = false,
    val quickInfo: QuickInfo? = null,
    // Usages popup
    val isUsagesPopupVisible: Boolean = false,
    val usagesResult: FindUsagesResult? = null,
    // Recent files popup
    val isRecentFilesVisible: Boolean = false,
    val recentFiles: List<NavigationHistoryEntry> = emptyList(),
    // Breadcrumbs
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
)

/**
 * Intent for navigation actions.
 */
public sealed interface NavigationIntent {
    // Search popup
    public data class ShowSearchPopup(
        val mode: SearchMode,
    ) : NavigationIntent

    public data object HideSearchPopup : NavigationIntent

    public data class Search(
        val query: String,
    ) : NavigationIntent

    public data class ChangeSearchMode(
        val mode: SearchMode,
    ) : NavigationIntent

    public data class SelectSearchResult(
        val result: NavigationSearchResult,
    ) : NavigationIntent

    // File structure
    public data class ShowFileStructure(
        val fileName: String,
        val filePath: String,
    ) : NavigationIntent

    public data object HideFileStructure : NavigationIntent

    public data class SelectStructureItem(
        val item: StructureItem,
    ) : NavigationIntent

    // Quick definition
    public data class ShowQuickDefinition(
        val filePath: String,
        val line: Int,
        val column: Int,
    ) : NavigationIntent

    public data object HideQuickDefinition : NavigationIntent

    public data object NavigateToDefinition : NavigationIntent

    // Usages
    public data class ShowUsages(
        val filePath: String,
        val line: Int,
        val column: Int,
    ) : NavigationIntent

    public data class ShowUsagesResult(
        val result: FindUsagesResult,
    ) : NavigationIntent

    public data object HideUsages : NavigationIntent

    public data class SelectUsage(
        val usage: su.kidoz.jetaprog.editor.navigation.UsageInfo,
    ) : NavigationIntent

    // Go to declaration
    public data class GoToDeclaration(
        val filePath: String,
        val line: Int,
        val column: Int,
    ) : NavigationIntent

    // Recent files
    public data object ShowRecentFiles : NavigationIntent

    public data object HideRecentFiles : NavigationIntent

    public data class SelectRecentFile(
        val entry: NavigationHistoryEntry,
    ) : NavigationIntent

    // Breadcrumbs
    public data class UpdateBreadcrumbs(
        val filePath: String,
        val line: Int,
        val column: Int,
    ) : NavigationIntent

    public data class NavigateToBreadcrumb(
        val item: BreadcrumbItem,
    ) : NavigationIntent

    // History navigation
    public data object GoBack : NavigationIntent

    public data object GoForward : NavigationIntent

    public data object GoToLastEditLocation : NavigationIntent
}

/**
 * Side effects from navigation actions.
 */
public sealed interface NavigationEffect {
    /**
     * Navigate to a specific file and position.
     */
    public data class NavigateTo(
        val filePath: String,
        val line: Int,
        val column: Int,
    ) : NavigationEffect

    /**
     * Show an error message.
     */
    public data class ShowError(
        val message: String,
    ) : NavigationEffect

    /**
     * Show a notification.
     */
    public data class ShowNotification(
        val message: String,
    ) : NavigationEffect
}
