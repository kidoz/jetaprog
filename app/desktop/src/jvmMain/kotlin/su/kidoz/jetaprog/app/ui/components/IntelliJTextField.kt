package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Modern flat-styled text field with IntelliJ-inspired design.
 *
 * Features:
 * - 36dp minimum height for comfortable input
 * - 8dp rounded corners for modern flat design
 * - Subtle border that becomes more prominent on focus
 * - Hover state with slightly brighter background
 * - Better placeholder contrast
 *
 * @param value current text value.
 * @param onValueChange invoked when the text changes.
 * @param modifier modifier applied to the complete field, including its label and error.
 * @param label optional label displayed above the input.
 * @param placeholder text displayed when [value] is empty.
 * @param error optional validation message displayed below the input.
 * @param enabled whether the input accepts interaction.
 * @param singleLine whether the input is restricted to one line.
 * @param trailingContent optional action or indicator displayed inside the input at its trailing edge.
 */
@Composable
public fun IntelliJTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp)

    val backgroundColor =
        when {
            !enabled -> IntelliJColors.backgroundLighter
            isHovered && !isFocused -> IntelliJColors.inputBackgroundHover
            else -> IntelliJColors.inputBackground
        }

    val borderColor =
        when {
            error != null -> IntelliJColors.error
            isFocused -> IntelliJColors.inputBorderFocused
            isHovered -> IntelliJColors.inputBorderHover
            else -> IntelliJColors.inputBorder
        }

    Column(modifier = modifier) {
        // Label
        label?.let {
            Text(
                text = it,
                color = if (error != null) IntelliJColors.error else IntelliJColors.textSecondary,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.codeFont,
                modifier = Modifier.padding(bottom = Spacing.xs.dp),
            )
        }

        // TextField
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle =
                TextStyle(
                    color = if (enabled) IntelliJColors.textPrimary else IntelliJColors.textDisabled,
                    fontSize = 13.sp,
                    fontFamily = JetaProgFonts.codeFont,
                ),
            cursorBrush = SolidColor(IntelliJColors.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.inputHeight.dp)
                    .background(backgroundColor, shape)
                    .border(1.dp, borderColor, shape)
                    .hoverable(interactionSource)
                    .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = IntelliJColors.inputPlaceholder,
                                fontSize = 13.sp,
                                fontFamily = JetaProgFonts.codeFont,
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.let { content ->
                        Box(
                            modifier = Modifier.padding(start = Spacing.sm.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            content()
                        }
                    }
                }
            },
        )

        // Error message
        error?.let {
            Text(
                text = it,
                color = IntelliJColors.error,
                fontSize = 11.sp,
                fontFamily = JetaProgFonts.codeFont,
                modifier = Modifier.padding(top = Spacing.xxs.dp),
            )
        }
    }
}
