package su.kidoz.jetaprog.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.PopupChromeMenu
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.dialogs.IntelliJDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** The left debug tool window: Frames (thread + call stack) over Breakpoints. */
@Composable
public fun DebugSidePanel(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().background(LocalIntelliJColors.current.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Frames", color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            DebugStateChip(state.status)
        }
        if (state.threads.isNotEmpty()) ThreadSelector(state, dispatch)
        Box(modifier = Modifier.weight(1f)) { CallStack(state, dispatch) }
        BreakpointsSection(state, dispatch)
    }
}

@Composable
private fun DebugStateChip(status: DebugStatus) {
    val (label, dot, text, bg) =
        when (status) {
            DebugStatus.PAUSED -> {
                StateChipColors(
                    "PAUSED",
                    LocalIntelliJColors.current.warning,
                    LocalIntelliJColors.current.debugPausedText,
                    LocalIntelliJColors.current.warning.copy(alpha = 0.16f),
                )
            }

            DebugStatus.RUNNING -> {
                StateChipColors(
                    "RUNNING",
                    LocalIntelliJColors.current.success,
                    LocalIntelliJColors.current.debugRunningText,
                    LocalIntelliJColors.current.success.copy(alpha = 0.16f),
                )
            }

            DebugStatus.TERMINATED -> {
                StateChipColors(
                    "IDLE",
                    LocalIntelliJColors.current.textMuted,
                    LocalIntelliJColors.current.textMuted,
                    LocalIntelliJColors.current.surfaceElevated,
                )
            }
        }
    Row(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(Dimensions.cornerRadiusSmall.dp),
                ).background(bg)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 1.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label, color = text, fontSize = 10.sp)
    }
}

private data class StateChipColors(
    val label: String,
    val dot: Color,
    val text: Color,
    val bg: Color,
)

