package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange

/**
 * In-IDE Git workflow: branch status, staged/unstaged changes, file diff, and
 * commit of staged changes.
 *
 * @param viewModel the Git view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun GitPanel(
    viewModel: GitViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    if (!state.isRepository) {
        Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
            Text("Not a Git repository", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Row(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.branch ?: "(detached)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.ahead > 0) Text("↑${state.ahead}", style = MaterialTheme.typography.labelSmall)
                if (state.behind > 0) Text("↓${state.behind}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = viewModel::refresh, enabled = !state.isBusy) { Text("Refresh") }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.unstaged.isNotEmpty()) {
                    item { SectionHeader("Unstaged") }
                    items(state.unstaged) { change ->
                        ChangeRow(
                            change,
                            actionLabel = "Stage",
                            onAction = { viewModel.stage(change) },
                            onClick = { viewModel.select(change) },
                        )
                    }
                }
                if (state.staged.isNotEmpty()) {
                    item { SectionHeader("Staged") }
                    items(state.staged) { change ->
                        ChangeRow(change, actionLabel = "Unstage", onAction = {
                            viewModel.unstage(change)
                        }, onClick = { viewModel.select(change) })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.commitMessage,
                    onValueChange = viewModel::setCommitMessage,
                    label = { Text("Commit message") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::commit,
                    enabled = !state.isBusy && state.staged.isNotEmpty() && state.commitMessage.isNotBlank(),
                ) {
                    Text("Commit")
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(start = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = state.diff.ifEmpty { "Select a file to view its diff" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun ChangeRow(
    change: GitChange,
    actionLabel: String,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text =
                change.type.name
                    .first()
                    .toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp),
        )
        Text(
            text = change.path,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
