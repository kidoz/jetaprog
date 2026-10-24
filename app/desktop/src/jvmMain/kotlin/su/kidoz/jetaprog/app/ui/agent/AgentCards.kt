package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.ui.panels.FileBadge
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** The brand blue→purple gradient used as the agent's signature accent. */
@Composable
internal fun agentGradient(): Brush =
    Brush.linearGradient(
        listOf(LocalIntelliJColors.current.brandGradientStart, LocalIntelliJColors.current.brandGradientEnd),
    )

/** A rounded gradient tile with the [AutoAwesome] glyph — the agent's avatar. */
@Composable
internal fun AgentAvatar(
    tileSize: Int,
    iconSize: Int,
    cornerRadius: Int = Dimensions.cornerRadiusLarge,
) {
    Box(
        modifier =
            Modifier
                .size(tileSize.dp)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(agentGradient()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/** Maps an ACP tool-kind wire value to an icon. */
internal fun toolIconFor(kind: String): ImageVector =
    when (kind) {
        "read" -> Icons.Default.Description
        "edit", "move" -> Icons.Default.Edit
        "delete" -> Icons.Default.Delete
        "search" -> Icons.Default.Search
        "execute" -> Icons.Default.Terminal
        "fetch" -> Icons.Default.Cloud
        else -> Icons.Default.Build
    }

/** A blinking caret appended to streaming text. */
@Composable
internal fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "caret-alpha",
    )
    Box(
        modifier =
            Modifier
                .padding(start = 2.dp)
                .size(width = 7.dp, height = 14.dp)
                .alpha(if (a > 0.5f) 1f else 0f)
                .background(LocalIntelliJColors.current.agentEffortText),
    )
}

/** A tool-call card: header (icon, name, args, status) plus an expandable result. */
@Composable
internal fun ToolCallCard(
    block: Block.Tool,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LocalIntelliJColors.current.toolCardBackground)
                .border(1.dp, LocalIntelliJColors.current.divider, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                imageVector = toolIconFor(block.kind),
                contentDescription = null,
                tint = LocalIntelliJColors.current.iconFile,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
            Text(block.name, color = LocalIntelliJColors.current.editorIdentifier, fontSize = 12.5.sp)
            if (block.args.isNotEmpty()) {
                Text(
                    text = block.args,
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ToolStatusIndicator(block.status)
            Icon(
                imageVector = if (block.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(Dimensions.iconLg.dp),
            )
        }
        if (block.expanded && !block.result.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalIntelliJColors.current.divider))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text(
                    text = "RESULT",
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                CodeBlock(
                    block.result,
                    tint =
                        if (block.status ==
                            ToolStatus.FAILED
                        ) {
                            LocalIntelliJColors.current.error
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun ToolStatusIndicator(status: ToolStatus) {
    when (status) {
        ToolStatus.RUNNING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = LocalIntelliJColors.current.accent,
                strokeWidth = 2.dp,
            )
        }

        ToolStatus.OK -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = LocalIntelliJColors.current.success,
                modifier = Modifier.size(15.dp),
            )
        }

        ToolStatus.FAILED -> {
            Icon(
                Icons.Default.Error,
                contentDescription = "Failed",
                tint = LocalIntelliJColors.current.error,
                modifier = Modifier.size(15.dp),
            )
        }

        ToolStatus.INTERRUPTED -> {
            Icon(
                Icons.Default.Close,
                contentDescription = "Interrupted",
                tint = LocalIntelliJColors.current.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** A monospace code/result block. */
@Composable
internal fun CodeBlock(
    text: String,
    tint: Color? = null,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
    Text(
        text = text,
        color = tint ?: LocalIntelliJColors.current.textSecondary,
        fontSize = 12.sp,
        fontFamily = JetaProgFonts.codeFont,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LocalIntelliJColors.current.codeBlockBackground)
                .border(1.dp, LocalIntelliJColors.current.divider, shape)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.sm.dp),
    )
}

/**
 * A proposed-diff card with a short hunk, accept/reject actions, and a link to the IDE diff.
 *
 * The hunk is capped, so [onOpen] is how a large change is reviewed.
 */
@Composable
internal fun ProposedDiffCard(
    block: Block.Diff,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LocalIntelliJColors.current.toolCardBackground)
                .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(LocalIntelliJColors.current.brandGradientEnd.copy(alpha = 0.10f), Color.Transparent),
                        ),
                    ).padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            FileBadge(block.fileName)
            Text(block.fileName, color = LocalIntelliJColors.current.editorIdentifier, fontSize = 12.5.sp)
            if (block.path.isNotEmpty()) {
                Text(
                    "…/${block.path.substringAfterLast('/')}",
                    color = LocalIntelliJColors.current.textMuted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (block.previewAvailable) {
                Text("+${block.added}", color = LocalIntelliJColors.current.diffAddedGutter, fontSize = 11.sp)
                Text("−${block.removed}", color = LocalIntelliJColors.current.diffRemovedText, fontSize = 11.sp)
            }
            DecisionPill(block.decision)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs.dp)) {
            if (!block.previewAvailable) {
                Text(
                    "Saved review. Open the file to inspect its current changes.",
                    color = LocalIntelliJColors.current.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md.dp),
                )
            }
            block.lines.forEach { DiffLineRow(it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            if (block.decision == DiffDecision.PROPOSED) {
                PillButton(
                    "Keep file",
                    Icons.Default.Check,
                    LocalIntelliJColors.current.diffAcceptBackground,
                    Color.White,
                    onAccept,
                )
                PillButton(
                    text = "Revert file",
                    icon = Icons.Default.Close,
                    background = LocalIntelliJColors.current.buttonBackground,
                    foreground = LocalIntelliJColors.current.textPrimary,
                    onClick = onReject,
                    iconTint = LocalIntelliJColors.current.diffRemovedText,
                    bordered = true,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.semantics { contentDescription = "Open in diff" }) {
                PillButton(
                    text = "Open",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    background = LocalIntelliJColors.current.buttonBackground,
                    foreground = LocalIntelliJColors.current.textPrimary,
                    onClick = onOpen,
                    bordered = true,
                )
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val background =
        when (line.kind) {
            DiffLineKind.ADDED -> LocalIntelliJColors.current.diffAddedBackground
            DiffLineKind.REMOVED -> LocalIntelliJColors.current.diffRemovedBackground
            DiffLineKind.CONTEXT -> Color.Transparent
        }
    val textColor =
        when (line.kind) {
            DiffLineKind.ADDED -> LocalIntelliJColors.current.diffAddedText
            DiffLineKind.REMOVED -> LocalIntelliJColors.current.diffRemovedText
            DiffLineKind.CONTEXT -> LocalIntelliJColors.current.textSecondary
        }
    Row(
        modifier = Modifier.fillMaxWidth().height(20.dp).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.number?.toString().orEmpty(),
            color = LocalIntelliJColors.current.lineNumberForeground,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(40.dp).padding(end = Spacing.sm.dp),
        )
        Text(
            text = line.sign.toString(),
            color = textColor,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = line.text,
            color = textColor,
            fontSize = 12.5.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
        )
    }
}

@Composable
private fun DecisionPill(decision: DiffDecision) {
    val (label, color) =
        when (decision) {
            DiffDecision.PROPOSED -> "Proposed" to LocalIntelliJColors.current.agentPillText
            DiffDecision.ACCEPTED -> "Kept" to LocalIntelliJColors.current.success
            DiffDecision.REJECTED -> "Reverted" to LocalIntelliJColors.current.textMuted
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .border(
                    1.dp,
                    LocalIntelliJColors.current.agentPillBorder,
                    RoundedCornerShape(Dimensions.cornerRadiusSmall.dp),
                ).padding(horizontal = Spacing.sm.dp, vertical = 1.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp)
    }
}

/**
 * An approval card. Each [Block.Approval.choices] entry is one outcome, sent back with its option id.
 *
 * @param block The permission gate to show.
 * @param onSelect Called with the ACP option id of the button the user picked.
 */
@Composable
internal fun ApprovalCard(
    block: Block.Approval,
    onSelect: (optionId: String) -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    val colors = LocalIntelliJColors.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.warning.copy(alpha = 0.06f))
                .border(1.dp, colors.approvalBorder, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(Dimensions.iconLg.dp),
            )
            Text(block.title, color = colors.approvalText, fontSize = 12.5.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (block.resolved) "resolved" else "requires approval",
                color = colors.textMuted,
                fontSize = 10.sp,
            )
        }
        block.command?.let {
            Box(modifier = Modifier.padding(horizontal = Spacing.md.dp)) { CodeBlock("$ $it") }
        }
        if (!block.resolved && block.choices.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                block.choices.forEach { choice -> ApprovalChoiceButton(choice) { onSelect(choice.optionId) } }
            }
        }
    }
}

