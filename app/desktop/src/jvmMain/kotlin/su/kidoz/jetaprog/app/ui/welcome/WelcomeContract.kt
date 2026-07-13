package su.kidoz.jetaprog.app.ui.welcome

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State

/**
 * A recent project as presented on the Welcome Hub.
 *
 * @property name Display name (project directory name).
 * @property path Absolute path of the project root.
 * @property lastOpenedEpochMillis Last-opened wall-clock time in epoch milliseconds.
 * @property accent Tile accent color, derived deterministically from [path].
 */
@Immutable
public data class RecentProject(
    val name: String,
    val path: String,
    val lastOpenedEpochMillis: Long,
    val accent: Color,
)

/**
 * State for the Welcome Hub.
 *
 * @property query Current search query filtering the recent list.
 * @property recents All known recent projects, most-recent first.
 */
@Immutable
public data class WelcomeState(
    val query: String = "",
    val recents: List<RecentProject> = emptyList(),
) : State {
    /** [recents] filtered by [query] against project name and path. */
    val filtered: List<RecentProject>
        get() =
            if (query.isBlank()) {
                recents
            } else {
                recents.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.path.contains(query, ignoreCase = true)
                }
            }
}

/** User intents for the Welcome Hub. */
public sealed interface WelcomeIntent : Intent {
    /** Open the project at [path] (recent-row click). */
    public data class Open(
        val path: String,
    ) : WelcomeIntent

    /** Start the New Project wizard. */
    public data object NewProject : WelcomeIntent

    /** Open an existing project via the directory picker. */
    public data object OpenProject : WelcomeIntent

    /** Clone a remote repository. */
    public data object Clone : WelcomeIntent

    /** Update the search query. */
    public data class Search(
        val query: String,
    ) : WelcomeIntent

    /** Remove the project at [path] from the recent list. */
    public data class Remove(
        val path: String,
    ) : WelcomeIntent

    /** Open the project at [path] in a new window (recent-row context menu). */
    public data class OpenInNewWindow(
        val path: String,
    ) : WelcomeIntent

    /** Reload the recent list from persistent storage. */
    public data object Refresh : WelcomeIntent
}

/** One-time side effects emitted by the Welcome Hub. */
public sealed interface WelcomeEffect : Effect {
    /** Request the host to open the project at [path]. */
    public data class OpenProject(
        val path: String,
    ) : WelcomeEffect

    /** Request the host to show the New Project wizard. */
    public data object ShowNewProject : WelcomeEffect

    /** Request the host to show the open-project directory picker. */
    public data object BrowseToOpen : WelcomeEffect

    /** Request the host to start a clone-repository flow. */
    public data object StartClone : WelcomeEffect

    /** Request the host to open the project at [path] in a new window. */
    public data class OpenInNewWindow(
        val path: String,
    ) : WelcomeEffect
}
