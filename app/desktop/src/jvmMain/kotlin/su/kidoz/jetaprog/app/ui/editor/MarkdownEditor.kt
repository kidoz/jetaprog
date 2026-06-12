package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.VerticalSplit
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.syntax.highlighting.DarkSyntaxTheme
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxTheme

/**
 * View mode for markdown editor.
 */
public enum class MarkdownViewMode {
    /** Show only the code editor. */
    EDITOR,

    /** Show both editor and preview side by side. */
    SPLIT,

    /** Show only the preview. */
    PREVIEW,
}

/**
 * Markdown editor with live preview support.
 */
@Composable
public fun MarkdownEditor(
    state: EditorState,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    syntaxTheme: SyntaxTheme = DarkSyntaxTheme,
) {
    var viewMode by remember { mutableStateOf(MarkdownViewMode.SPLIT) }
    val content = state.content

    Column(modifier = modifier.fillMaxSize()) {
        // View mode toolbar
        MarkdownToolbar(
            currentMode = viewMode,
            onModeChange = { viewMode = it },
        )

        // Content area
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            // Editor panel
            if (viewMode == MarkdownViewMode.EDITOR || viewMode == MarkdownViewMode.SPLIT) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    CodeEditor(
                        state = state,
                        onContentChange = onContentChange,
                        modifier = Modifier.fillMaxSize(),
                        syntaxTheme = syntaxTheme,
                    )
                }
            }

            // Divider between panels
            if (viewMode == MarkdownViewMode.SPLIT) {
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(IntelliJColors.border),
                )
            }

            // Preview panel
            if (viewMode == MarkdownViewMode.PREVIEW || viewMode == MarkdownViewMode.SPLIT) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(IntelliJColors.background),
                ) {
                    MarkdownPreview(
                        content = content,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownToolbar(
    currentMode: MarkdownViewMode,
    onModeChange: (MarkdownViewMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(IntelliJColors.toolWindowBackground)
                .padding(horizontal = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "Markdown",
            fontSize = 12.sp,
            color = IntelliJColors.textSecondary,
            modifier = Modifier.weight(1f),
        )

        // View mode buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
        ) {
            ViewModeButton(
                icon = Icons.Default.Code,
                label = "Editor",
                isSelected = currentMode == MarkdownViewMode.EDITOR,
                onClick = { onModeChange(MarkdownViewMode.EDITOR) },
            )
            ViewModeButton(
                icon = Icons.Default.VerticalSplit,
                label = "Split",
                isSelected = currentMode == MarkdownViewMode.SPLIT,
                onClick = { onModeChange(MarkdownViewMode.SPLIT) },
            )
            ViewModeButton(
                icon = Icons.Default.Preview,
                label = "Preview",
                isSelected = currentMode == MarkdownViewMode.PREVIEW,
                onClick = { onModeChange(MarkdownViewMode.PREVIEW) },
            )
        }
    }
}

@Composable
private fun ViewModeButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor =
        if (isSelected) {
            IntelliJColors.selectionBackground
        } else {
            IntelliJColors.toolWindowBackground
        }
    val iconColor =
        if (isSelected) {
            IntelliJColors.accent
        } else {
            IntelliJColors.textSecondary
        }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = iconColor,
        )
    }
}
