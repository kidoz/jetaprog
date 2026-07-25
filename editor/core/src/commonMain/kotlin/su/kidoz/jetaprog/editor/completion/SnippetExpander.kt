package su.kidoz.jetaprog.editor.completion

/**
 * A snippet with its placeholders removed and the caret position resolved.
 *
 * @property text The literal text to insert.
 * @property caretOffset Where the caret goes, relative to the start of [text].
 */
public data class ExpandedSnippet(
    val text: String,
    val caretOffset: Int,
)

/**
 * Expands LSP snippet syntax into plain text plus a caret position.
 *
 * Handles `$0`, `$1`, `${1:default}`, `${1|a,b|}` and `\$` escapes, including nested
 * placeholders such as `${1:${2:x}}`.
 *
 * Placeholder *default text* is dropped rather than inserted. clangd answers a function
 * completion with `square(${1:int x})`; keeping the default would put the parameter
 * declaration into the buffer as `square(int x)`, which does not compile. Dropping it
 * yields `square()` with the caret between the parentheses.
 *
 * There is no tab-stop navigation yet, so only the caret position is honoured: `$0` when
 * present, otherwise the lowest-numbered placeholder, otherwise the end of the text.
 */
public object SnippetExpander {
    /**
     * Expands [snippet] into literal text and a caret offset.
     */
    public fun expand(snippet: String): ExpandedSnippet {
        val text = StringBuilder()
        var finalStop: Int? = null
        var firstStop: Pair<Int, Int>? = null // tab stop number to offset

        var index = 0
        while (index < snippet.length) {
            val char = snippet[index]
            when {
                char == '\\' && index + 1 < snippet.length && snippet[index + 1] == '$' -> {
                    text.append('$')
                    index += 2
                }

                char == '$' -> {
                    val stop = readTabStop(snippet, index)
                    if (stop == null) {
                        text.append(char)
                        index++
                    } else {
                        if (stop.number == 0) {
                            // $0 is the explicit final caret position and always wins.
                            if (finalStop == null) finalStop = text.length
                        } else if (firstStop == null || stop.number < firstStop.first) {
                            firstStop = stop.number to text.length
                        }
                        index = stop.endIndex
                    }
                }

                else -> {
                    text.append(char)
                    index++
                }
            }
        }

        val caret = finalStop ?: firstStop?.second ?: text.length
        return ExpandedSnippet(text.toString(), caret.coerceIn(0, text.length))
    }

    private data class TabStop(
        val number: Int,
        val endIndex: Int,
    )

    /**
     * Reads a tab stop starting at the `$` in [index], or null when this `$` does not
     * begin one and should be treated as a literal.
     */
    private fun readTabStop(
        snippet: String,
        index: Int,
    ): TabStop? {
        val next = snippet.getOrNull(index + 1) ?: return null

        if (next.isDigit()) {
            var end = index + 1
            while (end < snippet.length && snippet[end].isDigit()) end++
            return TabStop(snippet.substring(index + 1, end).toInt(), end)
        }

        if (next != '{') return null

        var cursor = index + 2
        val numberStart = cursor
        while (cursor < snippet.length && snippet[cursor].isDigit()) cursor++
        if (cursor == numberStart) return null
        val number = snippet.substring(numberStart, cursor).toInt()

        val separator = snippet.getOrNull(cursor)
        if (separator == '}') return TabStop(number, cursor + 1)
        if (separator != ':' && separator != '|') return null

        // Skip the body, tracking nesting so ${1:${2:x}} consumes its inner stop too.
        var depth = 1
        while (cursor < snippet.length) {
            when (snippet[cursor]) {
                '\\' -> {
                    cursor++
                }

                '{' -> {
                    depth++
                }

                '}' -> {
                    depth--
                    if (depth == 0) return TabStop(number, cursor + 1)
                }
            }
            cursor++
        }
        // Unterminated: treat the rest as consumed so the braces are not inserted.
        return TabStop(number, snippet.length)
    }
}
