package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.popupChrome
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
                    .popupChrome(Dimensions.cornerRadius.dp)
                    .padding(vertical = Spacing.xs.dp),
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = Dimensions.popupCompletionMaxHeight.dp)) {
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
    PopupListRow(
        selected = isSelected,
        onClick = onClick,
        horizontalPadding = Spacing.md.dp,
    ) {
        Icon(
            imageVector = Icons.Default.AutoFixHigh,
            contentDescription = null,
            tint = IntelliJColors.warning,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
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
