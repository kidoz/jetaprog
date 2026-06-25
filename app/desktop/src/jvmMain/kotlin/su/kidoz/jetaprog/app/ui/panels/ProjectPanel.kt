package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ToolWindowButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import java.io.File

/**
 * Modern flat-styled Project panel (file tree).
 *
 * Features:
 * - Uses background difference instead of border
 * - More tree node spacing (28dp)
 * - Better icon alignment
 * - Smooth hover states
 */
@Composable
public fun ProjectPanel(
    projectPath: String,
    onFileOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val projectName = remember(projectPath) { File(projectPath).name }
    var rootFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val expandedDirs = remember { mutableStateMapOf<String, Boolean>() }
    val childrenCache = remember { mutableStateMapOf<String, List<File>>() }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    // Mark the clicked file as selected, then open it.
    val handleFileClick: (String) -> Unit = { path ->
        selectedPath = path
        onFileOpen(path)
    }

    LaunchedEffect(projectPath) {
        rootFiles = File(projectPath)
            .listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(IntelliJColors.treeBackground),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.panelHeaderHeight.dp)
                    .background(IntelliJColors.toolWindowHeader)
                    .padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Project",
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(modifier = Modifier.weight(1f))
            ToolWindowButton(
                icon = Icons.Default.Settings,
                onClick = { },
                contentDescription = "Settings",
            )
        }

        // Project tree
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = Spacing.xs.dp),
        ) {
            // Project root
            item {
                ProjectTreeNode(
                    file = File(projectPath),
                    displayName = projectName,
                    isRoot = true,
                    isExpanded = true,
                    indent = 0,
                    isSelected = false,
                    onFileClick = { }, // Root is always a directory, so no file click action
                    onToggleExpand = { },
                )
            }

            // Root files
            items(rootFiles) { file ->
                FileTreeNode(
                    file = file,
                    indent = 1,
                    expandedDirs = expandedDirs,
                    childrenCache = childrenCache,
                    selectedPath = selectedPath,
                    onFileClick = handleFileClick,
                )
            }
        }
    }
}

@Composable
private fun FileTreeNode(
    file: File,
    indent: Int,
    expandedDirs: MutableMap<String, Boolean>,
    childrenCache: MutableMap<String, List<File>>,
    selectedPath: String?,
    onFileClick: (String) -> Unit,
) {
    val isDirectory = file.isDirectory
    val isExpanded = expandedDirs[file.absolutePath] == true

    // Load children when expanded
    LaunchedEffect(isExpanded) {
        if (isDirectory && isExpanded && !childrenCache.containsKey(file.absolutePath)) {
            childrenCache[file.absolutePath] = file
                .listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        }
    }

    Column {
        ProjectTreeNode(
            file = file,
            displayName = file.name,
            isRoot = false,
            isExpanded = isExpanded,
            indent = indent,
            isSelected = !isDirectory && file.absolutePath == selectedPath,
            onFileClick = { onFileClick(file.absolutePath) },
            onToggleExpand = {
                if (isDirectory) {
                    expandedDirs[file.absolutePath] = !isExpanded
                }
            },
        )

        // Children
        if (isExpanded) {
            childrenCache[file.absolutePath]?.forEach { child ->
                FileTreeNode(
                    file = child,
                    indent = indent + 1,
                    expandedDirs = expandedDirs,
                    childrenCache = childrenCache,
                    selectedPath = selectedPath,
                    onFileClick = onFileClick,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ProjectTreeNode(
    file: File,
    displayName: String,
    isRoot: Boolean,
    isExpanded: Boolean,
    indent: Int,
    isSelected: Boolean,
    onFileClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDirectory = file.isDirectory

    val backgroundColor =
        when {
            isSelected -> IntelliJColors.treeSelectionBackground
            isHovered -> IntelliJColors.treeHoverBackground
            else -> Color.Transparent
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.treeNodeHeight.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                    .background(backgroundColor)
                    .hoverable(interactionSource)
                    .clickable { if (isDirectory) onToggleExpand() else onFileClick() }
                    .padding(start = Spacing.xs.dp, end = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Indent guides — one vertical line per depth level.
            repeat(indent) {
                IndentGuide()
            }
            // Expand/Collapse icon
            if (isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = IntelliJColors.textSecondary,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clickable(onClick = onToggleExpand),
                )
            } else {
                Box(modifier = Modifier.width(18.dp))
            }

            // File/Folder icon
            if (isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = IntelliJColors.iconFolder,
                    modifier = Modifier.size(18.dp).padding(end = Spacing.xs.dp),
                )
            } else {
                FileTypeIcon(
                    fileName = file.name,
                    modifier = Modifier.size(18.dp).padding(end = Spacing.xs.dp),
                )
            }

            // Name
            Text(
                text = displayName,
                color =
                    when {
                        isSelected -> Color.White
                        isRoot -> IntelliJColors.textPrimary
                        else -> IntelliJColors.treeForeground
                    },
                fontSize = 13.sp,
                fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Left accent bar on the selected row.
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .height(Dimensions.treeNodeHeight.dp)
                        .background(IntelliJColors.treeSelectionAccent),
            )
        }
    }
}

/** A single tree indent guide: a 16dp-wide cell with a 1px vertical line on its left. */
@Composable
private fun IndentGuide() {
    Box(
        modifier =
            Modifier
                .width(Spacing.lg.dp)
                .height(Dimensions.treeNodeHeight.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(IntelliJColors.treeIndentGuide),
        )
    }
}

@Composable
private fun FileTypeIcon(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    val (color, label) =
        when (extension) {
            "kt" -> IntelliJColors.iconKotlin to "K"
            "kts" -> IntelliJColors.iconKotlin to "K"
            "java" -> IntelliJColors.iconJava to "J"
            "rs" -> IntelliJColors.iconRust to "R"
            "cpp", "cc", "cxx", "c", "h", "hpp" -> IntelliJColors.iconCpp to "C"
            "vala", "vapi" -> IntelliJColors.iconVala to "V"
            "xml" -> Color(0xFFCC7832) to "X"
            "json" -> Color(0xFF6A8759) to "{"
            "md" -> Color(0xFF6897BB) to "M"
            "gradle" -> Color(0xFF499C54) to "G"
            "yaml", "yml" -> Color(0xFFCC7832) to "Y"
            "toml" -> Color(0xFFE76D50) to "T"
            "properties" -> Color(0xFF6897BB) to "P"
            "txt" -> IntelliJColors.textSecondary to "T"
            "py" -> Color(0xFF3776AB) to "P"
            "js", "jsx" -> Color(0xFFF7DF1E) to "J"
            "ts", "tsx" -> Color(0xFF3178C6) to "T"
            else -> IntelliJColors.iconFile to ""
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
