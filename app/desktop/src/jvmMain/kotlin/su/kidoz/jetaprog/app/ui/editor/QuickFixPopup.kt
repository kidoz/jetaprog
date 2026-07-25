package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.QuickFixState

/**
 * Quick-fix popup shown at the caret (Alt+Enter).
 *
 * Keyboard handling lives in the editor so the caret keeps focus; this renders
 * the offered fixes and reports clicks.
 */
@Composable
public fun QuickFixPopup(
    state: QuickFixState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset,
) {
    if (!state.isVisible || state.fixes.isEmpty()) return

    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        // Not focusable: the editor keeps the caret and handles the keys.
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(min = 260.dp, max = 460.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.popupBackground)
                    .padding(vertical = Spacing.xs.dp),
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                itemsIndexed(state.fixes) { index, fix ->
                    QuickFixRow(
                        title = fix.title,
                        isSelected = index == state.selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
            QuickFixFooter()
        }
    }
}

@Composable
private fun QuickFixRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val background =
        when {
            isSelected -> IntelliJColors.selectionBackground
            isHovered -> IntelliJColors.surfaceHover
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AutoFixHigh,
            contentDescription = null,
            tint = IntelliJColors.warning,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = title,
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickFixFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
    ) {
        Text(text = "↑↓ Navigate", color = IntelliJColors.textMuted, fontSize = 10.sp)
        Text(text = "Enter Apply", color = IntelliJColors.textMuted, fontSize = 10.sp)
        Text(text = "Esc Close", color = IntelliJColors.textMuted, fontSize = 10.sp)
    }
}
