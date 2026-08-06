package su.kidoz.jetaprog.app.ui.command

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.command.CommandPaletteIntent
import su.kidoz.jetaprog.app.command.CommandPaletteState
import su.kidoz.jetaprog.app.command.PaletteCommand
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.popupChrome
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Command palette popup listing every command registered by the active plugins.
 *
 * Mirrors the navigation [su.kidoz.jetaprog.app.ui.navigation.SearchPopup] so the two
 * feel like one control: type to filter, arrows to move, Enter to run, Escape to close.
 */
@Composable
public fun CommandPalette(
    state: CommandPaletteState,
    onIntent: (CommandPaletteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.results) {
        selectedIndex = 0
        if (state.results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Popup(
        onDismissRequest = { onIntent(CommandPaletteIntent.Hide) },
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                modifier
                    .width(Dimensions.popupSearchWidth.dp)
                    .popupChrome(),
        ) {
            PaletteInput(
                query = state.query,
                focusRequester = focusRequester,
                onQueryChange = { onIntent(CommandPaletteIntent.QueryChanged(it)) },
                onKeyEvent = { event ->
                    handlePaletteKey(
                        event = event,
                        results = state.results,
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it },
                        onIntent = onIntent,
                    )
                },
            )

            if (state.results.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = Dimensions.popupListMaxHeight.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs.dp),
                    ) {
                        itemsIndexed(state.results) { index, command ->
                            CommandRow(
                                command = command,
                                isSelected = index == selectedIndex,
                                onClick = { onIntent(CommandPaletteIntent.Execute(command)) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }

                LaunchedEffect(selectedIndex) {
                    if (selectedIndex in state.results.indices) {
                        listState.animateScrollToItem(selectedIndex)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMessage(state.query),
                        color = IntelliJColors.textMuted,
                        fontSize = FONT_BODY.sp,
                    )
                }
            }

            PaletteFooter(matchCount = state.results.size)
        }
    }
}

/**
 * Keyboard handling for the palette input.
 *
 * Only key-down events act, otherwise the matching key-up would run the command a
 * second time.
 */
private fun handlePaletteKey(
    event: KeyEvent,
    results: List<PaletteCommand>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onIntent: (CommandPaletteIntent) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionDown -> {
            if (results.isNotEmpty()) {
                onSelectedIndexChange((selectedIndex + 1).coerceAtMost(results.size - 1))
            }
            true
        }

        Key.DirectionUp -> {
            onSelectedIndexChange((selectedIndex - 1).coerceAtLeast(0))
            true
        }

        Key.Enter -> {
            results.getOrNull(selectedIndex)?.let { onIntent(CommandPaletteIntent.Execute(it)) }
            true
        }

        Key.Escape -> {
            onIntent(CommandPaletteIntent.Hide)
            true
        }

        else -> {
            false
        }
    }
}

@Composable
private fun PaletteInput(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(Dimensions.iconLg.dp),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle =
                TextStyle(
                    color = IntelliJColors.textPrimary,
                    fontSize = FONT_INPUT.sp,
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
                            text = "Run a command...",
                            color = IntelliJColors.textMuted,
                            fontSize = FONT_INPUT.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun CommandRow(
    command: PaletteCommand,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    PopupListRow(
        selected = isSelected,
        onClick = onClick,
        horizontalPadding = Spacing.md.dp,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        command.category?.takeIf { it.isNotBlank() }?.let { category ->
            Text(
                text = category,
                color = IntelliJColors.textSecondary,
                fontSize = FONT_SMALL.sp,
                maxLines = 1,
            )
        }

        Text(
            text = command.title,
            color = IntelliJColors.textPrimary,
            fontSize = FONT_BODY.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = command.id,
            color = IntelliJColors.textMuted,
            fontSize = FONT_SMALL.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PaletteFooter(matchCount: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterHint("↑↓", "Navigate")
        FooterHint("Enter", "Run")
        FooterHint("Esc", "Close")

        Box(modifier = Modifier.weight(1f))

        Text(
            text = if (matchCount == 1) "1 command" else "$matchCount commands",
            color = IntelliJColors.textMuted,
            fontSize = FONT_SMALL.sp,
        )
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
            fontSize = FONT_SMALL.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .background(
                        IntelliJColors.surfaceContainer,
                        RoundedCornerShape(Spacing.xxs.dp),
                    ).padding(horizontal = Spacing.xs.dp, vertical = 1.dp),
        )
        Text(
            text = description,
            color = IntelliJColors.textMuted,
            fontSize = FONT_SMALL.sp,
        )
    }
}

private fun emptyMessage(query: String): String =
    if (query.isBlank()) {
        "No commands available. Open a project so its plugins can register commands."
    } else {
        "No commands match '$query'"
    }

private const val FONT_INPUT = 14
private const val FONT_BODY = 13
private const val FONT_SMALL = 11
