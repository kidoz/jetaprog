package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors

/** Parsed opening transcript, scoped to one mounted conversation and never persisted. */
internal val LocalRestoredMarkdown = staticCompositionLocalOf<Map<String, State>> { emptyMap() }

/**
 * Prepares saved Markdown off the UI thread before measuring a restored reading position.
 * Empty asynchronous Markdown placeholders otherwise clamp and overwrite the saved anchor.
 * Later streaming content keeps the normal incremental rendering path.
 */
@Composable
internal fun RestoredConversationContent(
    state: AgentUiState,
    content: @Composable () -> Unit,
) {
    key(state.workspacePath, state.activeRecordId) {
        val texts =
            remember {
                if (state.activeRecordId in state.taskNavigation.tasks) {
                    state.turns
                        .filterIsInstance<Turn.Agent>()
                        .flatMap { it.blocks.filterIsInstance<Block.Text>() }
                        .filterNot { it.thought }
                        .map { it.text }
                        .distinct()
                } else {
                    emptyList()
                }
            }
        val parsed by produceState<Map<String, State>?>(if (texts.isEmpty()) emptyMap() else null) {
            value =
                withContext(Dispatchers.Default) {
                    texts.associateWith {
                        ensureActive()
                        parseMarkdown(it)
                    }
                }
        }
        val ready = parsed
        if (ready == null) {
            Text("Restoring reading position…", color = LocalIntelliJColors.current.textSecondary)
        } else {
            CompositionLocalProvider(LocalRestoredMarkdown provides ready, content = content)
        }
    }
}
