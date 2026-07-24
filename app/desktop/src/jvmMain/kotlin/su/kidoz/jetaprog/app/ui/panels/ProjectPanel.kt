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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.project.FileActionResult
import su.kidoz.jetaprog.app.project.ProjectFileActions
import su.kidoz.jetaprog.app.ui.components.ToolWindowButton
import su.kidoz.jetaprog.app.ui.dialogs.projectfile.ProjectFileDeleteDialog
import su.kidoz.jetaprog.app.ui.dialogs.projectfile.ProjectFileNameDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.platform.filesystem.FileSystem
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
@Suppress("LongParameterList")
public fun ProjectPanel(
    projectPath: String,
    onFileOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    fileSystem: FileSystem? = null,
    fileActions: ProjectFileActions? = null,
    onMessage: (String) -> Unit = {},
    onPathRemoved: (String) -> Unit = {},
) {
    val projectName = remember(projectPath) { File(projectPath).name }
    var rootFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val expandedDirs = remember { mutableStateMapOf<String, Boolean>() }
    val childrenCache = remember { mutableStateMapOf<String, List<File>>() }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<ProjectFileAction?>(null) }
    val scope = rememberCoroutineScope()

    // Mark the clicked file as selected, then open it.
    val handleFileClick: (String) -> Unit = { path ->
        selectedPath = path
        onFileOpen(path)
    }

    // Reloads a directory listing after the tree or disk changes.
    val reloadDirectory: (String) -> Unit = { path ->
        val children = listDirectory(path)
        if (path == projectPath) rootFiles = children else childrenCache[path] = children
    }

    LaunchedEffect(projectPath) {
        rootFiles = listDirectory(projectPath)
    }

    // Keep the root in sync with disk; expanded directories watch themselves.
    WatchDirectory(fileSystem, projectPath) { reloadDirectory(projectPath) }

    val runAction: (suspend () -> FileActionResult) -> Unit = { operation ->
        scope.launch {
            when (val result = operation()) {
                is FileActionResult.Success -> {
                    // The watcher refreshes too, but this keeps the tree instant.
                    result.path.substringBeforeLast('/').let(reloadDirectory)
                }

                is FileActionResult.Failure -> {
                    onMessage(result.reason)
                }
            }
        }
    }

    val menuItemsFor: (File) -> List<ProjectTreeMenuItem> = { file ->
        buildProjectTreeMenu(
            file = file,
            isRoot = file.absolutePath == projectPath,
            enabled = fileActions != null,
            onAction = { pendingAction = it },
        )
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
                icon = Icons.Default.Refresh,
                onClick = {
                    // Drop cached listings so expanded directories reload from disk.
                    childrenCache.keys.toList().forEach { path -> childrenCache[path] = listDirectory(path) }
                    rootFiles = listDirectory(projectPath)
                },
                contentDescription = "Refresh",
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
                    menuItems = menuItemsFor,
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
                    fileSystem = fileSystem,
                    menuItems = menuItemsFor,
                )
            }
        }
    }

    ProjectFileActionDialogs(
        action = pendingAction,
        onDismiss = { pendingAction = null },
        onCreateFile = { parent, name ->
            pendingAction = null
            fileActions?.let { actions -> runAction { actions.createFile(parent, name) } }
        },
        onCreateFolder = { parent, name ->
            pendingAction = null
            fileActions?.let { actions -> runAction { actions.createDirectory(parent, name) } }
        },
        onRename = { path, newName ->
            pendingAction = null
            fileActions?.let { actions ->
                runAction {
                    actions.rename(path, newName).also { result ->
                        // The old path is gone either way from the editor's point of view.
                        if (result is FileActionResult.Success) onPathRemoved(path)
                    }
                }
            }
        },
        onDelete = { path ->
            pendingAction = null
            fileActions?.let { actions ->
                runAction {
                    actions.delete(path).also { result ->
                        if (result is FileActionResult.Success) onPathRemoved(path)
                    }
                }
            }
        },
    )
}

/** Menu entries for a tree row; directories can also receive new children. */
private fun buildProjectTreeMenu(
    file: File,
    isRoot: Boolean,
    enabled: Boolean,
    onAction: (ProjectFileAction) -> Unit,
): List<ProjectTreeMenuItem> {
    if (!enabled) return emptyList()
    val path = file.absolutePath
    return buildList {
        if (file.isDirectory) {
            add(
                ProjectTreeMenuItem(
                    label = "New File…",
                    onClick = { onAction(ProjectFileAction.NewFile(path)) },
                ),
            )
            add(
                ProjectTreeMenuItem(
                    label = "New Folder…",
                    onClick = { onAction(ProjectFileAction.NewFolder(path)) },
                ),
            )
        }
        if (!isRoot) {
            add(
                ProjectTreeMenuItem(
                    label = "Rename…",
                    onClick = { onAction(ProjectFileAction.Rename(path, file.name)) },
                ),
            )
            add(
                ProjectTreeMenuItem(
                    label = "Delete…",
                    onClick = { onAction(ProjectFileAction.Delete(path, file.name, file.isDirectory)) },
                    isDestructive = true,
                ),
            )
        }
    }
}

