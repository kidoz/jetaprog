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
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.input.key.Key
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
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.navigation.NavigationHistoryEntry

/**
 * Recent files popup for quick switching between visited files (Ctrl+E).
 */
@Composable
public fun RecentFilesPopup(
    isVisible: Boolean,
    entries: List<NavigationHistoryEntry>,
    onEntrySelect: (NavigationHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
    projectPath: String = "",
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val filteredEntries =
        remember(entries, query) {
            if (query.isEmpty()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.filePath.substringAfterLast('/').contains(query, ignoreCase = true)
                }
            }
        }

    // Reset selection when filtered entries change
    LaunchedEffect(filteredEntries) {
        selectedIndex = 0
        if (filteredEntries.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Focus the filter field when popup opens
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
                    .width(500.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.popupBackground),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(IntelliJColors.surfaceElevated)
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Files",
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Filter input
            RecentFilesFilterInput(
                query = query,
                focusRequester = focusRequester,
                onQueryChange = { query = it },
                onKeyEvent = { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                if (filteredEntries.isNotEmpty()) {
                                    selectedIndex = (selectedIndex + 1).coerceAtMost(filteredEntries.size - 1)
                                }
                                true
                            }

                            Key.DirectionUp -> {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                true
                            }

                            Key.Enter -> {
                                if (filteredEntries.isNotEmpty() && selectedIndex in filteredEntries.indices) {
                                    onEntrySelect(filteredEntries[selectedIndex])
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
                    }
                },
            )

            // File list
            if (filteredEntries.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    itemsIndexed(filteredEntries) { index, entry ->
                        RecentFileRow(
                            entry = entry,
                            projectPath = projectPath,
                            isSelected = index == selectedIndex,
                            onClick = { onEntrySelect(entry) },
                        )
                    }
                }

                // Keep the selected entry visible while navigating with the keyboard
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex in filteredEntries.indices) {
                        listState.animateScrollToItem(selectedIndex)
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isEmpty()) "No recent files" else "No matches found",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            // Footer
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(IntelliJColors.surfaceElevated)
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
            ) {
                RecentFilesFooterHint("↑↓", "Navigate")
                RecentFilesFooterHint("Enter", "Open")
                RecentFilesFooterHint("Esc", "Close")
            }
        }
    }
}

@Composable
private fun RecentFilesFilterInput(
    query: String,
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
            modifier = Modifier.size(18.dp),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle =
                TextStyle(
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
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
                            text = "Filter by name...",
                            color = IntelliJColors.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun RecentFileRow(
    entry: NavigationHistoryEntry,
    projectPath: String,
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

    val fileName = entry.filePath.substringAfterLast('/')
    val directory =
        entry.filePath
            .substringBeforeLast('/', missingDelimiterValue = "")
            .removePrefix(projectPath)
            .trimStart('/')

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = IntelliJColors.iconDefault,
            modifier = Modifier.size(16.dp),
        )

        Text(
            text = fileName,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (directory.isNotEmpty()) {
            Text(
                text = directory,
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun RecentFilesFooterHint(
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