@Composable
private fun ThreadSelector(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    val selected =
        state.threads.firstOrNull { it.id == state.selectedThreadId } ?: state.threads.firstOrNull()
    Box(
        modifier = Modifier.onSizeChanged { anchorHeightPx = it.height },
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = Spacing.xs.dp)
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(LocalIntelliJColors.current.background)
                    .border(
                        1.dp,
                        LocalIntelliJColors.current.divider,
                        RoundedCornerShape(Dimensions.cornerRadius.dp),
                    ).clickable(enabled = state.threads.size > 1) { expanded = true }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = LocalIntelliJColors.current.iconFile,
                modifier = Modifier.size(15.dp),
            )
            Text(
                selected?.name ?: "No threads",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
        PopupChromeMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offsetY = anchorHeightPx,
        ) {
            state.threads.forEach { thread ->
                PopupListRow(
                    selected = thread.id == selected?.id,
                    onClick = {
                        dispatch(DebugIntent.SelectThread(thread.id))
                        expanded = false
                    },
                ) {
                    Text(
                        thread.name,
                        color = LocalIntelliJColors.current.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallStack(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
) {
    if (state.frames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(Spacing.md.dp)) {
            Text(
                if (state.status == DebugStatus.RUNNING) "Running…" else "Not paused",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.frames, key = { it.id }) { frame ->
            Box {
                if (frame.isCurrent) {
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(LocalIntelliJColors.current.warning))
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(
                                if (frame.isCurrent) {
                                    LocalIntelliJColors.current.warning.copy(
                                        alpha = 0.10f,
                                    )
                                } else {
                                    Color.Transparent
                                },
                            ).clickable { dispatch(DebugIntent.SelectFrame(frame.id)) }
                            .padding(horizontal = Spacing.md.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                ) {
                    Icon(
                        imageVector =
                            if (frame.isCurrent) {
                                Icons.AutoMirrored.Filled.ArrowRightAlt
                            } else {
                                Icons.Default.SubdirectoryArrowRight
                            },
                        contentDescription = null,
                        tint =
                            if (frame.isCurrent) {
                                LocalIntelliJColors.current.warning
                            } else {
                                LocalIntelliJColors.current.textMuted
                            },
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        frame.method,
                        color = if (frame.isCurrent) Color.White else LocalIntelliJColors.current.textPrimary,
                        fontSize = 12.5.sp,
                    )
                    Text(
                        frame.location,
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakpointsSection(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().height(172.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalIntelliJColors.current.divider))
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Breakpoints", color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.DoNotDisturbOn,
                contentDescription = null,
                tint = LocalIntelliJColors.current.activityBarForeground,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
            Spacer(Modifier.width(Spacing.sm.dp))
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = LocalIntelliJColors.current.activityBarForeground,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
        if (state.breakpoints.isEmpty()) {
            Text(
                "No breakpoints. Click the gutter to add one.",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.breakpoints, key = { it.id }) { bp -> BreakpointRow(bp, dispatch) }
            }
        }
    }
}

@Composable
private fun BreakpointRow(
    bp: BreakpointView,
    dispatch: (DebugIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp + 1.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(if (bp.enabled) LocalIntelliJColors.current.breakpointRed else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (bp.enabled) {
                            LocalIntelliJColors.current.breakpointRed
                        } else {
                            LocalIntelliJColors.current.textMuted
                        },
                        CircleShape,
                    ).clickable { dispatch(DebugIntent.SetBreakpointEnabled(bp.id, !bp.enabled)) },
        )
        Column {
            Text(
                bp.where,
                color =
                    if (bp.enabled) {
                        LocalIntelliJColors.current.textPrimary
                    } else {
                        LocalIntelliJColors.current.activityBarForeground
                    },
                fontSize = 12.sp,
                maxLines = 1,
            )
            bp.condition?.let { Text("if $it", color = LocalIntelliJColors.current.debugVarName, fontSize = 11.sp) }
        }
    }
}

/** The bottom debug panel body: step toolbar over Variables + Watches. */
@Composable
public fun DebugBottomContent(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
    onRestart: () -> Unit,
    onShowBreakpoints: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DebugToolbar(state, dispatch, onRestart, onShowBreakpoints)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { VariablesTree(state, dispatch) }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(LocalIntelliJColors.current.divider))
            Box(modifier = Modifier.width(300.dp).fillMaxHeight()) { WatchesPanel(state, dispatch) }
        }
    }
}

@Composable
private fun DebugToolbar(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
    onRestart: () -> Unit,
    onShowBreakpoints: () -> Unit,
) {
    val paused = state.status == DebugStatus.PAUSED
    val running = state.status == DebugStatus.RUNNING
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ToolbarButton(Icons.Default.PlayArrow, "Resume", enabled = paused, tint = LocalIntelliJColors.current.success) {
            dispatch(DebugIntent.Resume)
        }
        ToolbarButton(
            Icons.Default.Pause,
            "Pause",
            enabled = running,
            tint = LocalIntelliJColors.current.textSecondary,
        ) {
            dispatch(DebugIntent.Pause)
        }
        ToolbarButton(
            Icons.Default.Stop,
            "Stop",
            enabled = state.hasSession,
            tint = LocalIntelliJColors.current.error,
        ) {
            dispatch(DebugIntent.Stop)
        }
        ToolbarButton(
            Icons.Default.RestartAlt,
            "Restart",
            enabled = state.hasSession,
            tint = LocalIntelliJColors.current.textSecondary,
        ) { onRestart() }
        ToolbarDivider()
        ToolbarButton(
            Icons.AutoMirrored.Filled.ArrowForward,
            "Step over",
            enabled = paused,
        ) { dispatch(DebugIntent.StepOver) }
        ToolbarButton(Icons.Default.ArrowDownward, "Step into", enabled = paused) { dispatch(DebugIntent.StepInto) }
        ToolbarButton(Icons.Default.ArrowUpward, "Step out", enabled = paused) { dispatch(DebugIntent.StepOut) }
        ToolbarDivider()
        ToolbarButton(
            Icons.Default.DoNotDisturbOn,
            if (state.breakpointsMuted) "Unmute breakpoints" else "Mute breakpoints",
            enabled = state.breakpoints.isNotEmpty(),
            tint =
                if (state.breakpointsMuted) {
                    LocalIntelliJColors.current.warning
                } else {
                    LocalIntelliJColors.current.textPrimary
                },
        ) { dispatch(DebugIntent.SetBreakpointsMuted(!state.breakpointsMuted)) }
        ToolbarButton(
            Icons.AutoMirrored.Filled.FormatListBulleted,
            "View breakpoints",
            enabled = state.breakpoints.isNotEmpty(),
        ) { onShowBreakpoints() }
        Spacer(Modifier.weight(1f))
        val statusText =
            when {
                paused -> state.stoppedAt?.let { "Paused · ${it.path.substringAfterLast('/')}:${it.line}" } ?: "Paused"
                running -> "Running"
                else -> ""
            }
        Text(statusText, color = LocalIntelliJColors.current.debugPausedText, fontSize = 11.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalIntelliJColors.current.divider))
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = LocalIntelliJColors.current.textPrimary,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (enabled) tint else LocalIntelliJColors.current.scrollbarThumb,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(Spacing.xs.dp)
                .size(Dimensions.iconLg.dp),
    )
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier =
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(16.dp)
                .background(LocalIntelliJColors.current.divider),
    )
}

@Composable
private fun VariablesTree(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel("VARIABLES")
        if (state.status != DebugStatus.PAUSED) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg.dp, vertical = Spacing.md.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = LocalIntelliJColors.current.textMuted,
                    modifier = Modifier.size(Dimensions.iconMd.dp),
                )
                Text(
                    "Variables are unavailable while the program is running.",
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 12.5.sp,
                )
            }
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.variables, key = { "${it.depth}:${it.name}:${it.reference}" }) { v -> VariableRow(v, dispatch) }
        }
    }
}