/** A file operation awaiting confirmation in a dialog. */
private sealed interface ProjectFileAction {
    data class NewFile(
        val parentDirectory: String,
    ) : ProjectFileAction

    data class NewFolder(
        val parentDirectory: String,
    ) : ProjectFileAction

    data class Rename(
        val path: String,
        val currentName: String,
    ) : ProjectFileAction

    data class Delete(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
    ) : ProjectFileAction
}

@Composable
private fun ProjectFileActionDialogs(
    action: ProjectFileAction?,
    onDismiss: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (action == null) return

    when (action) {
        is ProjectFileAction.NewFile -> {
            ProjectFileNameDialog(
                title = "New File",
                confirmLabel = "Create",
                initialName = "",
                onConfirm = { name -> onCreateFile(action.parentDirectory, name) },
                onDismiss = onDismiss,
            )
        }

        is ProjectFileAction.NewFolder -> {
            ProjectFileNameDialog(
                title = "New Folder",
                confirmLabel = "Create",
                initialName = "",
                onConfirm = { name -> onCreateFolder(action.parentDirectory, name) },
                onDismiss = onDismiss,
            )
        }

        is ProjectFileAction.Rename -> {
            ProjectFileNameDialog(
                title = "Rename ${action.currentName}",
                confirmLabel = "Rename",
                initialName = action.currentName,
                onConfirm = { name -> onRename(action.path, name) },
                onDismiss = onDismiss,
            )
        }

        is ProjectFileAction.Delete -> {
            ProjectFileDeleteDialog(
                name = action.name,
                isDirectory = action.isDirectory,
                onConfirm = { onDelete(action.path) },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Reloads a directory whenever its contents change on disk.
 *
 * Watching is non-recursive and scoped to the composable's lifetime, so only
 * directories actually visible in the tree are watched — a recursive watch of
 * the project root would register every build output directory.
 */
@Composable
private fun WatchDirectory(
    fileSystem: FileSystem?,
    path: String,
    onChanged: () -> Unit,
) {
    if (fileSystem == null) return
    LaunchedEffect(path) {
        runCatching {
            fileSystem
                .watch(path, recursive = false)
                .conflate()
                .collectLatest {
                    // Coalesce bursts (a save can emit several events).
                    delay(WATCH_DEBOUNCE_MS)
                    onChanged()
                }
        }
    }
}

/** Lists visible children of [path], directories first. */
private fun listDirectory(path: String): List<File> =
    File(path)
        .listFiles()
        ?.filter { !it.name.startsWith(".") }
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?: emptyList()

/** Delay used to coalesce bursts of file-system events. */
private const val WATCH_DEBOUNCE_MS = 150L

@Composable
@Suppress("LongParameterList")
private fun FileTreeNode(
    file: File,
    indent: Int,
    expandedDirs: MutableMap<String, Boolean>,
    childrenCache: MutableMap<String, List<File>>,
    selectedPath: String?,
    onFileClick: (String) -> Unit,
    fileSystem: FileSystem?,
    menuItems: (File) -> List<ProjectTreeMenuItem>,
) {
    val isDirectory = file.isDirectory
    val path = file.absolutePath
    val isExpanded = expandedDirs[path] == true

    // Load children when expanded
    LaunchedEffect(isExpanded) {
        if (isDirectory && isExpanded && !childrenCache.containsKey(path)) {
            childrenCache[path] = listDirectory(path)
        }
    }

    // Only expanded directories are watched, so the watcher set matches what is visible.
    if (isDirectory && isExpanded) {
        WatchDirectory(fileSystem, path) { childrenCache[path] = listDirectory(path) }
    }

    Column {
        ProjectTreeNode(
            file = file,
            displayName = file.name,
            isRoot = false,
            isExpanded = isExpanded,
            indent = indent,
            isSelected = !isDirectory && path == selectedPath,
            onFileClick = { onFileClick(path) },
            onToggleExpand = {
                if (isDirectory) {
                    expandedDirs[path] = !isExpanded
                }
            },
            menuItems = menuItems,
        )

        // Children
        if (isExpanded) {
            childrenCache[path]?.forEach { child ->
                FileTreeNode(
                    file = child,
                    indent = indent + 1,
                    expandedDirs = expandedDirs,
                    childrenCache = childrenCache,
                    selectedPath = selectedPath,
                    onFileClick = onFileClick,
                    fileSystem = fileSystem,
                    menuItems = menuItems,
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
    menuItems: (File) -> List<ProjectTreeMenuItem>,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDirectory = file.isDirectory
    var menuOffset by remember { mutableStateOf<IntOffset?>(null) }

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
                    .pointerInput(file) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press &&
                                    event.buttons.isSecondaryPressed
                                ) {
                                    val position = event.changes.first().position
                                    menuOffset = IntOffset(position.x.toInt(), position.y.toInt())
                                }
                            }
                        }
                    }.padding(start = Spacing.xs.dp, end = Spacing.sm.dp),
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

        menuOffset?.let { offset ->
            val items = menuItems(file)
            if (items.isEmpty()) {
                menuOffset = null
            } else {
                ProjectTreeContextMenu(
                    offset = offset,
                    items = items,
                    onDismiss = { menuOffset = null },
                )
            }
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
