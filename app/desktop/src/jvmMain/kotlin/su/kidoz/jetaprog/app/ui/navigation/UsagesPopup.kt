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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.UsageGroup
import su.kidoz.jetaprog.editor.navigation.UsageInfo
import su.kidoz.jetaprog.editor.navigation.UsageKind

/**
 * Usages popup for showing all usages of a symbol (Ctrl+Alt+F7).
 */
@Composable
public fun UsagesPopup(
    isVisible: Boolean,
    result: FindUsagesResult?,
    onUsageSelect: (UsageInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible || result == null) return

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize all groups as expanded
    LaunchedEffect(result) {
        result.groups.forEach { group ->
            if (!expandedGroups.containsKey(group.filePath)) {
                expandedGroups[group.filePath] = true
            }
        }
    }

    // Build flat list of visible items
    val visibleItems =
        remember(result, expandedGroups.toMap()) {
            buildVisibleItems(result.groups, expandedGroups)
        }

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
                    .width(700.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.popupBackground)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                if (visibleItems.isNotEmpty()) {
                                    selectedIndex = (selectedIndex + 1).coerceAtMost(visibleItems.size - 1)
                                }
                                true
                            }

                            Key.DirectionUp -> {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                true
                            }

                            Key.Enter -> {
                                if (selectedIndex in visibleItems.indices) {
                                    val item = visibleItems[selectedIndex]
                                    when (item) {
                                        is UsageListItem.GroupHeader -> {
                                            expandedGroups[item.group.filePath] =
                                                !(expandedGroups[item.group.filePath] ?: true)
                                        }

                                        is UsageListItem.Usage -> {
                                            onUsageSelect(item.usage)
                                        }
                                    }
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
        ) {
            // Header
            UsagesHeader(result = result)

            // Usages list
            if (visibleItems.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    itemsIndexed(visibleItems) { index, item ->
                        when (item) {
                            is UsageListItem.GroupHeader -> {
                                UsageGroupHeaderRow(
                                    group = item.group,
                                    isExpanded = expandedGroups[item.group.filePath] ?: true,
                                    isSelected = index == selectedIndex,
                                    onClick = {
                                        expandedGroups[item.group.filePath] =
                                            !(expandedGroups[item.group.filePath] ?: true)
                                    },
                                )
                            }

                            is UsageListItem.Usage -> {
                                UsageRow(
                                    usage = item.usage,
                                    isSelected = index == selectedIndex,
                                    onClick = { onUsageSelect(item.usage) },
                                )
                            }
                        }
                    }
                }

                // Scroll to selected item
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex in visibleItems.indices) {
                        listState.animateScrollToItem(selectedIndex)
                    }
                }
            } else {
                // No usages message
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No usages found",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            // Footer
            UsagesFooter()
        }
    }
}

@Composable
private fun UsagesHeader(result: FindUsagesResult) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = "Usages of",
            color = IntelliJColors.textSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = result.symbol.name,
            color = IntelliJColors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "(${result.totalCount} usages in ${result.groups.size} files)",
            color = IntelliJColors.textMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun UsageGroupHeaderRow(
    group: UsageGroup,
    isExpanded: Boolean,
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
                .height(28.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        // Expand/collapse icon
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )

        // File icon
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = IntelliJColors.iconFile,
            modifier = Modifier.size(16.dp),
        )

        // File name
        Text(
            text = group.fileName,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Usage count
        Text(
            text = "${group.usages.size}",
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
            modifier =
                Modifier
                    .background(
                        IntelliJColors.surfaceContainer,
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun UsageRow(
    usage: UsageInfo,
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
                .height(24.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(start = (Spacing.md + Spacing.lg).dp, end = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        // Line number
        Text(
            text = "${usage.lineNumber}:",
            color = IntelliJColors.lineNumberText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        // Usage kind badge
        UsageKindBadge(kind = usage.usageKind)

        // Context line with highlighted usage
        Text(
            text = highlightUsageInLine(usage.contextLine, usage.columnRange.toIntRange()),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UsageKindBadge(kind: UsageKind) {
    val (text, color) =
        when (kind) {
            UsageKind.DEFINITION -> "def" to IntelliJColors.success
            UsageKind.READ -> "read" to IntelliJColors.info
            UsageKind.WRITE -> "write" to IntelliJColors.warning
            UsageKind.CALL -> "call" to IntelliJColors.accent
            UsageKind.IMPORT -> "import" to IntelliJColors.textSecondary
            UsageKind.TYPE_REFERENCE -> "type" to IntelliJColors.iconKotlin
            UsageKind.OVERRIDE -> "override" to IntelliJColors.iconJava
            UsageKind.IMPLEMENTATION -> "impl" to IntelliJColors.iconRust
            UsageKind.INHERITANCE -> "extends" to IntelliJColors.iconPython
            UsageKind.UNKNOWN -> "use" to IntelliJColors.textMuted
        }

    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .background(
                    color.copy(alpha = 0.15f),
                    RoundedCornerShape(2.dp),
                ).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun UsagesFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        UsageFooterHint("↑↓", "Navigate")
        UsageFooterHint("Enter", "Go to / Expand")
        UsageFooterHint("Esc", "Close")
    }
}

@Composable
private fun UsageFooterHint(
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
 * Sealed class for items in the usage list.
 */
private sealed class UsageListItem {
    data class GroupHeader(
        val group: UsageGroup,
    ) : UsageListItem()

    data class Usage(
        val usage: UsageInfo,
    ) : UsageListItem()
}

/**
 * Build a flat list of visible items based on expanded state.
 */
private fun buildVisibleItems(
    groups: List<UsageGroup>,
    expandedGroups: Map<String, Boolean>,
): List<UsageListItem> {
    val result = mutableListOf<UsageListItem>()
    for (group in groups) {
        result.add(UsageListItem.GroupHeader(group))
        if (expandedGroups[group.filePath] != false) {
            group.usages.forEach { usage ->
                result.add(UsageListItem.Usage(usage))
            }
        }
    }
    return result
}

/**
 * Highlight the usage range in a context line.
 */
private fun highlightUsageInLine(
    line: String,
    range: IntRange,
): AnnotatedString =
    buildAnnotatedString {
        val start = range.first.coerceIn(0, line.length)
        val end = (range.last + 1).coerceIn(0, line.length)

        // Text before usage
        if (start > 0) {
            withStyle(SpanStyle(color = IntelliJColors.textSecondary)) {
                append(line.substring(0, start))
            }
        }

        // Highlighted usage
        if (start < end) {
            withStyle(
                SpanStyle(
                    color = IntelliJColors.accent,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(line.substring(start, end))
            }
        }

        // Text after usage
        if (end < line.length) {
            withStyle(SpanStyle(color = IntelliJColors.textSecondary)) {
                append(line.substring(end))
            }
        }
    }
