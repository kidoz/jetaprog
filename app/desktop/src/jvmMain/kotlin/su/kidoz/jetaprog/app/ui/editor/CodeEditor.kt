package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionTriggerKind
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.highlighting.DarkSyntaxTheme
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxColor
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxTheme
import su.kidoz.jetaprog.editor.syntax.highlighting.TokenStyle

/**
 * Code editor composable with syntax highlighting support.
 */
@Composable
public fun CodeEditor(
    state: EditorState,
    onContentChange: (String) -> Unit,
    onCompletionRequest: (CompletionTriggerKind, Char?, String) -> Unit = { _, _, _ -> },
    onCompletionSelect: (CompletionItem) -> Unit = {},
    onCompletionMoveUp: () -> Unit = {},
    onCompletionMoveDown: () -> Unit = {},
    onCompletionDismiss: () -> Unit = {},
    onCompletionFilterChange: (String) -> Unit = {},
    onCursorMove: (TextPosition) -> Unit = {},
    onHoverRequest: (TextPosition) -> Unit = {},
    onHoverDismiss: () -> Unit = {},
    onSignatureHelpRequest: (Char?) -> Unit = {},
    onSignatureHelpNextSignature: () -> Unit = {},
    onSignatureHelpPreviousSignature: () -> Unit = {},
    onSignatureHelpDismiss: () -> Unit = {},
    onFormatDocument: () -> Unit = {},
    modifier: Modifier = Modifier,
    syntaxTheme: SyntaxTheme = DarkSyntaxTheme,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val textStyle =
        remember {
            TextStyle(
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 14.sp,
            )
        }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val charWidthPx =
        remember(textStyle, textMeasurer) {
            textMeasurer.measure(AnnotatedString("M"), style = textStyle).size.width
        }

    // Track the text field value - keyed by document URI to reset when switching files
    var textFieldValue by remember(state.activeDocumentUri) {
        mutableStateOf(TextFieldValue(state.content))
    }

    // Track last known content to detect external changes (file reload, undo from outside, etc.)
    var lastKnownContent by remember(state.activeDocumentUri) { mutableStateOf(state.content) }

    // Handle external content changes (not from user typing)
    LaunchedEffect(state.activeDocumentUri) {
        snapshotFlow { state.content }
            .collect { newContent ->
                // Only update if content changed externally (not from our own edit)
                if (newContent != lastKnownContent && newContent != textFieldValue.text) {
                    // External change - update text while preserving cursor position
                    val selection = textFieldValue.selection
                    val clampedStart = selection.start.coerceIn(0, newContent.length)
                    val clampedEnd = selection.end.coerceIn(0, newContent.length)
                    textFieldValue =
                        TextFieldValue(
                            text = newContent,
                            selection = TextRange(clampedStart, clampedEnd),
                        )
                }
                lastKnownContent = newContent
            }
    }

    val annotatedString =
        remember(state.content, state.tokens, syntaxTheme) {
            buildHighlightedText(state.content, state.tokens, syntaxTheme)
        }

    // Calculate popup offset based on current cursor position (approximate)
    val lineHeightPx = with(density) { 21.dp.roundToPx() }
    val gutterWidthPx =
        with(density) {
            if (state.showLineNumbers) {
                val digits =
                    state.lineCount
                        .toString()
                        .length
                        .coerceAtLeast(3)
                (digits * 10 + 36).dp.roundToPx()
            } else {
                0
            }
        }
    val editorPaddingStartPx = with(density) { 8.dp.roundToPx() }
    val popupOffset =
        remember(
            state.cursor.position,
            gutterWidthPx,
            editorPaddingStartPx,
            charWidthPx,
            horizontalScrollState.value,
            verticalScrollState.value,
        ) {
            val line = state.cursor.position.line
            val column = state.cursor.position.column
            val x =
                gutterWidthPx +
                    editorPaddingStartPx +
                    (column * charWidthPx) -
                    horizontalScrollState.value
            val y = ((line + 1) * lineHeightPx) - verticalScrollState.value
            IntOffset(x = x, y = y)
        }

    // Calculate hover popup offset
    val hoverOffset =
        remember(state.hoverState.position) {
            val line = state.hoverState.position.line
            IntOffset(x = 50, y = (line + 1) * lineHeightPx)
        }

    // Calculate signature help popup offset
    val signatureHelpOffset =
        remember(state.signatureHelpState.position) {
            val line = state.signatureHelpState.position.line
            IntOffset(x = 50, y = line * lineHeightPx)
        }

    Box(modifier = modifier.background(syntaxTheme.background.toComposeColor())) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers gutter
            if (state.showLineNumbers) {
                LineNumbers(
                    lineCount = state.lineCount,
                    currentLine = state.currentLine,
                    scrollState = verticalScrollState,
                    theme = syntaxTheme,
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            // Editor content area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val oldText = textFieldValue.text
                        val oldSelection = textFieldValue.selection
                        textFieldValue = newValue
                        if (newValue.selection != oldSelection) {
                            onCursorMove(offsetToPosition(newValue.text, newValue.selection.start))
                        }
                        if (newValue.text != state.content) {
                            // Update lastKnownContent to prevent LaunchedEffect from resetting cursor
                            lastKnownContent = newValue.text
                            onContentChange(newValue.text)

                            // Check for trigger characters to auto-trigger completion
                            if (newValue.text.length > oldText.length) {
                                val insertedChar = newValue.text.getOrNull(newValue.selection.start - 1)
                                if (insertedChar != null) {
                                    // Completion triggers for special characters
                                    if (insertedChar in COMPLETION_TRIGGER_CHARACTERS) {
                                        val prefix =
                                            extractIdentifierPrefix(newValue.text, newValue.selection.start)
                                        onCompletionRequest(
                                            CompletionTriggerKind.TriggerCharacter,
                                            insertedChar,
                                            prefix,
                                        )
                                    } else if (insertedChar.isLetterOrDigit() || insertedChar == '_') {
                                        // Auto-trigger completion when typing identifiers (after 2+ chars)
                                        val prefix = extractIdentifierPrefix(newValue.text, newValue.selection.start)
                                        onCompletionFilterChange(prefix)
                                        if (prefix.length >= MIN_AUTO_COMPLETION_LENGTH) {
                                            onCompletionRequest(CompletionTriggerKind.Invoked, null, prefix)
                                        }
                                    }
                                    // Signature help triggers
                                    if (insertedChar in SIGNATURE_HELP_TRIGGER_CHARACTERS) {
                                        onSignatureHelpRequest(insertedChar)
                                    }
                                    // Dismiss signature help on closing paren
                                    if (insertedChar == ')') {
                                        onSignatureHelpDismiss()
                                    }
                                }
                            }
                        } else if (newValue.text.length < oldText.length) {
                            val prefix = extractIdentifierPrefix(newValue.text, newValue.selection.start)
                            onCompletionFilterChange(prefix)
                        }
                    },
                    textStyle =
                        textStyle.copy(
                            // Keep the input layer invisible so the highlighted layer shows through.
                            color = Color.Transparent,
                        ),
                    cursorBrush = SolidColor(syntaxTheme.cursor.toComposeColor()),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .horizontalScroll(horizontalScrollState)
                            .padding(start = 8.dp, top = 4.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                                // Check for format shortcut: Ctrl+Alt+L (Windows/Linux) or Cmd+Option+L (macOS)
                                val isFormatShortcut =
                                    keyEvent.key == Key.L &&
                                        keyEvent.isAltPressed &&
                                        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)

                                when {
                                    // Format document shortcut
                                    isFormatShortcut -> {
                                        onFormatDocument()
                                        true
                                    }

                                    // Ctrl+Space - manual completion trigger
                                    keyEvent.isCtrlPressed && keyEvent.key == Key.Spacebar -> {
                                        val prefix =
                                            extractIdentifierPrefix(
                                                textFieldValue.text,
                                                textFieldValue.selection.start,
                                            )
                                        onCompletionRequest(CompletionTriggerKind.Invoked, null, prefix)
                                        true
                                    }

                                    // When completion is visible, handle navigation
                                    state.completionState.isVisible -> {
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> {
                                                onCompletionMoveUp()
                                                true
                                            }

                                            Key.DirectionDown -> {
                                                onCompletionMoveDown()
                                                true
                                            }

                                            Key.Enter, Key.Tab -> {
                                                state.completionState.selectedItem?.let {
                                                    onCompletionSelect(it)
                                                }
                                                true
                                            }

                                            Key.Escape -> {
                                                onCompletionDismiss()
                                                true
                                            }

                                            else -> {
                                                false
                                            }
                                        }
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            },
                    decorationBox = { innerTextField ->
                        Box {
                            // Render syntax-highlighted text as visual layer
                            Text(
                                text = annotatedString,
                                style =
                                    TextStyle(
                                        fontFamily = JetaProgFonts.codeFont,
                                        fontSize = 14.sp,
                                        color = syntaxTheme.defaultForeground.toComposeColor(),
                                    ),
                            )
                            // Invisible input field on top (handles cursor and input)
                            Box(modifier = Modifier.matchParentSize()) {
                                innerTextField()
                            }
                        }
                    },
                )

                // Vertical scrollbar
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(verticalScrollState),
                )

                // Horizontal scrollbar
                HorizontalScrollbar(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    adapter = rememberScrollbarAdapter(horizontalScrollState),
                )

                // Completion popup
                CompletionPopup(
                    state = state.completionState,
                    onItemSelect = onCompletionSelect,
                    onDismiss = onCompletionDismiss,
                    offset = popupOffset,
                )

                // Hover popup
                HoverPopup(
                    state = state.hoverState,
                    onDismiss = onHoverDismiss,
                    offset = hoverOffset,
                )

                // Signature help popup
                SignatureHelpPopup(
                    state = state.signatureHelpState,
                    onNextSignature = onSignatureHelpNextSignature,
                    onPreviousSignature = onSignatureHelpPreviousSignature,
                    onDismiss = onSignatureHelpDismiss,
                    offset = signatureHelpOffset,
                )
            }
        }
    }
}

