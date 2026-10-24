package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** Pinned next actions; permissions can be inspected without searching the transcript. */
@Composable
internal fun AgentAttentionSection(
    attention: AgentAttention,
    dispatch: (AgentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attention.status == AgentSessionStatus.IDLE || attention.status == AgentSessionStatus.RUNNING) return
    val approval = attention.approval
    var expanded by remember(approval?.id) { mutableStateOf(false) }
    val colors = LocalIntelliJColors.current
    Column(
        modifier.fillMaxWidth().background(colors.toolCardBackground).padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Text(
            sessionStatusLabel(attention.status),
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (attention.execution == AgentExecution.INTERRUPTED) {
            Text(
                "The turn stopped. Send a message to continue.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        attention.failure?.let {
            Text(
                it,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .heightIn(max = Dimensions.popupCompletionMaxHeight.dp)
                        .verticalScroll(rememberScrollState()),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            if (approval != null) {
                AttentionButton(
                    if (expanded) "Hide request" else "Review request",
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }
            if (attention.pendingFiles > 0 && attention.execution != AgentExecution.RUNNING) {
                AttentionButton(
                    "Review changes (${attention.pendingFiles})",
                    onClick = { dispatch(AgentIntent.ShowReview) },
                )
            }
        }
        if (expanded && approval != null) {
            Column(
                Modifier.heightIn(max = Dimensions.popupCompletionMaxHeight.dp).verticalScroll(rememberScrollState()),
            ) {
                ApprovalCard(approval) { dispatch(AgentIntent.SelectApproval(approval.id, it)) }
            }
        }
    }
}

/** Reuses the standard button with a visible keyboard focus ring. */
@Composable
private fun AttentionButton(
    text: String,
    expanded: Boolean? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IntelliJButton(
        text,
        onClick,
        modifier =
            Modifier
                .onFocusChanged { focused = it.hasFocus }
                .semantics {
                    role = Role.Button
                    expanded?.let {
                        stateDescription = if (it) "Expanded" else "Collapsed"
                    }
                }.border(
                    Spacing.xxs.dp,
                    if (focused) LocalIntelliJColors.current.accent else Color.Transparent,
                    RoundedCornerShape(Dimensions.cornerRadius.dp),
                ),
    )
}
