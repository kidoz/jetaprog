package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** Goals and plans share one disclosure so supporting work does not crowd the conversation. */
@Composable
internal fun AgentTaskDetails(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    if (state.goal == null && state.plan.isEmpty()) return
    var expanded by remember(state.activeRecordId) { mutableStateOf(false) }
    val colors = LocalIntelliJColors.current
    val complete = state.plan.count { it.status == PlanStepStatus.COMPLETED }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(role = Role.Button, onClickLabel = "Show or hide goal and plan") { expanded = !expanded }
                    .background(colors.toolCardBackground)
                    .padding(Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.textPrimary,
            )
            Column(Modifier.weight(1f)) {
                Text("Task details", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                val summary =
                    state.goal?.let { "${goalStatusLabel(it.status)} · ${it.objective}" }
                        ?: "Plan · $complete/${state.plan.size} completed"
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (expanded) {
            state.goal?.let { goal ->
                GoalCard(
                    goal = goal,
                    onPause = { dispatch(AgentIntent.PauseGoal) },
                    onResume = { dispatch(AgentIntent.ResumeGoal) },
                    onReplace = { dispatch(AgentIntent.ReplaceGoal) },
                    onClear = { dispatch(AgentIntent.ClearGoal) },
                )
            }
            PlanCard(state.plan)
        }
    }
}
