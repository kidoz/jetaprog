package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Modern flat-styled dropdown/combo box with IntelliJ-inspired design.
 *
 * Features:
 * - 36dp minimum height for comfortable interaction
 * - 8dp rounded corners on dropdown menu
 * - Subtle shadow on dropdown for depth
 * - Checkmark for selected item
 * - Better hover states
 */
@Composable
public fun <T> IntelliJDropdown(
    selectedItem: T,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    itemToString: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)

    val backgroundColor =
        when {
            !enabled -> IntelliJColors.backgroundLighter
            isHovered -> IntelliJColors.inputBackgroundHover
            else -> IntelliJColors.inputBackground
        }

    val borderColor =
        when {
            expanded -> IntelliJColors.inputBorderFocused
            isHovered -> IntelliJColors.inputBorderHover
            else -> IntelliJColors.inputBorder
        }

    Column(modifier = modifier) {
        // Label
        label?.let {
            Text(
                text = it,
                color = IntelliJColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = Spacing.xs.dp),
            )
        }

        // Dropdown button
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.inputHeight.dp)
                        .clip(shape)
                        .background(backgroundColor)
                        .border(1.dp, borderColor, shape)
                        .hoverable(interactionSource)
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = Spacing.md.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = itemToString(selectedItem),
                    color = if (enabled) IntelliJColors.textPrimary else IntelliJColors.textDisabled,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier =
                    Modifier
                        .shadow(4.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                        .background(
                            IntelliJColors.popupBackground,
                            RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
                        ).border(
                            1.dp,
                            IntelliJColors.popupBorder,
                            RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
                        ).widthIn(min = 200.dp),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = itemToString(item),
                        isSelected = item == selectedItem,
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.treeSelectionBackground
            isHovered -> IntelliJColors.menuItemHover
            else -> IntelliJColors.popupBackground
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.treeNodeHeight.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkmark for selected item
        Box(modifier = Modifier.size(18.dp)) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = IntelliJColors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = text,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = Spacing.sm.dp),
        )
    }
}
