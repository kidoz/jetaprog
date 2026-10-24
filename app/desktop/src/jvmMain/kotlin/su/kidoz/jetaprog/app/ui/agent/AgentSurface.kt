package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

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

private enum class TaskPanel { CHECKS, APPROVAL }

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
    val checks = agentChecks(state.turns, state.workspaceRevision, state.isStreaming, state.hasUnsavedEdits)
    val workspace = state.workspacePath.ifBlank { "Workspace unavailable" }
    val files = reviewFiles(state.turns, state.reviewCounts, state.reviewConflicts)
    val reviewing = state.reviewVisible
    val workspaceReview = reviewing && state.reviewScope == ReviewScope.WORKSPACE
    val attention = agentAttention(state.turns, state.isStreaming, state.error, state.interrupted)
    var expandedPanel by remember(state.activeRecordId, attention.approval?.id) { mutableStateOf<TaskPanel?>(null) }
    val showChecks =
        !reviewing && (checks.isNotEmpty() || files.isNotEmpty() || state.goal != null || state.turns.isEmpty())
    BoxWithConstraints(modifier.fillMaxSize()) {
        val detailMaxHeight = maxHeight / 4
        Column(modifier = Modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
            AgentHeader(state, dispatch, checkoutLabel, onSwitchCheckout, layoutDocked, mirrorLayout)
            AgentWorkspaceContext(state.workspacePath)
            state.restoration?.let { AgentRestorationNotice(it) }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier =
                            Modifier
                                .weight(if (reviewing) 2f else 1f, fill = false)
                                .fillMaxWidth()
                                .then(
                                    if (workspaceReview) Modifier else Modifier.verticalScroll(rememberScrollState()),
                                ),
                    ) {
                        val goalNotice = state.goalNotice
                        if (!goalNotice.isNullOrBlank()) AgentStatusMessage(goalNotice)
                        if (reviewing) {
                            AgentReviewPanel(
                                state,
                                dispatch,
                                files,
                                checks,
                                modifier = if (workspaceReview) Modifier.weight(1f, fill = false) else Modifier,
                            )
                        }
                        val reviewNotice = state.reviewNotice
                        if (!reviewNotice.isNullOrBlank()) AgentStatusMessage(reviewNotice)
                        val worktreeNotice = state.worktreeNotice
                        if (!worktreeNotice.isNullOrBlank()) AgentStatusMessage(worktreeNotice)
                        val agentNotice = state.agentNotice
                        if (!agentNotice.isNullOrBlank()) AgentStatusMessage(agentNotice)
                        if (!workspaceReview) AgentTaskDetails(state, dispatch)
                    }
                    Box(modifier = Modifier.weight(if (reviewing) 1f else 2f).fillMaxWidth()) {
                        if (state.turns.isEmpty()) {
                            EmptyState(dispatch)
                        } else {
                            Conversation(state, dispatch)
                        }
                    }
                }
                if (showRail && !workspaceReview) {
                    SessionChangesRail(state, dispatch)
                }
            }
            if (showChecks) {
                AgentChecksSection(
                    checks = checks,
                    workspace = workspace,
                    expanded = expandedPanel == TaskPanel.CHECKS,
                    onExpandedChange = { expandedPanel = if (it) TaskPanel.CHECKS else null },
                    detailMaxHeight = detailMaxHeight,
                )
            }
            AgentAttentionSection(
                attention,
                dispatch,
                reviewVisible = reviewing,
                presence = state.presence,
                connectionFailed = state.connection == AgentConnection.ERROR,
                detailMaxHeight = detailMaxHeight,
                requestExpanded = expandedPanel == TaskPanel.APPROVAL,
                onRequestExpandedChange = { expandedPanel = if (it) TaskPanel.APPROVAL else null },
            )
            AgentComposer(state, dispatch)
        }
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
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.activityBarWidth.dp)
                    .background(LocalIntelliJColors.current.background)
                    .padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            AgentAvatar(tileSize = 26, iconSize = 16, cornerRadius = 7)
            Column {
                Text(
                    "Agent",
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(subtitleFor(state), color = subtitleColor(state), fontSize = 10.sp, maxLines = 1)
            }
            ModelChip(state)
            if (checkoutLabel != null && onSwitchCheckout != null) {
                CheckoutChip(checkoutLabel, onSwitchCheckout)
            }
            HeaderIcon(Icons.AutoMirrored.Filled.CompareArrows, "Review changes") { dispatch(AgentIntent.ShowReview) }
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
                        interrupted = state.interrupted,
                    ),
                offsetY = headerHeightPx,
                onRestore = { id ->
                    if (id != state.activeRecordId) dispatch(AgentIntent.RestoreSession(id))
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
    val shape = RoundedCornerShape(Dimensions.cornerRadiusSmall.dp)
    Box(
        modifier =
            Modifier
                .size(Dimensions.toolbarIcon.dp)
                .agentFocusRing(shape)
                .clip(shape)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalIntelliJColors.current.textSecondary,
            modifier = Modifier.size(Dimensions.iconLg.dp),
        )
    }
}

