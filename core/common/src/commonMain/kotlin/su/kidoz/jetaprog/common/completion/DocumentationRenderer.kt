package su.kidoz.jetaprog.common.completion

/**
 * Represents a span of styled text for rendering documentation.
 */
public data class StyledSpan(
    /**
     * The text content.
     */
    val text: String,
    /**
     * The style of the span.
     */
    val style: SpanStyle = SpanStyle.NORMAL,
)

/**
 * Styles for documentation text spans.
 */
public enum class SpanStyle {
    /**
     * Normal text.
     */
    NORMAL,

    /**
     * Bold text.
     */
    BOLD,

    /**
     * Italic text.
     */
    ITALIC,

    /**
     * Inline code (monospace).
     */
    CODE,

    /**
     * Code block (monospace, multi-line).
     */
    CODE_BLOCK,

    /**
     * A heading.
     */
    HEADING,

    /**
     * A link/reference.
     */
    LINK,

    /**
     * A separator line.
     */
    SEPARATOR,

    /**
     * Parameter name in signature.
     */
    PARAMETER,

    /**
     * Type name.
     */
    TYPE,

    /**
     * Deprecated text (strikethrough).
     */
    DEPRECATED,
}

/**
 * Rendered documentation as a list of styled spans.
 */
public data class RenderedDocumentation(
    /**
     * The styled spans making up the documentation.
     */
    val spans: List<StyledSpan>,
) {
    /**
     * The plain text content (all spans concatenated).
     */
    public val plainText: String get() = spans.joinToString("") { it.text }

    /**
     * Whether the documentation is empty.
     */
    public val isEmpty: Boolean get() = spans.isEmpty()

    public companion object {
        /**
         * Empty documentation.
         */
        public val EMPTY: RenderedDocumentation = RenderedDocumentation(emptyList())
    }
}

/**
 * Renders markdown documentation into styled spans for display
 * in the completion popup detail panel.
 *
 * Supports a subset of markdown:
 * - **bold**, *italic*, `inline code`
 * - Code blocks (```)
 * - Headings (#, ##, ###)
 * - Horizontal rules (---, ***)
 * - Basic paragraph separation
 */
public object DocumentationRenderer {
    /**
     * Renders markdown text into styled spans.
     *
     * @param markdown The markdown text to render
     * @return Rendered documentation with styled spans
     */
    public fun render(markdown: String?): RenderedDocumentation {
        if (markdown.isNullOrBlank()) return RenderedDocumentation.EMPTY

        val spans = mutableListOf<StyledSpan>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Code block
                trimmed.startsWith("```") -> {
                    i++
                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    if (codeLines.isNotEmpty()) {
                        spans.add(StyledSpan(codeLines.joinToString("\n"), SpanStyle.CODE_BLOCK))
                        spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                    }
                }

                // Heading
                trimmed.startsWith("###") -> {
                    spans.add(StyledSpan(trimmed.removePrefix("###").trim(), SpanStyle.HEADING))
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }

                trimmed.startsWith("##") -> {
                    spans.add(StyledSpan(trimmed.removePrefix("##").trim(), SpanStyle.HEADING))
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }

                trimmed.startsWith("#") -> {
                    spans.add(StyledSpan(trimmed.removePrefix("#").trim(), SpanStyle.HEADING))
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }

                // Horizontal rule
                trimmed.matches(Regex("^[-*_]{3,}$")) -> {
                    spans.add(StyledSpan("", SpanStyle.SEPARATOR))
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }

                // Empty line = paragraph break
                trimmed.isEmpty() -> {
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }

                // Regular text with inline formatting
                else -> {
                    spans.addAll(parseInlineFormatting(trimmed))
                    spans.add(StyledSpan("\n", SpanStyle.NORMAL))
                }
            }
            i++
        }

        // Remove trailing newline
        if (spans.isNotEmpty() && spans.last().text == "\n") {
            return RenderedDocumentation(spans.dropLast(1))
        }

        return RenderedDocumentation(spans)
    }

    private fun parseInlineFormatting(text: String): List<StyledSpan> {
        val spans = mutableListOf<StyledSpan>()
        var pos = 0

        while (pos < text.length) {
            when {
                // Inline code
                text[pos] == '`' -> {
                    val end = text.indexOf('`', pos + 1)
                    if (end > pos) {
                        spans.add(StyledSpan(text.substring(pos + 1, end), SpanStyle.CODE))
                        pos = end + 1
                    } else {
                        spans.add(StyledSpan("`", SpanStyle.NORMAL))
                        pos++
                    }
                }

                // Bold
                pos + 1 < text.length && text[pos] == '*' && text[pos + 1] == '*' -> {
                    val end = text.indexOf("**", pos + 2)
                    if (end > pos) {
                        spans.add(StyledSpan(text.substring(pos + 2, end), SpanStyle.BOLD))
                        pos = end + 2
                    } else {
                        spans.add(StyledSpan("**", SpanStyle.NORMAL))
                        pos += 2
                    }
                }

                // Italic
                text[pos] == '*' -> {
                    val end = text.indexOf('*', pos + 1)
                    if (end > pos) {
                        spans.add(StyledSpan(text.substring(pos + 1, end), SpanStyle.ITALIC))
                        pos = end + 1
                    } else {
                        spans.add(StyledSpan("*", SpanStyle.NORMAL))
                        pos++
                    }
                }

                // Normal text - collect until next special char
                else -> {
                    val nextSpecial = findNextSpecial(text, pos)
                    spans.add(StyledSpan(text.substring(pos, nextSpecial), SpanStyle.NORMAL))
                    pos = nextSpecial
                }
            }
        }

        return spans
    }

    private fun findNextSpecial(
        text: String,
        from: Int,
    ): Int {
        for (i in from until text.length) {
            if (text[i] == '`' || text[i] == '*') return i
        }
        return text.length
    }
}
