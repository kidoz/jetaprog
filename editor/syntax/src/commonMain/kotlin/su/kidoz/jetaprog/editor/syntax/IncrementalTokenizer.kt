package su.kidoz.jetaprog.editor.syntax

/**
 * Incrementally tokenizes a document by caching per-line tokens and the
 * lexer state at each line boundary.
 *
 * On every [tokenize] call only the lines that changed — plus any following
 * lines whose entry [LexerState] changed (e.g. after opening a block
 * comment) — are re-lexed; the rest is reused from the cache. This turns the
 * per-keystroke cost from O(document) lexing into O(changed region) lexing
 * plus an O(tokens) rebase pass.
 *
 * Instances are not thread-safe; use one per document from a single thread.
 */
public class IncrementalTokenizer(
    private val lexer: Lexer,
) {
    /**
     * A cached line: its exact text (excluding the trailing newline), the
     * tokens with line-relative start offsets, and the lexer state after the
     * line.
     */
    private class CachedLine(
        val text: String,
        val tokens: List<Token>,
        val endState: LexerState,
    )

    private var cache: List<CachedLine> = emptyList()

    /**
     * Tokenizes [content], reusing cached lines where possible.
     */
    public fun tokenize(content: String): TokenList {
        val newLines = splitLines(content)
        cache = updateCache(newLines)
        return materialize(newLines)
    }

    /**
     * Drops all cached state, forcing the next [tokenize] to re-lex fully.
     */
    public fun reset() {
        cache = emptyList()
    }

    private fun updateCache(newLines: List<String>): List<CachedLine> {
        val oldCache = cache

        // Longest prefix of unchanged lines
        var prefix = 0
        while (prefix < newLines.size && prefix < oldCache.size && newLines[prefix] == oldCache[prefix].text) {
            prefix++
        }

        // Longest suffix of unchanged lines, not overlapping the prefix
        var suffix = 0
        while (
            suffix < newLines.size - prefix &&
            suffix < oldCache.size - prefix &&
            newLines[newLines.size - 1 - suffix] == oldCache[oldCache.size - 1 - suffix].text
        ) {
            suffix++
        }

        val result = ArrayList<CachedLine>(newLines.size)
        result.addAll(oldCache.subList(0, prefix))

        var state = oldCache.getOrNull(prefix - 1)?.endState ?: LexerState.Initial
        var index = prefix
        while (index < newLines.size) {
            // Once inside the unchanged suffix, stop re-lexing as soon as the
            // incoming state matches what the cached line was lexed with.
            if (index >= newLines.size - suffix) {
                val oldIndex = index - (newLines.size - oldCache.size)
                val oldEntryState = oldCache.getOrNull(oldIndex - 1)?.endState ?: LexerState.Initial
                if (state == oldEntryState) {
                    result.addAll(oldCache.subList(oldIndex, oldCache.size))
                    return result
                }
            }

            val (tokens, endState) = lexer.tokenizeLine(newLines[index], index, 0, state)
            result.add(CachedLine(text = newLines[index], tokens = tokens, endState = endState))
            state = endState
            index++
        }
        return result
    }

    private fun materialize(newLines: List<String>): TokenList {
        val tokens = ArrayList<Token>()
        var lineStart = 0
        cache.forEachIndexed { lineIndex, line ->
            line.tokens.forEach { token ->
                tokens.add(token.copy(start = lineStart + token.start, line = lineIndex))
            }
            lineStart += newLines[lineIndex].length + 1
        }
        return TokenList(tokens)
    }

    private fun splitLines(content: String): List<String> {
        val lines = ArrayList<String>()
        var start = 0
        while (true) {
            val newline = content.indexOf('\n', start)
            if (newline == -1) {
                lines.add(content.substring(start))
                return lines
            }
            lines.add(content.substring(start, newline))
            start = newline + 1
        }
    }
}
