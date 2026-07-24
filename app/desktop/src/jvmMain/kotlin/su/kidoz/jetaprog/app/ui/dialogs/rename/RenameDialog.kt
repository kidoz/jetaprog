package su.kidoz.jetaprog.app.ui.dialogs.rename

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
import su.kidoz.jetaprog.app.refactoring.RenamePlan
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Rename refactoring dialog (Shift+F6).
 *
 * Shows what the rename will touch before it runs, since applying it rewrites
 * files on disk.
 */
@Composable
public fun RenameDialog(
    plan: RenamePlan?,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (plan == null) return

    var newName by remember(plan) { mutableStateOf(plan.symbolName) }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (newName.isNotBlank() && newName != plan.symbolName) onRename(newName) }

    LaunchedEffect(plan) {
        focusRequester.requestFocus()
    }

    DialogOverlay(isVisible = true, onDismiss = onDismiss) {
        DialogContainer(
            modifier =
                Modifier
                    .width(420.dp)
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
                text = "Rename ${plan.symbolName}",
                color = IntelliJColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            IntelliJTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "New name",
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            Text(
                text = renameSummary(plan),
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))

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
                    text = "Rename",
                    onClick = submit,
                    style = ButtonStyle.PRIMARY,
                    enabled = newName.isNotBlank() && newName != plan.symbolName,
                )
            }
        }
    }
}

/** "3 occurrences in 2 files" — the scope of what is about to be rewritten. */
private fun renameSummary(plan: RenamePlan): String {
    val occurrences = plan.occurrenceCount
    val files = plan.affectedFiles.size
    val occurrenceLabel = if (occurrences == 1) "occurrence" else "occurrences"
    val fileLabel = if (files == 1) "file" else "files"
    return "$occurrences $occurrenceLabel in $files $fileLabel"
}