@Composable
private fun VariableRow(
    v: VarView,
    dispatch: (DebugIntent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(23.dp)
                .clickable(enabled = v.expandable) { dispatch(DebugIntent.ToggleVariable(v.reference)) }
                .padding(start = (v.depth * 16 + 8).dp, end = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (v.expandable) {
                Icon(
                    imageVector =
                        if (v.expanded) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                    contentDescription = null,
                    tint = LocalIntelliJColors.current.textSecondary,
                    modifier = Modifier.size(Dimensions.iconMd.dp),
                )
            }
        }
        Icon(
            imageVector = varIcon(v.kind),
            contentDescription = null,
            tint = varIconTint(v.kind),
            modifier = Modifier.padding(end = 6.dp).size(14.dp),
        )
        Text(
            v.name,
            color = LocalIntelliJColors.current.debugVarName,
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
        )
        Text(
            " = ",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
        )
        Text(
            v.value,
            color = varValueColor(v.kind),
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
        )
    }
}

@Composable
private fun WatchesPanel(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel("WATCHES")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.watches, key = { it.id }) { w -> WatchRow(w, dispatch) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add watch",
                tint = LocalIntelliJColors.current.activityBarForeground,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text(
                        "Add expression…",
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 12.sp,
                        fontFamily = JetaProgFonts.codeFont,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = LocalIntelliJColors.current.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = JetaProgFonts.codeFont,
                        ),
                    cursorBrush = SolidColor(LocalIntelliJColors.current.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (input.isNotBlank()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Confirm",
                    tint = LocalIntelliJColors.current.accent,
                    modifier =
                        Modifier
                            .clickable {
                                dispatch(DebugIntent.AddWatch(input.trim()))
                                input = ""
                            }.size(Dimensions.iconMd.dp),
                )
            }
        }
    }
}

@Composable
private fun WatchRow(
    w: WatchView,
    dispatch: (DebugIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(23.dp).padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Visibility,
            contentDescription = null,
            tint = LocalIntelliJColors.current.iconFile,
            modifier = Modifier.padding(end = 6.dp).size(14.dp),
        )
        Text(w.expr, color = LocalIntelliJColors.current.info, fontSize = 12.5.sp, fontFamily = JetaProgFonts.codeFont)
        Text(
            " = ",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
        )
        Text(
            w.value,
            color = if (w.error) LocalIntelliJColors.current.error else LocalIntelliJColors.current.debugVarNumber,
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove watch",
            tint = LocalIntelliJColors.current.textMuted,
            modifier = Modifier.clickable { dispatch(DebugIntent.RemoveWatch(w.id)) }.size(Dimensions.iconSm.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = Spacing.md.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = LocalIntelliJColors.current.textMuted, fontSize = 11.sp)
    }
}

private fun varIcon(kind: VarKind): ImageVector =
    when (kind) {
        VarKind.OBJECT -> Icons.Default.DataObject
        VarKind.ARRAY -> Icons.Default.DataArray
        VarKind.STRING, VarKind.NUMBER, VarKind.PRIMITIVE -> Icons.Default.Tag
    }

@Composable
private fun varIconTint(kind: VarKind): Color =
    when (kind) {
        VarKind.OBJECT -> LocalIntelliJColors.current.iconFolder
        VarKind.ARRAY -> LocalIntelliJColors.current.iconFile
        else -> LocalIntelliJColors.current.iconFile
    }

@Composable
private fun varValueColor(kind: VarKind): Color =
    when (kind) {
        VarKind.STRING -> LocalIntelliJColors.current.debugVarString
        VarKind.NUMBER -> LocalIntelliJColors.current.debugVarNumber
        VarKind.OBJECT, VarKind.ARRAY -> LocalIntelliJColors.current.textSecondary
        VarKind.PRIMITIVE -> LocalIntelliJColors.current.debugVarType
    }

/**
 * Dialog listing every project-level breakpoint with its verified state and an
 * enable checkbox, mirroring IntelliJ's breakpoints view.
 */
@Composable
public fun BreakpointsDialog(
    state: DebugUiState,
    dispatch: (DebugIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntelliJDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        minWidth = 420.dp,
    ) {
        Column {
            Text(
                text = "Breakpoints",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg.dp, vertical = Spacing.md.dp),
            )
            HorizontalDivider(color = LocalIntelliJColors.current.border)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(horizontal = Spacing.lg.dp, vertical = Spacing.md.dp),
            ) {
                items(state.breakpoints, key = { it.id }) { bp ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                    ) {
                        IntelliJCheckbox(
                            checked = bp.enabled,
                            onCheckedChange = { enabled ->
                                dispatch(DebugIntent.SetBreakpointEnabled(bp.id, enabled))
                            },
                        )
                        Column {
                            Text(
                                text = "${bp.file.substringAfterLast('/')}:${bp.line}",
                                color = LocalIntelliJColors.current.textPrimary,
                                fontSize = 12.sp,
                            )
                            if (bp.condition != null) {
                                Text(
                                    text = "if ${bp.condition}",
                                    color = LocalIntelliJColors.current.debugVarName,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (bp.verified) "verified" else "not verified",
                            color =
                                if (bp.verified) {
                                    LocalIntelliJColors.current.success
                                } else {
                                    LocalIntelliJColors.current.textMuted
                                },
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
