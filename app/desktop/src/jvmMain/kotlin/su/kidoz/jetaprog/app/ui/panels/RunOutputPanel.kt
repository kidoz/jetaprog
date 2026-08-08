package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.configuration.RunOutputLine
import su.kidoz.jetaprog.configuration.RunOutputType

/**
 * Displays streamed output from a cancellable run configuration.
 */
@Composable
public fun RunOutputPanel(
    configurationName: String,
    output: List<RunOutputLine>,
    isRunning: Boolean,
    exitCode: Int?,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) listState.scrollToItem(output.lastIndex)
    }

    Column(
        modifier = modifier.fillMaxSize().background(IntelliJColors.background),
    ) {
        RunOutputToolbar(
            configurationName = configurationName,
            isRunning = isRunning,
            exitCode = exitCode,
            onStop = onStop,
            onClear = onClear,
        )
        HorizontalDivider(color = IntelliJColors.divider)

        Box(modifier = Modifier.fillMaxSize()) {
            if (output.isEmpty()) {
                Text(
                    text = "No run output.",
                    color = IntelliJColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                            .padding(Spacing.sm.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
                ) {
                    items(output) { line ->
                        Text(
                            text = line.text,
                            color = line.type.outputColor(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetaProgFonts.codeFont,
                            softWrap = false,
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(horizontalScrollState),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RunOutputToolbar(
    configurationName: String,
    isRunning: Boolean,
    exitCode: Int?,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val status = runOutputStatus(isRunning, exitCode)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.panelHeaderHeight.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = status.color,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(
            text = "${status.label} · $configurationName",
            color = status.color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onClear,
            modifier = Modifier.size(Dimensions.toolbarIcon.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear run output",
                tint = IntelliJColors.textSecondary,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
        if (isRunning) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(Dimensions.toolbarIcon.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop run configuration",
                    tint = IntelliJColors.error,
                    modifier = Modifier.size(Dimensions.iconMd.dp),
                )
            }
        }
    }
}

private fun runOutputStatus(
    isRunning: Boolean,
    exitCode: Int?,
): RunOutputStatus =
    when {
        isRunning -> RunOutputStatus("RUNNING", Icons.Default.PlayArrow, IntelliJColors.accent)
        exitCode == 0 -> RunOutputStatus("SUCCESS", Icons.Default.CheckCircle, IntelliJColors.success)
        exitCode != null -> RunOutputStatus("FAILED", Icons.Default.Error, IntelliJColors.error)
        else -> RunOutputStatus("READY", Icons.Default.PlayArrow, IntelliJColors.textSecondary)
    }

private fun RunOutputType.outputColor(): Color =
    when (this) {
        RunOutputType.INFO -> IntelliJColors.textSecondary
        RunOutputType.STDOUT -> IntelliJColors.textPrimary
        RunOutputType.STDERR -> IntelliJColors.error
        RunOutputType.SUCCESS -> IntelliJColors.success
        RunOutputType.ERROR -> IntelliJColors.error
    }

private data class RunOutputStatus(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)
