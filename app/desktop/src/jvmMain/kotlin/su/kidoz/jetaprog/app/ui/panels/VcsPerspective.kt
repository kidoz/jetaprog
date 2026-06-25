package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitCommit

private const val LOG_SECTION_EXPANDED_HEIGHT = 230
private const val LOG_SECTION_COLLAPSED_HEIGHT = 32
private const val LOG_TAB_STRIP_HEIGHT = 30
private const val LOG_COLUMN_HEADER_HEIGHT = 24
private const val LOG_FILTER_HEIGHT = 24
private const val LOG_ROW_HEIGHT = 28
private const val AUTHOR_COLUMN_WIDTH = 150
private const val DATE_COLUMN_WIDTH = 120

private val DIFF_ADD_BG = Color(0xFF1F3326)
private val DIFF_DELETE_BG = Color(0xFF3A2526)
private val DIFF_ADD_TEXT = Color(0xFFA8C9A0)
private val DIFF_DELETE_TEXT = Color(0xFFD26B6B)
private val DIFF_ADD_SIGN = Color(0xFF7FB97A)

/** A single side of a parsed unified diff. */
private enum class DiffKind { CONTEXT, ADD, DELETE }

private data class DiffLine(
    val number: Int?,
    val sign: Char,
    val text: String,
    val kind: DiffKind,
)

