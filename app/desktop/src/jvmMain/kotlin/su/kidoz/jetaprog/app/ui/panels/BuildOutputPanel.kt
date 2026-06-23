package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.build.gradle.GradleDiagnostic
import su.kidoz.jetaprog.build.gradle.GradleDiagnosticSeverity
import su.kidoz.jetaprog.build.gradle.GradleTask
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.build.gradle.state.GradleState
import su.kidoz.jetaprog.build.gradle.state.OutputLine
import su.kidoz.jetaprog.build.gradle.state.OutputType

/**
 * Build output panel for Gradle tasks.
 */
@Composable
public fun BuildOutputPanel(
    state: GradleState,
    onIntent: (GradleIntent) -> Unit,
    onOpenDiagnostic: (GradleDiagnostic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color(0xFF1E1E1E)),
    ) {
        // Build toolbar
        BuildToolbar(
            isRunning = state.isRunning,
            runningTask = state.runningTask,
            favoriteTasks = state.favoriteTasks,
            diagnosticsCount = state.diagnostics.size,
            lastResult = state.lastBuildResult,
            onRunTask = { task -> onIntent(GradleIntent.RunTask(task)) },
            onCancel = { onIntent(GradleIntent.CancelTask) },
            onClear = { onIntent(GradleIntent.ClearOutput) },
            onRefresh = { onIntent(GradleIntent.RefreshTasks) },
        )

        DiscoveredTasksStrip(
            tasks = state.project?.tasks.orEmpty(),
            favoriteTasks = state.favoriteTasks,
            isRunning = state.isRunning,
            runningTask = state.runningTask,
            onRunTask = { task -> onIntent(GradleIntent.RunTask(task)) },
        )

        DiagnosticsList(
            diagnostics = state.diagnostics,
            onOpenDiagnostic = onOpenDiagnostic,
        )

        // Output area
        BuildOutputArea(
            output = state.output,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiscoveredTasksStrip(
    tasks: List<GradleTask>,
    favoriteTasks: List<String>,
    isRunning: Boolean,
    runningTask: String?,
    onRunTask: (String) -> Unit,
) {
    val visibleTasks = tasks.prioritizedForToolbar(favoriteTasks)
    if (visibleTasks.isEmpty()) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(Color(0xFF202124))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Tasks",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
        )

        visibleTasks.forEach { task ->
            TaskButton(
                taskName = task.path,
                isRunning = isRunning && runningTask == task.path,
                enabled = !isRunning,
                onClick = { onRunTask(task.path) },
            )
        }
    }
}

private fun List<GradleTask>.prioritizedForToolbar(favoriteTasks: List<String>): List<GradleTask> {
    val priority =
        listOf(
            ":app:desktop:run",
            "build",
            "test",
            "ktlintCheck",
            "detekt",
            "clean",
            ":app:desktop:packageDistributionForCurrentOS",
        )
    return distinctBy { it.path }
        .sortedWith(
            compareBy<GradleTask> { task ->
                val favoriteIndex = favoriteTasks.indexOf(task.path)
                if (favoriteIndex >= 0) favoriteIndex else Int.MAX_VALUE
            }.thenBy { task ->
                val priorityIndex = priority.indexOf(task.path)
                if (priorityIndex >= 0) priorityIndex else Int.MAX_VALUE
            }.thenBy { it.group ?: "" }
                .thenBy { it.path },
        ).take(MAX_DISCOVERED_TASK_BUTTONS)
}

@Composable
private fun DiagnosticsList(
    diagnostics: List<GradleDiagnostic>,
    onOpenDiagnostic: (GradleDiagnostic) -> Unit,
) {
    if (diagnostics.isEmpty()) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF241F1F))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        diagnostics.take(MAX_VISIBLE_DIAGNOSTICS).forEach { diagnostic ->
            DiagnosticRow(
                diagnostic = diagnostic,
                onClick = { onOpenDiagnostic(diagnostic) },
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    diagnostic: GradleDiagnostic,
    onClick: () -> Unit,
) {
    val severityColor =
        when (diagnostic.severity) {
            GradleDiagnosticSeverity.ERROR -> Color(0xFFE74C3C)
            GradleDiagnosticSeverity.WARNING -> Color(0xFFDCDCAA)
            GradleDiagnosticSeverity.INFO -> Color(0xFF569CD6)
        }
    val displayPath =
        diagnostic.filePath.substringAfterLast('/')
    val line = diagnostic.position.line + 1
    val column = diagnostic.position.column + 1

    Text(
        text = "$displayPath:$line:$column ${diagnostic.message}",
        color = severityColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
    )
}

@Composable
private fun BuildToolbar(
    isRunning: Boolean,
    runningTask: String?,
    favoriteTasks: List<String>,
    diagnosticsCount: Int,
    lastResult: su.kidoz.jetaprog.build.gradle.state.BuildResult?,
    onRunTask: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF252526))
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Build icon and title
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "Build",
            tint = Color(0xFF4EC9B0),
            modifier = Modifier.size(16.dp),
        )

        Text(
            text = if (isRunning) "Building: $runningTask" else "Build",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )

        // Status indicator
        if (lastResult != null && !isRunning) {
            val color = if (lastResult.success) Color(0xFF4EC9B0) else Color(0xFFE74C3C)
            val text = if (lastResult.success) "SUCCESS" else "FAILED"
            Text(
                text = "[$text]",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Diagnostics count
        if (diagnosticsCount > 0) {
            Text(
                text = "$diagnosticsCount errors",
                color = Color(0xFFE74C3C),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Spacer
        Box(modifier = Modifier.weight(1f))

        // Quick task buttons
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            favoriteTasks.forEach { task ->
                TaskButton(
                    taskName = task,
                    isRunning = isRunning && runningTask == task,
                    enabled = !isRunning,
                    onClick = { onRunTask(task) },
                )
            }
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Refresh tasks
            IconButton(
                onClick = onRefresh,
                enabled = !isRunning,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh tasks",
                    tint = if (isRunning) Color.Gray.copy(alpha = 0.5f) else Color.Gray,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Clear output
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Cancel build
            if (isRunning) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Cancel",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskButton(
    taskName: String,
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor =
        when {
            isRunning -> Color(0xFF4EC9B0).copy(alpha = 0.3f)
            enabled -> Color(0xFF3C3C3C)
            else -> Color(0xFF2D2D2D)
        }

    val textColor =
        when {
            isRunning -> Color(0xFF4EC9B0)
            enabled -> Color.White
            else -> Color.Gray
        }

    Row(
        modifier =
            Modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .widthIn(max = 220.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = taskName,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BuildOutputArea(
    output: List<OutputLine>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) {
            listState.animateScrollToItem(output.size - 1)
        }
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(8.dp),
        state = listState,
    ) {
        items(output) { line ->
            OutputLineRow(line)
        }
    }
}

@Composable
private fun OutputLineRow(line: OutputLine) {
    val color =
        when (line.type) {
            OutputType.STDOUT -> Color(0xFFD4D4D4)
            OutputType.STDERR -> Color(0xFFE74C3C)
            OutputType.INFO -> Color(0xFF569CD6)
            OutputType.SUCCESS -> Color(0xFF4EC9B0)
            OutputType.ERROR -> Color(0xFFE74C3C)
        }

    Text(
        text = line.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

private const val MAX_DISCOVERED_TASK_BUTTONS = 40
private const val MAX_VISIBLE_DIAGNOSTICS = 5
