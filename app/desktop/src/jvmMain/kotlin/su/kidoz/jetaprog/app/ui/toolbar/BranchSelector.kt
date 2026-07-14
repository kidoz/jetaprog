package su.kidoz.jetaprog.app.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    val closeMenu = {
        expanded = false
        isCreating = false
        newBranchName = ""
    }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.surfaceElevated)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = "Branch",
                tint = IntelliJColors.success,
                modifier = Modifier.size(15.dp),
            )
            Text(text = branchName, color = IntelliJColors.textPrimary, fontSize = 12.sp, maxLines = 1)
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = IntelliJColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = closeMenu,
            modifier =
                Modifier
                    .background(IntelliJColors.surface)
                    .width(260.dp),
        ) {
            if (isCreating) {
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
            } else {
                branches.forEach { branch ->
                    BranchMenuItem(
                        branch = branch,
                        onClick = {
                            if (!branch.isCurrent) {
                                onCheckoutBranch(branch.name)
                            }
                            closeMenu()
                        },
                    )
                }

                if (branches.isNotEmpty()) {
                    HorizontalDivider(color = IntelliJColors.border)
                }

                NewBranchAction(onClick = { isCreating = true })
            }
        }
    }
}

@Composable
private fun BranchMenuItem(
    branch: GitBranch,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        branch.isCurrent -> IntelliJColors.selectionBackground
                        isHovered -> IntelliJColors.buttonBackgroundHover
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
            tint = if (branch.isCurrent) IntelliJColors.accent else IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        Text(
            text = branch.name,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (branch.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                    if (isHovered) IntelliJColors.buttonBackgroundHover else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        Text(
            text = "New Branch...",
            color = IntelliJColors.textLink,
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
                focusedTextColor = IntelliJColors.textPrimary,
                unfocusedTextColor = IntelliJColors.textPrimary,
                focusedContainerColor = IntelliJColors.inputBackground,
                unfocusedContainerColor = IntelliJColors.inputBackground,
                focusedBorderColor = IntelliJColors.inputBorderFocused,
                unfocusedBorderColor = IntelliJColors.inputBorder,
                focusedPlaceholderColor = IntelliJColors.inputPlaceholder,
                unfocusedPlaceholderColor = IntelliJColors.inputPlaceholder,
            ),
        textStyle = MaterialTheme.typography.bodySmall.copy(color = IntelliJColors.textPrimary),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
    )
}
