package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Modern flat-styled status bar.
 *
 * Features:
 * - 24dp height for better proportions
 * - More spacing between items
 * - Rounded segment backgrounds on hover
 * - Subtle dividers
 */
@Composable
public fun IntelliJStatusBar(
    modifier: Modifier = Modifier,
    gitBranch: String? = null,
    lineInfo: String? = null,
    encodingInfo: String = "UTF-8",
    languageInfo: String? = null,
    isBuilding: Boolean = false,
    buildStatus: BuildStatus? = null,
    onBranchClick: () -> Unit = {},
    onEncodingClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.statusBarHeight.dp)
                .background(IntelliJColors.statusBarBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left section
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Build status indicator
            if (isBuilding) {
                StatusBarItem(
                    icon = Icons.Default.Sync,
                    text = "Building...",
                    iconColor = IntelliJColors.info,
                )
            } else {
                buildStatus?.let { status ->
                    StatusBarItem(
                        icon = if (status.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        text = if (status.success) "Build successful" else "Build failed",
                        iconColor = if (status.success) IntelliJColors.success else IntelliJColors.error,
                    )
                }
            }

            // Git branch
            gitBranch?.let { branch ->
                StatusBarClickableItem(
                    text = "Git: $branch",
                    onClick = onBranchClick,
                )
            }
        }

        // Vertical divider
        StatusBarDivider()

        // Right section
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Line/Column info
            lineInfo?.let { info ->
                StatusBarItem(text = info)
            }

            StatusBarDivider()

            // Encoding
            StatusBarClickableItem(
                text = encodingInfo,
                onClick = onEncodingClick,
            )

            StatusBarDivider()

            // Line separator
            StatusBarItem(text = "LF")

            StatusBarDivider()

            // Language
            languageInfo?.let { lang ->
                StatusBarClickableItem(
                    text = lang,
                    onClick = onLanguageClick,
                )
            }

            StatusBarDivider()

            // Memory indicator
            StatusBarItem(
                icon = Icons.Default.Memory,
                text = "512M",
                iconColor = IntelliJColors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatusBarItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = IntelliJColors.textSecondary,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = IntelliJColors.statusBarForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBarClickableItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(if (isHovered) IntelliJColors.statusBarHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xxs.dp),
    ) {
        Text(
            text = text,
            color = if (isHovered) IntelliJColors.textPrimary else IntelliJColors.statusBarForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBarDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(12.dp)
                .background(IntelliJColors.statusBarDivider),
    )
}

/**
 * Build status information.
 */
public data class BuildStatus(
    val success: Boolean,
    val message: String = "",
)
