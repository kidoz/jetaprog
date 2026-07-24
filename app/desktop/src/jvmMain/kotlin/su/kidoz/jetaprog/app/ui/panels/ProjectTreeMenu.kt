package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** An entry in the project tree context menu. */
public data class ProjectTreeMenuItem(
    /** Label shown to the user. */
    val label: String,
    /** Invoked when the entry is chosen. */
    val onClick: () -> Unit,
    /** Renders the entry in the error color, for destructive actions. */
    val isDestructive: Boolean = false,
)

/**
 * Context menu for a project tree row, anchored at the click position.
 *
 * Built from design tokens rather than the platform menu so it matches the
 * rest of the IDE chrome.
 */
@Composable
public fun ProjectTreeContextMenu(
    offset: IntOffset,
    items: List<ProjectTreeMenuItem>,
    onDismiss: () -> Unit,
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier
                    .width(180.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.popupBackground)
                    .padding(vertical = Spacing.xs.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0 && item.isDestructive) {
                    MenuSeparator()
                }
                ProjectTreeMenuRow(item = item, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun ProjectTreeMenuRow(
    item: ProjectTreeMenuItem,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Text(
        text = item.label,
        color = if (item.isDestructive) IntelliJColors.error else IntelliJColors.textPrimary,
        fontSize = 13.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isHovered) IntelliJColors.surfaceHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable {
                    onDismiss()
                    item.onClick()
                }.padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
    )
}

@Composable
private fun MenuSeparator() {
    Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs.dp)
                .height(Dimensions.splitterThickness.dp)
                .background(IntelliJColors.divider),
    )
}
