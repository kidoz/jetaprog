package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** A file or inclusive line-range comment collected before sending a revision request. */
@Immutable
public data class ReviewComment(
    val id: String,
    val path: String,
    val lines: String,
    val text: String,
    val scope: ReviewScope,
)

/** Renders a revision request as ordinary editable user text. */
internal fun reviewFeedbackPrompt(comments: List<ReviewComment>): String =
    buildString {
        append("Please revise the changes using this review feedback:\n")
        comments.forEachIndexed { index, comment ->
            append("\n${index + 1}. ${comment.path}")
            if (comment.lines.isNotBlank()) append(":${comment.lines}")
            append(
                if (comment.scope ==
                    ReviewScope.AGENT
                ) {
                    " (Agent changes)"
                } else {
                    " (Workspace changes; origin may be mixed)"
                },
            )
            append("\n${comment.text.trim()}\n")
        }
    }

/** Accepts a one-based line or inclusive range; empty means the whole file. */
internal fun validReviewLines(lines: String): Boolean {
    if (lines.isBlank()) return true
    val parts = lines.split('-')
    if (parts.size !in 1..2) return false
    val start = parts.first().toIntOrNull() ?: return false
    val end = parts.last().toIntOrNull() ?: return false
    return start > 0 && end >= start
}

/** Batched feedback stays editable in the composer before the user chooses Send or Queue. */
@Composable
internal fun AgentReviewFeedback(
    state: AgentUiState,
    paths: List<String>,
    dispatch: (AgentIntent) -> Unit,
) {
    var expanded by remember(state.activeRecordId) { mutableStateOf(false) }
    var path by remember(state.activeRecordId, state.reviewScope) { mutableStateOf("") }
    var lines by remember(state.activeRecordId, state.reviewScope) { mutableStateOf("") }
    var comment by remember(state.activeRecordId, state.reviewScope) { mutableStateOf("") }
    CompositionLocalProvider(LocalContentColor provides LocalIntelliJColors.current.textPrimary) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            IntelliJButton(
                if (expanded) "Hide feedback" else "Review feedback (${state.reviewComments.size})",
                onClick = { expanded = !expanded },
            )
            if (expanded) {
                Text(
                    "Choose a file, then add a comment. Lines are optional.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
                    paths.forEach { candidate ->
                        IntelliJButton(
                            candidate.removePrefix(state.workspacePath.trimEnd('/') + "/"),
                            onClick = { path = candidate },
                            modifier = Modifier.semantics { contentDescription = "Comment on $candidate" },
                        )
                    }
                }
                if (path.isNotBlank()) Text(path, style = MaterialTheme.typography.bodySmall)
                FeedbackField(lines, { lines = it }, "Line or range (for example 12-18)")
                FeedbackField(comment, { comment = it }, "Review comment")
                if (!validReviewLines(lines)) Text("Use a positive line number or an ordered range.")
                IntelliJButton(
                    "Add comment",
                    enabled = path in paths && comment.isNotBlank() && validReviewLines(lines),
                    onClick = {
                        dispatch(AgentIntent.AddReviewComment(path, lines, comment))
                        comment = ""
                        lines = ""
                    },
                )
                state.reviewComments.forEach { item ->
                    Text(
                        "${item.path}${if (item.lines.isBlank()) "" else ":${item.lines}"}\n${item.text}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IntelliJButton("Remove comment", onClick = { dispatch(AgentIntent.RemoveReviewComment(item.id)) })
                }
                IntelliJButton(
                    "Preview in composer",
                    enabled = state.reviewComments.isNotEmpty(),
                    onClick = { dispatch(AgentIntent.ComposeReviewFeedback) },
                )
            }
        }
    }
}

@Composable
private fun FeedbackField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
) {
    val colors = LocalIntelliJColors.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        BasicTextField(
            value,
            onChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = Dimensions.popupCompletionMaxHeight.dp)
                    .semantics { contentDescription = label }
                    .background(colors.inputBackground)
                    .padding(Spacing.sm.dp),
        )
    }
}
