package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.ToolWindowButton
import su.kidoz.jetaprog.app.ui.dialogs.ConfirmationDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.GitChangesViewMode
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange

/**
 * The Git **commit panel** (left tool window of the Version Control perspective):
 * changed files with stage checkboxes and status, plus a commit message with
 * Commit / Commit and Push. The diff and log live in the main-area perspective.
 *
 * @param viewModel the Git view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun GitPanel(
    viewModel: GitViewModel,
    modifier: Modifier = Modifier,
    onSaveOpenDocuments: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    if (!state.isRepository) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(LocalIntelliJColors.current.toolWindowBackground)
                    .padding(Spacing.md.dp),
        ) {
            Text(
                text = "Not a Git repository",
                color = LocalIntelliJColors.current.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val changes = state.staged + state.unstaged
    val stageSelection = changes.stageSelection()
    var collapsedDirectories by remember { mutableStateOf(emptySet<String>()) }
    var pendingRollback by remember { mutableStateOf<List<GitChange>?>(null) }
    var selectedPaths by remember { mutableStateOf(setOfNotNull(state.selected?.path)) }
    var selectionAnchor by remember { mutableStateOf(state.selected?.path) }
    val currentPaths = changes.map { it.path }.toSet()
    LaunchedEffect(currentPaths) { selectedPaths = selectedPaths.intersect(currentPaths) }
    val treeItems = buildGitChangeTreeItems(changes, collapsedDirectories)
    val visiblePaths =
        if (state.changesViewMode == GitChangesViewMode.FLAT) {
            changes.map { it.path }.distinct()
        } else {
            treeItems.filterIsInstance<GitChangeTreeItem.File>().map { it.change.path }.distinct()
        }
    val selectFile: (GitChange, Boolean, Boolean) -> Unit = { change, toggle, extend ->
        val anchorIndex = visiblePaths.indexOf(selectionAnchor)
        val clickedIndex = visiblePaths.indexOf(change.path)
        selectedPaths =
            when {
                extend && anchorIndex >= 0 && clickedIndex >= 0 -> {
                    val range =
                        visiblePaths
                            .subList(
                                minOf(anchorIndex, clickedIndex),
                                maxOf(anchorIndex, clickedIndex) + 1,
                            ).toSet()
                    if (toggle) selectedPaths + range else range
                }

                toggle -> {
                    if (change.path in selectedPaths) selectedPaths - change.path else selectedPaths + change.path
                }

                else -> {
                    setOf(change.path)
                }
            }
        if (!extend) selectionAnchor = change.path
        viewModel.select(change)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LocalIntelliJColors.current.toolWindowBackground),
    ) {
        CommitHeader(isBusy = state.isBusy, onRefresh = viewModel::refresh)

        state.error?.let { error ->
            Text(
                text = error,
                color = LocalIntelliJColors.current.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
            )
        }

        ChangesHeader(
            fileCount = changes.distinctBy { it.path }.size,
            stageSelection = stageSelection,
            viewMode = state.changesViewMode,
            onToggleAll = { viewModel.setAllStaged(stageSelection != StageSelection.CHECKED) },
            onToggleViewMode = {
                viewModel.setChangesViewMode(
                    if (state.changesViewMode == GitChangesViewMode.FLAT) {
                        GitChangesViewMode.DIRECTORY_TREE
                    } else {
                        GitChangesViewMode.FLAT
                    },
                )
            },
        )

        IntelliJButton(
            text = "Revert",
            enabled = !state.isBusy && changes.any { it.path in selectedPaths },
            onClick = { pendingRollback = changes.filter { it.path in selectedPaths } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (changes.isEmpty()) {
                item { EmptyChanges() }
            }
            if (state.changesViewMode == GitChangesViewMode.FLAT) {
                items(changes, key = { "${it.staged}:${it.path}" }) { change ->
                    ChangeRow(
                        change = change,
                        isSelected = change.path in selectedPaths,
                        showParentPath = true,
                        onToggle = { viewModel.toggleStage(change) },
                        onClick = { toggle, extend -> selectFile(change, toggle, extend) },
                        onRollback = { pendingRollback = changes.filter { it.path == change.path } },
                        canRevert = !state.isBusy,
                    )
                }
            } else {
                items(treeItems, key = GitChangeTreeItem::key) { item ->
                    when (item) {
                        is GitChangeTreeItem.Directory -> {
                            DirectoryRow(
                                directory = item,
                                isExpanded = item.path !in collapsedDirectories,
                                onToggleExpanded = {
                                    collapsedDirectories =
                                        if (item.path in collapsedDirectories) {
                                            collapsedDirectories - item.path
                                        } else {
                                            collapsedDirectories + item.path
                                        }
                                },
                                onToggleStage = {
                                    val selection = item.changes.stageSelection()
                                    viewModel.setStaged(item.changes, selection != StageSelection.CHECKED)
                                },
                            )
                        }

                        is GitChangeTreeItem.File -> {
                            ChangeRow(
                                change = item.change,
                                isSelected = item.change.path in selectedPaths,
                                depth = item.depth,
                                showParentPath = false,
                                onToggle = { viewModel.toggleStage(item.change) },
                                onClick = { toggle, extend -> selectFile(item.change, toggle, extend) },
                                onRollback = { pendingRollback = changes.filter { it.path == item.change.path } },
                                canRevert = !state.isBusy,
                            )
                        }
                    }
                }
            }
        }

        CommitArea(
            commitMessage = state.commitMessage,
            amendMode = state.amendMode,
            isBusy = state.isBusy,
            hasStagedChanges = state.staged.isNotEmpty(),
            onCommitMessageChange = viewModel::setCommitMessage,
            onAmendModeChange = viewModel::setAmendMode,
            onCommit = {
                // IntelliJ-style phase: persist open documents before committing
                // so unsaved editor content is not left out of the commit.
                onSaveOpenDocuments()
                viewModel.commit()
            },
            onCommitAndPush = {
                onSaveOpenDocuments()
                viewModel.commitAndPush()
            },
        )
    }

    pendingRollback?.let { rollbackTargets ->
        ConfirmationDialog(
            title = "Revert uncommitted changes?",
            message =
                revertChangesMessage(rollbackTargets),
            confirmLabel = "Revert changes",
            onConfirm = {
                viewModel.discardChanges(rollbackTargets.distinctBy { it.path })
                pendingRollback = null
            },
            onDismiss = { pendingRollback = null },
        )
    }
}

/** Describes the exact paths and both index/worktree effects of a confirmed revert. */
internal fun revertChangesMessage(changes: List<GitChange>): String {
    val paths = changes.flatMap { listOfNotNull(it.originalPath, it.path) }.distinct()
    val preview = paths.take(REVERT_PREVIEW_LIMIT).joinToString("\n")
    val remaining = paths.size - REVERT_PREVIEW_LIMIT
    return "Discard staged and unstaged changes in ${paths.size} file(s)?\n\n" +
        preview + (if (remaining > 0) "\n…and $remaining more" else "") +
        "\n\nTracked files will be restored to HEAD. Newly added and untracked files will be deleted. " +
        "This cannot be undone."
}

