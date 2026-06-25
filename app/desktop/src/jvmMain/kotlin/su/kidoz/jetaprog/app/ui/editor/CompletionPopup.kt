package su.kidoz.jetaprog.app.ui.editor

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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.Elevation
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionItemKind
import su.kidoz.jetaprog.editor.state.CompletionState

/**
 * Code completion popup showing completion items.
 *
 * The popup does not handle keyboard events directly - they are handled by the parent
 * CodeEditor component to keep focus on the text field.
 */
@Composable
public fun CompletionPopup(
    state: CompletionState,
    onItemSelect: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val listState = rememberLazyListState()

    // Scroll to selected item when selection changes
    LaunchedEffect(state.selectedIndex) {
        if (state.selectedIndex in state.items.indices) {
            listState.animateScrollToItem(state.selectedIndex)
        }
    }

    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier =
                modifier
                    .width(Dimensions.popupCompletionWidth.dp)
                    .shadow(Elevation.popup.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.popupBackground),
        ) {
            // Loading indicator
            if (state.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = IntelliJColors.accent,
                        )
                        Text(
                            text = "Loading...",
                            color = IntelliJColors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Completion items list
            if (state.items.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = Dimensions.popupCompletionMaxHeight.dp),
                ) {
                    itemsIndexed(state.items) { index, item ->
                        CompletionItemRow(
                            item = item,
                            isSelected = index == state.selectedIndex,
                            onClick = { onItemSelect(item) },
                        )
                    }
                }

                // Footer with keyboard hints
                CompletionFooter()
            } else if (!state.isLoading) {
                // No results
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No completions",
                        color = IntelliJColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * A single completion item row.
 */
@Composable
private fun CompletionItemRow(
    item: CompletionItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.menuItemHover
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
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        // Kind icon
        Icon(
            imageVector = item.kind.toIcon(),
            contentDescription = null,
            tint = item.kind.toColor(),
            modifier = Modifier.size(16.dp),
        )

        // Label
        Text(
            text = item.label,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Detail (type info)
        item.detail?.let { detail ->
            Text(
                text = detail,
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Snippet indicator
        if (item.insertTextIsSnippet) {
            Text(
                text = "snippet",
                color = IntelliJColors.textSecondary,
                fontSize = 9.sp,
                modifier =
                    Modifier
                        .background(
                            IntelliJColors.surfaceContainer,
                            RoundedCornerShape(2.dp),
                        ).padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Footer with keyboard hints.
 */
@Composable
private fun CompletionFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
    ) {
        FooterHint("Enter", "Insert")
        FooterHint("Tab", "Insert")
        FooterHint("Esc", "Cancel")
    }
}

/**
 * A keyboard hint in the footer.
 */
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .background(
                        IntelliJColors.surfaceContainer,
                        RoundedCornerShape(2.dp),
                    ).padding(horizontal = 3.dp, vertical = 1.dp),
        )
        Text(
            text = description,
            color = IntelliJColors.textMuted,
            fontSize = 10.sp,
        )
    }
}

/**
 * Convert CompletionItemKind to an icon.
 */
private fun CompletionItemKind.toIcon(): ImageVector =
    when (this) {
        CompletionItemKind.Method,
        CompletionItemKind.Function,
        -> Icons.Default.Functions

        CompletionItemKind.Constructor -> Icons.Default.Build

        CompletionItemKind.Class,
        CompletionItemKind.Interface,
        CompletionItemKind.Struct,
        -> Icons.Default.Class

        CompletionItemKind.Variable,
        CompletionItemKind.Field,
        -> Icons.Default.Code

        CompletionItemKind.Property -> Icons.Default.Settings

        CompletionItemKind.Module -> Icons.Default.Folder

        CompletionItemKind.Keyword -> Icons.Default.Key

        CompletionItemKind.Snippet -> Icons.Default.ContentPaste

        CompletionItemKind.Enum,
        CompletionItemKind.EnumMember,
        -> Icons.AutoMirrored.Filled.List

        CompletionItemKind.Constant -> Icons.Default.Lock

        else -> Icons.Default.Code
    }

/**
 * Convert CompletionItemKind to a color.
 */
private fun CompletionItemKind.toColor(): Color =
    when (this) {
        CompletionItemKind.Method,
        CompletionItemKind.Function,
        CompletionItemKind.Constructor,
        -> IntelliJColors.iconJava

        CompletionItemKind.Class,
        CompletionItemKind.Interface,
        CompletionItemKind.Struct,
        -> IntelliJColors.iconKotlin

        CompletionItemKind.Variable,
        CompletionItemKind.Field,
        -> IntelliJColors.iconRust

        CompletionItemKind.Property -> IntelliJColors.iconPython

        CompletionItemKind.Keyword -> IntelliJColors.accent

        CompletionItemKind.Snippet -> IntelliJColors.warning

        CompletionItemKind.Enum,
        CompletionItemKind.EnumMember,
        -> IntelliJColors.info

        CompletionItemKind.Constant -> IntelliJColors.success

        else -> IntelliJColors.textSecondary
    }
