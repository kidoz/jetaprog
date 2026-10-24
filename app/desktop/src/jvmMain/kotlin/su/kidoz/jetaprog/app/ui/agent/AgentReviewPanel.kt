package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.WorkspaceChange
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** Review scopes keep recorded agent decisions separate from read-only Git inspection. */
@Composable
internal fun AgentReviewPanel(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
    files: List<ReviewFile>,
    checks: List<AgentCheck>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            ReviewScope.entries.forEach { scope ->
                IntelliJButton(
                    if (scope == ReviewScope.AGENT) "Agent changes" else "Workspace changes",
                    onClick = { dispatch(AgentIntent.SetReviewScope(scope)) },
                    style = if (state.reviewScope == scope) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                    modifier = Modifier.semantics { selected = state.reviewScope == scope },
                )
            }
            IntelliJButton(
                "Close review",
                onClick = { dispatch(AgentIntent.HideReview) },
                modifier = Modifier.semantics { contentDescription = "Close review" },
            )
        }
        if (state.reviewScope == ReviewScope.AGENT) {
            if (files.isEmpty()) {
                ReviewText("No agent-reported changes. Check Workspace changes for other edits.")
                AgentChecksSection(checks, state.workspacePath)
            }
            ReviewCard(
                files = files,
                onAccept = { dispatch(AgentIntent.AcceptReviewFile(it)) },
                onDismiss = { dispatch(AgentIntent.DismissReviewFile(it)) },
                onClose = { dispatch(AgentIntent.HideReview) },
                showHeader = false,
                onOpen = { path ->
                    state.turns
                        .filterIsInstance<Turn.Agent>()
                        .flatMap { it.blocks }
                        .filterIsInstance<Block.Diff>()
                        .firstOrNull { it.absolutePath == path }
                        ?.let { dispatch(AgentIntent.OpenDiff(it.id)) }
                },
                verification = { AgentChecksSection(checks, state.workspacePath) },
            )
        } else {
            WorkspaceReview(state, dispatch, files, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun WorkspaceReview(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
    agentFiles: List<ReviewFile>,
    modifier: Modifier,
) {
    val review = state.workspaceReview
    val root = state.workspacePath.trimEnd('/', '\\') + "/"
    val agentPaths = agentFiles.map { it.key.replace('\\', '/').removePrefix(root.replace('\\', '/')) }.toSet()
    val listState = rememberLazyListState()
    LaunchedEffect(review.previewPath, review.preview) {
        if (review.previewPath != null) listState.animateScrollToItem(review.files.size + 1)
    }
    Box(modifier.fillMaxWidth().heightIn(max = Dimensions.popupCompletionMaxHeight.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.md.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                    ReviewText(
                        "Git snapshot · staged, unstaged, and untracked. Excludes ignored and committed changes.",
                    )
                    ReviewText("Read-only · file-level evidence can include mixed edits.")
                    if (review.preExistingPaths == null) {
                        ReviewText("Starting state unavailable. Unreported changes have unknown origin.")
                    }
                    if (state.hasUnsavedEdits) {
                        ReviewText(
                            "Unsaved editor changes are not included in this disk snapshot.",
                        )
                    }
                    if (state.isStreaming || (review.revision != null && review.revision != state.workspaceRevision)) {
                        ReviewText("Workspace may have changed. Refresh before reviewing the result.")
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                    ) {
                        IntelliJButton(
                            "Refresh",
                            onClick = { dispatch(AgentIntent.RefreshWorkspaceReview) },
                            enabled = !review.loading,
                        )
                        if (review.revision != null && review.error == null) {
                            ReviewText("${review.files.size} ${if (review.files.size == 1) "file" else "files"}")
                        }
                    }
                    when {
                        review.loading -> ReviewText("Loading workspace review…")
                        review.error != null -> ReviewText(review.error)
                        review.revision == null -> ReviewText("Refresh to inspect workspace changes.")
                        review.files.isEmpty() -> ReviewText("No local Git changes.")
                    }
                }
            }
            items(review.files, key = { it.path }) { file ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
                    SelectionContainer { ReviewText(file.path) }
                    file.originalPath?.let { ReviewText("Renamed/copied from $it") }
                    ReviewText(workspaceStatusLabel(file))
                    ReviewText(workspaceChangeOrigin(file, review.preExistingPaths, agentPaths))
                    IntelliJButton(
                        "Inspect",
                        onClick = { dispatch(AgentIntent.PreviewWorkspaceChange(file.path)) },
                        modifier = Modifier.semantics { contentDescription = "Inspect ${file.path}" },
                    )
                }
            }
            if (review.previewPath != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                        ReviewText("Preview · ${review.previewPath}")
                        when {
                            review.loading -> ReviewText("Loading preview…")
                            review.error != null -> ReviewText(review.error)
                            else -> review.preview?.let { WorkspacePreview(it) }
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun WorkspacePreview(preview: String) {
    SelectionContainer {
        Text(
            preview,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = Dimensions.popupCompletionMaxHeight.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            color = LocalIntelliJColors.current.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
        )
    }
}

@Composable
private fun ReviewText(text: String) {
    Text(text, color = LocalIntelliJColors.current.textPrimary, style = MaterialTheme.typography.bodySmall)
}

internal fun workspaceStatusLabel(file: WorkspaceChange): String {
    if (file.index == '?') return "Untracked"
    val status = "Staged: ${gitStatusLabel(file.index)} · Working tree: ${gitStatusLabel(file.workingTree)}"
    val conflict = "${file.index}${file.workingTree}" in setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")
    return if (conflict) "Conflict · $status" else status
}

private fun gitStatusLabel(code: Char): String =
    when (code) {
        ' ' -> "unchanged"
        'M' -> "modified"
        'A' -> "added"
        'D' -> "deleted"
        'R' -> "renamed"
        'C' -> "copied"
        'U' -> "conflicted"
        'T' -> "type changed"
        else -> "unknown ($code)"
    }
