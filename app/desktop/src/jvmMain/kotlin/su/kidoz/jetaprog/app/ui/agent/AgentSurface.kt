package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.PopupChromeMenu
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.popupChrome
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.panels.FileBadge
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.AgentSessionViewModel

private const val CONVERSATION_MAX_WIDTH = 760
private const val RAIL_WIDTH = 264
private const val COMPOSER_MAX_LINES = 8
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
 * @param checkoutLabel Label of the other checkout, such as Worktree or Main. Null hides the switch.
 * @param onSwitchCheckout Shows the other checkout without stopping this one.
 * @param layoutDocked The docked flag that controls the window, when it lives on another session.
 * @param mirrorLayout Receives dock and expand so the window follows the main session.
 */
@Composable
public fun AgentPerspective(
    viewModel: AgentSessionViewModel,
    modifier: Modifier = Modifier,
    checkoutLabel: String? = null,
    onSwitchCheckout: (() -> Unit)? = null,
    layoutDocked: Boolean? = null,
    mirrorLayout: ((AgentIntent) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    AgentSurfaceContent(
        state = state,
        dispatch = viewModel::dispatch,
        showRail = true,
        modifier = modifier,
        checkoutLabel = checkoutLabel,
        onSwitchCheckout = onSwitchCheckout,
        layoutDocked = layoutDocked,
        mirrorLayout = mirrorLayout,
    )
}

/**
 * The docked agent tool window: the same conversation without the right rail.
 *
 * @param viewModel the agent session view model.
 * @param modifier the layout modifier.
 * @param checkoutLabel Label of the other checkout, such as Worktree or Main. Null hides the switch.
 * @param onSwitchCheckout Shows the other checkout without stopping this one.
 * @param layoutDocked The docked flag that controls the window, when it lives on another session.
 * @param mirrorLayout Receives dock and expand so the window follows the main session.
 */
@Composable
public fun AgentToolWindow(
    viewModel: AgentSessionViewModel,
    modifier: Modifier = Modifier,
    checkoutLabel: String? = null,
    onSwitchCheckout: (() -> Unit)? = null,
    layoutDocked: Boolean? = null,
    mirrorLayout: ((AgentIntent) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    AgentSurfaceContent(
        state = state,
        dispatch = viewModel::dispatch,
        showRail = false,
        modifier = modifier,
        checkoutLabel = checkoutLabel,
        onSwitchCheckout = onSwitchCheckout,
        layoutDocked = layoutDocked,
        mirrorLayout = mirrorLayout,
    )
}

@Composable
internal fun AgentSurfaceContent(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
    showRail: Boolean,
    modifier: Modifier = Modifier,
    checkoutLabel: String? = null,
    onSwitchCheckout: (() -> Unit)? = null,
    layoutDocked: Boolean? = null,
    mirrorLayout: ((AgentIntent) -> Unit)? = null,
) {
    LaunchedEffect(Unit) { dispatch(AgentIntent.EnsureConnected) }
    Column(modifier = modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
        AgentHeader(state, dispatch, checkoutLabel, onSwitchCheckout, layoutDocked, mirrorLayout)
        if (state.connection == AgentConnection.ERROR) {
            AgentConnectionError(state, dispatch)
        } else if (state.error != null) {
            AgentStatusMessage(state.error.orEmpty())
        }
        state.restoration?.let { AgentRestorationNotice(it) }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val goal = state.goal
                if (goal != null) {
                    GoalCard(
                        goal = goal,
                        onPause = { dispatch(AgentIntent.PauseGoal) },
                        onResume = { dispatch(AgentIntent.ResumeGoal) },
                        onReplace = { dispatch(AgentIntent.ReplaceGoal) },
                        onClear = { dispatch(AgentIntent.ClearGoal) },
                        modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                    )
                }
                val goalNotice = state.goalNotice
                if (!goalNotice.isNullOrBlank()) AgentStatusMessage(goalNotice)
                if (state.reviewVisible) {
                    ReviewCard(
                        files = reviewFiles(state.turns),
                        onAccept = { key -> dispatch(AgentIntent.AcceptReviewFile(key)) },
                        onDismiss = { key -> dispatch(AgentIntent.DismissReviewFile(key)) },
                        onClose = { dispatch(AgentIntent.HideReview) },
                        modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                    )
                }
                val reviewNotice = state.reviewNotice
                if (!reviewNotice.isNullOrBlank()) AgentStatusMessage(reviewNotice)
                val worktreeNotice = state.worktreeNotice
                if (!worktreeNotice.isNullOrBlank()) AgentStatusMessage(worktreeNotice)
                if (state.plan.isNotEmpty()) {
                    PlanCard(
                        steps = state.plan,
                        modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.turns.isEmpty()) {
                        EmptyState(dispatch)
                    } else {
                        Conversation(state, dispatch)
                    }
                }
            }
            if (showRail) SessionChangesRail(state, dispatch)
        }
        AgentPresenceBar(state, dispatch)
        AgentComposer(state, dispatch)
    }
}

