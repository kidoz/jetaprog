package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.SignatureHelpState
import su.kidoz.jetaprog.editor.state.SignatureInfo

/**
 * Popup showing signature help (function parameter hints).
 */
@Composable
public fun SignatureHelpPopup(
    state: SignatureHelpState,
    onNextSignature: () -> Unit,
    onPreviousSignature: () -> Unit,
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
                    .widthIn(min = 200.dp, max = 600.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
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
            } else if (state.signatures.isNotEmpty()) {
                val activeSignature = state.activeSignatureInfo

                // Header with signature navigation
                if (state.signatures.size > 1) {
                    SignatureNavigationHeader(
                        currentIndex = state.activeSignature,
                        totalCount = state.signatures.size,
                        onPrevious = onPreviousSignature,
                        onNext = onNextSignature,
                    )
                    HorizontalDivider(color = IntelliJColors.border)
                }

                // Signature content
                if (activeSignature != null) {
                    SignatureContent(
                        signature = activeSignature,
                        activeParameter = state.activeParameter,
                    )
                }
            }
        }
    }
}

/**
 * Navigation header for multiple signatures.
 */
@Composable
private fun SignatureNavigationHeader(
    currentIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Previous signature",
            modifier =
                Modifier
                    .size(16.dp)
                    .clickable(enabled = currentIndex > 0) { onPrevious() },
            tint =
                if (currentIndex > 0) {
                    IntelliJColors.textPrimary
                } else {
                    IntelliJColors.textMuted.copy(alpha = 0.5f)
                },
        )

        Spacer(modifier = Modifier.width(Spacing.sm.dp))

        Text(
            text = "${currentIndex + 1} of $totalCount",
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
        )

        Spacer(modifier = Modifier.width(Spacing.sm.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next signature",
            modifier =
                Modifier
                    .size(16.dp)
                    .clickable(enabled = currentIndex < totalCount - 1) { onNext() },
            tint =
                if (currentIndex < totalCount - 1) {
                    IntelliJColors.textPrimary
                } else {
                    IntelliJColors.textMuted.copy(alpha = 0.5f)
                },
        )
    }
}

/**
 * Content for a single signature.
 */
@Composable
private fun SignatureContent(
    signature: SignatureInfo,
    activeParameter: Int,
) {
    Column(
        modifier = Modifier.padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        // Signature label with highlighted parameter
        SignatureLabel(
            signature = signature,
            activeParameter = activeParameter,
        )

        // Parameter documentation (if active parameter has docs)
        val activeParam = signature.parameters.getOrNull(activeParameter)
        val paramDoc = activeParam?.documentation
        if (paramDoc != null) {
            HorizontalDivider(
                color = IntelliJColors.border,
                modifier = Modifier.padding(vertical = Spacing.xs.dp),
            )
            Text(
                text = paramDoc,
                color = IntelliJColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }

        // Signature documentation
        val sigDoc = signature.documentation
        if (sigDoc != null) {
            HorizontalDivider(
                color = IntelliJColors.border,
                modifier = Modifier.padding(vertical = Spacing.xs.dp),
            )
            Text(
                text = sigDoc,
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * Signature label with the active parameter highlighted.
 */
@Composable
private fun SignatureLabel(
    signature: SignatureInfo,
    activeParameter: Int,
) {
    // Parse the signature label to highlight the active parameter
    // The label is typically like "functionName(param1: Type, param2: Type)"
    val annotatedString =
        buildAnnotatedString {
            val label = signature.label
            val params = signature.parameters

            if (params.isEmpty()) {
                // No parameters to highlight, just show the label
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(label)
                }
            } else {
                // Try to find and highlight the active parameter in the label
                var currentPos = 0
                var paramIndex = 0

                for (param in params) {
                    val paramLabel = param.label
                    val paramPos = label.indexOf(paramLabel, currentPos)

                    if (paramPos >= 0) {
                        // Add text before this parameter
                        if (paramPos > currentPos) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                                append(label.substring(currentPos, paramPos))
                            }
                        }

                        // Add the parameter with appropriate styling
                        val isActive = paramIndex == activeParameter
                        withStyle(
                            SpanStyle(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (isActive) {
                                        IntelliJColors.accent
                                    } else {
                                        IntelliJColors.textPrimary
                                    },
                            ),
                        ) {
                            append(paramLabel)
                        }

                        currentPos = paramPos + paramLabel.length
                        paramIndex++
                    }
                }

                // Add any remaining text
                if (currentPos < label.length) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                        append(label.substring(currentPos))
                    }
                }
            }
        }

    Text(
        text = annotatedString,
        color = IntelliJColors.textPrimary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp,
    )
}
