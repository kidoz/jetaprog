package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors

/**
 * File menu dropdown with standard file operations.
 */
@Composable
public fun FileMenu(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onCloseProject: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(modifier = modifier) {
        // Menu trigger button
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isHovered || expanded) IntelliJColors.buttonBackgroundHover else Color.Transparent,
                    ).hoverable(interactionSource)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "File",
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
            )
        }

        // Dropdown menu
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier =
                        Modifier
                            .shadow(8.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(IntelliJColors.toolWindowBackground)
                            .width(200.dp)
                            .padding(vertical = 4.dp),
                ) {
                    FileMenuItem(
                        text = "New Project...",
                        shortcut = "Ctrl+Shift+N",
                        onClick = {
                            expanded = false
                            onNewProject()
                        },
                    )
                    FileMenuItem(
                        text = "Open Project...",
                        shortcut = "Ctrl+Shift+O",
                        onClick = {
                            expanded = false
                            onOpenProject()
                        },
                    )
                    MenuDivider()
                    FileMenuItem(
                        text = "Open File...",
                        shortcut = "Ctrl+O",
                        onClick = {
                            expanded = false
                            onOpenFile()
                        },
                    )
                    MenuDivider()
                    FileMenuItem(
                        text = "Save",
                        shortcut = "Ctrl+S",
                        onClick = {
                            expanded = false
                            onSave()
                        },
                    )
                    FileMenuItem(
                        text = "Save As...",
                        shortcut = "Ctrl+Shift+S",
                        onClick = {
                            expanded = false
                            onSaveAs()
                        },
                    )
                    MenuDivider()
                    FileMenuItem(
                        text = "Settings...",
                        shortcut = "Ctrl+,",
                        onClick = {
                            expanded = false
                            onSettings()
                        },
                    )
                    MenuDivider()
                    FileMenuItem(
                        text = "Close Project",
                        onClick = {
                            expanded = false
                            onCloseProject()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Individual menu item in the file menu.
 */
@Composable
private fun FileMenuItem(
    text: String,
    shortcut: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (isHovered) IntelliJColors.treeSelectionBackground else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )
        if (shortcut != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = shortcut,
                color = IntelliJColors.textSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Divider line between menu sections.
 */
@Composable
private fun MenuDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(IntelliJColors.border),
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().padding(vertical = 0.5.dp))
    }
}
