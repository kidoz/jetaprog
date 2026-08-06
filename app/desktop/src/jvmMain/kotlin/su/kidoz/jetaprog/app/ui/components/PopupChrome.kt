package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.Elevation
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Shared chrome for floating layers: popup shadow, clip, fill and a 1dp
 * [IntelliJColors.popupBorder] border, all on one shape.
 *
 * Radius convention: caret-anchored popups (completion, hover, signature help,
 * quick fixes) pass [Dimensions.cornerRadius] (6); free-standing list panels
 * (search, usages, structure, command palette) use the default
 * [Dimensions.cornerRadiusLarge] (8).
 */
public fun Modifier.popupChrome(cornerRadius: Dp = Dimensions.cornerRadiusLarge.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(Elevation.popup.dp, shape)
        .clip(shape)
        .background(IntelliJColors.popupBackground)
        .border(Dimensions.splitterThickness.dp, IntelliJColors.popupBorder, shape)
}

/**
 * A selectable row inside a popup list, styled like a project-tree row: hover
 * fills [IntelliJColors.treeHoverBackground], the selected row fills
 * [IntelliJColors.treeSelectionBackground] and carries the 2dp accent bar on
 * its leading edge.
 *
 * @param selected Whether the row is the current (keyboard) selection.
 * @param onClick Invoked when the row is clicked.
 * @param height Row height; lists default to [Dimensions.popupRowHeight],
 *   dense lists (completion) pass [Dimensions.popupRowHeightCompact].
 * @param horizontalPadding Start/end padding applied to [content].
 * @param horizontalArrangement Arrangement of the [content] children.
 * @param content Row content, laid out center-aligned vertically.
 */
@Composable
public fun PopupListRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimensions.popupRowHeight.dp,
    horizontalPadding: Dp = Spacing.sm.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val background =
        when {
            selected -> IntelliJColors.treeSelectionBackground
            isHovered -> IntelliJColors.treeHoverBackground
            else -> Color.Transparent
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(background)
                .hoverable(interactionSource)
                .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(Dimensions.selectionAccentWidth.dp)
                        .fillMaxHeight()
                        .background(IntelliJColors.treeSelectionAccent),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
