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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.TextSearchState
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel
import su.kidoz.jetaprog.editor.search.FileTextMatches
import su.kidoz.jetaprog.editor.search.TextSearchMatch

/**
 * Project-wide full-text search ("Find in Files").
 *
 * @param viewModel the search view model.
 * @param onOpenMatch invoked with (filePath, line, column) when a match is clicked.
 * @param modifier the layout modifier.
 */
@Composable
public fun FindInFilesPanel(
    viewModel: TextSearchViewModel,
    onOpenMatch: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(IntelliJColors.background)) {
        FindInFilesHeader()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider),
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
                    SearchFieldAction(
                        enabled = state.query.isNotEmpty() && !state.isSearching,
                        onClick = viewModel::search,
                    )
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

            val statusText =
                when {
                    state.isSearching -> "Searching files…"
                    state.totalMatches > 0 -> "${state.totalMatches} matches in ${state.results.size} files"
                    else -> null
                }
            statusText?.let {
                Text(
                    text = it,
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                    fontFamily = JetaProgFonts.codeFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SearchPanelContent(
                state = state,
                onOpenMatch = onOpenMatch,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
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
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JetaProgFonts.codeFont,
        )
    }
}

@Composable
private fun SearchFieldAction(
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
                    contentDescription = "Search files"
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (enabled) IntelliJColors.textSecondary else IntelliJColors.textDisabled,
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
            selected -> IntelliJColors.accentSubtle
            isHovered -> IntelliJColors.surfaceHover
            else -> IntelliJColors.inputBackground
        }
    val borderColor =
        if (selected || isFocused) {
            IntelliJColors.accent
        } else {
            IntelliJColors.inputBorder
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
            color = if (selected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
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
        color = IntelliJColors.textMuted,
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
                    color = IntelliJColors.accent,
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
        color = IntelliJColors.textSecondary,
        fontSize = 12.sp,
        fontFamily = JetaProgFonts.codeFont,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isHovered) IntelliJColors.surfaceHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.xxs.dp, horizontal = Spacing.xs.dp),
    )
}
