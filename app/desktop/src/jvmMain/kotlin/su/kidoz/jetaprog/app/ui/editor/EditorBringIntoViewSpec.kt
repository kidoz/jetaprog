package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Bring-into-view policy for the code editor's scroll containers.
 *
 * Compose's text field asks its scroll parent to reveal the caret whenever it gains focus,
 * and it does so with the caret it held *before* the click that focused it. After a session
 * restore, or when a file was opened from the project tree (whose rows take focus on click),
 * that caret is usually far from where the user clicked, so the viewport jumped away from
 * the click and the caret could even land on a different line as the text moved under the
 * pointer. While [suppressed], every reveal request is reported as already satisfied, which
 * makes the scroll container drop it without animating. Once the field is focused and
 * settled the [delegate]'s behaviour is restored, so typing at the edge of the viewport still
 * scrolls the caret into view.
 */
internal class EditorBringIntoViewSpec(
    private val delegate: BringIntoViewSpec,
) : BringIntoViewSpec {
    /** Whether reveal requests are ignored. Starts suppressed: an unfocused editor never reveals. */
    var suppressed: Boolean = true

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = if (suppressed) 0f else delegate.calculateScrollDistance(offset, size, containerSize)
}

/** Remembers an [EditorBringIntoViewSpec] wrapping the platform default policy. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberEditorBringIntoViewSpec(): EditorBringIntoViewSpec {
    val platformSpec = LocalBringIntoViewSpec.current
    return remember(platformSpec) { EditorBringIntoViewSpec(platformSpec) }
}

/** Applies [spec] to every scroll container composed in [content]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProvideEditorBringIntoViewSpec(
    spec: EditorBringIntoViewSpec,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
