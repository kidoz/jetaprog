package su.kidoz.jetaprog.app.ui.panels

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.TerminalEffect
import su.kidoz.jetaprog.app.viewmodel.TerminalIntent
import su.kidoz.jetaprog.app.viewmodel.TerminalLine
import su.kidoz.jetaprog.app.viewmodel.TerminalState
import su.kidoz.jetaprog.app.viewmodel.TerminalTab

/**
 * Terminal panel with tabs and search.
 */
@Composable
public fun TerminalPanel(
    state: TerminalState,
    effects: StateFlow<TerminalEffect?>,
    onIntent: (TerminalIntent) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    if (!embedded && !state.isVisible) return

    val focusRequester = remember { FocusRequester() }

    // Handle effects (command history)
    val effect by effects.collectAsState()
    LaunchedEffect(effect) {
        when (val currentEffect = effect) {
            is TerminalEffect.HistoryCommand -> {
                onIntent(TerminalIntent.SendInput(currentEffect.command))
            }

            else -> {}
        }
    }

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
                filteredOutput = state.filteredOutput,
                onInput = { input -> onIntent(TerminalIntent.SendInput(input)) },
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
    filteredOutput: List<TerminalLine>,
    onInput: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(tab.id) {
        focusRequester.requestFocus()
    }

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(filteredOutput.size) {
        if (filteredOutput.isNotEmpty()) {
            listState.animateScrollToItem(filteredOutput.size - 1)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable { focusRequester.requestFocus() },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm.dp),
            state = listState,
        ) {
            items(filteredOutput) { line ->
                TerminalOutputLine(line = line)
            }
        }

        TerminalInputCapture(
            onInput = onInput,
            focusRequester = focusRequester,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun TerminalInputCapture(
    onInput: (String) -> Unit,
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
                    val input = keyEvent.toTerminalInput()
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

private fun KeyEvent.toTerminalInput(): String? {
    if (type != KeyEventType.KeyDown) return null

    if (isCtrlPressed && !isAltPressed && !isMetaPressed) {
        return when (key) {
            Key.A -> "\u0001"
            Key.B -> "\u0002"
            Key.C -> "\u0003"
            Key.D -> "\u0004"
            Key.E -> "\u0005"
            Key.F -> "\u0006"
            Key.K -> "\u000b"
            Key.L -> "\u000c"
            Key.R -> "\u0012"
            Key.U -> "\u0015"
            Key.W -> "\u0017"
            Key.Z -> "\u001a"
            else -> null
        }
    }

    return when (key) {
        Key.Enter -> {
            "\r"
        }

        Key.Backspace -> {
            "\u007f"
        }

        Key.Tab -> {
            "\t"
        }

        Key.Escape -> {
            "\u001b"
        }

        Key.DirectionUp -> {
            "\u001b[A"
        }

        Key.DirectionDown -> {
            "\u001b[B"
        }

        Key.DirectionRight -> {
            "\u001b[C"
        }

        Key.DirectionLeft -> {
            "\u001b[D"
        }

        Key.MoveHome -> {
            "\u001b[H"
        }

        Key.MoveEnd -> {
            "\u001b[F"
        }

        Key.Delete -> {
            "\u001b[3~"
        }

        Key.PageUp -> {
            "\u001b[5~"
        }

        Key.PageDown -> {
            "\u001b[6~"
        }

        else -> {
            val codePoint = utf16CodePoint
            when {
                isMetaPressed || isCtrlPressed -> null
                codePoint == 0 -> null
                else -> codePoint.toChar().toString()
            }
        }
    }
}

@Composable
private fun TerminalOutputLine(line: TerminalLine) {
    when {
        line.isCommand -> {
            // Shell command
            Text(
                text = line.text,
                color = IntelliJColors.terminalGreen,
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 1.25.em,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }

        line.isError -> {
            // Error output
            Text(
                text = line.text.ifEmpty { " " },
                color = IntelliJColors.terminalRed,
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 13.sp,
                lineHeight = 1.25.em,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }

        else -> {
            // Normal output
            Text(
                text = line.text.ifEmpty { " " },
                color = IntelliJColors.terminalForeground,
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 13.sp,
                lineHeight = 1.25.em,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}
