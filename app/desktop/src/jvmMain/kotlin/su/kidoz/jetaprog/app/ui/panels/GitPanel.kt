package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import su.kidoz.jetaprog.vcs.GitCommit
import kotlin.math.abs

private const val DIFF_PREVIEW_HEIGHT = 120
private const val LOG_SECTION_HEIGHT = 170

/** Deterministic avatar palette for commit authors. */
private val AVATAR_PALETTE =
    listOf(
        Color(0xFF5B9BD5),
        Color(0xFF7F52FF),
        Color(0xFF59A869),
        Color(0xFFE0883C),
        Color(0xFF9876AA),
    )

/**
 * In-IDE Git workflow rendered as a commit view: changed files with stage
 * checkboxes and status, a commit message with Commit / Commit and Push, an
 * optional diff preview, and the recent commit log.
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

    val changes = state.staged + state.unstaged
    val allStaged = state.unstaged.isEmpty() && state.staged.isNotEmpty()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(IntelliJColors.toolWindowBackground),
    ) {
        CommitHeader(isBusy = state.isBusy, onRefresh = viewModel::refresh)

        state.error?.let { error ->
            Text(
                text = error,
                color = IntelliJColors.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
            )
        }

        ChangesHeader(
            fileCount = changes.size,
            allStaged = allStaged,
            onToggleAll = { viewModel.setAllStaged(!allStaged) },
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (changes.isEmpty()) {
                item { EmptyChanges() }
            }
            items(changes, key = { "${it.staged}:${it.path}" }) { change ->
                ChangeRow(
                    change = change,
                    isSelected = state.selected == change,
                    onToggle = { viewModel.toggleStage(change) },
                    onClick = { viewModel.select(change) },
                )
            }
        }

        CommitArea(
            commitMessage = state.commitMessage,
            canCommit = !state.isBusy && state.staged.isNotEmpty() && state.commitMessage.isNotBlank(),
            onCommitMessageChange = viewModel::setCommitMessage,
            onCommit = viewModel::commit,
            onCommitAndPush = viewModel::commitAndPush,
        )

        if (state.selected != null) {
            DiffPreview(change = state.selected, diff = state.diff)
        }

        LogSection(commits = state.commitLog)
    }
}

@Composable
private fun CommitHeader(
    isBusy: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.panelHeaderHeight.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(start = Spacing.md.dp, end = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Commit",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Git status",
                tint = if (isBusy) IntelliJColors.textDisabled else IntelliJColors.iconDefault,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
    }
}

@Composable
private fun ChangesHeader(
    fileCount: Int,
    allStaged: Boolean,
    onToggleAll: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        StageCheckbox(checked = allStaged, enabled = fileCount > 0, onToggle = onToggleAll)
        Text(text = "Changes", color = IntelliJColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(text = "$fileCount files", color = IntelliJColors.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun StageCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) IntelliJColors.accent else Color.Transparent)
                .border(1.5.dp, if (enabled) IntelliJColors.accent else IntelliJColors.border, RoundedCornerShape(3.dp))
                .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun EmptyChanges() {
    Text(
        text = "No changes",
        color = IntelliJColors.textMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
    )
}

@Composable
private fun ChangeRow(
    change: GitChange,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
        when {
            isSelected -> IntelliJColors.treeSelectionBackground
            isHovered -> IntelliJColors.treeHoverBackground
            else -> Color.Transparent
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(backgroundColor)
                    .hoverable(interactionSource)
                    .clickable(onClick = onClick)
                    .padding(start = Spacing.md.dp, end = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            StageCheckbox(checked = change.staged, enabled = true, onToggle = onToggle)
            FileBadge(fileName = change.fileName())
            Text(
                text = change.fileName(),
                color = if (isSelected) Color.White else IntelliJColors.textPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = change.parentPath(),
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = change.type.statusLabel(),
                color = change.type.statusColor(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetaProgFonts.codeFont,
            )
        }
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .height(24.dp)
                        .background(IntelliJColors.accent),
            )
        }
    }
}

@Composable
private fun FileBadge(fileName: String) {
    val (color, label) = badgeFor(fileName)
    Box(
        modifier = Modifier.size(15.dp).clip(RoundedCornerShape(3.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CommitArea(
    commitMessage: String,
    canCommit: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
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
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp), modifier = Modifier.fillMaxWidth()) {
            IntelliJButton(
                text = "Commit",
                onClick = onCommit,
                style = ButtonStyle.PRIMARY,
                enabled = canCommit,
                icon = Icons.Default.Check,
                modifier = Modifier.weight(1f),
            )
            IntelliJButton(
                text = "Commit and Push",
                onClick = onCommitAndPush,
                style = ButtonStyle.SECONDARY,
                enabled = canCommit,
            )
        }
    }
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
                .height(DIFF_PREVIEW_HEIGHT.dp)
                .background(IntelliJColors.backgroundDarker),
    ) {
        Text(
            text = "Diff · ${change.fileName()}",
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
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
            diff.lineSequence().forEach { line ->
                Text(
                    text = line.ifEmpty { " " },
                    color = diffLineColor(line),
                    fontSize = 12.sp,
                    fontFamily = JetaProgFonts.codeFont,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LogSection(commits: List<GitCommit>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LOG_SECTION_HEIGHT.dp)
                .background(IntelliJColors.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(IntelliJColors.surface)
                    .padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Log", color = IntelliJColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(commits, key = { it.hash }) { commit ->
                LogRow(commit = commit)
            }
        }
    }
}

@Composable
private fun LogRow(commit: GitCommit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(avatarColor(commit.author)),
        )
        commit.refs.firstOrNull()?.let { ref ->
            Text(
                text = ref,
                color = IntelliJColors.success,
                fontSize = 11.sp,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .background(IntelliJColors.successMuted)
                        .padding(horizontal = 5.dp),
            )
        }
        Text(
            text = commit.message,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Avatar(author = commit.author)
        Text(text = commit.relativeDate, color = IntelliJColors.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Avatar(author: String) {
    val initials =
        author
            .split(' ', '.', '-')
            .mapNotNull { it.firstOrNull() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifBlank { "?" }
    Box(
        modifier = Modifier.size(18.dp).clip(CircleShape).background(avatarColor(author)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun avatarColor(seed: String): Color = AVATAR_PALETTE[abs(seed.hashCode()) % AVATAR_PALETTE.size]

private fun badgeFor(fileName: String): Pair<Color, String> =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> IntelliJColors.iconKotlin to "K"
        "java" -> IntelliJColors.iconJava to "J"
        "rs" -> IntelliJColors.iconRust to "R"
        "py" -> IntelliJColors.iconPython to "P"
        "" -> IntelliJColors.iconFile to "•"
        else -> IntelliJColors.iconFile to fileName.first().uppercase()
    }

private fun diffLineColor(line: String): Color =
    when {
        line.startsWith("+") && !line.startsWith("+++") -> IntelliJColors.success
        line.startsWith("-") && !line.startsWith("---") -> IntelliJColors.error
        line.startsWith("@@") -> IntelliJColors.accent
        else -> IntelliJColors.textPrimary
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