@Composable
private fun CheckoutChip(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
    Text(
        text = label,
        color = LocalIntelliJColors.current.accent,
        fontSize = 11.sp,
        modifier =
            Modifier
                .heightIn(min = Dimensions.popupRowHeightCompact.dp)
                .agentFocusRing(shape)
                .clip(shape)
                .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape)
                .clickable(role = Role.Button, onClick = onClick)
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
    val listState = remember(state.activeRecordId) { LazyListState() }
    var followOutput by remember(state.activeRecordId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val atLatest =
                layout.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?.let { it >= layout.totalItemsCount - 1 } ?: true
            listState.isScrollInProgress to atLatest
        }.collect { (scrolling, atLatest) ->
            if (atLatest) {
                followOutput = true
            } else if (scrolling) {
                followOutput = false
            }
        }
    }
    LaunchedEffect(listState, state.turns, followOutput) {
        if (followOutput && state.turns.isNotEmpty()) {
            listState.scrollToItem(state.turns.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize().semantics {
                contentDescription = "Conversation"
                stateDescription = if (followOutput) "Following output" else "Scroll paused"
            },
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
                if (block.thought) {
                    Text(
                        text = block.text,
                        color = LocalIntelliJColors.current.textMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    AgentMarkdown(
                        markdown = block.text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
            AgentQueueSection(state.queuedPrompts, state.queuePaused, dispatch)
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
                        text =
                            if (state.isStreaming || state.queuedPrompts.isNotEmpty()) {
                                "Enter queues a follow-up. Ctrl/Cmd+Enter steers next…"
                            } else {
                                "Ask the agent to build a feature, refactor, explain code, or run the project…"
                            },
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
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = Dimensions.inputHeight.dp)
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.agent != null) {
                        AgentPickerChip(state, dispatch)
                    }
                    if (state.mode != null) {
                        ModePickerChip(state, dispatch)
                    }
                    if (state.availableModels.isNotEmpty()) {
                        ModelPickerChip(state, dispatch)
                    }
                }
                SendButton(
                    enabled =
                        input.isNotBlank() && state.connection != AgentConnection.CONNECTING &&
                            state.restoration != SessionRestoration.Loading,
                    onClick = send,
                    label = if (state.isStreaming || state.queuedPrompts.isNotEmpty()) "Queue message" else "Send",
                    modifier = Modifier.align(Alignment.BottomEnd),
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
        Box(
            modifier =
                Modifier
                    .size(Dimensions.toolbarIcon.dp)
                    .agentFocusRing(CircleShape)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Remove $name" }
                    .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
        }
    }
}

/** Copies the ZCode install command. Tests replace this so they leave the system clipboard alone. */
internal var copyAgentInstallText: (String) -> Boolean = { text ->
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }.isSuccess
}

@Composable
private fun AgentPickerChip(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    val agent = state.agent ?: return
    var open by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    val sections = splitAgentMenu(state.agentMenu)
    val ready = sections.first
    val blocked = sections.second
    Box(modifier = Modifier.onSizeChanged { anchorHeightPx = it.height }) {
        val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
        Row(
            modifier =
                Modifier
                    .heightIn(min = Dimensions.popupRowHeightCompact.dp)
                    .agentFocusRing(shape)
                    .clip(shape)
                    .background(LocalIntelliJColors.current.background)
                    .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape)
                    .semantics {
                        contentDescription = "Agent: ${agent.name}"
                        stateDescription = if (open) "Expanded" else "Collapsed"
                    }.clickable(role = Role.DropdownList) {
                        dispatch(AgentIntent.RefreshAgents)
                        open = true
                    }.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
        ) {
            Text(agent.name, color = LocalIntelliJColors.current.textPrimary, fontSize = 11.sp)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
        }
        PopupChromeMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(Dimensions.popupCompletionWidth.dp),
            offsetY = anchorHeightPx,
        ) {
            ready.forEach { row -> AgentMenuEntry(row, agent.id, dispatch) { open = false } }
            if (ready.isNotEmpty() && blocked.isNotEmpty()) {
                HorizontalDivider(
                    thickness = Dimensions.dividerThickness.dp,
                    color = LocalIntelliJColors.current.border,
                )
            }
            blocked.forEach { row -> AgentMenuEntry(row, agent.id, dispatch) { open = false } }
            Text(
                text = AGENT_SCAN_FOOTER,
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 11.sp,
                modifier =
                    Modifier.padding(
                        horizontal = Spacing.sm.dp,
                        vertical = Spacing.xs.dp,
                    ),
            )
        }
    }
}

