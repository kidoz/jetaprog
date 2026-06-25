package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.syntax.Diagnostic
import java.awt.Cursor

/** The tool shown in the unified bottom panel. */
public enum class BottomTab {
    TERMINAL,
    BUILD,
    PROBLEMS,
}

private const val MIN_PANEL_HEIGHT = 120
private const val MAX_PANEL_HEIGHT = 600
private const val DEFAULT_PANEL_HEIGHT = 260

/**
 * Unified bottom tool window with a shared **Terminal / Build / Problems** tab strip.
 *
 * Replaces the previously stacked panels with a single resizable container whose
 * tab strip switches the foreground tool. The Problems tab shows a live count badge.
 *
 * @param selectedTab The currently foreground tab.
 * @param problemsCount Total problems (errors + warnings) for the Problems badge.
 * @param onSelectTab Invoked when a tab is clicked.
 * @param onClose Invoked when the panel close button is pressed.
 * @param content Renders the body for the given [BottomTab].
 */
@Composable
public fun BottomPanel(
    selectedTab: BottomTab,
    problemsCount: Int,
    onSelectTab: (BottomTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (BottomTab) -> Unit,
) {
    var panelHeight by remember { mutableStateOf(DEFAULT_PANEL_HEIGHT.dp) }
    val density = LocalDensity.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(panelHeight)
                .background(IntelliJColors.background),
    ) {
        // Top resize handle.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = with(density) { dragAmount.toDp() }
                            panelHeight = (panelHeight - delta).coerceIn(MIN_PANEL_HEIGHT.dp, MAX_PANEL_HEIGHT.dp)
                        }
                    },
        )

        // Tab strip.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(IntelliJColors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                icon = Icons.Filled.Terminal,
                label = "Terminal",
                selected = selectedTab == BottomTab.TERMINAL,
                onClick = { onSelectTab(BottomTab.TERMINAL) },
            )
            BottomTabItem(
                icon = Icons.Filled.Build,
                label = "Build",
                selected = selectedTab == BottomTab.BUILD,
                onClick = { onSelectTab(BottomTab.BUILD) },
            )
            BottomTabItem(
                icon = Icons.Filled.Warning,
                label = "Problems",
                selected = selectedTab == BottomTab.PROBLEMS,
                iconTint = IntelliJColors.warning,
                badgeCount = problemsCount,
                onClick = { onSelectTab(BottomTab.PROBLEMS) },
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close panel",
                tint = IntelliJColors.textMuted,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.sm.dp)
                        .size(Dimensions.iconMd.dp)
                        .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                        .clickable(onClick = onClose),
            )
        }

        // Content for the selected tab.
        Box(modifier = Modifier.fillMaxSize()) {
            content(selectedTab)
        }
    }
}

@Composable
private fun BottomTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconTint: Color = IntelliJColors.textSecondary,
    badgeCount: Int = 0,
) {
    Box(
        modifier =
            Modifier
                .fillMaxHeightTabStrip()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp - 1.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) IntelliJColors.textPrimary else iconTint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                color = if (selected) IntelliJColors.textPrimary else IntelliJColors.textSecondary,
                fontSize = 12.sp,
            )
            if (badgeCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IntelliJColors.warning)
                            .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = IntelliJColors.background,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.sm.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(IntelliJColors.accent),
            )
        }
    }
}

/** A 30dp-tall tab cell (matches the tab strip height). */
private fun Modifier.fillMaxHeightTabStrip(): Modifier = this.height(30.dp)

/**
 * Problems tab content: a simple list of diagnostics for the active document.
 */
@Composable
public fun ProblemsContent(
    diagnostics: List<Diagnostic>,
    modifier: Modifier = Modifier,
) {
    if (diagnostics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(Spacing.md.dp)) {
            Text(text = "No problems found.", color = IntelliJColors.textMuted, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize().padding(vertical = Spacing.xs.dp)) {
        items(diagnostics) { diagnostic ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                val isError = diagnostic.severity == DiagnosticSeverity.ERROR
                Icon(
                    imageVector = if (isError) Icons.Filled.Error else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isError) IntelliJColors.error else IntelliJColors.warning,
                    modifier = Modifier.size(Dimensions.iconSm.dp),
                )
                Text(text = diagnostic.message, color = IntelliJColors.textPrimary, fontSize = 13.sp)
                diagnostic.source?.let { source ->
                    Text(text = source, color = IntelliJColors.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
