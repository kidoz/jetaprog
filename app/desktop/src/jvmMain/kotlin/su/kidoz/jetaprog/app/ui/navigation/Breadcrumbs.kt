package su.kidoz.jetaprog.app.ui.navigation

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
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.navigation.BreadcrumbItem
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind

/**
 * Breadcrumbs bar showing navigation path to current position.
 */
@Composable
public fun Breadcrumbs(
    items: List<BreadcrumbItem>,
    onItemClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to the end when items change
    LaunchedEffect(items) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(IntelliJColors.surfaceElevated)
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            BreadcrumbItemView(
                item = item,
                isLast = index == items.lastIndex,
                onClick = { onItemClick(item) },
            )

            if (index < items.lastIndex) {
                BreadcrumbSeparator()
            }
        }
    }
}

@Composable
private fun BreadcrumbItemView(
    item: BreadcrumbItem,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textColor =
        when {
            isLast -> IntelliJColors.textPrimary
            isHovered -> IntelliJColors.accent
            else -> IntelliJColors.textSecondary
        }

    val backgroundColor =
        if (isHovered) {
            IntelliJColors.surfaceHover
        } else {
            Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xs.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        // Icon
        Icon(
            imageVector = item.kind.toBreadcrumbIcon(),
            contentDescription = null,
            tint = item.kind.toBreadcrumbColor(),
            modifier = Modifier.size(14.dp),
        )

        // Name
        Text(
            text = item.name,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BreadcrumbSeparator() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = IntelliJColors.textMuted,
        modifier = Modifier.size(14.dp),
    )
}

/**
 * Compact breadcrumbs for limited space (shows only file name and current symbol).
 */
@Composable
public fun CompactBreadcrumbs(
    fileName: String,
    currentSymbol: String?,
    onFileClick: () -> Unit,
    onSymbolClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(20.dp)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        // File
        CompactBreadcrumbItem(
            icon = Icons.Default.Description,
            iconColor = IntelliJColors.iconFile,
            text = fileName,
            onClick = onFileClick,
        )

        // Separator and symbol (if present)
        if (currentSymbol != null && onSymbolClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = IntelliJColors.textMuted,
                modifier = Modifier.size(12.dp),
            )

            CompactBreadcrumbItem(
                icon = Icons.Default.Code,
                iconColor = IntelliJColors.iconJava,
                text = currentSymbol,
                onClick = onSymbolClick,
            )
        }
    }
}

@Composable
private fun CompactBreadcrumbItem(
    icon: ImageVector,
    iconColor: Color,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(if (isHovered) IntelliJColors.surfaceHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(12.dp),
        )

        Text(
            text = text,
            color = if (isHovered) IntelliJColors.accent else IntelliJColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Breadcrumbs with dropdown for each segment.
 */
@Composable
public fun InteractiveBreadcrumbs(
    items: List<BreadcrumbItem>,
    onItemClick: (BreadcrumbItem) -> Unit,
    onShowSiblings: ((BreadcrumbItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(items) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(IntelliJColors.surfaceElevated)
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            InteractiveBreadcrumbItem(
                item = item,
                isLast = index == items.lastIndex,
                onClick = { onItemClick(item) },
                onShowSiblings = onShowSiblings?.let { { it(item) } },
            )

            if (index < items.lastIndex) {
                BreadcrumbSeparator()
            }
        }
    }
}

@Composable
private fun InteractiveBreadcrumbItem(
    item: BreadcrumbItem,
    isLast: Boolean,
    onClick: () -> Unit,
    onShowSiblings: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isHovered) IntelliJColors.surfaceHover else Color.Transparent)
                    .hoverable(interactionSource)
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.xs.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        ) {
            // Icon
            Icon(
                imageVector = item.kind.toBreadcrumbIcon(),
                contentDescription = null,
                tint = item.kind.toBreadcrumbColor(),
                modifier = Modifier.size(14.dp),
            )

            // Name
            Text(
                text = item.name,
                color =
                    when {
                        isLast -> IntelliJColors.textPrimary
                        isHovered -> IntelliJColors.accent
                        else -> IntelliJColors.textSecondary
                    },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Dropdown indicator (only show on hover if siblings are available)
            if (isHovered && onShowSiblings != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Show siblings",
                    tint = IntelliJColors.textMuted,
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clickable(onClick = onShowSiblings),
                )
            }
        }
    }
}

private fun NavigationSymbolKind.toBreadcrumbIcon(): ImageVector =
    when (this) {
        NavigationSymbolKind.FILE -> Icons.Default.Description

        NavigationSymbolKind.NAMESPACE,
        NavigationSymbolKind.PACKAGE,
        NavigationSymbolKind.MODULE,
        -> Icons.Default.Folder

        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        NavigationSymbolKind.ENUM,
        NavigationSymbolKind.STRUCT,
        NavigationSymbolKind.OBJECT,
        -> Icons.Default.Class

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        NavigationSymbolKind.CONSTRUCTOR,
        -> Icons.Default.Functions

        else -> Icons.Default.Code
    }

private fun NavigationSymbolKind.toBreadcrumbColor(): Color =
    when (this) {
        NavigationSymbolKind.FILE -> IntelliJColors.iconFile

        NavigationSymbolKind.NAMESPACE,
        NavigationSymbolKind.PACKAGE,
        NavigationSymbolKind.MODULE,
        -> IntelliJColors.iconFolder

        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        -> IntelliJColors.iconKotlin

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        -> IntelliJColors.iconJava

        NavigationSymbolKind.ENUM -> IntelliJColors.iconRust

        else -> IntelliJColors.textSecondary
    }
