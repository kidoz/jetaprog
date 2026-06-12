package su.kidoz.jetaprog.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

private const val SKELETON_ALPHA_LOW = 0.35f
private const val SKELETON_ALPHA_HIGH = 0.65f
private const val SKELETON_PULSE_MS = 1100

/**
 * A single skeleton bar — used as a placeholder for one row of content while
 * loading. Pulses gently between two alpha values so users see *something is
 * happening* without the visual noise of a spinner.
 *
 * Prefer this over `CircularProgressIndicator` for any list, tree, or panel
 * with predictable row geometry. Spinners are reserved for inline feedback
 * inside small buttons or status segments.
 */
@Composable
public fun SkeletonBar(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 12.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton-pulse")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_LOW,
        targetValue = SKELETON_ALPHA_HIGH,
        animationSpec =
            infiniteRepeatable(
                animation = tween(SKELETON_PULSE_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton-alpha",
    )
    Box(
        modifier =
            modifier
                .let { if (width == Dp.Unspecified) it.fillMaxWidth() else it.width(width) }
                .height(height)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(IntelliJColors.surfaceContainer.copy(alpha = alpha)),
    )
}

/**
 * Vertical stack of [count] skeleton bars at the given [rowHeight] — used for
 * lists, trees, and tool-window bodies. The last bar is rendered slightly
 * shorter (75 % width) so the placeholder reads as text-like.
 */
@Composable
public fun SkeletonList(
    rowHeight: Dp,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        repeat(count) { index ->
            val isLast = index == count - 1
            SkeletonBar(
                modifier = if (isLast) Modifier.fillMaxWidth(SKELETON_LAST_ROW_FRACTION) else Modifier.fillMaxWidth(),
                height = rowHeight,
            )
        }
    }
}

/**
 * Full-shell skeleton used while a project session is loading. Three columns
 * mimicking the activity bar / project panel / editor layout so the user sees
 * structure rather than a centered spinner.
 */
@Composable
public fun SessionSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(Spacing.lg.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
        SkeletonBar(height = Dimensions.menuBarHeight.dp)
        SkeletonBar(modifier = Modifier.fillMaxWidth(SKELETON_SHORT_ROW_FRACTION), height = Spacing.md.dp)
        SkeletonList(rowHeight = Dimensions.treeNodeHeight.dp, count = SESSION_SKELETON_ROWS)
    }
}

private const val SKELETON_LAST_ROW_FRACTION = 0.75f
private const val SKELETON_SHORT_ROW_FRACTION = 0.5f
private const val SESSION_SKELETON_ROWS = 8
