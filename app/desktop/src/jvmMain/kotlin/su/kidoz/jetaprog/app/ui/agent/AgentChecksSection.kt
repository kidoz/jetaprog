package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import java.time.Instant

/** Persistent active checkout identity; switch controls refer to a different checkout. */
@Composable
internal fun AgentWorkspaceContext(workspace: String) {
    val colors = LocalIntelliJColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Workspace", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                workspace.ifBlank { "Workspace unavailable" },
                modifier =
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .semantics { contentDescription = "Active workspace" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Collapsible command evidence, shared by the expanded and docked agent surfaces. */
@Composable
internal fun AgentChecksSection(
    checks: List<AgentCheck>,
    workspace: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides LocalIntelliJColors.current.textPrimary) {
        Column(modifier.fillMaxWidth().background(LocalIntelliJColors.current.toolCardBackground)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }.clickable(
                            role = Role.Button,
                            onClickLabel = "Show or hide check evidence",
                        ) { expanded = !expanded }
                        .padding(Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                Text("Checks", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(checkSummary(checks), style = MaterialTheme.typography.labelMedium)
            }
            if (expanded) {
                LazyColumn(
                    Modifier.heightIn(max = Dimensions.popupCompletionMaxHeight.dp).padding(horizontal = Spacing.md.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                ) {
                    item {
                        Text(
                            workspace,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalIntelliJColors.current.textSecondary,
                        )
                    }
                    if (checks.isEmpty()) {
                        item {
                            Text(
                                "No command evidence in this session.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(checks.asReversed(), key = { "${it.turnId}:${it.tool.id}" }) { check -> CheckEvidence(check) }
                }
            }
        }
    }
}

@Composable
private fun CheckEvidence(check: AgentCheck) {
    val color =
        when (check.status) {
            CheckStatus.PASSED -> LocalIntelliJColors.current.success
            CheckStatus.FAILED -> LocalIntelliJColors.current.error
            CheckStatus.STALE, CheckStatus.UNVERIFIED -> LocalIntelliJColors.current.warning
            CheckStatus.RUNNING -> LocalIntelliJColors.current.textSecondary
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
        Text("${check.status.label} · ${check.tool.name}", color = color, style = MaterialTheme.typography.labelLarge)
        check.tool.startedAtEpochMillis?.let { time ->
            Text(Instant.ofEpochMilli(time).toString(), style = MaterialTheme.typography.bodySmall)
        }
        SelectionContainer {
            Column {
                Text(check.tool.command ?: check.tool.args, style = MaterialTheme.typography.bodyMedium)
                Text(checkExplanation(check), style = MaterialTheme.typography.bodySmall)
                check.tool.result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
