package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxColor
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxTheme

/**
 * Fold region information.
 */
public data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val isFolded: Boolean = false,
)

/**
 * IntelliJ-style line numbers gutter with fold markers.
 */
@Composable
public fun LineNumbers(
    lineCount: Int,
    currentLine: Int,
    scrollState: ScrollState,
    theme: SyntaxTheme,
    modifier: Modifier = Modifier,
    foldRegions: List<FoldRegion> = emptyList(),
    onToggleFold: (FoldRegion) -> Unit = {},
) {
    // Calculate width based on number of digits
    val digits = lineCount.toString().length.coerceAtLeast(3)
    val gutterWidth = (digits * 10 + 36).dp // Extra space for fold markers

    val listState = rememberLazyListState()

    // Sync scroll with editor
    LaunchedEffect(scrollState.value) {
        val lineHeight = 21 // Approximate line height in pixels
        val firstVisibleLine = scrollState.value / lineHeight
        if (firstVisibleLine >= 0 && firstVisibleLine < lineCount) {
            listState.scrollToItem(firstVisibleLine)
        }
    }

    Row(
        modifier =
            modifier
                .width(gutterWidth)
                .background(IntelliJColors.gutterBackground),
    ) {
        // Fold markers column
        Box(
            modifier =
                Modifier
                    .width(16.dp)
                    .fillMaxHeight(),
        ) {
            LazyColumn(state = listState) {
                items(lineCount) { index ->
                    val lineNumber = index + 1
                    val foldRegion = foldRegions.find { it.startLine == lineNumber }

                    Box(
                        modifier =
                            Modifier
                                .height(21.dp)
                                .width(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (foldRegion != null) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()

                            Icon(
                                imageVector =
                                    if (foldRegion.isFolded) {
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                contentDescription =
                                    if (foldRegion.isFolded) {
                                        "Expand"
                                    } else {
                                        "Collapse"
                                    },
                                tint =
                                    if (isHovered) {
                                        IntelliJColors.textPrimary
                                    } else {
                                        IntelliJColors.textSecondary
                                    },
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .hoverable(interactionSource)
                                        .clickable { onToggleFold(foldRegion) },
                            )
                        }
                    }
                }
            }
        }

        // Divider line
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(IntelliJColors.divider),
        )

        // Line numbers column
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            items(lineCount) { index ->
                val lineNumber = index + 1
                val isCurrentLine = lineNumber == currentLine

                Box(
                    modifier =
                        Modifier
                            .height(21.dp)
                            .padding(end = 8.dp)
                            .background(
                                if (isCurrentLine) {
                                    IntelliJColors.editorCaretRow
                                } else {
                                    Color.Transparent
                                },
                            ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = lineNumber.toString(),
                        fontFamily = JetaProgFonts.codeFont,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color =
                            if (isCurrentLine) {
                                IntelliJColors.lineNumberForegroundActive
                            } else {
                                IntelliJColors.lineNumberForeground
                            },
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        // Right edge border
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(IntelliJColors.divider),
        )
    }
}

/**
 * Convert SyntaxColor to Compose Color.
 */
private fun SyntaxColor.toComposeColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)
