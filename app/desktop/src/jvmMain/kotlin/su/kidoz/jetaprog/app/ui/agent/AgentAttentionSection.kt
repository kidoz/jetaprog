package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    reviewVisible: Boolean = false,
    presence: Presence? = null,
    connectionFailed: Boolean = false,
    detailMaxHeight: Dp = Dimensions.popupCompletionMaxHeight.dp,
    requestExpanded: Boolean? = null,
    onRequestExpandedChange: ((Boolean) -> Unit)? = null,
) {
    if (attention.status == AgentSessionStatus.IDLE && !connectionFailed && !reviewVisible) return
    val approval = attention.approval
    var expanded by remember(approval?.id) { mutableStateOf(false) }
    val showingRequest = requestExpanded ?: expanded
    val colors = LocalIntelliJColors.current
    Column(
        modifier.fillMaxWidth().background(colors.toolCardBackground).padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Text(
                if (connectionFailed) "Connection failed" else sessionStatusLabel(attention.status),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (attention.execution == AgentExecution.RUNNING) {
                AttentionButton("Stop", onClick = { dispatch(AgentIntent.Stop) })
            }
            if (connectionFailed) {
                AttentionButton("Retry", onClick = { dispatch(AgentIntent.EnsureConnected) })
            }
        }
        if (attention.execution == AgentExecution.RUNNING && approval == null && presence != null) {
            TaskActivity(presence)
        }
        if (attention.execution == AgentExecution.INTERRUPTED) {
            Text(
                "The turn stopped. Send a message to continue.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (connectionFailed) {
            Text(
                "Check the agent command and sign-in, then retry.",
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
                        .heightIn(max = detailMaxHeight)
                        .verticalScroll(rememberScrollState()),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            if (approval != null) {
                AttentionButton(
                    if (showingRequest) "Hide request" else "Review request",
                    expanded = showingRequest,
                    onClick = {
                        expanded = !showingRequest
                        onRequestExpandedChange?.invoke(expanded)
                    },
                )
            }
            if (reviewVisible || (attention.pendingFiles > 0 && attention.execution != AgentExecution.RUNNING)) {
                AttentionButton(
                    if (reviewVisible) "Back to conversation" else "Review changes (${attention.pendingFiles})",
                    onClick = { dispatch(if (reviewVisible) AgentIntent.HideReview else AgentIntent.ShowReview) },
                )
            }
        }
        if (showingRequest && approval != null) {
            Column(
                Modifier.heightIn(max = detailMaxHeight).verticalScroll(rememberScrollState()),
            ) {
                ApprovalCard(approval) { dispatch(AgentIntent.SelectApproval(approval.id, it)) }
            }
        }
    }
}

/** Quiet activity detail; the explicit lifecycle determines whether this is shown. */
@Composable
private fun TaskActivity(presence: Presence) {
    var elapsed by remember(presence.startedAtEpochMillis) { mutableStateOf(0L) }
    LaunchedEffect(presence.startedAtEpochMillis) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - presence.startedAtEpochMillis) / TASK_CLOCK_MS).coerceAtLeast(0)
            delay(TASK_CLOCK_MS)
        }
    }
    val target = presence.fileName?.let { " · $it" }.orEmpty()
    val seconds = (elapsed % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    val duration = "${elapsed / SECONDS_PER_MINUTE}:$seconds"
    Text(
        "${presence.action}$target · $duration",
        color = LocalIntelliJColors.current.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val TASK_CLOCK_MS: Long = 1_000
private const val SECONDS_PER_MINUTE: Long = 60

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
