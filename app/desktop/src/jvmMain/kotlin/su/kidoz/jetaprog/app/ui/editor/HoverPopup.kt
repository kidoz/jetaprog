package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.Elevation
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.editor.state.HoverState

/**
 * Hover popup showing type information and documentation.
 */
@Composable
public fun HoverPopup(
    state: HoverState,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible && !state.isLoading) return

    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier =
                modifier
                    .widthIn(
                        min = Dimensions.popupHoverWidthMin.dp,
                        max = Dimensions.popupHoverWidthMax.dp,
                    ).shadow(Elevation.popup.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                    .background(IntelliJColors.popupBackground),
        ) {
            if (state.isLoading) {
                // Loading indicator
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = IntelliJColors.accent,
                        )
                        Text(
                            text = "Loading...",
                            color = IntelliJColors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else if (state.contents.isNotEmpty()) {
                // Hover contents
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = Dimensions.popupHoverHeightMax.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.sm.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
                ) {
                    state.contents.forEachIndexed { index, content ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = IntelliJColors.border,
                                modifier = Modifier.padding(vertical = Spacing.xs.dp),
                            )
                        }
                        MarkedStringContent(content)
                    }
                }
            }
        }
    }
}

/**
 * Render a single MarkedString (markdown or code).
 */
@Composable
private fun MarkedStringContent(content: MarkedString) {
    when (content) {
        is MarkedString.Markdown -> {
            // Simple markdown rendering (just plain text for now)
            Text(
                text = content.value,
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        is MarkedString.Code -> {
            // Code block with language hint
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            IntelliJColors.surfaceContainer,
                            RoundedCornerShape(4.dp),
                        ).padding(Spacing.sm.dp),
            ) {
                // Language label
                if (content.language.isNotBlank()) {
                    Text(
                        text = content.language,
                        color = IntelliJColors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = Spacing.xs.dp),
                    )
                }
                // Code content
                Text(
                    text = content.value,
                    color = IntelliJColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