@Composable
private fun ApprovalChoiceButton(
    choice: ApprovalChoice,
    onClick: () -> Unit,
) {
    val colors = LocalIntelliJColors.current
    when (choice.role) {
        ApprovalChoiceRole.ALLOW_ONCE -> {
            GradientButton(choice.label, Icons.Default.PlayArrow, onClick)
        }

        ApprovalChoiceRole.ALLOW_ALWAYS -> {
            PillButton(
                text = choice.label,
                icon = null,
                background = colors.warning.copy(alpha = 0.16f),
                foreground = colors.warning,
                onClick = onClick,
                bordered = true,
            )
        }

        ApprovalChoiceRole.REJECT -> {
            PillButton(
                text = choice.label,
                icon = null,
                background = colors.buttonBackground,
                foreground = colors.textPrimary,
                onClick = onClick,
                bordered = true,
            )
        }
    }
}

/** A compact filled/bordered pill button used inside cards. */
@Composable
internal fun PillButton(
    text: String,
    icon: ImageVector?,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    iconTint: Color = foreground,
    bordered: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
    Row(
        modifier =
            Modifier
                .height(28.dp)
                .clip(shape)
                .background(background)
                .then(if (bordered) Modifier.border(1.dp, LocalIntelliJColors.current.border, shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        }
        Text(text, color = foreground, fontSize = 12.sp)
    }
}

/** A gradient-filled primary button used for the agent's headline actions. */
@Composable
internal fun GradientButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadius.dp)
    Row(
        modifier =
            Modifier
                .height(28.dp)
                .clip(shape)
                .background(agentGradient())
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp + 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** A round gradient send button. */
@Composable
internal fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    label: String = "Send",
) {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                .background(agentGradient())
                .alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(Dimensions.iconLg.dp),
        )
    }
}

