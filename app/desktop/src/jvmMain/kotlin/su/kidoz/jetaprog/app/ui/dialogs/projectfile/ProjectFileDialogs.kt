package su.kidoz.jetaprog.app.ui.dialogs.projectfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Prompts for a single file or directory name.
 *
 * Used for New File, New Folder and Rename; Enter confirms, Escape cancels.
 */
@Composable
public fun ProjectFileNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (name.isNotBlank()) onConfirm(name.trim()) }

    LaunchedEffect(title, initialName) {
        focusRequester.requestFocus()
    }

    DialogOverlay(isVisible = true, onDismiss = onDismiss) {
        DialogContainer(
            modifier =
                Modifier
                    .width(400.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.Enter -> {
                                    submit()
                                    true
                                }

                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }
                    },
        ) {
            Text(
                text = title,
                color = IntelliJColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            IntelliJTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            DialogButtons(
                confirmLabel = confirmLabel,
                confirmEnabled = name.isNotBlank(),
                isDestructive = false,
                onConfirm = submit,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Confirms deletion of [name], which cannot be undone from within the IDE.
 */
@Composable
public fun ProjectFileDeleteDialog(
    name: String,
    isDirectory: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogOverlay(isVisible = true, onDismiss = onDismiss) {
        DialogContainer(
            modifier =
                Modifier
                    .width(400.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            Text(
                text = if (isDirectory) "Delete folder" else "Delete file",
                color = IntelliJColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            Text(
                text =
                    if (isDirectory) {
                        "Delete \"$name\" and everything inside it? This cannot be undone."
                    } else {
                        "Delete \"$name\"? This cannot be undone."
                    },
                color = IntelliJColors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            DialogButtons(
                confirmLabel = "Delete",
                confirmEnabled = true,
                isDestructive = true,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun DialogButtons(
    confirmLabel: String,
    confirmEnabled: Boolean,
    isDestructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
    ) {
        IntelliJButton(
            text = "Cancel",
            onClick = onDismiss,
            style = ButtonStyle.SECONDARY,
        )
        IntelliJButton(
            text = confirmLabel,
            onClick = onConfirm,
            style = if (isDestructive) ButtonStyle.DANGER else ButtonStyle.PRIMARY,
            enabled = confirmEnabled,
        )
    }
}
