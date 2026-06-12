package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Modern flat-styled checkbox with IntelliJ-inspired design.
 *
 * Features:
 * - 18dp checkbox size for better visibility
 * - 4dp rounded corners
 * - Hover state for better interactivity feedback
 * - Smooth checked state transitions
 */
@Composable
public fun IntelliJCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val shape = RoundedCornerShape(Dimensions.cornerRadiusSmall.dp)

    val backgroundColor =
        when {
            !enabled && checked -> IntelliJColors.accent.copy(alpha = 0.5f)
            checked -> IntelliJColors.accent
            isHovered -> IntelliJColors.inputBackgroundHover
            else -> IntelliJColors.inputBackground
        }

    val borderColor =
        when {
            !enabled -> IntelliJColors.inputBorder.copy(alpha = 0.5f)
            checked -> IntelliJColors.accent
            isHovered -> IntelliJColors.inputBorderHover
            else -> IntelliJColors.inputBorder
        }

    Row(
        modifier =
            modifier
                .hoverable(interactionSource)
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox box
        Box(
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(shape)
                    .background(backgroundColor)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = shape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Label
        label?.let {
            Text(
                text = it,
                color = if (enabled) IntelliJColors.textPrimary else IntelliJColors.textDisabled,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = Spacing.sm.dp),
            )
        }
    }
}