/**
 * Files changed in this session, grouped for a read-only review.
 *
 * Keep acknowledges existing changes. Revert restores pending edits when safe. Close hides the card.
 * The command does not ask the agent to edit.
 *
 * @param files The session's changed files, in first-seen order.
 * @param onAccept Keeps all pending changes in that file.
 * @param onDismiss Attempts to revert all pending changes in that file.
 * @param onClose Hides the card.
 * @param modifier Layout modifier for the card.
 */
@Composable
internal fun ReviewCard(
    files: List<ReviewFile>,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: ((String) -> Unit)? = null,
) {
    if (files.isEmpty()) return
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    val colors = LocalIntelliJColors.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.toolCardBackground)
                .border(1.dp, colors.agentCardBorder, shape)
                .padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Review",
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close review",
                tint = colors.textSecondary,
                modifier =
                    Modifier
                        .size(Dimensions.iconSm.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
            )
        }
        Text(
            text = "Net recorded changes. Revert restores all pending edits in a file.",
            color = colors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        files.forEach { file ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                ) {
                    Text(
                        text = file.fileName,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val counts =
                        if (file.added != null &&
                            file.removed != null
                        ) {
                            sessionDiffCountLabel(file.added, file.removed) ?: "No net change"
                        } else {
                            "Net diff unavailable"
                        }
                    Text(text = counts, color = colors.textSecondary, fontSize = 11.sp)
                }
                if (file.pathLabel.isNotBlank()) {
                    Text(
                        text = file.pathLabel,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                file.conflict?.let { notice ->
                    Text(
                        text = notice,
                        color = colors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                onOpen?.let { open ->
                    PillButton(
                        text = "Open file",
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        background = colors.buttonBackground,
                        foreground = colors.textPrimary,
                        onClick = { open(file.key) },
                        bordered = true,
                    )
                }
                val status = file.statusLabel
                if (status != null) {
                    Text(
                        text = status,
                        color = if (status == "Kept") colors.success else colors.textSecondary,
                        fontSize = 11.sp,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                    ) {
                        PillButton(
                            text = "Keep",
                            icon = Icons.Filled.Check,
                            background = colors.success.copy(alpha = 0.16f),
                            foreground = colors.success,
                            onClick = { onAccept(file.key) },
                            bordered = true,
                        )
                        PillButton(
                            text = "Revert",
                            icon = Icons.Filled.Close,
                            background = colors.buttonBackground,
                            foreground = colors.textPrimary,
                            onClick = { onDismiss(file.key) },
                            bordered = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The agent's current plan. A later update replaces these steps in place.
 *
 * @param steps The steps to show, in order. An empty list draws nothing.
 * @param modifier Layout modifier for the card.
 */
@Composable
internal fun PlanCard(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LocalIntelliJColors.current.toolCardBackground)
                .border(1.dp, LocalIntelliJColors.current.agentCardBorder, shape)
                .padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = "Plan",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        steps.forEach { step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Spacing.sm.dp)
                            .clip(CircleShape)
                            .background(planStatusColor(step.status)),
                )
                Text(
                    text = step.content,
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = planStatusLabel(step.status),
                    color = planStatusColor(step.status),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private const val GOAL_CLOCK_PERIOD_MS: Long = 1_000

/**
 * The session's one objective, with its status, elapsed time, and iteration.
 *
 * Pause holds the loop. Replace prepares `/goal` in the composer. Clear removes it.
 * Pass [now] to pin the clock; otherwise an active goal ticks once a second.
 *
 * @param goal The objective to show.
 * @param onPause Holds the loop.
 * @param onResume Continues a paused goal.
 * @param onReplace Asks for a new objective.
 * @param onClear Removes the goal.
 * @param modifier Layout modifier for the card.
 * @param now Pinned clock for tests. Null uses the current time.
 */
@Composable
internal fun GoalCard(
    goal: AgentGoal,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReplace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long? = null,
) {
    var clock by remember { mutableStateOf(now ?: System.currentTimeMillis()) }
    val running = goal.status == AgentGoalStatus.ACTIVE && goal.runningSinceEpochMillis != null
    if (now == null && running) {
        LaunchedEffect(goal.runningSinceEpochMillis) {
            while (true) {
                delay(GOAL_CLOCK_PERIOD_MS)
                clock = System.currentTimeMillis()
            }
        }
    }
    val shownNow = now ?: clock
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    val colors = LocalIntelliJColors.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.toolCardBackground)
                .border(1.dp, colors.agentCardBorder, shape)
                .padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = "Goal",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = goal.objective,
            color = colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Spacing.sm.dp)
                        .clip(CircleShape)
                        .background(GoalStatusColor(goal.status)),
            )
            Text(
                text = goalMetaLabel(goal, shownNow),
                color = GoalStatusColor(goal.status),
                fontSize = 11.sp,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            if (goal.status == AgentGoalStatus.ACTIVE) {
                GoalActionButton("Pause", Icons.Filled.Pause, colors.warning, filled = true, onPause)
            } else if (goal.status == AgentGoalStatus.PAUSED) {
                GoalActionButton("Resume", Icons.Filled.PlayArrow, colors.accent, filled = true, onResume)
            }
            GoalActionButton("Replace", Icons.Filled.Edit, colors.textPrimary, filled = false, onReplace)
            GoalActionButton("Clear", Icons.Filled.Close, colors.textPrimary, filled = false, onClear)
        }
    }
}

@Composable
private fun GoalActionButton(
    text: String,
    icon: ImageVector,
    foreground: Color,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalIntelliJColors.current
    PillButton(
        text = text,
        icon = icon,
        background = if (filled) foreground.copy(alpha = 0.16f) else colors.buttonBackground,
        foreground = foreground,
        onClick = onClick,
        bordered = true,
    )
}

@Composable
private fun GoalStatusColor(status: AgentGoalStatus): Color =
    when (status) {
        AgentGoalStatus.ACTIVE -> LocalIntelliJColors.current.accent
        AgentGoalStatus.PAUSED -> LocalIntelliJColors.current.warning
        AgentGoalStatus.MET -> LocalIntelliJColors.current.success
    }

private fun planStatusLabel(status: PlanStepStatus): String =
    when (status) {
        PlanStepStatus.PENDING -> "Pending"
        PlanStepStatus.IN_PROGRESS -> "In progress"
        PlanStepStatus.COMPLETED -> "Done"
        PlanStepStatus.UNKNOWN -> "Unknown"
    }

@Composable
private fun planStatusColor(status: PlanStepStatus): Color =
    when (status) {
        PlanStepStatus.PENDING -> LocalIntelliJColors.current.textMuted
        PlanStepStatus.IN_PROGRESS -> LocalIntelliJColors.current.accent
        PlanStepStatus.COMPLETED -> LocalIntelliJColors.current.success
        PlanStepStatus.UNKNOWN -> LocalIntelliJColors.current.textSecondary
    }
