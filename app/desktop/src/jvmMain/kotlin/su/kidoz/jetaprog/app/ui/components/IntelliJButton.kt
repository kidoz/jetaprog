package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Button style variants.
 */
public enum class ButtonStyle {
    /** Primary action button with accent color. */
    PRIMARY,

    /** Secondary action button with subtle background. */
    SECONDARY,

    /** Danger action button for destructive actions. */
    DANGER,
}

/**
 * Modern flat-styled button with IntelliJ-inspired design.
 *
 * Features:
 * - 32dp minimum height for better click targets
 * - 6dp rounded corners for modern flat design
 * - No borders on secondary buttons (uses background color change)
 * - Smooth hover and press state transitions
 */
@Composable
public fun IntelliJButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.SECONDARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val (backgroundColor, textColor) = resolveButtonColors(style, enabled, isHovered, isPressed)
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)

    Box(
        modifier =
            modifier
                .heightIn(min = Dimensions.buttonHeight.dp)
                .clip(shape)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.lg.dp, vertical = Spacing.sm.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                color = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Resolves button colors based on style and interaction state.
 */
@Composable
private fun resolveButtonColors(
    style: ButtonStyle,
    enabled: Boolean,
    isHovered: Boolean,
    isPressed: Boolean,
): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (style) {
        ButtonStyle.PRIMARY -> {
            val bg =
                when {
                    !enabled -> IntelliJColors.buttonPrimaryBackground.copy(alpha = 0.5f)
                    isPressed -> IntelliJColors.accentPressed
                    isHovered -> IntelliJColors.buttonPrimaryBackgroundHover
                    else -> IntelliJColors.buttonPrimaryBackground
                }
            bg to IntelliJColors.buttonPrimaryForeground
        }

        ButtonStyle.SECONDARY -> {
            val bg =
                when {
                    !enabled -> IntelliJColors.buttonBackground.copy(alpha = 0.5f)
                    isPressed -> IntelliJColors.buttonBackgroundPressed
                    isHovered -> IntelliJColors.buttonBackgroundHover
                    else -> IntelliJColors.buttonBackground
                }
            bg to IntelliJColors.buttonForeground
        }

        ButtonStyle.DANGER -> {
            val bg =
                when {
                    !enabled -> IntelliJColors.buttonDangerBackground.copy(alpha = 0.5f)
                    isPressed -> IntelliJColors.buttonDangerBackground.copy(alpha = 0.8f)
                    isHovered -> IntelliJColors.buttonDangerBackgroundHover
                    else -> IntelliJColors.buttonDangerBackground
                }
            bg to IntelliJColors.buttonDangerForeground
        }
    }
