package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.build.gradle.GradleDiagnostic
import su.kidoz.jetaprog.build.gradle.GradleDiagnosticSeverity
import su.kidoz.jetaprog.build.gradle.GradleTask
import su.kidoz.jetaprog.build.gradle.state.BuildResult
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.build.gradle.state.GradleState
import su.kidoz.jetaprog.build.gradle.state.OutputLine
import su.kidoz.jetaprog.build.gradle.state.OutputType

/** Build output panel for Gradle tasks. */
@Composable
public fun BuildOutputPanel(
    state: GradleState,
    onIntent: (GradleIntent) -> Unit,
    onOpenDiagnostic: (GradleDiagnostic) -> Unit = {},
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    if (!embedded && !state.isVisible) return
    val colors = buildPanelColors()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (embedded) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.height(
                            Dimensions.toolWindowDefaultBottomHeight.dp,
                        )
                    },
                ).background(colors.background),
    ) {
        BuildToolbar(
            state = state,
            colors = colors,
            onRunTask = { task -> onIntent(GradleIntent.RunTask(task)) },
            onCancel = { onIntent(GradleIntent.CancelTask) },
            onClear = { onIntent(GradleIntent.ClearOutput) },
            onRefresh = { onIntent(GradleIntent.RefreshTasks) },
        )

        DiagnosticsList(
            diagnostics = state.diagnostics,
            colors = colors,
            onOpenDiagnostic = onOpenDiagnostic,
        )

        BuildOutputArea(
            output = state.output,
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BuildToolbar(
    state: GradleState,
    colors: BuildPanelColors,
    onRunTask: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.panelHeaderHeight.dp)
                .background(colors.header)
                .border(
                    width = Dimensions.splitterThickness.dp,
                    color = colors.divider,
                ).padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        BuildStatus(
            isRunning = state.isRunning,
            runningTask = state.runningTask,
            lastResult = state.lastBuildResult,
            colors = colors,
            modifier = Modifier.weight(1f),
        )

        val diagnosticsSummary = diagnosticSummary(state.diagnostics)
        if (diagnosticsSummary.isNotEmpty()) {
            val diagnosticsColor =
                if (state.diagnostics.any { it.severity == GradleDiagnosticSeverity.ERROR }) {
                    colors.error
                } else {
                    colors.warning
                }
            Text(
                text = diagnosticsSummary,
                color = diagnosticsColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }

        Row(
            modifier =
                Modifier
                    .widthIn(max = Dimensions.popupCompletionWidth.dp)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            state.favoriteTasks.take(MAX_PINNED_TASKS).forEach { task ->
                TaskButton(
                    taskName = task,
                    isRunning = state.isRunning && state.runningTask == task,
                    enabled = !state.isRunning,
                    colors = colors,
                    onClick = { onRunTask(task) },
                )
            }
        }

        TaskPicker(
            tasks = state.project?.tasks.orEmpty(),
            favoriteTasks = state.favoriteTasks,
            enabled = !state.isRunning,
            colors = colors,
            onRunTask = onRunTask,
        )

        BuildActionButton(
            icon = Icons.Default.Refresh,
            contentDescription = "Refresh Gradle tasks",
            enabled = !state.isRunning,
            colors = colors,
            onClick = onRefresh,
        )
        BuildActionButton(
            icon = Icons.Default.Clear,
            contentDescription = "Clear build output",
            colors = colors,
            onClick = onClear,
        )
        if (state.isRunning) {
            BuildActionButton(
                icon = Icons.Default.Stop,
                contentDescription = "Cancel build",
                tint = colors.error,
                colors = colors,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun BuildStatus(
    isRunning: Boolean,
    runningTask: String?,
    lastResult: BuildResult?,
    colors: BuildPanelColors,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector
    val tint: Color
    val label: String
    when {
        isRunning -> {
            icon = Icons.Default.Build
            tint = colors.accent
            label = "RUNNING · ${runningTask ?: "Gradle"}"
        }

        lastResult?.success == true -> {
            icon = Icons.Default.CheckCircle
            tint = colors.success
            label = "SUCCESS · ${lastResult.taskPath} · ${formatBuildDuration(lastResult.durationMs)}"
        }

        lastResult != null -> {
            icon = Icons.Default.Error
            tint = colors.error
            label = "FAILED · ${lastResult.taskPath} · ${formatBuildDuration(lastResult.durationMs)}"
        }

        else -> {
            icon = Icons.Default.Build
            tint = colors.textSecondary
            label = "Ready"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TaskPicker(
    tasks: List<GradleTask>,
    favoriteTasks: List<String>,
    enabled: Boolean,
    colors: BuildPanelColors,
    onRunTask: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(tasks) { mutableStateOf("") }
    val visibleTasks = remember(tasks, favoriteTasks, query) { filterGradleTasksForPicker(tasks, favoriteTasks, query) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        BuildTooltip(text = "Find and run a Gradle task", colors = colors) {
            Row(
                modifier =
                    Modifier
                        .height(Dimensions.chipHeight.dp)
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .background(if (isHovered || expanded) colors.buttonHover else colors.button)
                        .hoverable(interactionSource)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = { expanded = true },
                        ).padding(horizontal = Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (enabled) colors.textSecondary else colors.textDisabled,
                    modifier = Modifier.size(Dimensions.iconSm.dp),
                )
                Text(
                    text = "Tasks",
                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) colors.textSecondary else colors.textDisabled,
                    modifier = Modifier.size(Dimensions.iconSm.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .width(Dimensions.popupListWidth.dp)
                    .heightIn(max = Dimensions.popupListMaxHeight.dp),
            shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
            containerColor = colors.popupBackground,
            border = BorderStroke(Dimensions.splitterThickness.dp, colors.popupBorder),
        ) {
            IntelliJTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter Gradle tasks",
                singleLine = true,
                trailingContent =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(Dimensions.toolbarIcon.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear task filter",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(Dimensions.iconSm.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth().padding(Spacing.sm.dp),
            )
            HorizontalDivider(color = colors.divider)

            when {
                tasks.isEmpty() -> {
                    TaskPickerMessage("No Gradle tasks discovered", colors)
                }

                visibleTasks.isEmpty() -> {
                    TaskPickerMessage("No matching tasks", colors)
                }

                else -> {
                    visibleTasks.forEach { task ->
                        TaskPickerRow(
                            task = task,
                            isFavorite = task.path in favoriteTasks,
                            colors = colors,
                            onClick = {
                                expanded = false
                                onRunTask(task.path)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskPickerRow(
    task: GradleTask,
    isFavorite: Boolean,
    colors: BuildPanelColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.treeNodeHeight.dp)
                .background(if (isHovered) colors.hover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isFavorite) colors.accent else colors.textSecondary,
            modifier = Modifier.size(Dimensions.iconSm.dp),
        )
        Text(
            text = task.path,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        task.group?.let { group ->
            Text(
                text = group,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TaskPickerMessage(
    message: String,
    colors: BuildPanelColors,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DiagnosticsList(
    diagnostics: List<GradleDiagnostic>,
    colors: BuildPanelColors,
    onOpenDiagnostic: (GradleDiagnostic) -> Unit,
) {
    if (diagnostics.isEmpty()) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.elevated)
                .border(Dimensions.splitterThickness.dp, colors.divider),
    ) {
        diagnostics.take(MAX_VISIBLE_DIAGNOSTICS).forEach { diagnostic ->
            DiagnosticRow(
                diagnostic = diagnostic,
                colors = colors,
                onClick = { onOpenDiagnostic(diagnostic) },
            )
        }
        if (diagnostics.size > MAX_VISIBLE_DIAGNOSTICS) {
            Text(
                text = "+${diagnostics.size - MAX_VISIBLE_DIAGNOSTICS} more diagnostics",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    diagnostic: GradleDiagnostic,
    colors: BuildPanelColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val severityColor = diagnostic.severity.color(colors)
    val severityIcon = diagnostic.severity.icon()
    val displayPath = diagnostic.filePath.substringAfterLast('/').substringAfterLast('\\')
    val position = "${diagnostic.position.line + 1}:${diagnostic.position.column + 1}"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.treeNodeHeight.dp)
                .background(if (isHovered) colors.hover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = severityIcon,
            contentDescription = diagnostic.severity.name.lowercase(),
            tint = severityColor,
            modifier = Modifier.size(Dimensions.iconSm.dp),
        )
        Text(
            text = "$displayPath:$position",
            color = severityColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Text(
            text = diagnostic.message,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TaskButton(
    taskName: String,
    isRunning: Boolean,
    enabled: Boolean,
    colors: BuildPanelColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
        when {
            isRunning -> colors.successMuted
            isHovered && enabled -> colors.buttonHover
            enabled -> colors.button
            else -> Color.Transparent
        }
    val textColor =
        when {
            isRunning -> colors.success
            enabled -> colors.textPrimary
            else -> colors.textDisabled
        }

    BuildTooltip(text = taskName, colors = colors) {
        Row(
            modifier =
                Modifier
                    .height(Dimensions.chipHeight.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(backgroundColor)
                    .hoverable(interactionSource)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ).widthIn(max = Dimensions.welcomeRailWidth.dp)
                    .padding(horizontal = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Build else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(Dimensions.iconXs.dp),
            )
            Text(
                text = taskName,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BuildActionButton(
    icon: ImageVector,
    contentDescription: String,
    colors: BuildPanelColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = colors.textSecondary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    BuildTooltip(text = contentDescription, colors = colors) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(Dimensions.toolbarIcon.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                    .background(if (isHovered && enabled) colors.hover else Color.Transparent)
                    .hoverable(interactionSource),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else colors.textDisabled,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
    }
}

@Composable
private fun BuildOutputArea(
    output: List<OutputLine>,
    colors: BuildPanelColors,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val shouldFollowOutput = !listState.canScrollForward

    LaunchedEffect(output.size) {
        if (output.isNotEmpty() && shouldFollowOutput) {
            listState.scrollToItem(output.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (output.isEmpty()) {
            Text(
                text = "No build output.",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalState)
                        .padding(
                            start = Spacing.sm.dp,
                            top = Spacing.sm.dp,
                            end = Spacing.lg.dp,
                            bottom = Spacing.lg.dp,
                        ),
                state = listState,
            ) {
                items(output) { line ->
                    OutputLineRow(line = line, colors = colors)
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalState),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            if (listState.canScrollForward) {
                BuildTooltip(text = "Jump to latest output", colors = colors) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(output.lastIndex)
                            }
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.md.dp)
                                .size(Dimensions.buttonHeight.dp)
                                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                                .background(colors.elevated)
                                .border(
                                    Dimensions.splitterThickness.dp,
                                    colors.divider,
                                    RoundedCornerShape(Dimensions.cornerRadius.dp),
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Jump to latest output",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(Dimensions.iconMd.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputLineRow(
    line: OutputLine,
    colors: BuildPanelColors,
) {
    val color =
        when (line.buildOutputTone()) {
            BuildOutputTone.NORMAL -> colors.textPrimary
            BuildOutputTone.INFO -> colors.info
            BuildOutputTone.SUCCESS -> colors.success
            BuildOutputTone.WARNING -> colors.warning
            BuildOutputTone.ERROR -> colors.error
        }
    Text(
        text = line.text,
        color = color,
        fontFamily = JetaProgFonts.codeFont,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(vertical = Spacing.xxs.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildTooltip(
    text: String,
    colors: BuildPanelColors,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(Dimensions.cornerRadiusSmall.dp),
                containerColor = colors.popupBackground,
                contentColor = colors.textPrimary,
            ) {
                Text(text = text, style = MaterialTheme.typography.labelMedium)
            }
        },
        state = rememberTooltipState(),
        content = content,
    )
}

internal fun filterGradleTasksForPicker(
    tasks: List<GradleTask>,
    favoriteTasks: List<String>,
    query: String,
): List<GradleTask> {
    val normalizedQuery = query.trim().lowercase()
    return tasks
        .distinctBy { it.path }
        .filter { task ->
            normalizedQuery.isEmpty() ||
                task.path.lowercase().contains(normalizedQuery) ||
                task.name.lowercase().contains(normalizedQuery) ||
                task.group?.lowercase()?.contains(normalizedQuery) == true ||
                task.description?.lowercase()?.contains(normalizedQuery) == true
        }.sortedWith(
            compareBy<GradleTask> { task ->
                val favoriteIndex = favoriteTasks.indexOf(task.path)
                if (favoriteIndex >= 0) favoriteIndex else Int.MAX_VALUE
            }.thenBy { task ->
                val priorityIndex =
                    PRIORITY_TASKS.indexOfFirst { priority ->
                        priority == task.path || priority == task.name
                    }
                if (priorityIndex >= 0) priorityIndex else Int.MAX_VALUE
            }.thenBy { it.group ?: "" }
                .thenBy { it.path },
        ).take(MAX_PICKER_TASKS)
}

internal fun diagnosticSummary(diagnostics: List<GradleDiagnostic>): String {
    val errors = diagnostics.count { it.severity == GradleDiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == GradleDiagnosticSeverity.WARNING }
    val information = diagnostics.count { it.severity == GradleDiagnosticSeverity.INFO }
    return buildList {
        if (errors > 0) add("$errors ${pluralize(errors, "error")}")
        if (warnings > 0) add("$warnings ${pluralize(warnings, "warning")}")
        if (information > 0) add("$information info")
    }.joinToString(" · ")
}

internal fun OutputLine.buildOutputTone(): BuildOutputTone {
    val normalized = text.trimStart()
    return when (type) {
        OutputType.INFO -> {
            BuildOutputTone.INFO
        }

        OutputType.SUCCESS -> {
            BuildOutputTone.SUCCESS
        }

        OutputType.ERROR -> {
            BuildOutputTone.ERROR
        }

        OutputType.STDOUT,
        OutputType.STDERR,
        -> {
            when {
                normalized.looksLikeWarning() -> BuildOutputTone.WARNING
                normalized.looksLikeError() -> BuildOutputTone.ERROR
                else -> BuildOutputTone.NORMAL
            }
        }
    }
}

internal enum class BuildOutputTone {
    NORMAL,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

private fun String.looksLikeWarning(): Boolean =
    startsWith("WARNING:", ignoreCase = true) ||
        startsWith("warning ", ignoreCase = true) ||
        startsWith("w:", ignoreCase = true)

private fun String.looksLikeError(): Boolean =
    startsWith("ERROR:", ignoreCase = true) ||
        startsWith("FAILURE:", ignoreCase = true) ||
        startsWith("e:", ignoreCase = true) ||
        endsWith(" FAILED", ignoreCase = true)

private fun pluralize(
    count: Int,
    noun: String,
): String = if (count == 1) noun else "${noun}s"

private fun formatBuildDuration(durationMs: Long): String =
    if (durationMs < MILLIS_PER_SECOND) {
        "${durationMs}ms"
    } else {
        "${durationMs / MILLIS_PER_SECOND}.${(durationMs % MILLIS_PER_SECOND) / TENTHS_DIVISOR}s"
    }

private fun GradleDiagnosticSeverity.icon(): ImageVector =
    when (this) {
        GradleDiagnosticSeverity.ERROR -> Icons.Default.Error
        GradleDiagnosticSeverity.WARNING -> Icons.Default.Warning
        GradleDiagnosticSeverity.INFO -> Icons.Default.Info
    }

private fun GradleDiagnosticSeverity.color(colors: BuildPanelColors): Color =
    when (this) {
        GradleDiagnosticSeverity.ERROR -> colors.error
        GradleDiagnosticSeverity.WARNING -> colors.warning
        GradleDiagnosticSeverity.INFO -> colors.info
    }

private fun buildPanelColors(): BuildPanelColors =
    BuildPanelColors(
        background = IntelliJColors.background,
        header = IntelliJColors.surfaceElevated,
        elevated = IntelliJColors.surface,
        hover = IntelliJColors.surfaceHover,
        divider = IntelliJColors.divider,
        popupBackground = IntelliJColors.popupBackground,
        popupBorder = IntelliJColors.popupBorder,
        button = IntelliJColors.buttonBackground,
        buttonHover = IntelliJColors.buttonBackgroundHover,
        textPrimary = IntelliJColors.textPrimary,
        textSecondary = IntelliJColors.textSecondary,
        textMuted = IntelliJColors.textMuted,
        textDisabled = IntelliJColors.textDisabled,
        accent = IntelliJColors.accent,
        success = IntelliJColors.success,
        successMuted = IntelliJColors.successMuted,
        warning = IntelliJColors.warning,
        error = IntelliJColors.error,
        info = IntelliJColors.info,
    )

@Immutable
private data class BuildPanelColors(
    val background: Color,
    val header: Color,
    val elevated: Color,
    val hover: Color,
    val divider: Color,
    val popupBackground: Color,
    val popupBorder: Color,
    val button: Color,
    val buttonHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val accent: Color,
    val success: Color,
    val successMuted: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

private val PRIORITY_TASKS =
    listOf(
        ":app:desktop:run",
        "build",
        "test",
        "ktlintCheck",
        "detekt",
        "clean",
        ":app:desktop:packageDistributionForCurrentOS",
    )

private const val MAX_PINNED_TASKS = 5
private const val MAX_PICKER_TASKS = 50
private const val MAX_VISIBLE_DIAGNOSTICS = 5
private const val MILLIS_PER_SECOND = 1_000L
private const val TENTHS_DIVISOR = 100L
