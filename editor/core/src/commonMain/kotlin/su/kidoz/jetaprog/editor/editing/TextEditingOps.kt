package su.kidoz.jetaprog.editor.editing

/**
 * Result of a text editing operation: the new text and selection offsets.
 */
public data class EditResult(
    /**
     * The new document text.
     */
    val text: String,
    /**
     * The new selection start offset.
     */
    val selectionStart: Int,
    /**
     * The new selection end offset.
     */
    val selectionEnd: Int = selectionStart,
)

/**
 * Pure text-editing operations shared by editor front ends.
 *
 * All offsets are character offsets into the document text. Operations return
 * null when they do not apply, letting the caller fall back to default behavior.
 */
public object TextEditingOps {
    private val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val closeToOpen = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val quoteChars = setOf('"', '\'', '`')

    private data class DocumentLine(
        val content: String,
        val separator: String,
    )

    private fun splitLines(text: String): List<DocumentLine> {
        val lines = mutableListOf<DocumentLine>()
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            val separator =
                when {
                    text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n' -> "\r\n"
                    text[index] == '\r' -> "\r"
                    text[index] == '\n' -> "\n"
                    else -> null
                }
            if (separator == null) {
                index++
                continue
            }
            lines += DocumentLine(text.substring(lineStart, index), separator)
            index += separator.length
            lineStart = index
        }
        lines += DocumentLine(text.substring(lineStart), "")
        return lines
    }

    private fun joinLines(
        lines: List<DocumentLine>,
        contents: List<String>,
    ): String =
        buildString {
            contents.forEachIndexed { index, content ->
                append(content)
                append(lines[index].separator)
            }
        }

    private fun preferredLineSeparator(text: String): String {
        for (index in text.indices) {
            when (text[index]) {
                '\r' -> return if (index + 1 < text.length && text[index + 1] == '\n') "\r\n" else "\r"
                '\n' -> return "\n"
            }
        }
        return "\n"
    }

    /**
     * Insert a newline keeping the current line's indentation.
     *
     * After an opening brace the indentation is increased by [indentUnit]; when the
     * caret sits between a brace pair (`{|}`), the closing brace is moved to its own
     * line at the original indentation. The first existing line separator is used;
     * documents without one default to LF.
     */
    public fun autoIndentNewline(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        indentUnit: String = DEFAULT_INDENT_UNIT,
    ): EditResult {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val indent =
            buildString {
                var i = lineStart
                while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
                    append(text[i])
                    i++
                }
            }

        val previousChar = if (start > 0) text[start - 1] else null
        val nextChar = if (end < text.length) text[end] else null
        val opensBlock = previousChar != null && previousChar in openToClose.keys
        val separator = preferredLineSeparator(text)

        return when {
            opensBlock && nextChar != null && nextChar == openToClose[previousChar] -> {
                // Between a brace pair: expand to three lines with the caret indented
                val insertion = "$separator$indent$indentUnit$separator$indent"
                val newText = text.substring(0, start) + insertion + text.substring(end)
                val caret = start + separator.length + indent.length + indentUnit.length
                EditResult(newText, caret)
            }

            opensBlock -> {
                val insertion = "$separator$indent$indentUnit"
                val newText = text.substring(0, start) + insertion + text.substring(end)
                EditResult(newText, start + insertion.length)
            }

            else -> {
                val insertion = "$separator$indent"
                val newText = text.substring(0, start) + insertion + text.substring(end)
                EditResult(newText, start + insertion.length)
            }
        }
    }

    /**
     * Handle a just-typed character for auto-closing pairs.
     *
     * [text] is the document after the character was inserted and [caret] is the
     * offset right after it. Returns the adjusted document, or null when no
     * auto-closing applies:
     * - opening bracket: the matching closer is inserted after the caret
     * - closing bracket or quote typed before an identical character: the duplicate
     *   is removed so the caret "skips over" the existing one
     * - quote: a closing quote is inserted unless the caret touches a word character
     */
    public fun autoCloseAfterInsert(
        text: String,
        caret: Int,
        inserted: Char,
    ): EditResult? {
        if (caret < 1 || caret > text.length || text[caret - 1] != inserted) return null
        val nextChar = if (caret < text.length) text[caret] else null

        return when {
            // Skip over an existing closing char: "(|)" + ')' typed -> "()|"
            (inserted in closeToOpen.keys || inserted in quoteChars) && nextChar == inserted -> {
                EditResult(text.removeRange(caret, caret + 1), caret)
            }

            inserted in openToClose.keys && canAutoCloseBefore(nextChar) -> {
                val closing = openToClose.getValue(inserted)
                EditResult(text.substring(0, caret) + closing + text.substring(caret), caret)
            }

            inserted in quoteChars && canAutoCloseQuote(text, caret, nextChar) -> {
                EditResult(text.substring(0, caret) + inserted + text.substring(caret), caret)
            }

            else -> {
                null
            }
        }
    }

    private fun canAutoCloseBefore(nextChar: Char?): Boolean = nextChar == null || !nextChar.isLetterOrDigit()

    private fun canAutoCloseQuote(
        text: String,
        caret: Int,
        nextChar: Char?,
    ): Boolean {
        // Don't auto-close when touching a word character on either side,
        // e.g. typing an apostrophe inside a word.
        val charBeforeInserted = if (caret >= 2) text[caret - 2] else null
        val beforeIsWord =
            charBeforeInserted != null && (charBeforeInserted.isLetterOrDigit() || charBeforeInserted == '_')
        val afterIsWord = nextChar != null && (nextChar.isLetterOrDigit() || nextChar == '_')
        return !beforeIsWord && !afterIsWord
    }

    /**
     * Toggle the line comment [prefix] on all lines covered by the selection.
     *
     * Lines are commented when at least one covered non-blank line is uncommented;
     * otherwise the prefix is removed.
     */
    public fun toggleLineComment(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
    ): EditResult {
        val lines = splitLines(text)
        val startLine = lineIndexOf(text, selectionStart.coerceIn(0, text.length))
        val endLine = lineIndexOf(text, selectionEnd.coerceIn(0, text.length))

        val covered = (startLine..endLine).map { lines[it].content }
        val addComments =
            covered.any { line ->
                line.isNotBlank() && !line.trimStart().startsWith(prefix)
            }

        var startDelta = 0
        var totalDelta = 0
        val newLines =
            lines.mapIndexed { index, documentLine ->
                val line = documentLine.content
                if (index !in startLine..endLine || line.isBlank()) return@mapIndexed line
                val newLine =
                    if (addComments) {
                        val indentLength = line.length - line.trimStart().length
                        line.substring(0, indentLength) + prefix + " " + line.substring(indentLength)
                    } else {
                        uncommentLine(line, prefix)
                    }
                val delta = newLine.length - line.length
                if (index == startLine) startDelta = delta
                totalDelta += delta
                newLine
            }

        val newText = joinLines(lines, newLines)
        val newStart = (selectionStart + startDelta).coerceIn(0, newText.length)
        val newEnd = (selectionEnd + totalDelta).coerceIn(0, newText.length)
        return EditResult(newText, newStart, newEnd)
    }

    private fun uncommentLine(
        line: String,
        prefix: String,
    ): String {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(prefix)) return line
        val indentLength = line.length - trimmed.length
        val afterPrefix = trimmed.removePrefix(prefix).removePrefix(" ")
        return line.substring(0, indentLength) + afterPrefix
    }

    /**
     * Indent all lines covered by the selection by [indentUnit].
     *
     * With an empty selection the indent unit is inserted at the caret instead.
     */
    public fun indentLines(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        indentUnit: String = DEFAULT_INDENT_UNIT,
    ): EditResult {
        if (selectionStart == selectionEnd) {
            val caret = selectionStart.coerceIn(0, text.length)
            val newText = text.substring(0, caret) + indentUnit + text.substring(caret)
            return EditResult(newText, caret + indentUnit.length)
        }

        val lines = splitLines(text)
        val startLine = lineIndexOf(text, selectionStart)
        val endLine = lineIndexOf(text, selectionEnd)

        val newLines =
            lines.mapIndexed { index, documentLine ->
                val line = documentLine.content
                if (index in startLine..endLine && line.isNotEmpty()) indentUnit + line else line
            }
        val newText = joinLines(lines, newLines)
        val startDelta = if (lines[startLine].content.isNotEmpty()) indentUnit.length else 0
        val totalDelta = newText.length - text.length
        return EditResult(
            newText,
            (selectionStart + startDelta).coerceIn(0, newText.length),
            (selectionEnd + totalDelta).coerceIn(0, newText.length),
        )
    }

    /**
     * Remove up to one [indentUnit] (or fewer leading spaces/one tab) from all lines
     * covered by the selection.
     */
    public fun dedentLines(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        indentUnit: String = DEFAULT_INDENT_UNIT,
    ): EditResult {
        val lines = splitLines(text)
        val startLine = lineIndexOf(text, selectionStart.coerceIn(0, text.length))
        val endLine = lineIndexOf(text, selectionEnd.coerceIn(0, text.length))

        var startDelta = 0
        var totalDelta = 0
        val newLines =
            lines.mapIndexed { index, documentLine ->
                val line = documentLine.content
                if (index !in startLine..endLine) return@mapIndexed line
                val removed = dedentAmount(line, indentUnit)
                val newLine = line.substring(removed)
                if (index == startLine) startDelta = -removed
                totalDelta -= removed
                newLine
            }

        val newText = joinLines(lines, newLines)
        return EditResult(
            newText,
            (selectionStart + startDelta).coerceAtLeast(0).coerceAtMost(newText.length),
            (selectionEnd + totalDelta).coerceAtLeast(0).coerceAtMost(newText.length),
        )
    }

    private fun dedentAmount(
        line: String,
        indentUnit: String,
    ): Int =
        when {
            line.startsWith(indentUnit) -> indentUnit.length
            line.startsWith("\t") -> 1
            else -> line.takeWhile { it == ' ' }.length.coerceAtMost(indentUnit.length)
        }

    /**
     * Move the lines covered by the selection up or down by one line.
     *
     * Returns null when the move would go past the start or end of the document.
     */
    public fun moveLines(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        up: Boolean,
    ): EditResult? {
        val lines = splitLines(text)
        val startLine = lineIndexOf(text, selectionStart.coerceIn(0, text.length))
        val hasFinalSeparator = lines.size > 1 && lines.last().content.isEmpty()
        val lastMovableLine = lines.lastIndex - if (hasFinalSeparator) 1 else 0
        if (startLine > lastMovableLine) return null
        val endLine =
            lineIndexOf(text, selectionEnd.coerceIn(0, text.length)).coerceAtMost(lastMovableLine)

        if (up && startLine == 0) return null
        if (!up && endLine == lastMovableLine) return null

        val contents = lines.map { it.content }.toMutableList()
        if (up) {
            // Move the line above to just below the selected block
            val above = contents.removeAt(startLine - 1)
            contents.add(endLine, above)
        } else {
            // Move the line below to just above the selected block
            val below = contents.removeAt(endLine + 1)
            contents.add(startLine, below)
        }

        val newText = joinLines(lines, contents)
        val delta =
            if (up) {
                -(lines[startLine - 1].content.length + lines[startLine - 1].separator.length)
            } else {
                lines[endLine + 1].content.length + lines[endLine].separator.length
            }
        return EditResult(
            newText,
            (selectionStart + delta).coerceIn(0, newText.length),
            (selectionEnd + delta).coerceIn(0, newText.length),
        )
    }

    /**
     * Find the bracket at or immediately before [caret] and its matching partner.
     *
     * Returns the pair of offsets (bracket, match) or null when the caret is not
     * adjacent to a bracket or no match exists.
     */
    public fun findMatchingBracket(
        text: String,
        caret: Int,
    ): Pair<Int, Int>? {
        val at = caret.coerceIn(0, text.length)
        val candidate =
            when {
                at < text.length && isBracket(text[at]) -> at
                at > 0 && isBracket(text[at - 1]) -> at - 1
                else -> return null
            }

        val char = text[candidate]
        return openToClose[char]?.let { close ->
            scanForward(text, candidate, char, close)?.let { candidate to it }
        } ?: closeToOpen[char]?.let { open ->
            scanBackward(text, candidate, open, char)?.let { candidate to it }
        }
    }

    private fun isBracket(char: Char): Boolean = char in openToClose.keys || char in closeToOpen.keys

    private fun scanForward(
        text: String,
        from: Int,
        open: Char,
        close: Char,
    ): Int? {
        var depth = 0
        for (i in from until text.length) {
            when (text[i]) {
                open -> {
                    depth++
                }

                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun scanBackward(
        text: String,
        from: Int,
        open: Char,
        close: Char,
    ): Int? {
        var depth = 0
        for (i in from downTo 0) {
            when (text[i]) {
                close -> {
                    depth++
                }

                open -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /**
     * The line index (0-based) containing the given character offset.
     */
    public fun lineIndexOf(
        text: String,
        offset: Int,
    ): Int {
        var line = 0
        for (i in 0 until offset.coerceAtMost(text.length)) {
            if (text[i] == '\n' || (text[i] == '\r' && (i + 1 == text.length || text[i + 1] != '\n'))) {
                line++
            }
        }
        return line
    }

    /**
     * The default indentation unit (four spaces).
     */
    public const val DEFAULT_INDENT_UNIT: String = "    "
}
