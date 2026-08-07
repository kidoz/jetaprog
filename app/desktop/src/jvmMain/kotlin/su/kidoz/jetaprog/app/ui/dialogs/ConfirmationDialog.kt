package su.kidoz.jetaprog.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Confirmation dialog for actions that can discard unsaved work.
 */
@Composable
public fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    alternateLabel: String? = null,
    onAlternate: (() -> Unit)? = null,
) {
    IntelliJDialog(
        onDismissRequest = onDismiss,
        minWidth = Dimensions.dialogMinWidth.dp,
        maxWidth = Dimensions.dialogMinWidth.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        ) {
            Text(
                text = title,
                color = IntelliJColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = IntelliJColors.textSecondary,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntelliJButton(text = "Cancel", onClick = onDismiss)
                IntelliJButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    style = ButtonStyle.DANGER,
                )
                if (alternateLabel != null && onAlternate != null) {
                    IntelliJButton(
                        text = alternateLabel,
                        onClick = onAlternate,
                        style = ButtonStyle.PRIMARY,
                    )
                }
            }
        }
    }
}