/**
 * The main-area **Version Control perspective**: a side-by-side diff of the
 * selected change above the recent commit log. Rendered in place of the editor
 * when the VCS activity item is active.
 *
 * @param viewModel the Git view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun VcsMainArea(
    viewModel: GitViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val selected = state.selected
    var isLogExpanded by rememberSaveable { mutableStateOf(false) }
    val (oldLines, newLines) =
        remember(state.diff, selected) {
            if (selected == null) emptyList<DiffLine>() to emptyList() else parseUnifiedDiff(state.diff)
        }

    Column(modifier = modifier.fillMaxSize().background(IntelliJColors.background)) {
        DiffHeader(
            change = selected,
            additions = newLines.count { it.kind == DiffKind.ADD },
            deletions = oldLines.count { it.kind == DiffKind.DELETE },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selected == null) {
                Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg.dp)) {
                    Text(
                        text = "Select a changed file to view its diff.",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    DiffColumn(
                        title = "Before — HEAD",
                        lines = oldLines,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        borderRight = true,
                    )
                    DiffColumn(
                        title = "After — Working tree",
                        lines = newLines,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        borderRight = false,
                    )
                }
            }
        }
        GitLogTable(
            commits = state.commitLog,
            expanded = isLogExpanded,
            onToggleExpanded = { isLogExpanded = !isLogExpanded },
        )
    }
}

@Composable
private fun DiffHeader(
    change: GitChange?,
    additions: Int,
    deletions: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.panelHeaderHeight.dp)
                .background(IntelliJColors.background)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        if (change == null) {
            Text(text = "Version Control", color = IntelliJColors.textSecondary, fontSize = 13.sp)
            return@Row
        }
        FileBadge(fileName = change.fileName())
        Text(text = change.fileName(), color = Color.White, fontSize = 13.sp)
        Text(
            text = change.parentPath(),
            color = IntelliJColors.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DiffStat(symbol = "+", count = additions, color = IntelliJColors.success)
        DiffStat(symbol = "−", count = deletions, color = DIFF_DELETE_TEXT)
    }
}

@Composable
private fun DiffStat(
    symbol: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text = "$symbol$count", color = IntelliJColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun DiffColumn(
    title: String,
    lines: List<DiffLine>,
    modifier: Modifier = Modifier,
    borderRight: Boolean = false,
) {
    Column(
        modifier =
            modifier.then(
                if (borderRight) {
                    Modifier.padding(end = Dimensions.splitterThickness.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = title,
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        )
        val horizontalScroll = rememberScrollState()
        LazyColumn(modifier = Modifier.fillMaxSize().background(IntelliJColors.background)) {
            items(lines) { line ->
                DiffRow(line = line, horizontalScroll = horizontalScroll)
            }
        }
    }
}

@Composable
private fun DiffRow(
    line: DiffLine,
    horizontalScroll: androidx.compose.foundation.ScrollState,
) {
    val background =
        when (line.kind) {
            DiffKind.ADD -> DIFF_ADD_BG
            DiffKind.DELETE -> DIFF_DELETE_BG
            DiffKind.CONTEXT -> Color.Transparent
        }
    val signColor =
        when (line.kind) {
            DiffKind.ADD -> DIFF_ADD_SIGN
            DiffKind.DELETE -> DIFF_DELETE_TEXT
            DiffKind.CONTEXT -> IntelliJColors.textMuted
        }
    val textColor =
        when (line.kind) {
            DiffKind.ADD -> DIFF_ADD_TEXT
            DiffKind.DELETE -> DIFF_DELETE_TEXT
            DiffKind.CONTEXT -> IntelliJColors.textPrimary
        }
    Row(modifier = Modifier.fillMaxWidth().height(21.dp).background(background)) {
        Text(
            text = line.number?.toString().orEmpty(),
            color = IntelliJColors.lineNumberForeground,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(44.dp).padding(end = Spacing.md.dp),
            maxLines = 1,
        )
        Text(
            text = line.sign.toString().trim(),
            color = signColor,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(14.dp),
            maxLines = 1,
        )
        Text(
            text = line.text.ifEmpty { " " },
            color = textColor,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(horizontalScroll),
        )
    }
}

@Composable
private fun GitLogTable(
    commits: List<GitCommit>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (expanded) LOG_SECTION_EXPANDED_HEIGHT.dp else LOG_SECTION_COLLAPSED_HEIGHT.dp)
                .background(IntelliJColors.background),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LOG_TAB_STRIP_HEIGHT.dp)
                    .background(IntelliJColors.surface)
                    .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
        ) {
            LogTab(icon = Icons.Filled.AccountTree, label = "Log", selected = true)
            if (expanded) {
                LogTab(icon = Icons.Filled.History, label = "File History", selected = false)
            } else {
                Text(
                    text = "${commits.size} commits",
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (expanded) {
                LogFilterChip()
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (expanded) "Collapse log" else "Expand log",
                tint = IntelliJColors.textSecondary,
                modifier =
                    Modifier
                        .size(Dimensions.iconMd.dp)
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .clickable(onClick = onToggleExpanded),
            )
        }
        if (expanded) {
            GitLogRows(commits = commits)
        }
    }
}

@Composable
private fun LogFilterChip() {
    Row(
        modifier =
            Modifier
                .height(LOG_FILTER_HEIGHT.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(IntelliJColors.inputBackground)
                .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            tint = IntelliJColors.textMuted,
            modifier = Modifier.size(Dimensions.iconSm.dp),
        )
        Text(text = "Branch · User · Date", color = IntelliJColors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun GitLogRows(commits: List<GitCommit>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LOG_COLUMN_HEADER_HEIGHT.dp)
                    .padding(start = Spacing.md.dp + Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Branch / Commit",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Author",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.width(AUTHOR_COLUMN_WIDTH.dp),
            )
            Text(
                text = "Date",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.width(DATE_COLUMN_WIDTH.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(commits, key = { it.hash }) { commit ->
                LogRow(commit = commit)
            }
        }
    }
}

@Composable
private fun LogTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
) {
    Box(modifier = Modifier.height(LOG_TAB_STRIP_HEIGHT.dp)) {
        Row(
            modifier = Modifier.height(LOG_TAB_STRIP_HEIGHT.dp).padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                color = if (selected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
                fontSize = 12.sp,
            )
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(IntelliJColors.accent),
            )
        }
    }
}

@Composable
private fun LogRow(commit: GitCommit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LOG_ROW_HEIGHT.dp)
                .padding(start = Spacing.md.dp + Spacing.xs.dp, end = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(gitColorFor(commit.author)))
        commit.refs.firstOrNull()?.let { ref ->
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .background(IntelliJColors.successMuted)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = IntelliJColors.success,
                    modifier = Modifier.size(12.dp),
                )
                Text(text = ref, color = IntelliJColors.success, fontSize = 11.sp, maxLines = 1)
            }
        }
        Text(
            text = commit.message,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.width(AUTHOR_COLUMN_WIDTH.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Avatar(author = commit.author)
            Text(
                text = commit.author,
                color = IntelliJColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = commit.relativeDate,
            color = IntelliJColors.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(DATE_COLUMN_WIDTH.dp),
        )
    }
}

/** Splits a unified diff into independent "old" and "new" line lists with line numbers. */
private fun parseUnifiedDiff(diff: String): Pair<List<DiffLine>, List<DiffLine>> {
    val old = mutableListOf<DiffLine>()
    val new = mutableListOf<DiffLine>()
    var oldNumber = 0
    var newNumber = 0
    var inHunk = false
    val hunkHeader = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

    for (line in diff.lineSequence()) {
        val match = hunkHeader.find(line)
        if (match != null) {
            oldNumber = match.groupValues[1].toInt()
            newNumber = match.groupValues[2].toInt()
            inHunk = true
            continue
        }
        if (!inHunk) continue
        when {
            line.startsWith("+") -> {
                new.add(DiffLine(newNumber, '+', line.drop(1), DiffKind.ADD))
                newNumber++
            }

            line.startsWith("-") -> {
                old.add(DiffLine(oldNumber, '-', line.drop(1), DiffKind.DELETE))
                oldNumber++
            }

            line.startsWith("\\") -> {
                // Ignore "\ No newline at end of file" markers.
            }

            else -> {
                val text = if (line.isEmpty()) "" else line.drop(1)
                old.add(DiffLine(oldNumber, ' ', text, DiffKind.CONTEXT))
                new.add(DiffLine(newNumber, ' ', text, DiffKind.CONTEXT))
                oldNumber++
                newNumber++
            }
        }
    }
    return old to new
}