private const val REVERT_PREVIEW_LIMIT = 8

@Composable
private fun CommitHeader(
    isBusy: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.panelHeaderHeight.dp)
                .background(LocalIntelliJColors.current.toolWindowHeader)
                .padding(start = Spacing.md.dp, end = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Commit",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Git status",
                tint =
                    if (isBusy) {
                        LocalIntelliJColors.current.textDisabled
                    } else {
                        LocalIntelliJColors.current.iconDefault
                    },
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
    }
}

@Composable
private fun ChangesHeader(
    fileCount: Int,
    stageSelection: StageSelection,
    viewMode: GitChangesViewMode,
    onToggleAll: () -> Unit,
    onToggleViewMode: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.gitChangesHeaderHeight.dp)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        StageCheckbox(selection = stageSelection, enabled = fileCount > 0, onToggle = onToggleAll)
        Text(
            text = "Changes",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "$fileCount ${if (fileCount == 1) "file" else "files"}",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        ToolWindowButton(
            icon =
                if (viewMode == GitChangesViewMode.FLAT) {
                    Icons.Default.AccountTree
                } else {
                    Icons.AutoMirrored.Filled.ViewList
                },
            onClick = onToggleViewMode,
            contentDescription =
                if (viewMode == GitChangesViewMode.FLAT) {
                    "Group changes by directory"
                } else {
                    "Show changes as a flat list"
                },
        )
    }
}

internal enum class StageSelection {
    UNCHECKED,
    CHECKED,
    INDETERMINATE,
}

internal fun List<GitChange>.stageSelection(): StageSelection =
    when {
        isEmpty() || none { it.staged } -> StageSelection.UNCHECKED
        all { it.staged } -> StageSelection.CHECKED
        else -> StageSelection.INDETERMINATE
    }

@Composable
private fun StageCheckbox(
    selection: StageSelection,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val isSelected = selection != StageSelection.UNCHECKED
    Box(
        modifier =
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelected) LocalIntelliJColors.current.accent else Color.Transparent)
                .border(
                    1.5.dp,
                    if (enabled) LocalIntelliJColors.current.accent else LocalIntelliJColors.current.border,
                    RoundedCornerShape(3.dp),
                ).clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector =
                    if (selection == StageSelection.CHECKED) {
                        Icons.Default.Check
                    } else {
                        Icons.Default.Remove
                    },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun EmptyChanges() {
    Text(
        text = "No changes",
        color = LocalIntelliJColors.current.textMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
    )
}

