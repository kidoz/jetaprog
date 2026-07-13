package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.terminal.TerminalInputMode
import su.kidoz.jetaprog.app.terminal.toTerminalInput
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.TerminalIntent
import su.kidoz.jetaprog.app.viewmodel.TerminalState
import su.kidoz.jetaprog.app.viewmodel.TerminalTab

/**
 * Terminal panel with tabs and search.
 */
@Composable
public fun TerminalPanel(
    state: TerminalState,
    onIntent: (TerminalIntent) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    if (!embedded && !state.isVisible) return

    val focusRequester = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (embedded) Modifier.fillMaxSize() else Modifier.height(state.panelHeight.dp))
                .background(IntelliJColors.terminalBackground),
    ) {
        // Resize handle at top (the unified bottom panel supplies its own when embedded)
        if (!embedded) {
            ResizeHandle(
                onResize = { delta -> onIntent(TerminalIntent.ResizePanel(state.panelHeight - delta.toInt())) },
            )
        }

        // Terminal tabs bar with actions
        TerminalTabBar(
            tabs = state.tabs,
            activeTabIndex = state.activeTabIndex,
            onTabClick = { index -> onIntent(TerminalIntent.SwitchTerminal(index)) },
            onTabClose = { tabId -> onIntent(TerminalIntent.CloseTerminal(tabId)) },
            onNewTerminal = { onIntent(TerminalIntent.CreateTerminal()) },
            onClear = { onIntent(TerminalIntent.ClearOutput) },
            onKill = { onIntent(TerminalIntent.KillProcess) },
            onToggleSearch = { onIntent(TerminalIntent.ToggleSearch) },
            isRunning = state.activeTab?.isRunning == true,
            isSearchVisible = state.isSearchVisible,
        )

        // Search bar
        if (state.isSearchVisible) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { onIntent(TerminalIntent.SetSearchQuery(it)) },
                onClose = { onIntent(TerminalIntent.ToggleSearch) },
            )
        }

        // Terminal content
        val activeTab = state.activeTab
        if (activeTab != null) {
            TerminalContent(
                tab = activeTab,
                lines = state.filteredLines,
                showCursor = state.searchQuery.isEmpty() && activeTab.isCursorVisible,
                onInput = { input -> onIntent(TerminalIntent.SendInput(input)) },
                onViewportResize = { columns, rows -> onIntent(TerminalIntent.ResizeTerminal(columns, rows)) },
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Empty state
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = IntelliJColors.textMuted,
                        modifier = Modifier.size(48.dp).padding(bottom = Spacing.md.dp),
                    )
                    Text(
                        "No terminal open",
                        color = IntelliJColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Click + to create a new terminal",
                        color = IntelliJColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(onResize: (Float) -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(IntelliJColors.borderSubtle)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.y)
                    }
                },
    )
}

