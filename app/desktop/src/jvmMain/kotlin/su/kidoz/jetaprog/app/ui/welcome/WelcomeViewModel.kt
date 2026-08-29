package su.kidoz.jetaprog.app.ui.welcome

import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.settings.recent.RecentProjectEntry
import su.kidoz.jetaprog.settings.recent.RecentProjectsService
import kotlin.math.abs

/**
 * ViewModel for the Welcome Hub.
 *
 * Loads the recent-projects list from [recentProjectsService], filters it by the
 * search query, and translates user actions into [WelcomeEffect]s that the host
 * ([su.kidoz.jetaprog.app.ui.MainScreen]) wires to the real open/new/clone flows.
 */
public class WelcomeViewModel(
    private val recentProjectsService: RecentProjectsService,
) : MviViewModel<WelcomeIntent, WelcomeState, WelcomeEffect>(WelcomeState()) {
    init {
        refresh()
    }

    override suspend fun handleIntent(intent: WelcomeIntent) {
        when (intent) {
            is WelcomeIntent.Open -> {
                emitEffect(WelcomeEffect.OpenProject(intent.path))
            }

            is WelcomeIntent.NewProject -> {
                emitEffect(WelcomeEffect.ShowNewProject)
            }

            is WelcomeIntent.OpenProject -> {
                emitEffect(WelcomeEffect.BrowseToOpen)
            }

            is WelcomeIntent.Clone -> {
                emitEffect(WelcomeEffect.StartClone)
            }

            is WelcomeIntent.Search -> {
                updateState { copy(query = intent.query) }
            }

            is WelcomeIntent.Remove -> {
                val updated = recentProjectsService.remove(intent.path)
                updateState { copy(recents = updated.toRecentProjects()) }
            }

            is WelcomeIntent.OpenInNewWindow -> {
                emitEffect(WelcomeEffect.OpenInNewWindow(intent.path))
            }

            is WelcomeIntent.Refresh -> {
                val loaded = recentProjectsService.load()
                updateState { copy(recents = loaded.toRecentProjects()) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val loaded = recentProjectsService.load()
            updateState { copy(recents = loaded.toRecentProjects()) }
        }
    }

    private fun List<RecentProjectEntry>.toRecentProjects(): List<RecentProject> =
        map {
            RecentProject(
                name = it.name,
                path = it.path,
                lastOpenedEpochMillis = it.lastOpenedEpochMillis,
                accentIndex = accentIndexFor(it.path),
            )
        }

    private companion object {
        /** Number of colors in the welcome-tile accent palette (see `WelcomeScreen`). */
        private const val TILE_ACCENT_COUNT = 7

        /** Deterministically picks a tile accent index from a project path. */
        private fun accentIndexFor(path: String): Int = abs(path.hashCode()) % TILE_ACCENT_COUNT
    }
}
