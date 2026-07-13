package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import su.kidoz.jetaprog.app.ui.panels.FileBadge
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.AgentSessionViewModel

private const val CONVERSATION_MAX_WIDTH = 760
private const val RAIL_WIDTH = 264
private val EXAMPLE_PROMPTS =
    listOf(
        "Explain how this project is structured",
        "Add a feature and wire it into the UI",
        "Find and fix a bug",
        "Write tests for the selected file",
    )

/**
 * The full-window agent perspective: conversation flanked by a session/permissions rail.
 *
 * @param viewModel the agent session view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun AgentPerspective(
    viewModel: AgentSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    AgentSurfaceContent(state = state, dispatch = viewModel::dispatch, showRail = true, modifier = modifier)
}

/**
 * The docked agent tool window: the same conversation without the right rail.
 *
 * @param viewModel the agent session view model.
 * @param modifier the layout modifier.
 */
@Composable
public fun AgentToolWindow(
    viewModel: AgentSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    AgentSurfaceContent(state = state, dispatch = viewModel::dispatch, showRail = false, modifier = modifier)
}

@Composable
private fun AgentSurfaceContent(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
    showRail: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { dispatch(AgentIntent.EnsureConnected) }
    Column(modifier = modifier.fillMaxSize().background(IntelliJColors.background)) {
        AgentHeader(state, dispatch)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (state.turns.isEmpty()) {
                    EmptyState(dispatch)
                } else {
                    Conversation(state, dispatch)
                }
            }
            if (showRail) SessionChangesRail(state, dispatch)
        }
        AgentPresenceBar(state, dispatch)
        AgentComposer(state, dispatch)
    }
}

@Composable
private fun AgentHeader(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(IntelliJColors.background)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        AgentAvatar(tileSize = 26, iconSize = 16, cornerRadius = 7)
        Column {
            Text("Agent", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitleFor(state), color = subtitleColor(state), fontSize = 10.sp)
        }
        ModelEffortChip(state)
        Spacer(Modifier.weight(1f))
        HeaderIcon(Icons.Default.History, "History") {}
        HeaderIcon(Icons.Default.Add, "New chat") { dispatch(AgentIntent.NewChat) }
        if (state.docked) {
            HeaderIcon(Icons.Default.OpenInFull, "Expand") { dispatch(AgentIntent.ExpandToPerspective) }
        } else {
            HeaderIcon(Icons.Default.CloseFullscreen, "Dock") { dispatch(AgentIntent.DockToToolWindow) }
        }
        HeaderIcon(Icons.Default.MoreHoriz, "More") {}
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IntelliJColors.divider))
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = IntelliJColors.textSecondary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .clickable(onClick = onClick)
                .padding(Spacing.xxs.dp)
                .size(Dimensions.iconLg.dp),
    )
}

@Composable
private fun ModelEffortChip(state: AgentUiState) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(IntelliJColors.surface)
                .border(1.dp, IntelliJColors.agentCardBorder, RoundedCornerShape(Dimensions.cornerRadius.dp))
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
    ) {
        GradientDot()
        Text(state.model.shortName, color = IntelliJColors.textPrimary, fontSize = 11.sp)
        Text("·", color = IntelliJColors.textMuted, fontSize = 11.sp)
        Text(state.effort.label, color = IntelliJColors.agentEffortText, fontSize = 11.sp)
    }
}

@Composable
private fun GradientDot(diameter: Int = 8) {
    Box(modifier = Modifier.size(diameter.dp).clip(CircleShape).background(agentGradient))
}

@Composable
private fun Conversation(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.lg.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        items(state.turns, key = { it.id }) { turn ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = CONVERSATION_MAX_WIDTH.dp)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl.dp - 2.dp),
                ) {
                    when (turn) {
                        is Turn.User -> UserTurnView(turn)
                        is Turn.Agent -> AgentTurnView(turn, dispatch)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTurnView(turn: Turn.User) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(IntelliJColors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text("You".first().toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text("You", color = IntelliJColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(turn.timeLabel, color = IntelliJColors.textMuted, fontSize = 11.sp)
            }
            Text(turn.text, color = IntelliJColors.textPrimary, fontSize = 13.sp)
            if (turn.context.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp)) {
                    turn.context.forEach { ContextChip(it) }
                }
            }
        }
    }
}

