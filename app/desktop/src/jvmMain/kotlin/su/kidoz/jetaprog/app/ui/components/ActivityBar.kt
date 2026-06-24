package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Activity bar item representing a tool window.
 */
public enum class ActivityBarItem(
    public val icon: ImageVector,
    public val label: String,
    public val shortcut: String,
) {
    PROJECT(Icons.Default.Folder, "Project", "Alt+1"),
    SEARCH(Icons.Default.Search, "Search", "Alt+2"),
    BUILD(Icons.Default.Build, "Build", "Alt+3"),
    DEBUG(Icons.Default.BugReport, "Debug", "Alt+4"),
    TERMINAL(Icons.Default.Terminal, "Terminal", "Alt+5"),
    VCS(Icons.Default.AccountTree, "Git", "Alt+6"),
    AGENT(Icons.Default.SmartToy, "AI Agent", "Alt+7"),
    SETTINGS(Icons.Default.Settings, "Settings", ""),
}

/**
 * Modern flat-styled activity bar (left icon sidebar).
 *
 * Features:
 * - 48dp width for more breathing room
 * - Larger icons (24dp) for better visibility
 * - Rounded pill indicator for selected item
 * - More vertical padding between icons
 */
@Composable
public fun ActivityBar(
    selectedItem: ActivityBarItem?,
    onItemClick: (ActivityBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(Dimensions.activityBarWidth.dp)
                .fillMaxHeight()
                .background(IntelliJColors.activityBarBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Spacing.sm.dp))

        // Top items
        ActivityBarItem.entries.filter { it != ActivityBarItem.SETTINGS }.forEach { item ->
            ActivityBarButton(
                item = item,
                isSelected = selectedItem == item,
                onClick = { onItemClick(item) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom items (Settings)
        ActivityBarButton(
            item = ActivityBarItem.SETTINGS,
            isSelected = selectedItem == ActivityBarItem.SETTINGS,
            onClick = { onItemClick(ActivityBarItem.SETTINGS) },
        )

        Spacer(modifier = Modifier.height(Spacing.md.dp))
    }
}

@Composable
private fun ActivityBarButton(
    item: ActivityBarItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.accentSubtle
            isHovered -> IntelliJColors.activityBarHover
            else -> Color.Transparent
        }

    val iconColor =
        when {
            isSelected -> IntelliJColors.activityBarForegroundActive
            isHovered -> IntelliJColors.activityBarForegroundActive
            else -> IntelliJColors.activityBarForeground
        }

    Box(
        modifier =
            Modifier
                .padding(vertical = Spacing.xs.dp, horizontal = Spacing.xs.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Selection indicator (rounded pill on the left)
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(IntelliJColors.activityBarIndicator),
            )
        }

        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Tool window header (title bar for panels).
 */
@Composable
public fun ToolWindowHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .height(Dimensions.panelHeaderHeight.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = Spacing.md.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            actions()
        }
    }
}
