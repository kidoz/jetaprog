package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType

/**
 * In-IDE Git workflow: branch status, staged/unstaged changes, file diff, and
 * commit of staged changes.
 *
 * @param viewModel the Git view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun GitPanel(
    viewModel: GitViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    if (!state.isRepository) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(IntelliJColors.toolWindowBackground)
                    .padding(Spacing.md.dp),
        ) {
            Text(
                text = "Not a Git repository",
                color = IntelliJColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(IntelliJColors.toolWindowBackground),
    ) {
        GitHeader(
            branch = state.branch,
            ahead = state.ahead,
            behind = state.behind,
            isBusy = state.isBusy,
            onRefresh = viewModel::refresh,
        )

        state.error?.let { error ->
            Text(
                text = error,
                color = IntelliJColors.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = Spacing.sm.dp,
                    vertical = Spacing.xs.dp,
                ),
        ) {
            if (state.unstaged.isEmpty() && state.staged.isEmpty()) {
                item {
                    EmptyChanges()
                }
            }

            if (state.unstaged.isNotEmpty()) {
                item { SectionHeader("Unstaged") }
                items(state.unstaged) { change ->
                    ChangeRow(
                        change = change,
                        actionIcon = Icons.Default.Add,
                        actionContentDescription = "Stage ${change.path}",
                        isSelected = state.selected == change,
                        onAction = { viewModel.stage(change) },
                        onClick = { viewModel.select(change) },
                    )
                }
            }

            if (state.staged.isNotEmpty()) {
                item { SectionHeader("Staged") }
                items(state.staged) { change ->
                    ChangeRow(
                        change = change,
                        actionIcon = Icons.Default.Remove,
                        actionContentDescription = "Unstage ${change.path}",
                        isSelected = state.selected == change,
                        onAction = { viewModel.unstage(change) },
                        onClick = { viewModel.select(change) },
                    )
                }
            }
        }

        DiffPreview(change = state.selected, diff = state.diff)
        CommitArea(
            commitMessage = state.commitMessage,
            canCommit = !state.isBusy && state.staged.isNotEmpty() && state.commitMessage.isNotBlank(),
            onCommitMessageChange = viewModel::setCommitMessage,
            onCommit = viewModel::commit,
        )
    }
}

@Composable
private fun GitHeader(
    branch: String?,
    ahead: Int,
    behind: Int,
    isBusy: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(start = Spacing.sm.dp, end = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Text(
            text = branch ?: "(detached)",
            color = IntelliJColors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BranchCounter(prefix = "↑", count = ahead)
        BranchCounter(prefix = "↓", count = behind)
        IconButton(
            onClick = onRefresh,
            enabled = !isBusy,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Git status",
                tint = if (isBusy) IntelliJColors.textDisabled else IntelliJColors.iconDefault,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BranchCounter(
    prefix: String,
    count: Int,
) {
    if (count <= 0) return
    Text(
        text = "$prefix$count",
        color = IntelliJColors.textSecondary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Composable
private fun EmptyChanges() {
    Text(
        text = "No changes",
        color = IntelliJColors.textMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm.dp),
    )
}

@Composable
private fun DiffPreview(
    change: GitChange?,
    diff: String,
) {
    if (change == null) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(IntelliJColors.backgroundDarker),
    ) {
        HorizontalDivider(color = IntelliJColors.divider)
        Text(
            text = "Diff: ${change.fileName()}",
            color = IntelliJColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.md.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = diff.ifEmpty { "No diff available for selected file" },
                color = if (diff.isEmpty()) IntelliJColors.textMuted else IntelliJColors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetaProgFonts.codeFont,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = IntelliJColors.accent,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm.dp, bottom = Spacing.xs.dp),
    )
}

@Composable
private fun ChangeRow(
    change: GitChange,
    actionIcon: ImageVector,
    actionContentDescription: String,
    isSelected: Boolean,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
        when {
            isSelected -> IntelliJColors.treeSelectionInactive
            isHovered -> IntelliJColors.treeHoverBackground
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xs.dp, vertical = Spacing.xxs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Text(
            text = change.type.statusLabel(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetaProgFonts.codeFont,
            color = change.type.statusColor(),
            modifier =
                Modifier
                    .background(IntelliJColors.surfaceContainer)
                    .widthIn(min = 18.dp)
                    .padding(horizontal = 4.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = change.fileName(),
                color = IntelliJColors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parentPath = change.parentPath()
            if (parentPath.isNotEmpty()) {
                Text(
                    text = parentPath,
                    color = IntelliJColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onAction,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionContentDescription,
                tint = IntelliJColors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CommitArea(
    commitMessage: String,
    canCommit: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.toolWindowHeader)
                .padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        OutlinedTextField(
            value = commitMessage,
            onValueChange = onCommitMessageChange,
            placeholder = { Text("Commit message") },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = IntelliJColors.textPrimary,
                    unfocusedTextColor = IntelliJColors.textPrimary,
                    focusedContainerColor = IntelliJColors.inputBackground,
                    unfocusedContainerColor = IntelliJColors.inputBackground,
                    focusedBorderColor = IntelliJColors.inputBorderFocused,
                    unfocusedBorderColor = IntelliJColors.inputBorder,
                    focusedPlaceholderColor = IntelliJColors.inputPlaceholder,
                    unfocusedPlaceholderColor = IntelliJColors.inputPlaceholder,
                ),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    color = IntelliJColors.textPrimary,
                    fontFamily = JetaProgFonts.codeFont,
                ),
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onCommit,
            enabled = canCommit,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = IntelliJColors.buttonPrimaryBackground,
                    contentColor = IntelliJColors.buttonPrimaryForeground,
                    disabledContainerColor = IntelliJColors.buttonBackground,
                    disabledContentColor = IntelliJColors.textDisabled,
                ),
            modifier = Modifier.fillMaxWidth().height(32.dp),
        ) {
            Text("Commit", maxLines = 1)
        }
    }
}

private fun GitChangeType.statusLabel(): String =
    when (this) {
        GitChangeType.ADDED -> "A"
        GitChangeType.MODIFIED -> "M"
        GitChangeType.DELETED -> "D"
        GitChangeType.RENAMED -> "R"
        GitChangeType.COPIED -> "C"
        GitChangeType.UNTRACKED -> "?"
        GitChangeType.CONFLICTED -> "!"
        GitChangeType.UNKNOWN -> "?"
    }

@Composable
private fun GitChangeType.statusColor(): Color =
    when (this) {
        GitChangeType.ADDED -> IntelliJColors.success
        GitChangeType.MODIFIED -> IntelliJColors.info
        GitChangeType.DELETED -> IntelliJColors.error
        GitChangeType.RENAMED -> IntelliJColors.warning
        GitChangeType.COPIED -> IntelliJColors.warning
        GitChangeType.UNTRACKED -> IntelliJColors.textSecondary
        GitChangeType.CONFLICTED -> IntelliJColors.error
        GitChangeType.UNKNOWN -> IntelliJColors.textMuted
    }

private fun GitChange.fileName(): String = path.substringAfterLast('/')

private fun GitChange.parentPath(): String = path.substringBeforeLast('/', missingDelimiterValue = "")