@Composable
private fun AgentConnectionError(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(LocalIntelliJColors.current.surface)
                .padding(Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
    ) {
        Text(
            text =
                "Could not connect to the agent. Check the agent command and sign-in, then retry. " +
                    state.error.orEmpty(),
            color = LocalIntelliJColors.current.textSecondary,
            modifier = Modifier.weight(1f),
        )
        IntelliJButton("Retry", onClick = { dispatch(AgentIntent.EnsureConnected) })
    }
}

@Composable
private fun AgentStatusMessage(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(LocalIntelliJColors.current.surface)
                .padding(Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = LocalIntelliJColors.current.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AgentRestorationNotice(restoration: SessionRestoration) {
    val message =
        when (restoration) {
            SessionRestoration.Loading -> {
                "Restoring conversation…"
            }

            SessionRestoration.Resumed -> {
                "Conversation resumed. The agent has the previous context."
            }

            is SessionRestoration.TranscriptOnly -> {
                "Transcript only. ${restoration.reason} Your next message starts a new chat."
            }
        }
    AgentStatusMessage(message)
}

@Composable
private fun AgentHeader(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
    checkoutLabel: String?,
    onSwitchCheckout: (() -> Unit)?,
    layoutDocked: Boolean?,
    mirrorLayout: ((AgentIntent) -> Unit)?,
) {
    var headerHeightPx by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<SessionListRow?>(null) }
    Box(
        modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(LocalIntelliJColors.current.background)
                    .padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            AgentAvatar(tileSize = 26, iconSize = 16, cornerRadius = 7)
            Column {
                Text(
                    "Agent",
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(subtitleFor(state), color = subtitleColor(state), fontSize = 10.sp)
            }
            ModelChip(state)
            if (checkoutLabel != null && onSwitchCheckout != null) {
                CheckoutChip(checkoutLabel, onSwitchCheckout)
            }
            Spacer(Modifier.weight(1f))
            HeaderIcon(Icons.Default.History, "History") { dispatch(AgentIntent.ShowHistory) }
            HeaderIcon(Icons.Default.Add, "New chat") { dispatch(AgentIntent.NewChat) }
            val docked = layoutDocked ?: state.docked
            if (docked) {
                HeaderIcon(Icons.Default.OpenInFull, "Expand") {
                    dispatch(AgentIntent.ExpandToPerspective)
                    mirrorLayout?.invoke(AgentIntent.ExpandToPerspective)
                }
            } else {
                HeaderIcon(Icons.Default.CloseFullscreen, "Dock") {
                    dispatch(AgentIntent.DockToToolWindow)
                    mirrorLayout?.invoke(AgentIntent.DockToToolWindow)
                }
            }
        }
        if (state.historyVisible) {
            AgentHistoryPopup(
                rows =
                    sessionListRows(
                        history = state.sessionHistory,
                        activeRecordId = state.activeRecordId,
                        turns = state.turns,
                        isStreaming = state.isStreaming,
                        error = state.error,
                        now = System.currentTimeMillis(),
                    ),
                offsetY = headerHeightPx,
                onRestore = { id ->
                    dispatch(AgentIntent.RestoreSession(id))
                    dispatch(AgentIntent.HideHistory)
                },
                onDeleteRequest = { pendingDelete = it },
                onDismiss = { dispatch(AgentIntent.HideHistory) },
            )
        }
    }
    if (pendingDelete != null) {
        val record = pendingDelete
        if (record != null) {
            // Inline overlay (not a secondary Dialog window) so the confirm
            // stays part of the main composition — and semantics tree.
            DialogOverlay(isVisible = true, onDismiss = { pendingDelete = null }) {
                DialogContainer(modifier = Modifier.width(420.dp)) {
                    Column(modifier = Modifier.padding(Spacing.lg.dp)) {
                        Text(
                            text = "Delete session?",
                            color = LocalIntelliJColors.current.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(Spacing.sm.dp))
                        Text(
                            text = "Delete \"${record.title}\"? This cannot be undone.",
                            color = LocalIntelliJColors.current.textSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(Spacing.lg.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IntelliJButton(
                                text = "Cancel",
                                onClick = { pendingDelete = null },
                                style = ButtonStyle.SECONDARY,
                            )
                            Spacer(Modifier.width(Spacing.sm.dp))
                            IntelliJButton(
                                text = "Delete",
                                onClick = {
                                    dispatch(AgentIntent.DeleteSession(record.id))
                                    pendingDelete = null
                                },
                                style = ButtonStyle.DANGER,
                            )
                        }
                    }
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalIntelliJColors.current.divider))
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
        tint = LocalIntelliJColors.current.textSecondary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .clickable(onClick = onClick)
                .padding(Spacing.xxs.dp)
                .size(Dimensions.iconLg.dp),
    )
}

@Composable
private fun CheckoutChip(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = LocalIntelliJColors.current.accent,
        fontSize = 11.sp,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .border(
                    1.dp,
                    LocalIntelliJColors.current.agentCardBorder,
                    RoundedCornerShape(Dimensions.cornerRadius.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
    )
}

@Composable
private fun ModelChip(state: AgentUiState) {
    val model = state.model ?: return
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(LocalIntelliJColors.current.surface)
                .border(
                    1.dp,
                    LocalIntelliJColors.current.agentCardBorder,
                    RoundedCornerShape(Dimensions.cornerRadius.dp),
                ).padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
    ) {
        GradientDot()
        Text(model.shortName, color = LocalIntelliJColors.current.textPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun GradientDot(diameter: Int = 8) {
    Box(modifier = Modifier.size(diameter.dp).clip(CircleShape).background(agentGradient()))
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
                        is Turn.User -> UserTurnView(turn, state.isStreaming, dispatch)
                        is Turn.Agent -> AgentTurnView(turn, dispatch)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTurnView(
    turn: Turn.User,
    streaming: Boolean,
    dispatch: (AgentIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(LocalIntelliJColors.current.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text("You".first().toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text(
                    "You",
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(turn.timeLabel, color = LocalIntelliJColors.current.textMuted, fontSize = 11.sp)
                if (turn.canRestore && !streaming) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Restore",
                        color = LocalIntelliJColors.current.accent,
                        fontSize = 11.sp,
                        modifier =
                            Modifier
                                .semantics { contentDescription = "Restore checkpoint" }
                                .clickable { dispatch(AgentIntent.RestoreCheckpoint(turn.id)) },
                    )
                }
            }
            Text(turn.text, color = LocalIntelliJColors.current.textPrimary, fontSize = 13.sp)
            turn.restoreNotice?.let { notice ->
                Text(notice, color = LocalIntelliJColors.current.textMuted, fontSize = 11.sp)
            }
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
                .background(LocalIntelliJColors.current.surface)
                .border(1.dp, LocalIntelliJColors.current.divider, RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 1.dp),
    ) {
        FileBadge(fileName)
        Text(fileName, color = LocalIntelliJColors.current.textSecondary, fontSize = 11.sp)
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
            Text(
                "Agent",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(turn.timeLabel, color = LocalIntelliJColors.current.textMuted, fontSize = 11.sp)
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
                    color =
                        if (block.thought) {
                            LocalIntelliJColors.current.textMuted
                        } else {
                            LocalIntelliJColors.current.textPrimary
                        },
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
                onOpen = { dispatch(AgentIntent.OpenDiff(block.id)) },
            )
        }

        is Block.Approval -> {
            ApprovalCard(block) { optionId ->
                dispatch(AgentIntent.SelectApproval(block.id, optionId))
            }
        }
    }
}

@Composable
private fun EmptyState(dispatch: (AgentIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AgentAvatar(tileSize = 48, iconSize = 26)
        Text(
            text = "Ask the agent to build, refactor, or explain your project",
            color = LocalIntelliJColors.current.textSecondary,
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
                            .background(LocalIntelliJColors.current.surface)
                            .border(
                                1.dp,
                                LocalIntelliJColors.current.divider,
                                RoundedCornerShape(Dimensions.cornerRadius.dp),
                            ).clickable { dispatch(AgentIntent.Send(prompt)) }
                            .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                ) {
                    Text(prompt, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
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
                .background(LocalIntelliJColors.current.brandGradientEnd.copy(alpha = 0.10f))
                .padding(horizontal = Spacing.lg.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        PulsingDot()
        Text(presence.action, color = LocalIntelliJColors.current.editorIdentifier, fontSize = 12.sp)
        presence.fileName?.let { ContextChip(it) }
        Text(
            text = "· ${formatElapsed(elapsed)}",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        PillButton(
            text = "Stop",
            icon = Icons.Default.Stop,
            background = LocalIntelliJColors.current.buttonBackground,
            foreground = LocalIntelliJColors.current.agentStopText,
            onClick = { dispatch(AgentIntent.Stop) },
            iconTint = LocalIntelliJColors.current.agentStopText,
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
                .background(LocalIntelliJColors.current.brandGradientEnd),
    )
}

@Composable
private fun AgentComposer(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val input = state.composerInput
    val canSubmit = input.isNotBlank() && state.restoration != SessionRestoration.Loading
    val send: () -> Unit = {
        if (canSubmit) dispatch(AgentIntent.Send(input.trim()))
    }
    val steer: () -> Unit = {
        if (canSubmit) dispatch(AgentIntent.Steer(input.trim()))
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalIntelliJColors.current.divider))
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(LocalIntelliJColors.current.surface)
                    .border(
                        1.dp,
                        LocalIntelliJColors.current.border,
                        RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
                    ).padding(Spacing.md.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            state.queuedPrompt?.let { QueuedPromptChip(it, dispatch) }
            if (state.composerAttachments.isNotEmpty()) {
                AttachmentRow(state.composerAttachments, dispatch)
            }
            if (mentionQuery(input) != null) {
                MentionMenu(state.mentionFiles, dispatch)
            } else {
                SlashCommandMenu(input, slashCommands(state.commands), dispatch)
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                if (input.isEmpty()) {
                    Text(
                        text = "Ask the agent to build a feature, refactor, explain code, or run the project…",
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { dispatch(AgentIntent.SetComposerInput(it)) },
                    textStyle =
                        TextStyle(
                            color = LocalIntelliJColors.current.textPrimary,
                            fontSize = 13.sp,
                            fontFamily = JetaProgFonts.codeFont,
                        ),
                    cursorBrush = SolidColor(LocalIntelliJColors.current.accent),
                    singleLine = false,
                    maxLines = COMPOSER_MAX_LINES,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimensions.inputHeight.dp)
                            .onPreviewKeyEvent { event ->
                                val nextMode = nextCycledModeId(state.mode?.id, state.availableModes)
                                val cycleMode =
                                    nextMode != null &&
                                        event.key == Key.Tab &&
                                        event.isShiftPressed &&
                                        !event.isCtrlPressed &&
                                        !event.isMetaPressed &&
                                        !event.isAltPressed
                                if (cycleMode) {
                                    if (event.type == KeyEventType.KeyDown) dispatch(AgentIntent.SetMode(nextMode))
                                    true
                                } else if (event.key != Key.Enter || event.isShiftPressed) {
                                    false
                                } else {
                                    if (event.type == KeyEventType.KeyDown) {
                                        if (event.isCtrlPressed || event.isMetaPressed) steer() else send()
                                    }
                                    true
                                }
                            },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                if (state.mode != null) {
                    ModePickerChip(state, dispatch)
                }
                Spacer(Modifier.weight(1f))
                if (state.availableModels.isNotEmpty()) {
                    ModelPickerChip(state, dispatch)
                }
                SendButton(
                    enabled =
                        input.isNotBlank() && state.connection != AgentConnection.CONNECTING &&
                            state.restoration != SessionRestoration.Loading,
                    onClick = send,
                )
            }
        }
    }
}

@Composable
private fun SlashCommandMenu(
    input: String,
    commands: List<AgentCommand>,
    dispatch: (AgentIntent) -> Unit,
) {
    val query = slashCommandQuery(input) ?: return
    val matches = commands.filter { it.name.startsWith(query, ignoreCase = true) }
    if (matches.isEmpty()) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = Dimensions.popupListMaxHeight.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        matches.forEach { command ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimensions.popupRowHeightCompact.dp)
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .clickable { dispatch(AgentIntent.SetComposerInput("/${command.name} ")) }
                        .padding(horizontal = Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text(
                    text = "/${command.name}",
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 12.sp,
                )
                val description = command.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionMenu(
    files: List<MentionMatch>,
    dispatch: (AgentIntent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = Dimensions.popupListMaxHeight.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        MentionRow(title = "Current file", detail = "Attach the open file") {
            dispatch(AgentIntent.AttachCurrentFile)
        }
        MentionRow(title = "Current selection", detail = "Attach the selected text") {
            dispatch(AgentIntent.AttachCurrentSelection)
        }
        files.forEach { file ->
            MentionRow(title = file.label, detail = file.path) {
                dispatch(AgentIntent.AttachMention(path = file.path, label = file.label))
            }
        }
    }
}

@Composable
private fun MentionRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.popupRowHeightCompact.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(title, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp, maxLines = 1)
        Text(
            text = detail,
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttachmentRow(
    attachments: List<ComposerAttachment>,
    dispatch: (AgentIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(attachment) {
                dispatch(
                    AgentIntent.RemoveAttachment(
                        path = attachment.path,
                        selection = attachment.excerpt != null,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
) {
    val name = chipLabel(attachment)
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(LocalIntelliJColors.current.background)
                .border(
                    1.dp,
                    LocalIntelliJColors.current.divider,
                    RoundedCornerShape(Dimensions.cornerRadiusSmall.dp),
                ).padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        FileBadge(attachment.label)
        Text(name, color = LocalIntelliJColors.current.textSecondary, fontSize = 11.sp, maxLines = 1)
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove $name",
            tint = LocalIntelliJColors.current.textMuted,
            modifier = Modifier.size(Dimensions.iconSm.dp).clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun QueuedPromptChip(
    queued: QueuedFollowUp,
    dispatch: (AgentIntent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(LocalIntelliJColors.current.background)
                .border(
                    1.dp,
                    LocalIntelliJColors.current.agentCardBorder,
                    RoundedCornerShape(Dimensions.cornerRadiusSmall.dp),
                ).padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = if (queued.steer) "Steering" else "Queued",
            color = if (queued.steer) LocalIntelliJColors.current.accent else LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
        )
        Text(
            text = queued.prompt,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove queued prompt",
            tint = LocalIntelliJColors.current.textMuted,
            modifier =
                Modifier
                    .size(Dimensions.iconSm.dp)
                    .clickable { dispatch(AgentIntent.RemoveQueuedPrompt) },
        )
    }
}

@Composable
private fun ModePickerChip(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val mode = state.mode ?: return
    var open by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    val canSwitch = state.availableModes.size > 1
    Box(modifier = Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(LocalIntelliJColors.current.background)
                    .border(
                        1.dp,
                        LocalIntelliJColors.current.agentCardBorder,
                        RoundedCornerShape(Dimensions.cornerRadius.dp),
                    ).clickable(enabled = canSwitch) { open = true }
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
        ) {
            Text(mode.name, color = LocalIntelliJColors.current.textPrimary, fontSize = 11.sp)
            if (canSwitch) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Session mode",
                    tint = LocalIntelliJColors.current.textMuted,
                    modifier = Modifier.size(Dimensions.iconSm.dp),
                )
            }
        }
        PopupChromeMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offsetY = anchorHeightPx,
        ) {
            state.availableModes.forEach { option ->
                PopupListRow(
                    selected = option.id == mode.id,
                    height = Dimensions.popupRowHeightCompact.dp,
                    onClick = {
                        dispatch(AgentIntent.SetMode(option.id))
                        open = false
                    },
                ) {
                    Text(option.name, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelPickerChip(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val model = state.model ?: return
    var open by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    Box(modifier = Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(LocalIntelliJColors.current.background)
                    .border(
                        1.dp,
                        LocalIntelliJColors.current.agentCardBorder,
                        RoundedCornerShape(Dimensions.cornerRadius.dp),
                    ).clickable { open = true }
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
        ) {
            GradientDot(diameter = 7)
            Text(model.shortName, color = LocalIntelliJColors.current.textPrimary, fontSize = 11.sp)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorHeightPx),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(Dimensions.menuWidth.dp)
                            .popupChrome(Dimensions.cornerRadius.dp)
                            .heightIn(max = Dimensions.popupListMaxHeight.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = Spacing.xs.dp),
                ) {
                    state.availableModels.forEach { option ->
                        PopupListRow(
                            selected = option.id == model.id,
                            height = Dimensions.popupRowHeightCompact.dp,
                            onClick = {
                                dispatch(AgentIntent.SetModel(option.id))
                                open = false
                            },
                        ) {
                            Text(
                                option.displayName,
                                color = LocalIntelliJColors.current.textPrimary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
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
                .background(LocalIntelliJColors.current.surface)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.xs.dp))
        RailLabel("THIS SESSION")
        if (state.sessionChanges.isEmpty()) {
            Text(
                "No changes yet",
                color = LocalIntelliJColors.current.textMuted,
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
                    background = LocalIntelliJColors.current.buttonBackground,
                    foreground = LocalIntelliJColors.current.textPrimary,
                    onClick = { dispatch(AgentIntent.AcceptAll) },
                    bordered = true,
                )
                PillButton(
                    text = "Revert all",
                    icon = null,
                    background = LocalIntelliJColors.current.surface,
                    foreground = LocalIntelliJColors.current.textSecondary,
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
                    .background(LocalIntelliJColors.current.divider),
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
        color = LocalIntelliJColors.current.textMuted,
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
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (change.added >
            0
        ) {
            Text("+${change.added}", color = LocalIntelliJColors.current.diffAddedGutter, fontSize = 11.sp)
        }
        if (change.removed >
            0
        ) {
            Text("−${change.removed}", color = LocalIntelliJColors.current.diffRemovedText, fontSize = 11.sp)
        }
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
        Text(label, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
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
                .background(if (on) LocalIntelliJColors.current.success else LocalIntelliJColors.current.divider)
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
        AgentConnection.CONNECTED -> "Agent · connected"
        AgentConnection.CONNECTING -> "Agent · connecting…"
        AgentConnection.ERROR -> "Agent · connection failed"
        AgentConnection.DISCONNECTED -> "Agent · idle"
    }

@Composable
private fun subtitleColor(state: AgentUiState): Color =
    if (state.connection ==
        AgentConnection.ERROR
    ) {
        LocalIntelliJColors.current.error
    } else {
        LocalIntelliJColors.current.textMuted
    }

private fun formatElapsed(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * Session list. Each row shows the title, a status, a relative time, and the
 * diff line counts. Clicking a row restores that conversation. The trailing
 * icon asks the host to delete it.
 *
 * @param now Clock used for the relative time, so tests can pin it.
 */
@Composable
internal fun AgentHistoryPopup(
    rows: List<SessionListRow>,
    offsetY: Int,
    onRestore: (String) -> Unit,
    onDeleteRequest: (SessionListRow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    PopupChromeMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = modifier.width((Dimensions.menuWidth + Spacing.xxl).dp),
        offsetY = offsetY,
    ) {
        if (rows.isEmpty()) {
            Text(
                text = "No saved sessions yet.",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            )
        } else {
            for (row in rows) {
                SessionListPopupRow(row, now, onRestore, onDeleteRequest)
            }
        }
    }
}

@Composable
private fun SessionListPopupRow(
    row: SessionListRow,
    now: Long,
    onRestore: (String) -> Unit,
    onDeleteRequest: (SessionListRow) -> Unit,
) {
    val colors = LocalIntelliJColors.current
    PopupListRow(
        selected = row.current,
        height = (Dimensions.popupRowHeight + Dimensions.popupRowHeightCompact).dp,
        onClick = { onRestore(row.id) },
    ) {
        Box(
            modifier =
                Modifier
                    .size(Spacing.sm.dp)
                    .clip(CircleShape)
                    .background(SessionStatusColor(row.status)),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm.dp)) {
            Text(
                text = row.title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${sessionStatusLabel(row.status)} · ${relativeSessionTime(row.savedAtEpochMillis, now)}",
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        sessionDiffCountLabel(row.addedLines, row.removedLines)?.let { counts ->
            Text(counts, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(
            onClick = { onDeleteRequest(row) },
            modifier = Modifier.size(Dimensions.iconLg.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete session",
                tint = colors.textSecondary,
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
        }
    }
}

@Composable
private fun SessionStatusColor(status: AgentSessionStatus): Color {
    val colors = LocalIntelliJColors.current
    return when (status) {
        AgentSessionStatus.RUNNING -> colors.accent
        AgentSessionStatus.NEEDS_YOU -> colors.warning
        AgentSessionStatus.FAILED -> colors.error
        AgentSessionStatus.IDLE -> colors.textMuted
    }
}
