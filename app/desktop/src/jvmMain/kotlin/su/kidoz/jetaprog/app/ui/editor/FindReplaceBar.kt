package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.FindReplaceState
import su.kidoz.jetaprog.editor.state.FindToggle

/**
 * IntelliJ-style find/replace bar shown at the top of the editor.
 */
@Composable
public fun FindReplaceBar(
    state: FindReplaceState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val findFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        findFocusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            FindBarField(
                value = state.query,
                onValueChange = { onIntent(EditorIntent.UpdateFindQuery(it)) },
                placeholder = "Search",
                modifier = Modifier.width(FIELD_WIDTH.dp).focusRequester(findFocusRequester),
                onEnter = { onIntent(EditorIntent.FindNext) },
                onShiftEnter = { onIntent(EditorIntent.FindPrevious) },
                onEscape = { onIntent(EditorIntent.CloseFindBar) },
            )

            FindToggleButton(
                label = "Cc",
                active = state.options.caseSensitive,
                onClick = { onIntent(EditorIntent.ToggleFindOption(FindToggle.CASE_SENSITIVE)) },
            )
            FindToggleButton(
                label = "W",
                active = state.options.wholeWord,
                onClick = { onIntent(EditorIntent.ToggleFindOption(FindToggle.WHOLE_WORD)) },
            )
            FindToggleButton(
                label = ".*",
                active = state.options.regex,
                onClick = { onIntent(EditorIntent.ToggleFindOption(FindToggle.REGEX)) },
            )

            MatchCounter(state)

            FindBarIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Previous match",
                enabled = state.matches.isNotEmpty(),
                onClick = { onIntent(EditorIntent.FindPrevious) },
            )
            FindBarIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                description = "Next match",
                enabled = state.matches.isNotEmpty(),
                onClick = { onIntent(EditorIntent.FindNext) },
            )

            Box(modifier = Modifier.weight(1f))

            FindBarIconButton(
                icon = Icons.Default.Close,
                description = "Close find bar",
                enabled = true,
                onClick = { onIntent(EditorIntent.CloseFindBar) },
            )
        }

        if (state.showReplace) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                modifier = Modifier.padding(top = Spacing.xs.dp),
            ) {
                FindBarField(
                    value = state.replaceText,
                    onValueChange = { onIntent(EditorIntent.UpdateReplaceText(it)) },
                    placeholder = "Replace",
                    modifier = Modifier.width(FIELD_WIDTH.dp),
                    onEnter = { onIntent(EditorIntent.ReplaceCurrent) },
                    onShiftEnter = {},
                    onEscape = { onIntent(EditorIntent.CloseFindBar) },
                )

                IntelliJButton(
                    text = "Replace",
                    onClick = { onIntent(EditorIntent.ReplaceCurrent) },
                    style = ButtonStyle.SECONDARY,
                    enabled = state.currentMatch != null,
                )
                IntelliJButton(
                    text = "Replace All",
                    onClick = { onIntent(EditorIntent.ReplaceAll) },
                    style = ButtonStyle.SECONDARY,
                    enabled = state.matches.isNotEmpty(),
                )
            }
        }
    }
}

private const val FIELD_WIDTH = 260

/**
 * Compact single-line text field used inside the find bar.
 */
@Composable
private fun FindBarField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onEnter: () -> Unit,
    onShiftEnter: () -> Unit,
    onEscape: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor =
        if (isFocused) IntelliJColors.inputBorderFocused else IntelliJColors.inputBorder

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle =
            TextStyle(
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 13.sp,
                color = IntelliJColors.textPrimary,
            ),
        cursorBrush = SolidColor(IntelliJColors.accent),
        modifier =
            modifier
                .clip(RoundedCornerShape(3.dp))
                .background(IntelliJColors.inputBackground)
                .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        keyEvent.key == Key.Enter && keyEvent.isShiftPressed -> {
                            onShiftEnter()
                            true
                        }

                        keyEvent.key == Key.Enter -> {
                            onEnter()
                            true
                        }

                        keyEvent.key == Key.Escape -> {
                            onEscape()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = IntelliJColors.textMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Small toggle button for find options (match case, whole words, regex).
 */
@Composable
private fun FindToggleButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (active) IntelliJColors.accentSubtle else IntelliJColors.toolWindowHeader)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            color = if (active) IntelliJColors.accent else IntelliJColors.textSecondary,
        )
    }
}

/**
 * Match counter, e.g. "3/17" or "No results".
 */
@Composable
private fun MatchCounter(state: FindReplaceState) {
    val text =
        when {
            state.query.isEmpty() -> ""
            state.matches.isEmpty() -> "No results"
            else -> "${state.currentMatchIndex + 1}/${state.matches.size}"
        }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            fontSize = 12.sp,
            color =
                if (state.matches.isEmpty()) {
                    IntelliJColors.error
                } else {
                    IntelliJColors.textSecondary
                },
        )
    }
}

/**
 * Small icon button used in the find bar.
 */
@Composable
private fun FindBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(Spacing.xs.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) IntelliJColors.textSecondary else IntelliJColors.textDisabled,
            modifier = Modifier.size(16.dp),
        )
    }
}
