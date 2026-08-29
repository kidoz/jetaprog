package su.kidoz.jetaprog.app.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.components.popupChrome
import su.kidoz.jetaprog.app.ui.dialogs.ConfirmationDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.vcs.GitBranch

/**
 * Toolbar branch chip with a dropdown for switching and creating branches.
 *
 * Styled to match IntelliJ IDEA's branch widget in the main toolbar.
 */
@Composable
public fun BranchSelector(
    branchName: String,
    branches: List<GitBranch>,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit,
    onRenameBranch: (oldName: String, newName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var renamingBranch by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val closeMenu = {
        expanded = false
        isCreating = false
        newBranchName = ""
        renamingBranch = null
        renameValue = ""
    }

    var anchorHeightPx by remember { mutableStateOf(0) }
    Box(modifier = modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier =
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(LocalIntelliJColors.current.surfaceElevated)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = "Branch",
                tint = LocalIntelliJColors.current.success,
                modifier = Modifier.size(15.dp),
            )
            Text(text = branchName, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp, maxLines = 1)
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorHeightPx),
                onDismissRequest = closeMenu,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(260.dp)
                            .popupChrome(Dimensions.cornerRadius.dp)
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    when {
                        isCreating -> {
                            NewBranchField(
                                name = newBranchName,
                                onNameChange = { newBranchName = it },
                                onConfirm = {
                                    if (newBranchName.isNotBlank()) {
                                        onCreateBranch(newBranchName.trim())
                                        closeMenu()
                                    }
                                },
                            )
                        }

                        renamingBranch != null -> {
                            NewBranchField(
                                name = renameValue,
                                onNameChange = { renameValue = it },
                                onConfirm = {
                                    if (renameValue.isNotBlank()) {
                                        onRenameBranch(renamingBranch.orEmpty(), renameValue.trim())
                                        closeMenu()
                                    }
                                },
                            )
                        }

                        else -> {
                            branches.forEach { branch ->
                                BranchMenuItem(
                                    branch = branch,
                                    onClick = {
                                        if (!branch.isCurrent) {
                                            onCheckoutBranch(branch.name)
                                        }
                                        closeMenu()
                                    },
                                    onRename = {
                                        renamingBranch = branch.name
                                        renameValue = branch.name
                                    },
                                    onDelete = {
                                        pendingDelete = branch.name
                                        closeMenu()
                                    },
                                )
                            }

                            if (branches.isNotEmpty()) {
                                HorizontalDivider(color = LocalIntelliJColors.current.border)
                            }

                            NewBranchAction(onClick = { isCreating = true })
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { branchName ->
        ConfirmationDialog(
            title = "Delete branch?",
            message = "Delete local branch \"$branchName\"? Git refuses to delete branches that are not fully merged.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteBranch(branchName)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun BranchMenuItem(
    branch: GitBranch,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        branch.isCurrent -> LocalIntelliJColors.current.selectionBackground
                        isHovered -> LocalIntelliJColors.current.buttonBackgroundHover
                        else -> Color.Transparent
                    },
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (branch.isCurrent) Icons.Filled.Check else Icons.Filled.AccountTree,
            contentDescription = null,
            tint =
                if (branch.isCurrent) {
                    LocalIntelliJColors.current.accent
                } else {
                    LocalIntelliJColors.current.textSecondary
                },
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        Text(
            text = branch.name,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (branch.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isHovered) {
            if (branch.isCurrent) {
                IconButton(onClick = onRename, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename branch",
                        tint = LocalIntelliJColors.current.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete branch",
                        tint = LocalIntelliJColors.current.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewBranchAction(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (isHovered) LocalIntelliJColors.current.buttonBackgroundHover else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = LocalIntelliJColors.current.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        Text(
            text = "New Branch...",
            color = LocalIntelliJColors.current.textLink,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NewBranchField(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        placeholder = { Text("New branch name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
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
        textStyle = MaterialTheme.typography.bodySmall.copy(color = LocalIntelliJColors.current.textPrimary),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
    )
}
