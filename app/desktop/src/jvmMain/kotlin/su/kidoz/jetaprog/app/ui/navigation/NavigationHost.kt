package su.kidoz.jetaprog.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.keymap.DefaultKeymap
import su.kidoz.jetaprog.app.keymap.NavigationActions
import su.kidoz.jetaprog.app.notification.NotificationCenter

/**
 * Host composable that renders all navigation popups.
 *
 * Place this at the root of your editor layout to enable navigation features.
 */
@Composable
public fun NavigationHost(
    viewModel: NavigationViewModel,
    currentFilePath: String,
    currentFileName: String,
    currentLine: Int,
    currentColumn: Int,
    onNavigate: (filePath: String, line: Int, column: Int) -> Unit,
    notificationCenter: NotificationCenter,
    modifier: Modifier = Modifier,
    projectPath: String = "",
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Handle effects
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NavigationEffect.NavigateTo -> {
                    onNavigate(effect.filePath, effect.line, effect.column)
                }

                is NavigationEffect.ShowError -> {
                    notificationCenter.error(title = "Navigation", message = effect.message)
                }

                is NavigationEffect.ShowNotification -> {
                    notificationCenter.info(title = "Navigation", message = effect.message)
                }
            }
        }
    }

    // Search popup
    SearchPopup(
        isVisible = state.isSearchPopupVisible,
        mode = state.searchMode,
        results = state.searchResults,
        onQueryChange = { query ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.Search(query))
            }
        },
        onResultSelect = { result ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.SelectSearchResult(result))
            }
        },
        onDismiss = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.HideSearchPopup)
            }
        },
        onModeChange = { mode ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.ChangeSearchMode(mode))
            }
        },
        modifier = modifier,
    )

    // File structure popup
    FileStructurePopup(
        isVisible = state.isFileStructureVisible,
        fileName = state.fileStructureFileName,
        items = state.fileStructureItems,
        onItemSelect = { item ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.SelectStructureItem(item))
            }
        },
        onDismiss = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.HideFileStructure)
            }
        },
    )

    // Quick definition popup
    QuickDefinitionPopup(
        isVisible = state.isQuickDefinitionVisible,
        quickInfo = state.quickInfo,
        onNavigateToDefinition = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.NavigateToDefinition)
            }
        },
        onDismiss = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.HideQuickDefinition)
            }
        },
    )

    // Recent files popup
    RecentFilesPopup(
        isVisible = state.isRecentFilesVisible,
        entries = state.recentFiles,
        onEntrySelect = { entry ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.SelectRecentFile(entry))
            }
        },
        onDismiss = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.HideRecentFiles)
            }
        },
        projectPath = projectPath,
    )

    // Usages popup
    UsagesPopup(
        isVisible = state.isUsagesPopupVisible,
        result = state.usagesResult,
        onUsageSelect = { usage ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.SelectUsage(usage))
            }
        },
        onDismiss = {
            scope.launch {
                viewModel.processIntent(NavigationIntent.HideUsages)
            }
        },
    )
}

/**
 * Handle keyboard shortcuts for navigation.
 *
 * Call this from your key event handler.
 *
 * @return true if the event was handled, false otherwise
 */
public fun handleNavigationKeyEvent(
    event: KeyEvent,
    viewModel: NavigationViewModel,
    currentFilePath: String,
    currentFileName: String,
    currentLine: Int,
    currentColumn: Int,
    scope: kotlinx.coroutines.CoroutineScope,
): Boolean {
    // Only handle key down events
    if (event.type != KeyEventType.KeyDown) return false

    val action = DefaultKeymap.findAction(event) ?: return false

    when (action) {
        NavigationActions.GOTO_CLASS -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.ShowSearchPopup(SearchMode.CLASSES))
            }
            return true
        }

        NavigationActions.GOTO_FILE -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.ShowSearchPopup(SearchMode.FILES))
            }
            return true
        }

        NavigationActions.GOTO_SYMBOL -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.ShowSearchPopup(SearchMode.SYMBOLS))
            }
            return true
        }

        NavigationActions.SEARCH_EVERYWHERE -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.ShowSearchPopup(SearchMode.ALL))
            }
            return true
        }

        NavigationActions.FILE_STRUCTURE -> {
            scope.launch {
                viewModel.processIntent(
                    NavigationIntent.ShowFileStructure(currentFileName, currentFilePath),
                )
            }
            return true
        }

        NavigationActions.QUICK_DEFINITION -> {
            scope.launch {
                viewModel.processIntent(
                    NavigationIntent.ShowQuickDefinition(currentFilePath, currentLine, currentColumn),
                )
            }
            return true
        }

        NavigationActions.SHOW_USAGES -> {
            scope.launch {
                viewModel.processIntent(
                    NavigationIntent.ShowUsages(currentFilePath, currentLine, currentColumn),
                )
            }
            return true
        }

        NavigationActions.GOTO_DECLARATION -> {
            scope.launch {
                viewModel.processIntent(
                    NavigationIntent.GoToDeclaration(currentFilePath, currentLine, currentColumn),
                )
            }
            return true
        }

        NavigationActions.RECENT_FILES -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.ShowRecentFiles)
            }
            return true
        }

        NavigationActions.BACK -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.GoBack)
            }
            return true
        }

        NavigationActions.FORWARD -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.GoForward)
            }
            return true
        }

        NavigationActions.LAST_EDIT_LOCATION -> {
            scope.launch {
                viewModel.processIntent(NavigationIntent.GoToLastEditLocation)
            }
            return true
        }

        else -> {
            return false
        }
    }
}

/**
 * Breadcrumbs bar component with navigation support.
 */
@Composable
public fun NavigationBreadcrumbs(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    Breadcrumbs(
        items = state.breadcrumbs,
        onItemClick = { item ->
            scope.launch {
                viewModel.processIntent(NavigationIntent.NavigateToBreadcrumb(item))
            }
        },
        modifier = modifier,
    )
}
