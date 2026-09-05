package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.ConfirmationDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.ReplaceInFilesSummary
import su.kidoz.jetaprog.app.viewmodel.TextSearchState
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel
import su.kidoz.jetaprog.editor.search.FileTextMatches
import su.kidoz.jetaprog.editor.search.TextSearchMatch

/**
 * Project-wide full-text search and replacement ("Find in Files").
 *
 * @param viewModel the search view model.
 * @param onOpenMatch invoked with (filePath, line, column) when a match is clicked.
 * @param modifier the layout modifier.
 * @param dirtyOpenPaths open files with unsaved changes; they are excluded
 *   from Replace in Files so their buffers cannot clobber the replacements.
 * @param onFilesReplaced invoked with the rewritten paths once a replace run
 *   finishes, so open editors can reload their buffers from disk.
 */
@Composable
public fun FindInFilesPanel(
    viewModel: TextSearchViewModel,
    onOpenMatch: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    dirtyOpenPaths: Set<String> = emptySet(),
    onFilesReplaced: (List<String>) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var replaceMode by remember { mutableStateOf(false) }

    state.replaceSummary?.let { summary ->
        if (summary.replacedPaths.isNotEmpty()) {
            LaunchedEffect(summary) { onFilesReplaced(summary.replacedPaths) }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
        FindInFilesHeader()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(LocalIntelliJColors.current.divider),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            IntelliJTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = "Search files",
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs.dp)) {
                        SearchFieldAction(
                            icon = Icons.Default.FindReplace,
                            description = "Toggle replace in files",
                            enabled = !state.isSearching && !state.isReplacing,
                            onClick = { replaceMode = !replaceMode },
                        )
                        SearchFieldAction(
                            icon = Icons.Default.Search,
                            description = "Search files",
                            enabled = state.query.isNotEmpty() && !state.isSearching,
                            onClick = viewModel::search,
                        )
                    }
                },
                modifier =
                    Modifier.fillMaxWidth().onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            viewModel.search()
                            true
                        } else {
                            false
                        }
                    },
            )

            if (replaceMode) {
                IntelliJTextField(
                    value = state.replacement,
                    onValueChange = viewModel::setReplacement,
                    singleLine = true,
                    placeholder = "Replace with",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                SearchOptionToggle(
                    label = "Aa",
                    description = "Match case",
                    selected = state.caseSensitive,
                    onToggle = viewModel::toggleCaseSensitive,
                )
                SearchOptionToggle(
                    label = "W",
                    description = "Whole words",
                    selected = state.wholeWord,
                    onToggle = viewModel::toggleWholeWord,
                )
                SearchOptionToggle(
                    label = ".*",
                    description = "Regular expression",
                    selected = state.regex,
                    onToggle = viewModel::toggleRegex,
                )
            }

            val statusText = replaceStatusText(state)
            statusText?.let {
                Text(
                    text = it,
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 11.sp,
                    fontFamily = JetaProgFonts.codeFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (replaceMode && state.results.isNotEmpty()) {
                IntelliJButton(
                    text = "Replace All…",
                    onClick = viewModel::requestReplaceAll,
                    enabled = !state.isSearching && !state.isReplacing,
                    style = ButtonStyle.PRIMARY,
                )
            }

            SearchPanelContent(
                state = state,
                onOpenMatch = onOpenMatch,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    if (state.awaitingReplaceConfirmation) {
        ReplaceConfirmationDialog(
            state = state,
            onConfirm = { viewModel.confirmReplaceAll(dirtyOpenPaths) },
            onDismiss = viewModel::cancelReplaceAll,
        )
    }
}

/** One-line status for the search header, or null when there is nothing to report. */
@Composable
private fun replaceStatusText(state: TextSearchState): String? {
    val summary = state.replaceSummary
    return when {
        state.isReplacing -> "Replacing…"
        summary != null -> replaceSummaryText(summary)
        state.isSearching -> "Searching files…"
        state.totalMatches > 0 -> "${state.totalMatches} matches in ${state.results.size} files"
        else -> null
    }
}

private fun replaceSummaryText(summary: ReplaceInFilesSummary): String =
    buildString {
        append("Replaced ${summary.occurrences} occurrence")
        if (summary.occurrences != 1) append("s")
        append(" in ${summary.replacedPaths.size} file")
        if (summary.replacedPaths.size != 1) append("s")
        if (summary.skippedPaths.isNotEmpty()) {
            append(", ${summary.skippedPaths.size} skipped (unsaved changes)")
        }
        if (summary.failures.isNotEmpty()) {
            append(", ${summary.failures.size} failed")
        }
    }

@Composable
private fun ReplaceConfirmationDialog(
    state: TextSearchState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fileCount = state.results.size
    val message =
        buildString {
            append("Replace ${state.totalMatches} occurrence")
            if (state.totalMatches != 1) append("s")
            append(" of \"${state.query}\" with \"${state.replacement}\"")
            append(" in $fileCount file")
            if (fileCount != 1) append("s")
            append("?")
        }
    ConfirmationDialog(
        title = "Replace in Files",
        message = message,
        confirmLabel = "Replace All",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun FindInFilesHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.toolWindowHeaderHeight.dp)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Find in Files",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JetaProgFonts.codeFont,
        )
    }
}

@Composable
private fun SearchFieldAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(Dimensions.toolbarIcon.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) LocalIntelliJColors.current.textSecondary else LocalIntelliJColors.current.textDisabled,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
    }
}

