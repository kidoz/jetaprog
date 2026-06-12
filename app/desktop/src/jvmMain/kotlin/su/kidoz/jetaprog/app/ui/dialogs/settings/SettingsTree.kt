package su.kidoz.jetaprog.app.ui.dialogs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.SettingsCategory

/**
 * Navigation tree for settings categories.
 */
@Composable
public fun SettingsTree(
    selectedCategory: SettingsCategory,
    selectedSubItem: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSelectSubItem: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(Spacing.sm.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        SettingsCategory.entries.forEach { category ->
            CategoryTreeItem(
                category = category,
                isSelected = selectedCategory == category,
                selectedSubItem = if (selectedCategory == category) selectedSubItem else null,
                onSelect = { onSelectCategory(category) },
                onSelectSubItem = onSelectSubItem,
            )
        }
    }
}

@Composable
private fun CategoryTreeItem(
    category: SettingsCategory,
    isSelected: Boolean,
    selectedSubItem: String?,
    onSelect: () -> Unit,
    onSelectSubItem: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(isSelected) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val subItems = getSubItems(category)
    val hasSubItems = subItems.isNotEmpty()

    Column(modifier = modifier) {
        // Main category item
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isSelected && selectedSubItem == null -> IntelliJColors.treeSelectionBackground
                            isHovered -> IntelliJColors.surfaceHover
                            else -> IntelliJColors.backgroundDarker
                        },
                    ).hoverable(interactionSource)
                    .clickable {
                        onSelect()
                        if (hasSubItems) isExpanded = !isExpanded
                    }.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse icon
            if (hasSubItems) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Box(modifier = Modifier.size(16.dp))
            }

            // Category icon
            Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint = if (isSelected) IntelliJColors.accent else IntelliJColors.textSecondary,
                modifier =
                    Modifier
                        .padding(start = Spacing.xs.dp)
                        .size(16.dp),
            )

            // Category name
            Text(
                text = category.displayName,
                color = if (isSelected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = Spacing.sm.dp),
            )
        }

        // Sub-items
        if (isExpanded && hasSubItems) {
            subItems.forEach { subItem ->
                SubItemTreeItem(
                    name = subItem,
                    isSelected = selectedSubItem == subItem,
                    onSelect = { onSelectSubItem(subItem) },
                )
            }
        }
    }
}

@Composable
private fun SubItemTreeItem(
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isSelected -> IntelliJColors.treeSelectionBackground
                        isHovered -> IntelliJColors.surfaceHover
                        else -> IntelliJColors.backgroundDarker
                    },
                ).hoverable(interactionSource)
                .clickable(onClick = onSelect)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = if (isSelected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

private fun getCategoryIcon(category: SettingsCategory): ImageVector =
    when (category) {
        SettingsCategory.APPEARANCE -> Icons.Default.Palette
        SettingsCategory.EDITOR -> Icons.Default.TextFields
        SettingsCategory.LANGUAGES -> Icons.Default.Code
        SettingsCategory.TOOLS -> Icons.Default.Build
        SettingsCategory.PLUGINS -> Icons.Default.Extension
    }

private fun getSubItems(category: SettingsCategory): List<String> =
    when (category) {
        SettingsCategory.APPEARANCE -> listOf("Theme", "Font", "UI Scale")
        SettingsCategory.EDITOR -> listOf("General", "Code Style", "Save Actions")
        SettingsCategory.LANGUAGES -> listOf("Kotlin", "Vala", "LSP Servers")
        SettingsCategory.TOOLS -> listOf("Build Systems", "MCP Servers", "External Tools")
        SettingsCategory.PLUGINS -> emptyList()
    }
