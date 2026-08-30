package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import su.kidoz.jetaprog.app.ui.editor.toComposeColor
import su.kidoz.jetaprog.app.ui.editor.toSpanStyle
import su.kidoz.jetaprog.editor.syntax.BuiltinLexers
import su.kidoz.jetaprog.editor.syntax.LexerRegistry
import su.kidoz.jetaprog.editor.syntax.TokenType
import su.kidoz.jetaprog.editor.syntax.highlighting.SyntaxTheme

/** Tokens per line above which word-level diffing is skipped. */
internal const val MAX_INTRALINE_TOKENS = 96

/**
 * Word-level change ranges for a removed/added line pair (IntelliJ's
 * "intraline" highlighting): the ranges mark the parts of each line that the
 * other side does not contain.
 *
 * @property baseRanges Changed ranges inside the removed (old) line.
 * @property changedRanges Changed ranges inside the added (new) line.
 */
internal data class IntralineRanges(
    val baseRanges: List<IntRange>,
    val changedRanges: List<IntRange>,
)

/** Extension → lexer language id for the lexers registered by [BuiltinLexers]. */
private val EXTENSION_LANGUAGE_IDS: Map<String, String> =
    mapOf(
        "kt" to "kotlin",
        "kts" to "kotlin",
        "java" to "java",
        "go" to "go",
        "rs" to "rust",
        "py" to "python",
        "js" to "javascript",
        "jsx" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "c" to "c",
        "h" to "c",
        "cpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "hpp" to "cpp",
        "cs" to "csharp",
        "vala" to "vala",
        "vapi" to "vala",
        "sql" to "sql",
        "md" to "markdown",
        "toml" to "toml",
        "xml" to "xml",
        "html" to "xml",
        "prolog" to "dotprolog",
        "pl" to "dotprolog",
    )

/**
 * Highlights one diff line with the file type's lexer, or returns plain text
 * when no lexer is registered for [extension].
 */
internal fun highlightDiffLine(
    text: String,
    extension: String,
    theme: SyntaxTheme,
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString(text)
    val languageId = EXTENSION_LANGUAGE_IDS[extension.lowercase()] ?: return AnnotatedString(text)
    val lexer = LexerRegistry.get(languageId) ?: return AnnotatedString(text)

    return buildAnnotatedString {
        var lastEnd = 0
        for (token in lexer.tokenize(text)) {
            if (token.start > lastEnd) {
                append(text.substring(lastEnd, token.start))
            }
            val tokenEnd = minOf(token.end, text.length)
            if (tokenEnd > token.start) {
                withStyle(theme.styleFor(token.type).toSpanStyle()) {
                    append(text.substring(token.start, tokenEnd))
                }
                lastEnd = tokenEnd
            }
        }
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

/**
 * Computes the word-level (intraline) change ranges between a removed line and
 * its added counterpart, pairing their word tokens with a bounded LCS so cost
 * stays linear-ish per line. Returns null when either line is too long to be
 * worth diffing or the lines share no words.
 */
internal fun intralineChangeRanges(
    base: String,
    changed: String,
): IntralineRanges? {
    if (base.isEmpty() || changed.isEmpty()) return null
    val baseTokens = wordTokens(base)
    val changedTokens = wordTokens(changed)
    if (baseTokens.size > MAX_INTRALINE_TOKENS || changedTokens.size > MAX_INTRALINE_TOKENS) return null

    // LCS over token texts (case-sensitive: case-only changes are worth marking).
    val lcs = Array(baseTokens.size + 1) { IntArray(changedTokens.size + 1) }
    for (i in baseTokens.indices.reversed()) {
        for (j in changedTokens.indices.reversed()) {
            lcs[i][j] =
                if (baseTokens[i].text == changedTokens[j].text) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
        }
    }

    val baseChanged = mutableListOf<IntRange>()
    val changedChanged = mutableListOf<IntRange>()
    var i = 0
    var j = 0
    while (i < baseTokens.size && j < changedTokens.size) {
        when {
            baseTokens[i].text == changedTokens[j].text -> {
                i++
                j++
            }

            lcs[i + 1][j] >= lcs[i][j + 1] -> {
                baseChanged += baseTokens[i].range
                i++
            }

            else -> {
                changedChanged += changedTokens[j].range
                j++
            }
        }
    }
    while (i < baseTokens.size) {
        baseChanged += baseTokens[i].range
        i++
    }
    while (j < changedTokens.size) {
        changedChanged += changedTokens[j].range
        j++
    }
    if (baseChanged.isEmpty() && changedChanged.isEmpty()) return null

    return IntralineRanges(
        baseRanges = coalesceRanges(baseChanged),
        changedRanges = coalesceRanges(changedChanged),
    )
}

internal data class WordToken(
    val text: String,
    val range: IntRange,
)

private fun wordTokens(text: String): List<WordToken> {
    val tokens = mutableListOf<WordToken>()
    var start = 0
    while (start < text.length) {
        val isWord = text[start].isLetterOrDigit()
        var end = start + 1
        while (end < text.length && text[end].isLetterOrDigit() == isWord) {
            end++
        }
        tokens += WordToken(text = text.substring(start, end), range = start until end)
        start = end
    }
    return tokens
}

private fun coalesceRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return ranges
    val sorted = ranges.sortedBy { it.first }
    val result = mutableListOf(sorted.first())
    for (range in sorted.drop(1)) {
        val last = result.last()
        if (range.first <= last.last + 1) {
            result[result.size - 1] = last.first..maxOf(last.last, range.last)
        } else {
            result += range
        }
    }
    return result
}