/**
 * Characters that auto-trigger completion.
 */
private val COMPLETION_TRIGGER_CHARACTERS = setOf('.', ':', '@')

/**
 * Characters that auto-trigger signature help.
 */
private val SIGNATURE_HELP_TRIGGER_CHARACTERS = setOf('(', ',')

/**
 * Minimum identifier length to auto-trigger completion.
 */
private const val MIN_AUTO_COMPLETION_LENGTH = 2

/**
 * Extract the identifier prefix at the given cursor position.
 */
private fun extractIdentifierPrefix(
    text: String,
    cursorPosition: Int,
): String {
    if (cursorPosition <= 0 || cursorPosition > text.length) return ""

    var start = cursorPosition - 1
    while (start >= 0) {
        val char = text[start]
        if (char.isLetterOrDigit() || char == '_') {
            start--
        } else {
            break
        }
    }
    return text.substring(start + 1, cursorPosition)
}

/**
 * Convert a character offset to a line/column position.
 */
private fun offsetToPosition(
    text: String,
    offset: Int,
): TextPosition {
    if (offset <= 0) return TextPosition.Zero
    var line = 0
    var column = 0
    var index = 0
    while (index < text.length && index < offset) {
        if (text[index] == '\n') {
            line++
            column = 0
        } else {
            column++
        }
        index++
    }
    return TextPosition(line, column)
}

