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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.panels.FileBadge
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** The brand blue→purple gradient used as the agent's signature accent. */
internal val agentGradient: Brush
    get() = Brush.linearGradient(listOf(IntelliJColors.brandGradientStart, IntelliJColors.brandGradientEnd))

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
                .background(agentGradient),
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
                .background(IntelliJColors.agentEffortText),
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
                .background(IntelliJColors.toolCardBackground)
                .border(1.dp, IntelliJColors.divider, shape),
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
                tint = IntelliJColors.iconFile,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
            Text(block.name, color = IntelliJColors.editorIdentifier, fontSize = 12.5.sp)
            if (block.args.isNotEmpty()) {
                Text(
                    text = block.args,
                    color = IntelliJColors.textMuted,
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
                tint = IntelliJColors.textMuted,
                modifier = Modifier.size(Dimensions.iconLg.dp),
            )
        }
        if (block.expanded && !block.result.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IntelliJColors.divider))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text(
                    text = "RESULT",
                    color = IntelliJColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                CodeBlock(block.result, tint = if (block.status == ToolStatus.FAILED) IntelliJColors.error else null)
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
                color = IntelliJColors.accent,
                strokeWidth = 2.dp,
            )
        }

        ToolStatus.OK -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = IntelliJColors.success,
                modifier = Modifier.size(15.dp),
            )
        }

        ToolStatus.FAILED -> {
            Icon(
                Icons.Default.Error,
                contentDescription = "Failed",
                tint = IntelliJColors.error,
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
        color = tint ?: IntelliJColors.textSecondary,
        fontSize = 12.sp,
        fontFamily = JetaProgFonts.codeFont,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IntelliJColors.codeBlockBackground)
                .border(1.dp, IntelliJColors.divider, shape)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.sm.dp),
    )
}

/** A proposed-diff card with an inline hunk and accept/reject actions. */
@Composable
internal fun ProposedDiffCard(
    block: Block.Diff,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IntelliJColors.toolCardBackground)
                .border(1.dp, IntelliJColors.agentCardBorder, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(IntelliJColors.brandGradientEnd.copy(alpha = 0.10f), Color.Transparent),
                        ),
                    ).padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            FileBadge(block.fileName)
            Text(block.fileName, color = IntelliJColors.editorIdentifier, fontSize = 12.5.sp)
            if (block.path.isNotEmpty()) {
                Text("…/${block.path.substringAfterLast('/')}", color = IntelliJColors.textMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("+${block.added}", color = IntelliJColors.diffAddedGutter, fontSize = 11.sp)
            Text("−${block.removed}", color = IntelliJColors.diffRemovedText, fontSize = 11.sp)
            DecisionPill(block.decision)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs.dp)) {
            block.lines.forEach { DiffLineRow(it) }
        }
        if (block.decision == DiffDecision.PROPOSED) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                PillButton("Accept", Icons.Default.Check, IntelliJColors.diffAcceptBackground, Color.White, onAccept)
                PillButton(
                    text = "Reject",
                    icon = Icons.Default.Close,
                    background = IntelliJColors.buttonBackground,
                    foreground = IntelliJColors.textPrimary,
                    onClick = onReject,
                    iconTint = IntelliJColors.diffRemovedText,
                    bordered = true,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val background =
        when (line.kind) {
            DiffLineKind.ADDED -> IntelliJColors.diffAddedBackground
            DiffLineKind.REMOVED -> IntelliJColors.diffRemovedBackground
            DiffLineKind.CONTEXT -> Color.Transparent
        }
    val textColor =
        when (line.kind) {
            DiffLineKind.ADDED -> IntelliJColors.diffAddedText
            DiffLineKind.REMOVED -> IntelliJColors.diffRemovedText
            DiffLineKind.CONTEXT -> IntelliJColors.textSecondary
        }
    Row(
        modifier = Modifier.fillMaxWidth().height(20.dp).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.number?.toString().orEmpty(),
            color = IntelliJColors.lineNumberForeground,
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
            DiffDecision.PROPOSED -> "Proposed" to IntelliJColors.agentPillText
            DiffDecision.ACCEPTED -> "Accepted" to IntelliJColors.success
            DiffDecision.REJECTED -> "Reverted" to IntelliJColors.textMuted
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .border(1.dp, IntelliJColors.agentPillBorder, RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .padding(horizontal = Spacing.sm.dp, vertical = 1.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp)
    }
}

/** An approval (permission gate) card with approve/deny actions. */
@Composable
internal fun ApprovalCard(
    block: Block.Approval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IntelliJColors.warning.copy(alpha = 0.06f))
                .border(1.dp, IntelliJColors.approvalBorder, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = IntelliJColors.warning,
                modifier = Modifier.size(Dimensions.iconLg.dp),
            )
            Text(block.title, color = IntelliJColors.approvalText, fontSize = 12.5.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (block.resolved) "resolved" else "requires approval",
                color = IntelliJColors.textMuted,
                fontSize = 10.sp,
            )
        }
        block.command?.let {
            Box(modifier = Modifier.padding(horizontal = Spacing.md.dp)) { CodeBlock("$ $it") }
        }
        if (!block.resolved) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                GradientButton("Approve & run", Icons.Default.PlayArrow, onApprove)
                PillButton(
                    text = "Deny",
                    icon = null,
                    background = IntelliJColors.buttonBackground,
                    foreground = IntelliJColors.textPrimary,
                    onClick = onDeny,
                    bordered = true,
                )
            }
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
                .then(if (bordered) Modifier.border(1.dp, IntelliJColors.border, shape) else Modifier)
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
                .background(agentGradient)
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
) {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                .background(agentGradient)
                .alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = "Send",
            tint = Color.White,
            modifier = Modifier.size(Dimensions.iconLg.dp),
        )
    }
}
