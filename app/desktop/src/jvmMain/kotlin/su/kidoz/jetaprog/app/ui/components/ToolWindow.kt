package su.kidoz.jetaprog.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors

/**
 * IntelliJ-style tool window container.
 */
@Composable
public fun ToolWindow(
    title: String,
    isVisible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (!isVisible) return

    Column(
        modifier = modifier.background(IntelliJColors.toolWindowBackground),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(IntelliJColors.toolWindowHeader)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Box(modifier = Modifier.width(6.dp))
            }

            // Title
            Text(
                text = title,
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            Box(modifier = Modifier.weight(1f))

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()

                // Minimize button
                ToolWindowButton(
                    icon = Icons.Default.Remove,
                    onClick = onClose,
                    contentDescription = "Hide",
                )
            }
        }

        // Border
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(IntelliJColors.toolWindowBorder),
        )

        // Content
        Box(modifier = Modifier.background(IntelliJColors.toolWindowBackground)) {
            content()
        }
    }
}

/**
 * Small icon button for tool window headers.
 */
@Composable
public fun ToolWindowButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            modifier
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isHovered) IntelliJColors.buttonBackgroundHover else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Tree node for file explorer.
 */
@Composable
public fun TreeNode(
    text: String,
    isExpanded: Boolean = false,
    isSelected: Boolean = false,
    hasChildren: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    indent: Int = 0,
    onClick: () -> Unit = {},
    onExpand: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.treeSelectionBackground
            isHovered -> IntelliJColors.treeSelectionBackground.copy(alpha = 0.5f)
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(start = (16 + indent * 16).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Expand/Collapse icon
        if (hasChildren) {
            Icon(
                imageVector =
                    if (isExpanded) {
                        Icons.Default.ExpandMore
                    } else {
                        Icons.Default.ChevronRight
                    },
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = IntelliJColors.textSecondary,
                modifier =
                    Modifier
                        .size(16.dp)
                        .clickable(onClick = onExpand),
            )
        } else {
            Box(modifier = Modifier.size(16.dp))
        }

        // Icon
        icon?.let {
            Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                it()
            }
        }

        // Text
        Text(
            text = text,
            color = IntelliJColors.treeForeground,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * Horizontal splitter/divider.
 */
@Composable
public fun HorizontalSplitter(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(thickness)
                .background(IntelliJColors.divider),
    )
}

/**
 * Vertical splitter/divider.
 */
@Composable
public fun VerticalSplitter(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
) {
    Box(
        modifier =
            modifier
                .width(thickness)
                .background(IntelliJColors.divider),
    )
}