@Composable
private fun SearchOptionToggle(
    label: String,
    description: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
    val backgroundColor =
        when {
            selected -> LocalIntelliJColors.current.accentSubtle
            isHovered -> LocalIntelliJColors.current.surfaceHover
            else -> LocalIntelliJColors.current.inputBackground
        }
    val borderColor =
        if (selected || isFocused) {
            LocalIntelliJColors.current.accent
        } else {
            LocalIntelliJColors.current.inputBorder
        }

    Box(
        modifier =
            Modifier
                .height(Dimensions.chipHeight.dp)
                .clip(shape)
                .background(backgroundColor)
                .border(Dimensions.splitterThickness.dp, borderColor, shape)
                .hoverable(interactionSource)
                .onFocusChanged { isFocused = it.isFocused }
                .toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).semantics { contentDescription = description }
                .padding(horizontal = Spacing.sm.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (selected) {
                    LocalIntelliJColors.current.textPrimary
                } else {
                    LocalIntelliJColors.current.textSecondary
                },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = JetaProgFonts.codeFont,
        )
    }
}

@Composable
private fun SearchPanelContent(
    state: TextSearchState,
    onOpenMatch: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            state.results.isNotEmpty() -> SearchResults(state.results, onOpenMatch)
            state.isSearching -> SearchPanelMessage("Searching files…")
            state.searched -> SearchPanelMessage("No matches found")
            else -> SearchPanelMessage("Enter text to search in files")
        }
    }
}

@Composable
private fun SearchPanelMessage(text: String) {
    Text(
        text = text,
        color = LocalIntelliJColors.current.textMuted,
        fontSize = 11.sp,
        fontFamily = JetaProgFonts.codeFont,
    )
}

@Composable
private fun SearchResults(
    results: List<FileTextMatches>,
    onOpenMatch: (String, Int, Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        results.forEach { file ->
            item(key = file.filePath) {
                Text(
                    text = file.filePath.substringAfterLast('/'),
                    color = LocalIntelliJColors.current.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetaProgFonts.codeFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm.dp, bottom = Spacing.xs.dp),
                )
            }
            items(file.matches) { match ->
                SearchMatchRow(
                    match = match,
                    onClick = { onOpenMatch(file.filePath, match.line, match.startColumn) },
                )
            }
        }
    }
}

@Composable
private fun SearchMatchRow(
    match: TextSearchMatch,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Text(
        text = "${match.line + 1}: ${match.lineText.trim()}",
        color = LocalIntelliJColors.current.textSecondary,
        fontSize = 12.sp,
        fontFamily = JetaProgFonts.codeFont,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isHovered) LocalIntelliJColors.current.surfaceHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.xxs.dp, horizontal = Spacing.xs.dp),
    )
}
