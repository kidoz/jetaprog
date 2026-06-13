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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
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
import su.kidoz.jetaprog.editor.editing.CommentSyntax
import su.kidoz.jetaprog.editor.editing.EditResult
import su.kidoz.jetaprog.editor.editing.TextEditingOps
import su.kidoz.jetaprog.editor.search.FindMatch
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorIntent
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
    onIntent: (EditorIntent) -> Unit = {},
    indentUnit: String = TextEditingOps.DEFAULT_INDENT_UNIT,
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

    // Apply a text-editing operation result to the field and propagate the change
    val applyEdit: (EditResult) -> Unit = { result ->
        lastKnownContent = result.text
        textFieldValue =
            TextFieldValue(
                text = result.text,
                selection = TextRange(result.selectionStart, result.selectionEnd),
            )
        onContentChange(result.text)
    }

    val commentPrefix = remember(state.languageId) { CommentSyntax.lineCommentPrefix(state.languageId) }

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

    // Select and reveal the current find match
    LaunchedEffect(state.findReplaceState.currentMatchIndex, state.findReplaceState.matches) {
        val match = state.findReplaceState.currentMatch ?: return@LaunchedEffect
        val length = textFieldValue.text.length
        if (match.start > length || match.end > length) return@LaunchedEffect

        textFieldValue = textFieldValue.copy(selection = TextRange(match.start, match.end))

        // Scroll the match into view, roughly centered
        val matchLine = offsetToPosition(textFieldValue.text, match.start).line
        val lineHeight = with(density) { 21.dp.roundToPx() }
        val target = (matchLine * lineHeight - verticalScrollState.viewportSize / 2).coerceAtLeast(0)
        verticalScrollState.animateScrollTo(target)
    }

    // Diagnostic ranges converted to character offsets for underline rendering
    val diagnosticSpans =
        remember(state.content, state.diagnostics) {
            state.diagnostics.map { diagnostic ->
                val start = positionToOffset(state.content, diagnostic.range.start)
                val end = positionToOffset(state.content, diagnostic.range.end)
                DiagnosticSpan(
                    start = start,
                    // Widen empty ranges so they stay visible
                    end = if (end > start) end else (start + 1).coerceAtMost(state.content.length),
                    severity = diagnostic.severity,
                )
            }
        }

    val annotatedString =
        remember(
            state.content,
            state.tokens,
            syntaxTheme,
            state.findReplaceState.matches,
            state.findReplaceState.currentMatchIndex,
            diagnosticSpans,
        ) {
            buildHighlightedText(
                state.content,
                state.tokens,
                syntaxTheme,
                state.findReplaceState.matches,
                state.findReplaceState.currentMatchIndex,
                diagnosticSpans,
            )
        }

    // Matching bracket pair at the caret
    val bracketPair =
        remember(textFieldValue.text, textFieldValue.selection) {
            if (textFieldValue.selection.collapsed) {
                TextEditingOps.findMatchingBracket(textFieldValue.text, textFieldValue.selection.start)
            } else {
                null
            }
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
    val editorPaddingTopPx = with(density) { 4.dp.roundToPx() }
    val charWidthDp = with(density) { charWidthPx.toDp() }
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

    Column(modifier = modifier.background(syntaxTheme.background.toComposeColor())) {
        if (state.findReplaceState.isVisible) {
            FindReplaceBar(
                state = state.findReplaceState,
                onIntent = onIntent,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(state.activeDocumentUri) {
                                // Track the mouse to request hover info for the symbol under it
                                var lastHoverPosition: TextPosition? = null
                                var cachedText = ""
                                var cachedLines = listOf("")
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        when (event.type) {
                                            PointerEventType.Move -> {
                                                if (event.buttons.isPrimaryPressed) continue
                                                val pointer =
                                                    event.changes.firstOrNull()?.position ?: continue
                                                val text = textFieldValue.text
                                                if (text != cachedText) {
                                                    cachedText = text
                                                    cachedLines = text.lines()
                                                }
                                                val position =
                                                    pointerTextPosition(
                                                        pointer = pointer,
                                                        lines = cachedLines,
                                                        scrollY = verticalScrollState.value - editorPaddingTopPx,
                                                        scrollX = horizontalScrollState.value - editorPaddingStartPx,
                                                        lineHeightPx = lineHeightPx,
                                                        charWidthPx = charWidthPx,
                                                    )
                                                if (position != lastHoverPosition) {
                                                    lastHoverPosition = position
                                                    if (position != null) {
                                                        onHoverRequest(position)
                                                    } else {
                                                        onHoverDismiss()
                                                    }
                                                }
                                            }

                                            PointerEventType.Exit -> {
                                                lastHoverPosition = null
                                                onHoverDismiss()
                                            }
                                        }
                                    }
                                }
                            },
                ) {
                    // Current line highlight (behind the text layer)
                    if (textFieldValue.selection.collapsed) {
                        val caretLine =
                            offsetToPosition(textFieldValue.text, textFieldValue.selection.start).line
                        Box(
                            modifier =
                                Modifier
                                    .offset {
                                        IntOffset(
                                            0,
                                            editorPaddingTopPx + caretLine * lineHeightPx -
                                                verticalScrollState.value,
                                        )
                                    }.fillMaxWidth()
                                    .height(21.dp)
                                    .background(IntelliJColors.editorCaretRow),
                        )
                    }

                    // Matching bracket highlights (behind the text layer)
                    bracketPair?.let { (first, second) ->
                        listOf(first, second).forEach { bracketOffset ->
                            val position = offsetToPosition(textFieldValue.text, bracketOffset)
                            Box(
                                modifier =
                                    Modifier
                                        .offset {
                                            IntOffset(
                                                editorPaddingStartPx + position.column * charWidthPx -
                                                    horizontalScrollState.value,
                                                editorPaddingTopPx + position.line * lineHeightPx -
                                                    verticalScrollState.value,
                                            )
                                        }.size(width = charWidthDp, height = 21.dp)
                                        .background(IntelliJColors.accentSubtle),
                            )
                        }
                    }

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val oldText = textFieldValue.text
                            val oldSelection = textFieldValue.selection

                            // Detect a single typed character (for auto-close and triggers)
                            val typedChar =
                                if (newValue.text.length == oldText.length + 1 &&
                                    newValue.selection.collapsed &&
                                    newValue.selection.start > 0 &&
                                    oldText ==
                                    newValue.text.removeRange(
                                        newValue.selection.start - 1,
                                        newValue.selection.start,
                                    )
                                ) {
                                    newValue.text[newValue.selection.start - 1]
                                } else {
                                    null
                                }

                            // Auto-close brackets/quotes or skip over an existing closer
                            val processed =
                                typedChar
                                    ?.let {
                                        TextEditingOps.autoCloseAfterInsert(
                                            newValue.text,
                                            newValue.selection.start,
                                            it,
                                        )
                                    }?.let {
                                        TextFieldValue(
                                            text = it.text,
                                            selection = TextRange(it.selectionStart, it.selectionEnd),
                                        )
                                    } ?: newValue

                            textFieldValue = processed
                            if (processed.selection != oldSelection) {
                                onCursorMove(offsetToPosition(processed.text, processed.selection.start))
                            }
                            if (processed.text != state.content) {
                                // Update lastKnownContent to prevent LaunchedEffect from resetting cursor
                                lastKnownContent = processed.text
                                onContentChange(processed.text)

                                // Check for trigger characters to auto-trigger completion
                                if (typedChar != null) {
                                    // Completion triggers for special characters
                                    if (typedChar in COMPLETION_TRIGGER_CHARACTERS) {
                                        val prefix =
                                            extractIdentifierPrefix(processed.text, processed.selection.start)
                                        onCompletionRequest(
                                            CompletionTriggerKind.TriggerCharacter,
                                            typedChar,
                                            prefix,
                                        )
                                    } else if (typedChar.isLetterOrDigit() || typedChar == '_') {
                                        // Auto-trigger completion when typing identifiers (after 2+ chars)
                                        val prefix =
                                            extractIdentifierPrefix(processed.text, processed.selection.start)
                                        onCompletionFilterChange(prefix)
                                        if (prefix.length >= MIN_AUTO_COMPLETION_LENGTH) {
                                            onCompletionRequest(CompletionTriggerKind.Invoked, null, prefix)
                                        }
                                    }
                                    // Signature help triggers
                                    if (typedChar in SIGNATURE_HELP_TRIGGER_CHARACTERS) {
                                        onSignatureHelpRequest(typedChar)
                                    }
                                }
                            } else if (processed.text.length < oldText.length) {
                                val prefix = extractIdentifierPrefix(processed.text, processed.selection.start)
                                onCompletionFilterChange(prefix)
                            }
                            // Dismiss signature help on closing paren (also fires on skip-over)
                            if (typedChar == ')') {
                                onSignatureHelpDismiss()
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
                                    val ctrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                    val plainCtrlOrMeta =
                                        ctrlOrMeta && !keyEvent.isAltPressed && !keyEvent.isShiftPressed

                                    when {
                                        // Format document shortcut
                                        isFormatShortcut -> {
                                            onFormatDocument()
                                            true
                                        }

                                        // Redo: Ctrl+Shift+Z / Cmd+Shift+Z
                                        ctrlOrMeta && keyEvent.isShiftPressed && keyEvent.key == Key.Z -> {
                                            onIntent(EditorIntent.Redo)
                                            true
                                        }

                                        // Undo: Ctrl+Z / Cmd+Z
                                        plainCtrlOrMeta && keyEvent.key == Key.Z -> {
                                            onIntent(EditorIntent.Undo)
                                            true
                                        }

                                        // Find: Ctrl+F / Cmd+F
                                        plainCtrlOrMeta && keyEvent.key == Key.F -> {
                                            onIntent(EditorIntent.OpenFindBar(withReplace = false))
                                            true
                                        }

                                        // Replace: Ctrl+R / Cmd+R
                                        plainCtrlOrMeta && keyEvent.key == Key.R -> {
                                            onIntent(EditorIntent.OpenFindBar(withReplace = true))
                                            true
                                        }

                                        // Next/previous match: F3 / Shift+F3
                                        keyEvent.key == Key.F3 && state.findReplaceState.isVisible -> {
                                            onIntent(
                                                if (keyEvent.isShiftPressed) {
                                                    EditorIntent.FindPrevious
                                                } else {
                                                    EditorIntent.FindNext
                                                },
                                            )
                                            true
                                        }

                                        // Duplicate line: Ctrl+D / Cmd+D
                                        plainCtrlOrMeta && keyEvent.key == Key.D -> {
                                            onIntent(EditorIntent.DuplicateLine)
                                            true
                                        }

                                        // Delete line: Ctrl+Y / Cmd+Y
                                        plainCtrlOrMeta && keyEvent.key == Key.Y -> {
                                            onIntent(EditorIntent.DeleteLine)
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

                                        // Smart Enter with auto-indent
                                        keyEvent.key == Key.Enter &&
                                            !ctrlOrMeta &&
                                            !keyEvent.isAltPressed &&
                                            !keyEvent.isShiftPressed -> {
                                            applyEdit(
                                                TextEditingOps.autoIndentNewline(
                                                    textFieldValue.text,
                                                    textFieldValue.selection.min,
                                                    textFieldValue.selection.max,
                                                    indentUnit,
                                                ),
                                            )
                                            true
                                        }

                                        // Dedent: Shift+Tab
                                        keyEvent.key == Key.Tab && keyEvent.isShiftPressed -> {
                                            applyEdit(
                                                TextEditingOps.dedentLines(
                                                    textFieldValue.text,
                                                    textFieldValue.selection.min,
                                                    textFieldValue.selection.max,
                                                    indentUnit,
                                                ),
                                            )
                                            true
                                        }

                                        // Indent: Tab
                                        keyEvent.key == Key.Tab -> {
                                            applyEdit(
                                                TextEditingOps.indentLines(
                                                    textFieldValue.text,
                                                    textFieldValue.selection.min,
                                                    textFieldValue.selection.max,
                                                    indentUnit,
                                                ),
                                            )
                                            true
                                        }

                                        // Toggle line comment: Ctrl+/ or Cmd+/
                                        plainCtrlOrMeta && keyEvent.key == Key.Slash -> {
                                            commentPrefix?.let { prefix ->
                                                applyEdit(
                                                    TextEditingOps.toggleLineComment(
                                                        textFieldValue.text,
                                                        textFieldValue.selection.min,
                                                        textFieldValue.selection.max,
                                                        prefix,
                                                    ),
                                                )
                                            }
                                            true
                                        }

                                        // Move lines up/down: Alt+Shift+Up / Alt+Shift+Down
                                        keyEvent.isAltPressed &&
                                            keyEvent.isShiftPressed &&
                                            (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown) -> {
                                            TextEditingOps
                                                .moveLines(
                                                    textFieldValue.text,
                                                    textFieldValue.selection.min,
                                                    textFieldValue.selection.max,
                                                    up = keyEvent.key == Key.DirectionUp,
                                                )?.let(applyEdit)
                                            true
                                        }

                                        // Close find bar with Escape
                                        keyEvent.key == Key.Escape && state.findReplaceState.isVisible -> {
                                            onIntent(EditorIntent.CloseFindBar)
                                            true
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
 * A diagnostic mapped to character offsets for rendering.
 */
private data class DiagnosticSpan(
    val start: Int,
    val end: Int,
    val severity: DiagnosticSeverity,
)

/**
 * Map a pointer location to the text position under it, or null when the pointer
 * is not over a character.
 */
private fun pointerTextPosition(
    pointer: Offset,
    lines: List<String>,
    scrollY: Int,
    scrollX: Int,
    lineHeightPx: Int,
    charWidthPx: Int,
): TextPosition? {
    val line = ((pointer.y + scrollY) / lineHeightPx).toInt()
    val column = ((pointer.x + scrollX) / charWidthPx).toInt()
    val onCharacter = line in lines.indices && column >= 0 && column < lines[line].length
    return if (onCharacter) TextPosition(line, column) else null
}

/**
 * Convert a line/column position to a character offset.
 */
private fun positionToOffset(
    text: String,
    position: TextPosition,
): Int {
    if (position.line <= 0 && position.column <= 0) return 0
    var index = 0
    var line = 0
    while (index < text.length && line < position.line) {
        if (text[index] == '\n') line++
        index++
    }
    return (index + position.column).coerceIn(0, text.length)
}

/**
 * Build an AnnotatedString with syntax highlighting, diagnostics, and find-match
 * highlighting applied.
 */
private fun buildHighlightedText(
    text: String,
    tokens: TokenList,
    theme: SyntaxTheme,
    findMatches: List<FindMatch> = emptyList(),
    currentMatchIndex: Int = -1,
    diagnostics: List<DiagnosticSpan> = emptyList(),
): AnnotatedString =
    buildAnnotatedString {
        if (tokens.isEmpty()) {
            // No tokens, just append plain text
            append(text)
        } else {
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

        // Overlay diagnostic underlines (find matches draw on top)
        diagnostics.forEach { diagnostic ->
            if (diagnostic.start < text.length) {
                val background =
                    when (diagnostic.severity) {
                        DiagnosticSeverity.ERROR -> IntelliJColors.errorMuted
                        DiagnosticSeverity.WARNING -> IntelliJColors.warningMuted
                        DiagnosticSeverity.INFORMATION, DiagnosticSeverity.HINT -> IntelliJColors.infoMuted
                    }
                addStyle(
                    SpanStyle(background = background, textDecoration = TextDecoration.Underline),
                    diagnostic.start,
                    diagnostic.end.coerceAtMost(text.length),
                )
            }
        }

        // Overlay find-match highlighting
        findMatches.forEachIndexed { index, match ->
            if (match.start < text.length) {
                val background =
                    if (index == currentMatchIndex) {
                        IntelliJColors.accentMuted
                    } else {
                        IntelliJColors.editorSelection
                    }
                addStyle(
                    SpanStyle(background = background),
                    match.start,
                    match.end.coerceAtMost(text.length),
                )
            }
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
