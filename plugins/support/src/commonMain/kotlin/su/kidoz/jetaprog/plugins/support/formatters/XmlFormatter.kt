package su.kidoz.jetaprog.plugins.support.formatters

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit

/**
 * XML formatter that indents XML elements properly.
 */
public class XmlFormatter : CodeFormatter {
    override val languageId: LanguageId = LanguageId.XML

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult =
        try {
            val formatted = prettyPrint(content.trim(), options)
            val finalContent =
                if (options.insertFinalNewline && !formatted.endsWith("\n")) {
                    formatted + "\n"
                } else {
                    formatted
                }

            if (finalContent == content) {
                FormattingResult.Success(content, emptyList())
            } else {
                val edit =
                    TextEdit(
                        range = TextRange(TextPosition.Zero, positionAtEnd(content)),
                        newText = finalContent,
                    )
                FormattingResult.Success(finalContent, listOf(edit))
            }
        } catch (e: Exception) {
            FormattingResult.Failure("XML formatting error: ${e.message}")
        }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult = format(content, options)

    private fun prettyPrint(
        xml: String,
        options: FormattingOptions,
    ): String {
        val indent =
            if (options.insertSpaces) {
                " ".repeat(options.tabSize)
            } else {
                "\t"
            }

        val result = StringBuilder()
        var indentLevel = 0
        var inTag = false
        var inComment = false
        var inCData = false
        var tagContent = StringBuilder()
        var i = 0

        while (i < xml.length) {
            // Handle CDATA sections
            if (xml.startsWith("<![CDATA[", i)) {
                val cdataEnd = xml.indexOf("]]>", i)
                if (cdataEnd != -1) {
                    result.append(xml.substring(i, cdataEnd + 3))
                    i = cdataEnd + 3
                    continue
                }
            }

            // Handle comments
            if (xml.startsWith("<!--", i)) {
                val commentEnd = xml.indexOf("-->", i)
                if (commentEnd != -1) {
                    if (result.isNotEmpty() && !result.endsWith("\n")) {
                        result.append("\n")
                    }
                    result.append(indent.repeat(indentLevel))
                    result.append(xml.substring(i, commentEnd + 3))
                    i = commentEnd + 3
                    continue
                }
            }

            // Handle processing instructions
            if (xml.startsWith("<?", i)) {
                val piEnd = xml.indexOf("?>", i)
                if (piEnd != -1) {
                    result.append(xml.substring(i, piEnd + 2))
                    result.append("\n")
                    i = piEnd + 2
                    continue
                }
            }

            // Handle DOCTYPE
            if (xml.startsWith("<!DOCTYPE", i)) {
                val doctypeEnd = xml.indexOf(">", i)
                if (doctypeEnd != -1) {
                    result.append(xml.substring(i, doctypeEnd + 1))
                    result.append("\n")
                    i = doctypeEnd + 1
                    continue
                }
            }

            val c = xml[i]

            when {
                c == '<' && !inTag -> {
                    inTag = true
                    tagContent = StringBuilder()
                    tagContent.append(c)
                }

                c == '>' && inTag -> {
                    tagContent.append(c)
                    val tag = tagContent.toString()
                    val isClosingTag = tag.startsWith("</")
                    val isSelfClosing = tag.endsWith("/>")

                    if (isClosingTag) {
                        indentLevel = maxOf(0, indentLevel - 1)
                    }

                    if (result.isNotEmpty() && !result.endsWith("\n")) {
                        // Check if we should add newline before this tag
                        val lastContentEnd = result.lastIndex
                        val lastChar = if (lastContentEnd >= 0) result[lastContentEnd] else '\n'
                        if (lastChar != '>') {
                            // Text content before tag - don't add newline
                        } else {
                            result.append("\n")
                            result.append(indent.repeat(indentLevel))
                        }
                    } else if (result.isEmpty()) {
                        // First tag, no indent needed
                    } else {
                        result.append(indent.repeat(indentLevel))
                    }

                    result.append(tag)

                    if (!isClosingTag && !isSelfClosing) {
                        indentLevel++
                    }

                    inTag = false
                }

                inTag -> {
                    tagContent.append(c)
                }

                c.isWhitespace() -> {
                    // Skip whitespace between tags
                    if (result.isNotEmpty() && result.last() == '>') {
                        // Skip whitespace after closing tag
                    } else if (result.isNotEmpty() && !result.last().isWhitespace()) {
                        // Keep single space in text content
                        result.append(' ')
                    }
                }

                else -> {
                    // Text content
                    if (result.isNotEmpty() && result.last() == '>') {
                        result.append("\n")
                        result.append(indent.repeat(indentLevel))
                    }
                    result.append(c)
                }
            }
            i++
        }

        return result.toString().trim()
    }

    private fun positionAtEnd(content: String): TextPosition {
        val lines = content.lines()
        return if (lines.isEmpty()) {
            TextPosition.Zero
        } else {
            TextPosition(lines.size - 1, lines.last().length)
        }
    }
}
