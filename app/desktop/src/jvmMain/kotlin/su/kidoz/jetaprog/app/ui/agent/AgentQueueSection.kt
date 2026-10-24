package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** Ordered follow-ups with explicit paused delivery and item-level controls. */
@Composable
internal fun AgentQueueSection(
    queue: List<QueuedFollowUp>,
    paused: Boolean,
    dispatch: (AgentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<QueueEdit?>(null) }
    val colors = LocalIntelliJColors.current
    Column(
        modifier.fillMaxWidth().background(colors.background),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            Text(
                "${if (paused) "Queue paused" else "Queued"} (${queue.size}) · ${if (expanded) "Hide" else "Show"}",
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .weight(1f)
                        .agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }.clickable(role = Role.Button) { expanded = !expanded }
                        .padding(Spacing.sm.dp),
            )
            QueueButton(
                if (paused) "Resume queue" else "Pause queue",
                "${if (paused) "Resume" else "Pause"} queued delivery",
                enabled = editor == null || queue.none { it.id == editor?.id },
            ) {
                dispatch(if (paused) AgentIntent.ResumeQueue else AgentIntent.PauseQueue)
            }
        }
        if (editor != null && queue.any { it.id == editor?.id }) {
            Text(
                "Save or cancel your edit to resume.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.sm.dp),
            )
        }
        if (!expanded) {
            Text(
                "Next: ${queue.first().prompt}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.sm.dp),
            )
        } else {
            LazyColumn(
                Modifier.heightIn(max = Dimensions.popupCompletionMaxHeight.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    QueueItem(item, index, queue.lastIndex, editor, { editor = it }, dispatch)
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    item: QueuedFollowUp,
    index: Int,
    lastIndex: Int,
    editor: QueueEdit?,
    onEdit: (QueueEdit?) -> Unit,
    dispatch: (AgentIntent) -> Unit,
) {
    val editing = editor?.id == item.id
    val draft = if (editing) editor.text else item.prompt
    val colors = LocalIntelliJColors.current
    Column(Modifier.fillMaxWidth().padding(Spacing.sm.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
        Text(
            "${index + 1}${if (item.steer) " · Steering next" else ""}",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (editing) {
            BasicTextField(
                draft,
                { onEdit(QueueEdit(item.id, it)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Edit queued message ${index + 1}" }
                        .background(colors.inputBackground)
                        .padding(Spacing.sm.dp),
            )
        } else {
            Text(
                item.prompt,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.attachments.isNotEmpty()) {
            Text(
                item.attachments.joinToString { chipLabel(it) },
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            if (editing) {
                QueueButton("Save", "Save queued message ${index + 1}", enabled = draft.isNotBlank()) {
                    dispatch(AgentIntent.UpdateQueuedPrompt(item.id, draft))
                    onEdit(null)
                }
                QueueButton("Cancel", "Cancel editing queued message ${index + 1}") {
                    onEdit(null)
                }
            } else {
                QueueButton("Edit", "Edit queued message ${index + 1}", enabled = editor == null) {
                    dispatch(AgentIntent.PauseQueue)
                    onEdit(QueueEdit(item.id, item.prompt))
                }
                QueueButton("Up", "Move queued message ${index + 1} earlier", enabled = index > 0) {
                    dispatch(AgentIntent.MoveQueuedPrompt(item.id, earlier = true))
                }
                QueueButton("Down", "Move queued message ${index + 1} later", enabled = index < lastIndex) {
                    dispatch(AgentIntent.MoveQueuedPrompt(item.id, earlier = false))
                }
                QueueButton(
                    "Remove",
                    "Remove queued message ${index + 1}",
                ) { dispatch(AgentIntent.RemoveQueuedPrompt(item.id)) }
            }
        }
    }
}

@Composable
private fun QueueButton(
    text: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IntelliJButton(
        text,
        onClick,
        enabled = enabled,
        modifier =
            Modifier
                .onFocusChanged { focused = it.hasFocus }
                .semantics {
                    contentDescription = description
                    role =
                        Role.Button
                }.border(
                    Spacing.xxs.dp,
                    if (focused) LocalIntelliJColors.current.accent else Color.Transparent,
                    RoundedCornerShape(Dimensions.cornerRadius.dp),
                ),
    )
}

private data class QueueEdit(
    val id: String,
    val text: String,
)
