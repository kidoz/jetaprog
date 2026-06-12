package su.kidoz.jetaprog.app.ui.dialogs.settings.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * A row for displaying a setting with label, description, and control.
 */
@Composable
public fun SettingRow(
    label: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = Spacing.md.dp),
        ) {
            Text(
                text = label,
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
            )
            description?.let {
                Text(
                    text = it,
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        control()
    }
}

/**
 * A section header for grouping settings.
 */
@Composable
public fun SettingSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = Spacing.lg.dp)) {
        Text(
            text = title,
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = Spacing.sm.dp),
        )
        content()
    }
}