@Composable
private fun AgentMenuEntry(
    row: AgentMenuRow,
    activeId: String,
    dispatch: (AgentIntent) -> Unit,
    close: () -> Unit,
) {
    val colors = LocalIntelliJColors.current
    val nameColor = if (row.selectable) colors.textPrimary else colors.textMuted
    val detailColor = if (row.warning) colors.warning else colors.textMuted
    PopupListRow(
        selected = row.selectable && row.id == activeId,
        height = (Dimensions.popupRowHeight + Dimensions.popupRowHeightCompact).dp,
        onClick = {
            val command = row.installCommand
            if (command != null) {
                dispatch(AgentIntent.ReportAgentInstallCopy(copyAgentInstallText(command)))
                close()
            } else if (row.selectable) {
                dispatch(AgentIntent.SelectAgent(row.id))
                close()
            }
        },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                color = nameColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.detail,
                color = detailColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.installCommand != null) {
            Text(
                text = "Copy install command",
                color = colors.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = Spacing.sm.dp),
            )
        }
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
        val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
        Row(
            modifier =
                Modifier
                    .heightIn(min = Dimensions.popupRowHeightCompact.dp)
                    .agentFocusRing(shape)
                    .clip(shape)
                    .background(LocalIntelliJColors.current.background)
                    .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape)
                    .semantics {
                        contentDescription = "Session mode: ${mode.name}"
                        stateDescription = if (open) "Expanded" else "Collapsed"
                    }.clickable(enabled = canSwitch, role = Role.DropdownList) { open = true }
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
        ) {
            Text(mode.name, color = LocalIntelliJColors.current.textPrimary, fontSize = 11.sp)
            if (canSwitch) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
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
        val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
        Row(
            modifier =
                Modifier
                    .heightIn(min = Dimensions.popupRowHeightCompact.dp)
                    .agentFocusRing(shape)
                    .clip(shape)
                    .background(LocalIntelliJColors.current.background)
                    .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape)
                    .semantics {
                        contentDescription = "Model: ${model.shortName}"
                        stateDescription = if (open) "Expanded" else "Collapsed"
                    }.clickable(role = Role.DropdownList) { open = true }
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
                    text = "Keep all",
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
        if ((change.added ?: 0) >
            0
        ) {
            Text("+${change.added}", color = LocalIntelliJColors.current.diffAddedGutter, fontSize = 11.sp)
        }
        if ((change.removed ?: 0) >
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
    val policy = state.permissions[kind] ?: PermissionPolicy.ASK
    var open by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = Dimensions.popupRowHeight.dp,
                ).padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(label, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.onSizeChanged { anchorHeightPx = it.height }) {
            val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
            Row(
                modifier =
                    Modifier
                        .heightIn(min = Dimensions.popupRowHeightCompact.dp)
                        .agentFocusRing(shape)
                        .clip(shape)
                        .clickable(role = Role.DropdownList) { open = true }
                        .semantics {
                            contentDescription = "$label permission"
                            stateDescription = "${permissionPolicyLabel(policy)} policy"
                        }.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            ) {
                Text(permissionPolicyLabel(policy), color = LocalIntelliJColors.current.accent, fontSize = 11.sp)
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LocalIntelliJColors.current.textMuted,
                    modifier = Modifier.size(Dimensions.iconSm.dp),
                )
            }
            PopupChromeMenu(
                expanded = open,
                onDismissRequest = { open = false },
                offsetY = anchorHeightPx,
            ) {
                PermissionPolicy.entries.forEach { option ->
                    PopupListRow(
                        selected = option == policy,
                        height = Dimensions.popupRowHeightCompact.dp,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "$label permission: ${permissionPolicyLabel(option)}"
                                role = Role.RadioButton
                                selected = option == policy
                            },
                        onClick = {
                            dispatch(AgentIntent.SetPermission(kind, option))
                            open = false
                        },
                    ) {
                        Text(
                            permissionPolicyLabel(option),
                            color = LocalIntelliJColors.current.textPrimary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun permissionPolicyLabel(policy: PermissionPolicy): String =
    when (policy) {
        PermissionPolicy.ASK -> "Ask"
        PermissionPolicy.AUTO -> "Auto"
        PermissionPolicy.NEVER -> "Never"
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
            modifier = Modifier.size(Dimensions.toolbarIcon.dp).agentFocusRing(CircleShape),
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
        AgentSessionStatus.NEEDS_YOU, AgentSessionStatus.WAITING_APPROVAL -> colors.warning
        AgentSessionStatus.REVIEW_READY -> colors.accent
        AgentSessionStatus.INTERRUPTED -> colors.textSecondary
        AgentSessionStatus.FAILED -> colors.error
        AgentSessionStatus.IDLE -> colors.textMuted
    }
}
