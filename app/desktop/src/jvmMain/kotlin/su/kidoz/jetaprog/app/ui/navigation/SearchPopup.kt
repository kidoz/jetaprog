package su.kidoz.jetaprog.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.navigation.MatchRange
import su.kidoz.jetaprog.editor.navigation.NavigationSearchResult
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind

/**
 * Search mode for the popup.
 */
public enum class SearchMode {
    ALL,
    CLASSES,
    FILES,
    SYMBOLS,
}

/**
 * Search popup for navigation (Go to Class, File, Symbol, Search Everywhere).
 */
@Composable
public fun SearchPopup(
    isVisible: Boolean,
    mode: SearchMode,
    results: List<NavigationSearchResult>,
    onQueryChange: (String) -> Unit,
    onResultSelect: (NavigationSearchResult) -> Unit,
    onDismiss: () -> Unit,
    onModeChange: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Reset selection when results change
    LaunchedEffect(results) {
        selectedIndex = 0
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Focus the text field when popup opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                modifier
                    .width(600.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.popupBackground),
        ) {
            // Mode tabs (only for Search Everywhere)
            if (mode == SearchMode.ALL) {
                SearchModeTabs(
                    currentMode = mode,
                    onModeChange = onModeChange,
                )
            }

            // Search input
            SearchInput(
                query = query,
                placeholder = getPlaceholder(mode),
                focusRequester = focusRequester,
                onQueryChange = { newQuery ->
                    query = newQuery
                    onQueryChange(newQuery)
                },
                onKeyEvent = { keyEvent ->
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (results.isNotEmpty()) {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(results.size - 1)
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }

                        Key.Enter -> {
                            if (results.isNotEmpty() && selectedIndex in results.indices) {
                                onResultSelect(results[selectedIndex])
                            }
                            true
                        }

                        Key.Escape -> {
                            onDismiss()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
            )

            // Results list
            if (results.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    itemsIndexed(results) { index, result ->
                        SearchResultItem(
                            result = result,
                            isSelected = index == selectedIndex,
                            onClick = { onResultSelect(result) },
                        )
                    }
                }

                // Scroll to selected item
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex in results.indices) {
                        listState.animateScrollToItem(selectedIndex)
                    }
                }
            } else if (query.isNotEmpty()) {
                // No results message
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matches found",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            // Footer with shortcut hints
            SearchFooter(mode = mode)
        }
    }
}

@Composable
private fun SearchModeTabs(
    currentMode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        SearchMode.entries.forEach { mode ->
            TabButton(
                text = mode.displayName,
                isSelected = mode == currentMode,
                onClick = { onModeChange(mode) },
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) IntelliJColors.accent else Color.Transparent
    val textColor = if (isSelected) Color.White else IntelliJColors.textSecondary

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun SearchInput(
    query: String,
    placeholder: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(20.dp),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle =
                TextStyle(
                    color = IntelliJColors.textPrimary,
                    fontSize = 14.sp,
                ),
            cursorBrush = SolidColor(IntelliJColors.accent),
            singleLine = true,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent(onKeyEvent),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = IntelliJColors.textMuted,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SearchResultItem(
    result: NavigationSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.selectionBackground
            isHovered -> IntelliJColors.surfaceHover
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        // Icon
        Icon(
            imageVector = result.target.kind.toIcon(),
            contentDescription = null,
            tint = result.target.kind.toColor(),
            modifier = Modifier.size(16.dp),
        )

        // Name with highlighted matches
        Text(
            text = highlightMatches(result.target.name, result.matchRanges),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Container name (e.g., class name, package)
        result.target.containerName?.let { container ->
            Text(
                text = container,
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // File path
        Text(
            text = result.target.filePath.substringAfterLast('/'),
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchFooter(mode: SearchMode) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        FooterHint("↑↓", "Navigate")
        FooterHint("Enter", "Open")
        FooterHint("Esc", "Close")
        if (mode == SearchMode.ALL) {
            FooterHint("Tab", "Switch tab")
        }
    }
}

@Composable
private fun FooterHint(
    shortcut: String,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortcut,
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .background(
                        IntelliJColors.surfaceContainer,
                        RoundedCornerShape(2.dp),
                    ).padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = description,
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

/**
 * Highlight matching characters in the text.
 */
private fun highlightMatches(
    text: String,
    matchRanges: List<MatchRange>,
): AnnotatedString =
    buildAnnotatedString {
        var lastEnd = 0
        val sortedRanges = matchRanges.sortedBy { it.start }

        for (range in sortedRanges) {
            val start = range.start.coerceIn(0, text.length)
            val end = (range.endInclusive + 1).coerceIn(0, text.length)

            if (start > lastEnd) {
                withStyle(SpanStyle(color = IntelliJColors.textPrimary)) {
                    append(text.substring(lastEnd, start))
                }
            }
            if (start < end) {
                withStyle(
                    SpanStyle(
                        color = IntelliJColors.accent,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(text.substring(start, end))
                }
            }
            lastEnd = end
        }

        if (lastEnd < text.length) {
            withStyle(SpanStyle(color = IntelliJColors.textPrimary)) {
                append(text.substring(lastEnd))
            }
        }
    }

private val SearchMode.displayName: String
    get() =
        when (this) {
            SearchMode.ALL -> "All"
            SearchMode.CLASSES -> "Classes"
            SearchMode.FILES -> "Files"
            SearchMode.SYMBOLS -> "Symbols"
        }

private fun getPlaceholder(mode: SearchMode): String =
    when (mode) {
        SearchMode.ALL -> "Search everywhere..."
        SearchMode.CLASSES -> "Enter class name..."
        SearchMode.FILES -> "Enter file name..."
        SearchMode.SYMBOLS -> "Enter symbol name..."
    }

private fun NavigationSymbolKind.toIcon(): ImageVector =
    when (this) {
        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        NavigationSymbolKind.ENUM,
        NavigationSymbolKind.STRUCT,
        NavigationSymbolKind.OBJECT,
        -> Icons.Default.Class

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        NavigationSymbolKind.CONSTRUCTOR,
        -> Icons.Default.Functions

        NavigationSymbolKind.FILE -> Icons.Default.Description

        NavigationSymbolKind.NAMESPACE,
        NavigationSymbolKind.PACKAGE,
        NavigationSymbolKind.MODULE,
        -> Icons.Default.Folder

        else -> Icons.Default.Code
    }

private fun NavigationSymbolKind.toColor(): Color =
    when (this) {
        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        -> IntelliJColors.iconKotlin

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        -> IntelliJColors.iconJava

        NavigationSymbolKind.ENUM -> IntelliJColors.iconRust

        NavigationSymbolKind.FILE -> IntelliJColors.iconFile

        NavigationSymbolKind.NAMESPACE,
        NavigationSymbolKind.PACKAGE,
        -> IntelliJColors.iconFolder

        else -> IntelliJColors.textSecondary
    }
