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
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.StructureItem
import su.kidoz.jetaprog.editor.navigation.SymbolVisibility

/**
 * File structure popup for quick navigation within a file (Ctrl+F12).
 */
@Composable
public fun FileStructurePopup(
    isVisible: Boolean,
    fileName: String,
    items: List<StructureItem>,
    onItemSelect: (StructureItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Flatten the tree for display
    val flattenedItems = remember(items) { flattenStructure(items) }

    // Filter items based on query
    val filteredItems =
        remember(flattenedItems, query) {
            if (query.isEmpty()) {
                flattenedItems
            } else {
                flattenedItems.filter { item ->
                    item.target.name.contains(query, ignoreCase = true)
                }
            }
        }

    // Reset selection when filtered items change
    LaunchedEffect(filteredItems) {
        selectedIndex = 0
        if (filteredItems.isNotEmpty()) {
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
                    .width(500.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.popupBackground),
        ) {
            // Header with file name
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(IntelliJColors.surfaceElevated)
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Structure of $fileName",
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Search input
            StructureSearchInput(
                query = query,
                focusRequester = focusRequester,
                onQueryChange = { query = it },
                onKeyEvent = { keyEvent ->
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (filteredItems.isNotEmpty()) {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(filteredItems.size - 1)
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }

                        Key.Enter -> {
                            if (filteredItems.isNotEmpty() && selectedIndex in filteredItems.indices) {
                                onItemSelect(filteredItems[selectedIndex])
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

            // Structure list
            if (filteredItems.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    itemsIndexed(filteredItems) { index, item ->
                        StructureItemRow(
                            item = item,
                            isSelected = index == selectedIndex,
                            onClick = { onItemSelect(item) },
                        )
                    }
                }

                // Scroll to selected item
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex in filteredItems.indices) {
                        listState.animateScrollToItem(selectedIndex)
                    }
                }
            } else {
                // No results message
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isEmpty()) "No structure available" else "No matches found",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            // Footer
            StructureFooter()
        }
    }
}

@Composable
private fun StructureSearchInput(
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
private fun StructureItemRow(
    item: StructureItem,
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
                .padding(start = (Spacing.md + item.depth * Spacing.lg).dp, end = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        // Visibility icon
        Icon(
            imageVector = item.visibility.toIcon(),
            contentDescription = null,
            tint = item.visibility.toColor(),
            modifier = Modifier.size(12.dp),
        )

        // Symbol icon
        Icon(
            imageVector = item.target.kind.toStructureIcon(),
            contentDescription = null,
            tint = item.target.kind.toStructureColor(),
            modifier = Modifier.size(16.dp),
        )

        // Name
        Text(
            text = item.target.name,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            fontStyle = if (item.isAbstract) FontStyle.Italic else FontStyle.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Detail (type, return type, etc.)
        item.target.detail?.let { detail ->
            Text(
                text = detail,
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Modifiers
        if (item.isStatic) {
            Text(
                text = "static",
                color = IntelliJColors.textSecondary,
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .background(
                            IntelliJColors.surfaceContainer,
                            RoundedCornerShape(2.dp),
                        ).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (item.isFinal) {
            Text(
                text = "final",
                color = IntelliJColors.textSecondary,
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .background(
                            IntelliJColors.surfaceContainer,
                            RoundedCornerShape(2.dp),
                        ).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun StructureFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        StructureFooterHint("↑↓", "Navigate")
        StructureFooterHint("Enter", "Go to")
        StructureFooterHint("Esc", "Close")
    }
}

@Composable
private fun StructureFooterHint(
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
 * Flatten the tree structure for display.
 */
private fun flattenStructure(items: List<StructureItem>): List<StructureItem> {
    val result = mutableListOf<StructureItem>()

    fun addItems(
        items: List<StructureItem>,
        depth: Int,
    ) {
        for (item in items) {
            result.add(item.copy(depth = depth))
            addItems(item.children, depth + 1)
        }
    }
    addItems(items, 0)
    return result
}

private fun SymbolVisibility.toIcon(): ImageVector =
    when (this) {
        SymbolVisibility.PUBLIC -> Icons.Default.LockOpen
        SymbolVisibility.PROTECTED -> Icons.Default.Lock
        SymbolVisibility.INTERNAL -> Icons.Default.Settings
        SymbolVisibility.PRIVATE -> Icons.Default.Lock
    }

private fun SymbolVisibility.toColor(): Color =
    when (this) {
        SymbolVisibility.PUBLIC -> IntelliJColors.success
        SymbolVisibility.PROTECTED -> IntelliJColors.warning
        SymbolVisibility.INTERNAL -> IntelliJColors.info
        SymbolVisibility.PRIVATE -> IntelliJColors.error
    }

private fun NavigationSymbolKind.toStructureIcon(): ImageVector =
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

        NavigationSymbolKind.PROPERTY,
        NavigationSymbolKind.FIELD,
        NavigationSymbolKind.VARIABLE,
        NavigationSymbolKind.CONSTANT,
        -> Icons.Default.Code

        else -> Icons.Default.Code
    }

private fun NavigationSymbolKind.toStructureColor(): Color =
    when (this) {
        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        -> IntelliJColors.iconKotlin

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        NavigationSymbolKind.CONSTRUCTOR,
        -> IntelliJColors.iconJava

        NavigationSymbolKind.PROPERTY,
        NavigationSymbolKind.FIELD,
        -> IntelliJColors.iconRust

        NavigationSymbolKind.ENUM -> IntelliJColors.iconPython

        else -> IntelliJColors.textSecondary
    }
