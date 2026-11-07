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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.editor.syntax.highlighting.DarkSyntaxTheme
import su.kidoz.jetaprog.editor.syntax.highlighting.LightSyntaxTheme
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitCommit

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
    val extension = selected?.path?.substringAfterLast('.', "") ?: ""

    Column(modifier = modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
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
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                if (oldLines.isEmpty() && newLines.isEmpty()) {
                    Text(
                        text =
                            if (state.diff.contains(
                                    "Binary files",
                                )
                            ) {
                                "Binary files differ; text preview is unavailable."
                            } else {
                                "No text changes to display."
                            },
                        color = LocalIntelliJColors.current.textMuted,
                        modifier = Modifier.padding(Spacing.lg.dp),
                    )
                } else {
                    DiffColumns(oldLines, newLines, extension, selected.staged)
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
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        if (change == null) {
            Text(text = "Version Control", color = LocalIntelliJColors.current.textSecondary, fontSize = 13.sp)
            return@Row
        }
        FileBadge(fileName = change.fileName())
        Text(text = change.fileName(), color = LocalIntelliJColors.current.textPrimary, fontSize = 13.sp)
        Text(
            text = change.parentPath(),
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DiffStat(symbol = "+", count = additions, color = LocalIntelliJColors.current.success)
        DiffStat(symbol = "−", count = deletions, color = LocalIntelliJColors.current.diffRemovedText)
    }
}

@Composable
private fun DiffStat(
    symbol: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
        Box(
            modifier =
                Modifier
                    .size(Spacing.sm.dp)
                    .clip(RoundedCornerShape(Dimensions.gitDiffStatCornerRadius.dp))
                    .background(color),
        )
        Text(text = "$symbol$count", color = LocalIntelliJColors.current.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun DiffColumns(
    oldLines: List<DiffLine>,
    newLines: List<DiffLine>,
    extension: String,
    staged: Boolean,
) {
    val oldScroll = rememberScrollState()
    val newScroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                if (staged) "Before — HEAD" else "Before — Index",
                if (staged) "After — Index" else "After — Working tree",
            ).forEach { title ->
                Text(
                    text = title,
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(oldLines.size) { index ->
                val old = oldLines[index]
                val new = newLines[index]
                Row(modifier = Modifier.fillMaxWidth()) {
                    DiffRow(
                        old,
                        old.counterpart?.let { newLines[it].text },
                        extension,
                        oldScroll,
                        Modifier.weight(1f).padding(end = Dimensions.splitterThickness.dp),
                    )
                    DiffRow(new, new.counterpart?.let { oldLines[it].text }, extension, newScroll, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiffRow(
    line: DiffLine,
    counterpartText: String?,
    extension: String,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalIntelliJColors.current
    val isDark = palette.isDark
    val theme = if (isDark) DarkSyntaxTheme else LightSyntaxTheme
    val background =
        when (line.kind) {
            DiffKind.ADD -> palette.diffAddedBackground
            DiffKind.DELETE -> palette.diffRemovedBackground
            DiffKind.CONTEXT -> Color.Transparent
        }
    val annotated =
        remember(line.text, extension, isDark) { highlightDiffLine(line.text, extension, theme) }
    // Word-level highlight inside changed lines (IntelliJ's intraline diff).
    val intraline =
        remember(line.text, counterpartText) {
            counterpartText?.let { counterpart ->
                when (line.kind) {
                    DiffKind.ADD -> intralineChangeRanges(counterpart, line.text)?.changedRanges
                    DiffKind.DELETE -> intralineChangeRanges(line.text, counterpart)?.baseRanges
                    DiffKind.CONTEXT -> null
                }
            }
        }
    val intralineBackground =
        when (line.kind) {
            DiffKind.ADD -> palette.diffAddedGutter.copy(alpha = INTRALINE_ALPHA)
            DiffKind.DELETE -> palette.diffRemovedText.copy(alpha = INTRALINE_ALPHA)
            DiffKind.CONTEXT -> Color.Transparent
        }
    val styled =
        remember(annotated, intraline, intralineBackground) {
            if (intraline.isNullOrEmpty()) {
                annotated
            } else {
                buildAnnotatedString {
                    append(annotated)
                    for (range in intraline) {
                        addStyle(SpanStyle(background = intralineBackground), range.first, range.last + 1)
                    }
                }
            }
        }
    val signColor =
        when (line.kind) {
            DiffKind.ADD -> palette.diffAddedGutter
            DiffKind.DELETE -> palette.diffRemovedText
            DiffKind.CONTEXT -> palette.textMuted
        }
    val textColor =
        when (line.kind) {
            DiffKind.ADD -> palette.diffAddedText
            DiffKind.DELETE -> palette.diffRemovedText
            DiffKind.CONTEXT -> palette.textPrimary
        }
    Row(modifier = modifier.fillMaxWidth().height(Dimensions.lineHeightCode.dp).background(background)) {
        Text(
            text = line.number?.toString().orEmpty(),
            color = LocalIntelliJColors.current.lineNumberForeground,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(Dimensions.gitDiffLineNumberWidth.dp).padding(end = Spacing.md.dp),
            maxLines = 1,
        )
        Text(
            text = line.sign.toString().trim(),
            color = signColor,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(Dimensions.gitDiffSignWidth.dp),
            maxLines = 1,
        )
        Text(
            text = styled.ifEmpty { AnnotatedString(" ") },
            color = textColor,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(horizontalScroll),
        )
    }
}

/** Word-highlight opacity over the line background for intraline changes. */
private const val INTRALINE_ALPHA = 0.35f

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
                .height(
                    if (expanded) {
                        Dimensions.gitLogExpandedHeight.dp
                    } else {
                        Dimensions.gitLogCollapsedHeight.dp
                    },
                ).background(LocalIntelliJColors.current.background),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(LocalIntelliJColors.current.divider),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.gitLogTabStripHeight.dp)
                    .background(LocalIntelliJColors.current.surface)
                    .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
        ) {
            LogTab(icon = Icons.Filled.AccountTree, label = "Log", selected = true)
            Text(
                text = "${commits.size} commits",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (expanded) "Collapse log" else "Expand log",
                tint = LocalIntelliJColors.current.textSecondary,
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
private fun GitLogRows(commits: List<GitCommit>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.gitLogColumnHeaderHeight.dp)
                    .padding(start = Spacing.md.dp + Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Branch / Commit",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Author",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.width(Dimensions.gitLogAuthorColumnWidth.dp),
            )
            Text(
                text = "Date",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.width(Dimensions.gitLogDateColumnWidth.dp),
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
    Box(modifier = Modifier.height(Dimensions.gitLogTabStripHeight.dp)) {
        Row(
            modifier = Modifier.height(Dimensions.gitLogTabStripHeight.dp).padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.gitLogRefHorizontalPadding.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (selected) {
                        LocalIntelliJColors.current.textPrimary
                    } else {
                        LocalIntelliJColors.current.textSecondary
                    },
                modifier = Modifier.size(Dimensions.gitLogIconSize.dp),
            )
            Text(
                text = label,
                color =
                    if (selected) {
                        LocalIntelliJColors.current.textPrimary
                    } else {
                        LocalIntelliJColors.current.textSecondary
                    },
                fontSize = 12.sp,
            )
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Dimensions.selectionAccentWidth.dp)
                        .background(LocalIntelliJColors.current.accent),
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
                .height(Dimensions.gitLogRowHeight.dp)
                .padding(start = Spacing.md.dp + Spacing.xs.dp, end = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(Dimensions.gitLogCommitDotSize.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(gitColorFor(commit.author, gitAvatarPalette())),
        )
        commit.refs.firstOrNull()?.let { ref ->
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .background(LocalIntelliJColors.current.successMuted)
                        .padding(
                            horizontal = Dimensions.gitLogRefHorizontalPadding.dp,
                            vertical = Dimensions.gitLogRefVerticalPadding.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = LocalIntelliJColors.current.success,
                    modifier = Modifier.size(Dimensions.iconXs.dp),
                )
                Text(text = ref, color = LocalIntelliJColors.current.success, fontSize = 11.sp, maxLines = 1)
            }
        }
        Text(
            text = commit.message,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.width(Dimensions.gitLogAuthorColumnWidth.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.gitLogAvatarSpacing.dp),
        ) {
            Avatar(author = commit.author)
            Text(
                text = commit.author,
                color = LocalIntelliJColors.current.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = commit.relativeDate,
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(Dimensions.gitLogDateColumnWidth.dp),
        )
    }
}