/**
 * Build an AnnotatedString with syntax highlighting applied.
 */
private fun buildHighlightedText(
    text: String,
    tokens: TokenList,
    theme: SyntaxTheme,
): AnnotatedString =
    buildAnnotatedString {
        if (tokens.isEmpty()) {
            // No tokens, just append plain text
            append(text)
            return@buildAnnotatedString
        }

        var lastEnd = 0
        for (token in tokens) {
            // Add unstyled text before this token
            if (token.start > lastEnd && lastEnd < text.length) {
                val endIndex = minOf(token.start, text.length)
                append(text.substring(lastEnd, endIndex))
            }

            // Add styled token
            if (token.start < text.length) {
                val tokenEnd = minOf(token.end, text.length)
                val style = theme.styleFor(token.type)
                withStyle(style.toSpanStyle()) {
                    append(text.substring(token.start, tokenEnd))
                }
                lastEnd = tokenEnd
            }
        }

        // Add remaining text after the last token
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }

/**
 * Convert SyntaxColor to Compose Color.
 */
private fun SyntaxColor.toComposeColor(): Color =
    Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = alpha / 255f,
    )

/**
 * Convert TokenStyle to Compose SpanStyle.
 */
private fun TokenStyle.toSpanStyle(): SpanStyle =
    SpanStyle(
        color = foreground.toComposeColor(),
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )

/**
 * Empty editor placeholder when no file is open.
 */
@Composable
public fun EmptyEditorPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(IntelliJColors.editorBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(
                "JetaProg",
                fontSize = 36.sp,
                color = IntelliJColors.textSecondary,
            )
            androidx.compose.material3.Text(
                "Welcome to JetaProg IDE",
                fontSize = 16.sp,
                color = IntelliJColors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            androidx.compose.material3.Text(
                "Open a file from the Project panel to get started",
                fontSize = 14.sp,
                color = IntelliJColors.textDisabled,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
