package su.kidoz.jetaprog.app.ui.dialogs.clone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryIntent
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.CloneRepositoryViewModel

/**
 * Modal dialog for cloning a Git repository: repository URL, destination
 * directory, and project folder name, with live `git clone` progress.
 */
@Composable
public fun CloneRepositoryDialog(
    viewModel: CloneRepositoryViewModel,
    onBrowseDestination: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    DialogOverlay(
        isVisible = state.isVisible,
        onDismiss = { viewModel.dispatch(CloneRepositoryIntent.Hide) },
    ) {
        DialogContainer(
            modifier = Modifier.width(560.dp),
        ) {
            Text(
                text = "Clone Repository",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                IntelliJTextField(
                    value = state.repositoryUrl,
                    onValueChange = { viewModel.dispatch(CloneRepositoryIntent.SetRepositoryUrl(it)) },
                    label = "Repository URL:",
                    placeholder = "https://github.com/user/repo.git",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                ) {
                    IntelliJTextField(
                        value = state.destinationDirectory,
                        onValueChange = { viewModel.dispatch(CloneRepositoryIntent.SetDestinationDirectory(it)) },
                        label = "Destination directory:",
                        modifier = Modifier.weight(1f),
                    )
                    IntelliJButton(
                        text = "Browse...",
                        onClick = onBrowseDestination,
                        style = ButtonStyle.SECONDARY,
                    )
                }

                IntelliJTextField(
                    value = state.projectName,
                    onValueChange = { viewModel.dispatch(CloneRepositoryIntent.SetProjectName(it)) },
                    label = "Project name:",
                    placeholder = "Derived from the URL",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                Text(
                    text = error,
                    color = LocalIntelliJColors.current.error,
                    fontSize = 12.sp,
                )
            }

            if (state.isCloning) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                Text(
                    text = state.progressText.ifEmpty { "Cloning…" },
                    color = LocalIntelliJColors.current.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntelliJButton(
                    text = "Cancel",
                    onClick = { viewModel.dispatch(CloneRepositoryIntent.Hide) },
                    style = ButtonStyle.SECONDARY,
                )
                Spacer(modifier = Modifier.width(Spacing.sm.dp))
                IntelliJButton(
                    text = if (state.isCloning) "Cloning…" else "Clone",
                    onClick = { viewModel.dispatch(CloneRepositoryIntent.Clone) },
                    style = ButtonStyle.PRIMARY,
                    enabled = state.canClone,
                )
            }
        }
    }
}
