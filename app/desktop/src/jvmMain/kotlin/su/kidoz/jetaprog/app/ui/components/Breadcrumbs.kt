package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Breadcrumb segment representing a path element.
 *
 * [position] is set for [BreadcrumbType.SYMBOL] segments and points at the
 * symbol's declaration within [path].
 */
public data class BreadcrumbSegment(
    val name: String,
    val path: String,
    val type: BreadcrumbType = BreadcrumbType.DIRECTORY,
    val position: TextPosition? = null,
)

/**
 * Type of breadcrumb segment.
 */
public enum class BreadcrumbType {
    PROJECT,
    DIRECTORY,
    FILE,
    SYMBOL,

    /** Non-interactive placeholder rendered when middle crumbs are collapsed. */
    ELLIPSIS,
}

/** Maximum crumbs shown before the middle is collapsed to an ellipsis. */
private const val MAX_VISIBLE_CRUMBS = 5

/** Number of trailing crumbs always kept visible (so the filename is never hidden). */
private const val TAIL_CRUMBS = 3

private val ELLIPSIS_SEGMENT =
    BreadcrumbSegment(name = "…", path = "", type = BreadcrumbType.ELLIPSIS)

/**
 * Middle-truncates a deep breadcrumb trail to `first › … › …tail`, always keeping
 * the project root and the trailing [TAIL_CRUMBS] (including the filename).
 */
private fun collapseSegments(segments: List<BreadcrumbSegment>): List<BreadcrumbSegment> {
    if (segments.size <= MAX_VISIBLE_CRUMBS) return segments
    return buildList {
        add(segments.first())
        add(ELLIPSIS_SEGMENT)
        addAll(segments.takeLast(TAIL_CRUMBS))
    }
}

/**
 * IntelliJ-style breadcrumbs navigation bar.
 */
@Composable
public fun Breadcrumbs(
    segments: List<BreadcrumbSegment>,
    onSegmentClick: (BreadcrumbSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return

    val visible = remember(segments) { collapseSegments(segments) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.breadcrumbsHeight.dp)
                .background(IntelliJColors.breadcrumbsBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        visible.forEachIndexed { index, segment ->
            BreadcrumbItem(
                segment = segment,
                onClick = { if (segment.type != BreadcrumbType.ELLIPSIS) onSegmentClick(segment) },
            )

            if (index < visible.lastIndex) {
                BreadcrumbSeparator()
            }
        }
    }
}

/**
 * Individual breadcrumb item.
 */
@Composable
private fun BreadcrumbItem(
    segment: BreadcrumbSegment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textColor =
        when {
            isHovered -> IntelliJColors.breadcrumbsForegroundHover
            segment.type == BreadcrumbType.FILE -> IntelliJColors.breadcrumbsFileForeground
            else -> IntelliJColors.breadcrumbsForeground
        }

    val backgroundColor =
        when {
            isHovered -> IntelliJColors.breadcrumbsBackgroundHover
            else -> Color.Transparent
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(2.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = segment.name,
            color = textColor,
            fontSize = 11.sp,
            fontWeight =
                if (segment.type == BreadcrumbType.FILE) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Breadcrumb separator (chevron).
 */
@Composable
private fun BreadcrumbSeparator(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = IntelliJColors.breadcrumbsSeparator,
        modifier = modifier.size(12.dp),
    )
}

/**
 * Create breadcrumb segments from a file path.
 */
public fun createBreadcrumbsFromPath(
    projectPath: String,
    projectName: String,
    filePath: String,
): List<BreadcrumbSegment> {
    val segments = mutableListOf<BreadcrumbSegment>()

    // Add project root
    segments.add(
        BreadcrumbSegment(
            name = projectName,
            path = projectPath,
            type = BreadcrumbType.PROJECT,
        ),
    )

    // Get relative path from project
    val relativePath =
        if (filePath.startsWith(projectPath)) {
            filePath.removePrefix(projectPath).trimStart('/', '\\')
        } else {
            filePath
        }

    // Split into path components
    val parts = relativePath.split('/', '\\').filter { it.isNotEmpty() }

    var currentPath = projectPath
    parts.forEachIndexed { index, part ->
        currentPath = "$currentPath/$part"
        val isFile = index == parts.lastIndex

        segments.add(
            BreadcrumbSegment(
                name = part,
                path = currentPath,
                type = if (isFile) BreadcrumbType.FILE else BreadcrumbType.DIRECTORY,
            ),
        )
    }

    return segments
}