@Composable
private fun TerminalTabBar(
    tabs: List<TerminalTab>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewTerminal: () -> Unit,
    onClear: () -> Unit,
    onKill: () -> Unit,
    onToggleSearch: () -> Unit,
    isRunning: Boolean,
    isSearchVisible: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(IntelliJColors.terminalHeader),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tabs
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
        ) {
            tabs.forEachIndexed { index, tab ->
                TerminalTabItem(
                    tab = tab,
                    isActive = index == activeTabIndex,
                    onClick = { onTabClick(index) },
                    onClose = { onTabClose(tab.id) },
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
        ) {
            // New terminal button
            IconButton(
                onClick = onNewTerminal,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New terminal",
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Search toggle
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchVisible) IntelliJColors.accent else IntelliJColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Clear
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Kill process (only shown when running)
            if (isRunning) {
                IconButton(
                    onClick = onKill,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Kill process",
                        tint = IntelliJColors.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalTabItem(
    tab: TerminalTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isActive -> IntelliJColors.terminalBackground
            isHovered -> IntelliJColors.surfaceHover
            else -> Color.Transparent
        }
    val textColor = if (isActive) IntelliJColors.textPrimary else IntelliJColors.textSecondary

    Row(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Terminal icon
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = IntelliJColors.terminalGreen,
            modifier = Modifier.size(14.dp),
        )

        Box(modifier = Modifier.width(6.dp))

        // Running indicator
        if (tab.isRunning) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .background(IntelliJColors.terminalGreen, RoundedCornerShape(3.dp)),
            )
            Box(modifier = Modifier.width(4.dp))
        }

        Text(
            text = tab.name,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
        )

        // Exit code badge
        tab.exitCode?.let { exitCode ->
            val badgeColor = if (exitCode == 0) IntelliJColors.terminalGreen else IntelliJColors.terminalRed
            Text(
                text = "[$exitCode]",
                color = badgeColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Spacing.xs.dp),
            )
        }

        // Close button (visible on hover or when active)
        if (isActive || isHovered) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = textColor.copy(alpha = 0.6f),
                modifier =
                    Modifier
                        .padding(start = Spacing.sm.dp)
                        .size(12.dp)
                        .clickable(onClick = onClose),
            )
        } else {
            Box(modifier = Modifier.width(Spacing.sm.dp + 12.dp))
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle =
                TextStyle(
                    fontFamily = JetaProgFonts.codeFont,
                    fontSize = 13.sp,
                    color = IntelliJColors.textPrimary,
                ),
            cursorBrush = SolidColor(IntelliJColors.accent),
            singleLine = true,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Escape) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    },
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search terminal output...",
                            color = IntelliJColors.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search",
                tint = IntelliJColors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TerminalContent(
    tab: TerminalTab,
    lines: List<String>,
    showCursor: Boolean,
    onInput: (String) -> Unit,
    onViewportResize: (Int, Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val terminalTextStyle =
        remember {
            TextStyle(
                fontFamily = JetaProgFonts.codeFont,
                fontSize = TERMINAL_FONT_SIZE.sp,
                lineHeight = TERMINAL_LINE_HEIGHT.em,
            )
        }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellSize =
        remember(terminalTextStyle, textMeasurer) {
            val measurement = textMeasurer.measure(AnnotatedString("M"), style = terminalTextStyle)
            TerminalCellSize(
                width = measurement.size.width.coerceAtLeast(1),
                height = measurement.size.height.coerceAtLeast(1),
            )
        }

    LaunchedEffect(tab.id) {
        focusRequester.requestFocus()
    }

    // Auto-scroll to bottom when content changes. Keyed on the cheap [revision] counter so it
    // also fires when the last line updates in place (progress bars, spinners), and uses a
    // non-animated jump to keep up with fast output.
    val totalItems = lines.size + tab.errorMessages.size
    LaunchedEffect(tab.revision) {
        if (totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    val horizontalPadding = with(density) { (Spacing.sm * 2).dp.roundToPx() }
                    val verticalPadding = with(density) { (Spacing.sm * 2).dp.roundToPx() }
                    val columns = ((size.width - horizontalPadding) / cellSize.width).coerceAtLeast(1)
                    val rows = ((size.height - verticalPadding) / cellSize.height).coerceAtLeast(1)
                    if (columns != tab.columns || rows != tab.rows) {
                        onViewportResize(columns, rows)
                    }
                }.clickable { focusRequester.requestFocus() },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm.dp),
            state = listState,
        ) {
            itemsIndexed(lines, key = { index, _ -> "line$index" }) { index, text ->
                TerminalOutputLine(
                    text = text,
                    cursorColumn = if (showCursor && index == tab.cursorLineIndex) tab.cursorColumn else -1,
                    horizontalScrollState = horizontalScrollState,
                )
            }
            itemsIndexed(tab.errorMessages, key = { index, _ -> "error$index" }) { _, message ->
                TerminalErrorLine(text = message, horizontalScrollState = horizontalScrollState)
            }
        }

        TerminalInputCapture(
            onInput = onInput,
            inputMode = TerminalInputMode(applicationCursorKeys = tab.applicationCursorKeys),
            focusRequester = focusRequester,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun TerminalInputCapture(
    onInput: (String) -> Unit,
    inputMode: TerminalInputMode,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = "",
        onValueChange = {},
        textStyle =
            TextStyle(
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 1.sp,
                color = Color.Transparent,
            ),
        cursorBrush = SolidColor(Color.Transparent),
        singleLine = true,
        modifier =
            modifier
                .size(1.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    val input = keyEvent.toTerminalInput(inputMode)
                    if (input != null) {
                        onInput(input)
                        true
                    } else {
                        false
                    }
                },
        decorationBox = { innerTextField ->
            Box {
                innerTextField()
            }
        },
    )
}

/**
 * A single grid row. When [cursorColumn] is non-negative the cell at that column is drawn in
 * reverse video so the character beneath the cursor stays visible.
 */
@Composable
private fun TerminalOutputLine(
    text: String,
    cursorColumn: Int,
    horizontalScrollState: ScrollState,
) {
    val lineModifier = Modifier.horizontalScroll(horizontalScrollState).padding(vertical = 1.dp)
    if (cursorColumn >= 0) {
        Text(
            text = text.withCursor(cursorColumn),
            fontFamily = JetaProgFonts.codeFont,
            fontSize = TERMINAL_FONT_SIZE.sp,
            lineHeight = TERMINAL_LINE_HEIGHT.em,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = lineModifier,
        )
    } else {
        Text(
            text = text.ifEmpty { " " },
            color = IntelliJColors.terminalForeground,
            fontFamily = JetaProgFonts.codeFont,
            fontSize = TERMINAL_FONT_SIZE.sp,
            lineHeight = TERMINAL_LINE_HEIGHT.em,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = lineModifier,
        )
    }
}

@Composable
private fun TerminalErrorLine(
    text: String,
    horizontalScrollState: ScrollState,
) {
    Text(
        text = text.ifEmpty { " " },
        color = IntelliJColors.terminalRed,
        fontFamily = JetaProgFonts.codeFont,
        fontSize = TERMINAL_FONT_SIZE.sp,
        lineHeight = TERMINAL_LINE_HEIGHT.em,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        modifier = Modifier.horizontalScroll(horizontalScrollState).padding(vertical = 1.dp),
    )
}

private fun String.withCursor(column: Int): AnnotatedString =
    buildAnnotatedString {
        val safeColumn = column.coerceAtLeast(0)
        val padded =
            if (length <= safeColumn) {
                this@withCursor + " ".repeat(safeColumn - length + 1)
            } else {
                this@withCursor
            }
        append(padded)
        addStyle(SpanStyle(color = IntelliJColors.terminalForeground), 0, padded.length)
        addStyle(
            SpanStyle(color = IntelliJColors.terminalBackground, background = IntelliJColors.terminalForeground),
            safeColumn,
            safeColumn + 1,
        )
    }

private data class TerminalCellSize(
    val width: Int,
    val height: Int,
)

private const val TERMINAL_FONT_SIZE = 13
private const val TERMINAL_LINE_HEIGHT = 1.25f
