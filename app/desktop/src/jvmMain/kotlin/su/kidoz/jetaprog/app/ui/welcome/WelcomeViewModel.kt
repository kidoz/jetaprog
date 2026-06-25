package su.kidoz.jetaprog.app.ui.welcome

import androidx.compose.ui.graphics.Color
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
                accent = accentFor(it.path),
            )
        }

    private companion object {
        /** Stable accent palette for project tiles (matches the design references). */
        private val TILE_PALETTE =
            listOf(
                Color(0xFF7F52FF),
                Color(0xFF5B9BD5),
                Color(0xFF4EC969),
                Color(0xFFDBA800),
                Color(0xFFE37933),
                Color(0xFF52B8B0),
                Color(0xFFC586C0),
            )

        /** Deterministically picks a palette color from a project path. */
        private fun accentFor(path: String): Color = TILE_PALETTE[abs(path.hashCode()) % TILE_PALETTE.size]
    }
}
