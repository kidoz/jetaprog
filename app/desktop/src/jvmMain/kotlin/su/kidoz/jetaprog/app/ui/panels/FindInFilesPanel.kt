package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel

/**
 * Project-wide full-text search ("Find in Files").
 *
 * @param viewModel the search view model.
 * @param onOpenMatch invoked with (filePath, line, column) when a match is clicked.
 * @param modifier the layout modifier.
 */
@Composable
public fun FindInFilesPanel(
    viewModel: TextSearchViewModel,
    onOpenMatch: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                label = { Text("Find in files") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::search, enabled = state.query.isNotEmpty() && !state.isSearching) {
                Text(if (state.isSearching) "Searching…" else "Search")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = state.caseSensitive, onClick = viewModel::toggleCaseSensitive, label = { Text("Cc") })
            FilterChip(selected = state.wholeWord, onClick = viewModel::toggleWholeWord, label = { Text("W") })
            FilterChip(selected = state.regex, onClick = viewModel::toggleRegex, label = { Text(".*") })
            if (state.totalMatches > 0) {
                Text(
                    text = "${state.totalMatches} in ${state.results.size} files",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (state.searched && !state.isSearching) {
                Text(
                    text = "No matches",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            state.results.forEach { file ->
                item {
                    Text(
                        text = file.filePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                items(file.matches) { match ->
                    Text(
                        text = "${match.line + 1}: ${match.lineText.trim()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMatch(file.filePath, match.line, match.startColumn) }
                                .padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