@Composable
private fun ChangeRow(
    change: GitChange,
    isSelected: Boolean,
    depth: Int = 0,
    showParentPath: Boolean,
    onToggle: () -> Unit,
    onClick: (toggle: Boolean, extend: Boolean) -> Unit,
    onRollback: () -> Unit,
    canRevert: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var clickModifiers by remember { mutableStateOf(PointerKeyboardModifiers()) }
    val backgroundColor =
        when {
            isSelected -> LocalIntelliJColors.current.treeSelectionBackground
            isHovered -> LocalIntelliJColors.current.treeHoverBackground
            else -> Color.Transparent
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.gitChangeRowHeight.dp)
                    .background(backgroundColor)
                    .semantics { selected = isSelected }
                    .hoverable(interactionSource)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) clickModifiers = event.keyboardModifiers
                            }
                        }
                    }.clickable {
                        val modifiers = clickModifiers
                        clickModifiers = PointerKeyboardModifiers()
                        onClick(modifiers.isCtrlPressed || modifiers.isMetaPressed, modifiers.isShiftPressed)
                    }.padding(start = (Spacing.md + depth * Spacing.lg).dp, end = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            StageCheckbox(
                selection = if (change.staged) StageSelection.CHECKED else StageSelection.UNCHECKED,
                enabled = true,
                onToggle = onToggle,
            )
            FileBadge(fileName = change.fileName())
            Text(
                text = change.fileName(),
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showParentPath) {
                Text(
                    text = change.parentPath(),
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            if (isHovered || isSelected) {
                IconButton(onClick = onRollback, enabled = canRevert, modifier = Modifier.size(Dimensions.iconLg.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Revert uncommitted changes in ${change.path}",
                        tint = LocalIntelliJColors.current.textSecondary,
                        modifier = Modifier.size(Dimensions.iconSm.dp),
                    )
                }
            }
            Text(
                text = change.type.statusLabel(),
                color = change.type.statusColor(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetaProgFonts.codeFont,
            )
        }
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .height(Dimensions.gitChangeRowHeight.dp)
                        .background(LocalIntelliJColors.current.accent),
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    directory: GitChangeTreeItem.Directory,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleStage: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val fileCount = directory.changes.distinctBy { it.path }.size

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.gitChangeRowHeight.dp)
                .background(if (isHovered) LocalIntelliJColors.current.treeHoverBackground else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onToggleExpanded)
                .padding(start = (Spacing.xs + directory.depth * Spacing.lg).dp, end = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (isExpanded) "Collapse ${directory.name}" else "Expand ${directory.name}",
            tint = LocalIntelliJColors.current.iconDefault,
            modifier = Modifier.size(Dimensions.iconSm.dp),
        )
        StageCheckbox(
            selection = directory.changes.stageSelection(),
            enabled = directory.changes.isNotEmpty(),
            onToggle = onToggleStage,
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = LocalIntelliJColors.current.iconDefault,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(
            text = directory.name,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = fileCount.toString(),
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun CommitArea(
    commitMessage: String,
    amendMode: Boolean,
    isBusy: Boolean,
    hasStagedChanges: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onAmendModeChange: (Boolean) -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
) {
    val canCommit = !isBusy && (hasStagedChanges || amendMode) && commitMessage.isNotBlank()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(LocalIntelliJColors.current.toolWindowHeader)
                .padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        IntelliJCheckbox(
            checked = amendMode,
            onCheckedChange = onAmendModeChange,
            label = "Amend previous commit",
            enabled = !isBusy,
        )
        OutlinedTextField(
            value = commitMessage,
            onValueChange = onCommitMessageChange,
            placeholder = { Text("Commit message") },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalIntelliJColors.current.textPrimary,
                    unfocusedTextColor = LocalIntelliJColors.current.textPrimary,
                    focusedContainerColor = LocalIntelliJColors.current.inputBackground,
                    unfocusedContainerColor = LocalIntelliJColors.current.inputBackground,
                    focusedBorderColor = LocalIntelliJColors.current.inputBorderFocused,
                    unfocusedBorderColor = LocalIntelliJColors.current.inputBorder,
                    focusedPlaceholderColor = LocalIntelliJColors.current.inputPlaceholder,
                    unfocusedPlaceholderColor = LocalIntelliJColors.current.inputPlaceholder,
                ),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    color = LocalIntelliJColors.current.textPrimary,
                    fontFamily = JetaProgFonts.codeFont,
                ),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp), modifier = Modifier.fillMaxWidth()) {
            IntelliJButton(
                text = "Commit",
                onClick = onCommit,
                style = ButtonStyle.PRIMARY,
                enabled = canCommit,
                icon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            IntelliJButton(
                text = "Commit and Push",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCommitAndPush,
                style = ButtonStyle.SECONDARY,
                enabled = canCommit,
            )
        }
    }
}
