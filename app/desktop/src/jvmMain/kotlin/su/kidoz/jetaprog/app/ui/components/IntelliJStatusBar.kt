package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Modern flat-styled status bar.
 *
 * Features:
 * - 24dp height for better proportions
 * - More spacing between items
 * - Rounded segment backgrounds on hover
 * - Subtle dividers
 */
@Composable
@Suppress("LongParameterList")
public fun IntelliJStatusBar(
    modifier: Modifier = Modifier,
    gitBranch: String? = null,
    isDirty: Boolean = false,
    errorCount: Int = 0,
    warningCount: Int = 0,
    lineInfo: String? = null,
    indentInfo: String? = null,
    encodingInfo: String = "UTF-8",
    lineEnding: String = "LF",
    languageInfo: String? = null,
    isBuilding: Boolean = false,
    buildStatus: BuildStatus? = null,
    onBranchClick: () -> Unit = {},
    onEncodingClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.statusBarHeight.dp)
                .background(IntelliJColors.statusBarBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ---- Left group: build status · branch · dirty dot ----
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBuilding) {
                StatusBarItem(
                    icon = Icons.Default.Sync,
                    text = "Building...",
                    iconColor = IntelliJColors.info,
                )
            } else {
                buildStatus?.let { status ->
                    StatusBarItem(
                        icon = if (status.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        text = if (status.success) "Build successful" else "Build failed",
                        iconColor = if (status.success) IntelliJColors.success else IntelliJColors.error,
                    )
                }
            }

            gitBranch?.let { branch ->
                StatusBarItem(
                    icon = Icons.Default.AccountTree,
                    text = branch,
                    iconColor = IntelliJColors.success,
                    onClick = onBranchClick,
                )
            }
            if (isDirty) {
                Dot(color = IntelliJColors.warning)
            }
        }

        // ---- Center group: problems ----
        if (errorCount > 0 || warningCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBarItem(
                    icon = Icons.Default.Warning,
                    text = warningCount.toString(),
                    iconColor = IntelliJColors.warning,
                )
                StatusBarItem(
                    icon = Icons.Default.Error,
                    text = errorCount.toString(),
                    iconColor = IntelliJColors.error,
                )
            }
        }

        // ---- Right group: caret pos · indent · encoding · EOL · language · memory ----
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lineInfo?.let { info ->
                StatusBarItem(text = info)
                StatusBarDivider()
            }
            indentInfo?.let { indent ->
                StatusBarItem(text = indent)
                StatusBarDivider()
            }
            StatusBarClickableItem(text = encodingInfo, onClick = onEncodingClick)
            StatusBarDivider()
            StatusBarItem(text = lineEnding)
            StatusBarDivider()
            languageInfo?.let { lang ->
                StatusBarClickableItem(text = lang, onClick = onLanguageClick)
                StatusBarDivider()
            }
            MemoryChip()
        }
    }
}

/** A small filled dot (e.g. the unsaved-changes indicator). */
@Composable
private fun Dot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
    )
}

/**
 * Live JVM heap usage chip. Refreshes periodically and triggers a garbage
 * collection (and immediate refresh) when clicked, mirroring IntelliJ's
 * click-to-GC memory widget.
 */
@Composable
private fun MemoryChip() {
    var label by remember { mutableStateOf(memoryLabel()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(Unit) {
        while (true) {
            label = memoryLabel()
            kotlinx.coroutines.delay(MEMORY_REFRESH_MS)
        }
    }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(if (isHovered) IntelliJColors.statusBarHover else IntelliJColors.inputBackground)
                .hoverable(interactionSource)
                .clickable {
                    System.gc()
                    label = memoryLabel()
                }.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xxs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = "Memory usage (click to run garbage collection)",
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(13.dp),
        )
        Text(text = label, color = IntelliJColors.statusBarForeground, fontSize = 11.sp)
    }
}

private const val MEMORY_REFRESH_MS = 2_000L
private const val BYTES_PER_MB = 1024L * 1024L

/** Formats current heap usage as `used / max M`. */
private fun memoryLabel(): String {
    val runtime = Runtime.getRuntime()
    val used = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
    val max = runtime.maxMemory() / BYTES_PER_MB
    return "$used / ${max}M"
}

@Composable
private fun StatusBarItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = IntelliJColors.textSecondary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = IntelliJColors.statusBarForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBarClickableItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(if (isHovered) IntelliJColors.statusBarHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xxs.dp),
    ) {
        Text(
            text = text,
            color = if (isHovered) IntelliJColors.textPrimary else IntelliJColors.statusBarForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBarDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(12.dp)
                .background(IntelliJColors.statusBarDivider),
    )
}

/**
 * Build status information.
 */
public data class BuildStatus(
    val success: Boolean,
    val message: String = "",
)
