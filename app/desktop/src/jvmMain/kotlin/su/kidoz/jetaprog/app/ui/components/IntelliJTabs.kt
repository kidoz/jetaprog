package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.EditorTab

/**
 * Modern flat-styled editor tabs with IntelliJ-inspired design.
 *
 * Features:
 * - 32dp height for better click targets
 * - Active tab: rounded background with accent underline
 * - Better close button visibility on hover
 * - Smoother visual transitions
 */
@Composable
public fun IntelliJEditorTabs(
    tabs: List<EditorTab>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.tabHeight.dp)
                .background(IntelliJColors.surface)
                .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            IntelliJTab(
                tab = tab,
                isActive = index == activeTabIndex,
                onClick = { onTabClick(index) },
                onClose = { onTabClose(index) },
            )
        }
    }
}

@Composable
private fun IntelliJTab(
    tab: EditorTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor =
        when {
            isActive -> IntelliJColors.tabBackgroundSelected
            isHovered -> IntelliJColors.tabBackgroundHover
            else -> Color.Transparent
        }

    val textColor =
        when {
            isActive -> IntelliJColors.textPrimary
            isHovered -> IntelliJColors.textPrimary
            else -> IntelliJColors.textSecondary
        }

    Box(
        modifier =
            Modifier
                .height(Dimensions.tabHeight.dp)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xxs.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Tab content with rounded background
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(backgroundColor)
                    .padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            // File icon
            FileIcon(
                fileName = tab.name,
                modifier = Modifier.size(16.dp),
            )

            // File name
            Text(
                text = tab.name,
                color = textColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Dirty indicator or close button
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    tab.isDirty && !isHovered -> {
                        // Dirty indicator (dot)
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(IntelliJColors.accent),
                        )
                    }

                    isHovered || isActive -> {
                        // Close button
                        CloseButton(onClick = onClose)
                    }
                }
            }
        }

        // Active tab underline
        if (isActive) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.xs.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                        .background(IntelliJColors.tabUnderline),
            )
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (isHovered) IntelliJColors.error.copy(alpha = 0.15f) else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = if (isHovered) IntelliJColors.error else IntelliJColors.textSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * File icon based on file extension.
 */
@Composable
public fun FileIcon(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val color = getFileIconColor(extension)

    // Simple colored square as icon placeholder
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = extension.take(2).uppercase(),
            color = color,
            fontSize = 7.sp,
        )
    }
}

/**
 * Returns the icon color for a file extension.
 */
private fun getFileIconColor(extension: String): Color =
    when (extension) {
        "kt", "kts" -> IntelliJColors.iconKotlin
        "java" -> IntelliJColors.iconJava
        "rs" -> IntelliJColors.iconRust
        "cpp", "cc", "cxx", "c", "h", "hpp" -> IntelliJColors.iconCpp
        "vala", "vapi" -> IntelliJColors.iconVala
        "xml", "html", "htm" -> Color(0xFFCC7832)
        "json" -> Color(0xFF6A8759)
        "md", "markdown" -> Color(0xFF6897BB)
        "gradle", "gradle.kts" -> Color(0xFF499C54)
        "yaml", "yml" -> Color(0xFFCC7832)
        "toml" -> Color(0xFFE76D50)
        "py" -> Color(0xFF3776AB)
        "js", "jsx" -> Color(0xFFF7DF1E)
        "ts", "tsx" -> Color(0xFF3178C6)
        else -> IntelliJColors.iconFile
    }