@Composable
private fun ContextChip(fileName: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(IntelliJColors.surface)
                .border(1.dp, IntelliJColors.divider, RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 1.dp),
    ) {
        FileBadge(fileName)
        Text(fileName, color = IntelliJColors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun AgentTurnView(
    turn: Turn.Agent,
    dispatch: (AgentIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            AgentAvatar(tileSize = 24, iconSize = 15, cornerRadius = 12)
            Text("Agent", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(turn.timeLabel, color = IntelliJColors.textMuted, fontSize = 11.sp)
        }
        Column(
            modifier = Modifier.padding(start = 34.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.dp),
        ) {
            turn.blocks.forEach { block -> BlockView(block, dispatch) }
        }
    }
}

@Composable
private fun BlockView(
    block: Block,
    dispatch: (AgentIntent) -> Unit,
) {
    when (block) {
        is Block.Text -> {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = block.text,
                    color = if (block.thought) IntelliJColors.textMuted else IntelliJColors.textPrimary,
                    fontStyle = if (block.thought) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (block.streaming) StreamingCaret()
            }
        }

        is Block.Tool -> {
            ToolCallCard(block) { dispatch(AgentIntent.ToggleToolCall(block.id)) }
        }

        is Block.Diff -> {
            ProposedDiffCard(
                block = block,
                onAccept = { dispatch(AgentIntent.AcceptDiff(block.id)) },
                onReject = { dispatch(AgentIntent.RejectDiff(block.id)) },
            )
        }

        is Block.Approval -> {
            ApprovalCard(
                block = block,
                onApprove = { dispatch(AgentIntent.Approve(block.id)) },
                onDeny = { dispatch(AgentIntent.Deny(block.id)) },
            )
        }
    }
}

@Composable
private fun EmptyState(dispatch: (AgentIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AgentAvatar(tileSize = 48, iconSize = 26)
        Text(
            text = "Ask the agent to build, refactor, or explain your project",
            color = IntelliJColors.textSecondary,
            fontSize = 14.sp,
        )
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EXAMPLE_PROMPTS.forEach { prompt ->
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                            .background(IntelliJColors.surface)
                            .border(1.dp, IntelliJColors.divider, RoundedCornerShape(Dimensions.cornerRadius.dp))
                            .clickable { dispatch(AgentIntent.Send(prompt)) }
                            .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                ) {
                    Text(prompt, color = IntelliJColors.textPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AgentPresenceBar(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val presence = state.presence ?: return
    var elapsed by remember(presence.startedAtEpochMillis) { mutableStateOf(0L) }
    LaunchedEffect(presence.startedAtEpochMillis) {
        while (true) {
            elapsed = (System.currentTimeMillis() - presence.startedAtEpochMillis) / 1000
            delay(1000)
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(IntelliJColors.brandGradientEnd.copy(alpha = 0.10f))
                .padding(horizontal = Spacing.lg.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        PulsingDot()
        Text(presence.action, color = IntelliJColors.editorIdentifier, fontSize = 12.sp)
        presence.fileName?.let { ContextChip(it) }
        Text(
            text = "· ${formatElapsed(elapsed)}",
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        PillButton(
            text = "Stop",
            icon = Icons.Default.Stop,
            background = IntelliJColors.buttonBackground,
            foreground = IntelliJColors.agentStopText,
            onClick = { dispatch(AgentIntent.Stop) },
            iconTint = IntelliJColors.agentStopText,
            bordered = true,
        )
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Box(
        modifier =
            Modifier
                .size(9.dp)
                .alpha(a)
                .clip(CircleShape)
                .background(IntelliJColors.brandGradientEnd),
    )
}

@Composable
private fun AgentComposer(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val input = state.composerInput
    val send: () -> Unit = {
        if (input.isNotBlank()) {
            dispatch(AgentIntent.Send(input.trim()))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IntelliJColors.divider))
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.surface)
                    .border(1.dp, IntelliJColors.border, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .padding(Spacing.md.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(38.dp)) {
                if (input.isEmpty()) {
                    Text(
                        text = "Ask the agent to build a feature, refactor, explain code, or run the project…",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { dispatch(AgentIntent.SetComposerInput(it)) },
                    textStyle =
                        TextStyle(
                            color = IntelliJColors.textPrimary,
                            fontSize = 13.sp,
                            fontFamily = JetaProgFonts.codeFont,
                        ),
                    cursorBrush = SolidColor(IntelliJColors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                ComposerChip(Icons.Default.AlternateEmail, "Add context") {}
                ComposerChip(Icons.Default.Tune, "auto-run reads") {}
                Spacer(Modifier.weight(1f))
                ModelPickerChip(state, dispatch)
                SendButton(
                    enabled = input.isNotBlank() && state.connection != AgentConnection.CONNECTING,
                    onClick = send,
                )
            }
        }
    }
}

@Composable
private fun ComposerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(IntelliJColors.background)
                .border(1.dp, IntelliJColors.divider, RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 1.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(Dimensions.iconSm.dp),
        )
        Text(label, color = IntelliJColors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ModelPickerChip(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.background)
                    .border(1.dp, IntelliJColors.agentCardBorder, RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .clickable { open = true }
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
        ) {
            GradientDot(diameter = 7)
            Text(state.model.shortName, color = IntelliJColors.textPrimary, fontSize = 11.sp)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = IntelliJColors.textMuted,
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AGENT_MODELS.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName, fontSize = 12.sp) },
                    onClick = {
                        dispatch(AgentIntent.SetModel(model, state.effort))
                        open = false
                    },
                )
            }
            Effort.entries.forEach { effort ->
                DropdownMenuItem(
                    text = { Text("Effort: ${effort.label}", fontSize = 12.sp) },
                    onClick = {
                        dispatch(AgentIntent.SetModel(state.model, effort))
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionChangesRail(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(RAIL_WIDTH.dp)
                .fillMaxHeight()
                .background(IntelliJColors.surface)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.xs.dp))
        RailLabel("THIS SESSION")
        if (state.sessionChanges.isEmpty()) {
            Text(
                "No changes yet",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            )
        } else {
            state.sessionChanges.forEach { SessionChangeRow(it) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                PillButton(
                    text = "Accept all",
                    icon = null,
                    background = IntelliJColors.buttonBackground,
                    foreground = IntelliJColors.textPrimary,
                    onClick = { dispatch(AgentIntent.AcceptAll) },
                    bordered = true,
                )
                PillButton(
                    text = "Revert all",
                    icon = null,
                    background = IntelliJColors.surface,
                    foreground = IntelliJColors.textSecondary,
                    onClick = { dispatch(AgentIntent.RevertAll) },
                    bordered = true,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        1.dp,
                    ).padding(horizontal = Spacing.md.dp)
                    .background(IntelliJColors.divider),
        )
        RailLabel("PERMISSIONS")
        PermissionRow(PermissionKind.READ, "Read files", state, dispatch)
        PermissionRow(PermissionKind.EDIT, "Edit files", state, dispatch)
        PermissionRow(PermissionKind.RUN, "Run commands", state, dispatch)
        Spacer(Modifier.height(Spacing.md.dp))
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(
        text = text,
        color = IntelliJColors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
    )
}

@Composable
private fun SessionChangeRow(change: SessionFileChange) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        FileBadge(change.name)
        Text(
            change.name,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (change.added > 0) Text("+${change.added}", color = IntelliJColors.diffAddedGutter, fontSize = 11.sp)
        if (change.removed > 0) Text("−${change.removed}", color = IntelliJColors.diffRemovedText, fontSize = 11.sp)
    }
}

@Composable
private fun PermissionRow(
    kind: PermissionKind,
    label: String,
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val on = state.permissions[kind] == PermissionPolicy.AUTO
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(label, color = IntelliJColors.textPrimary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        PermissionToggle(on) {
            dispatch(AgentIntent.SetPermission(kind, if (on) PermissionPolicy.ASK else PermissionPolicy.AUTO))
        }
    }
}

@Composable
private fun PermissionToggle(
    on: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(34.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (on) IntelliJColors.success else IntelliJColors.divider)
                .clickable(onClick = onToggle),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White),
        )
    }
}

private fun subtitleFor(state: AgentUiState): String =
    when (state.connection) {
        AgentConnection.CONNECTED -> "MCP · connected to workspace"
        AgentConnection.CONNECTING -> "MCP · connecting…"
        AgentConnection.ERROR -> state.error ?: "MCP · disconnected"
        AgentConnection.DISCONNECTED -> "MCP · idle"
    }

private fun subtitleColor(state: AgentUiState): Color =
    if (state.connection == AgentConnection.ERROR) IntelliJColors.error else IntelliJColors.textMuted

private fun formatElapsed(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
