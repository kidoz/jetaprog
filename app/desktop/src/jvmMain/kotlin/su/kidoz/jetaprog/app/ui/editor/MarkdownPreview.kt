package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Markdown preview component that renders markdown content as rich text.
 */
@Composable
public fun MarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
) {
    val elements = remember(content) { parseMarkdown(content) }
    val scrollState = rememberScrollState()

    SelectionContainer {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(IntelliJColors.background)
                    .verticalScroll(scrollState)
                    .padding(Spacing.lg.dp),
        ) {
            for (element in elements) {
                MarkdownElement(element = element)
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
            }
        }
    }
}

@Composable
private fun MarkdownElement(element: MarkdownNode) {
    when (element) {
        is MarkdownNode.Header -> {
            HeaderElement(element)
        }

        is MarkdownNode.Paragraph -> {
            ParagraphElement(element)
        }

        is MarkdownNode.CodeBlock -> {
            CodeBlockElement(element)
        }

        is MarkdownNode.BlockQuote -> {
            BlockQuoteElement(element)
        }

        is MarkdownNode.ListItem -> {
            ListItemElement(element)
        }

        is MarkdownNode.HorizontalRule -> {
            HorizontalDivider(
                color = IntelliJColors.border,
                modifier = Modifier.padding(vertical = Spacing.md.dp),
            )
        }
    }
}

@Composable
private fun HeaderElement(header: MarkdownNode.Header) {
    val (fontSize, fontWeight) =
        when (header.level) {
            1 -> 28.sp to FontWeight.Bold
            2 -> 24.sp to FontWeight.Bold
            3 -> 20.sp to FontWeight.SemiBold
            4 -> 18.sp to FontWeight.SemiBold
            5 -> 16.sp to FontWeight.Medium
            else -> 14.sp to FontWeight.Medium
        }

    val annotatedText = buildInlineAnnotatedString(header.content)

    Text(
        text = annotatedText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = IntelliJColors.textPrimary,
        modifier = Modifier.padding(bottom = Spacing.sm.dp),
    )
}

@Composable
private fun ParagraphElement(paragraph: MarkdownNode.Paragraph) {
    val annotatedText = buildInlineAnnotatedString(paragraph.content)

    Text(
        text = annotatedText,
        fontSize = 14.sp,
        color = IntelliJColors.textPrimary,
        lineHeight = 22.sp,
    )
}

@Composable
private fun CodeBlockElement(codeBlock: MarkdownNode.CodeBlock) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(IntelliJColors.terminalBackground)
                .padding(Spacing.md.dp),
    ) {
        if (codeBlock.language.isNotEmpty()) {
            Text(
                text = codeBlock.language,
                fontSize = 11.sp,
                color = IntelliJColors.textMuted,
                modifier = Modifier.padding(bottom = Spacing.xs.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = codeBlock.code,
                fontSize = 13.sp,
                fontFamily = JetaProgFonts.codeFont,
                color = IntelliJColors.terminalForeground,
            )
        }
    }
}

@Composable
private fun BlockQuoteElement(blockQuote: MarkdownNode.BlockQuote) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(IntelliJColors.accent, RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.width(Spacing.md.dp))
        Text(
            text = blockQuote.content,
            fontSize = 14.sp,
            color = IntelliJColors.textSecondary,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ListItemElement(listItem: MarkdownNode.ListItem) {
    val annotatedText = buildInlineAnnotatedString(listItem.content)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = (listItem.indent * 16).dp, bottom = Spacing.xs.dp),
    ) {
        Text(
            text = if (listItem.ordered) "${listItem.number}." else "\u2022",
            fontSize = 14.sp,
            color = IntelliJColors.textSecondary,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = annotatedText,
            fontSize = 14.sp,
            color = IntelliJColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Build annotated string with inline formatting (bold, italic, code, links).
 */
private fun buildInlineAnnotatedString(text: String): AnnotatedString =
    buildAnnotatedString {
        var pos = 0

        while (pos < text.length) {
            when {
                // Inline code
                text.startsWith("`", pos) -> {
                    val endPos = text.indexOf('`', pos + 1)
                    if (endPos > pos) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = IntelliJColors.surfaceContainer,
                                color = IntelliJColors.accent,
                            ),
                        ) {
                            append(text.substring(pos + 1, endPos))
                        }
                        pos = endPos + 1
                    } else {
                        append(text[pos])
                        pos++
                    }
                }

                // Bold **text** or __text__
                text.startsWith("**", pos) || text.startsWith("__", pos) -> {
                    val delimiter = text.substring(pos, pos + 2)
                    val endPos = text.indexOf(delimiter, pos + 2)
                    if (endPos > pos) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(pos + 2, endPos))
                        }
                        pos = endPos + 2
                    } else {
                        append(text[pos])
                        pos++
                    }
                }

                // Italic *text* or _text_ (not followed by same char)
                (text[pos] == '*' || text[pos] == '_') &&
                    text.getOrNull(pos + 1) != text[pos] -> {
                    val delimiter = text[pos]
                    var endPos = pos + 1
                    while (endPos < text.length) {
                        if (text[endPos] == delimiter && text.getOrNull(endPos + 1) != delimiter) {
                            break
                        }
                        endPos++
                    }
                    if (endPos < text.length && endPos > pos + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(pos + 1, endPos))
                        }
                        pos = endPos + 1
                    } else {
                        append(text[pos])
                        pos++
                    }
                }

                // Link [text](url)
                text.startsWith("[", pos) -> {
                    val closeBracket = text.indexOf(']', pos + 1)
                    if (closeBracket > pos && text.getOrNull(closeBracket + 1) == '(') {
                        val closeParenthesis = text.indexOf(')', closeBracket + 2)
                        if (closeParenthesis > closeBracket) {
                            val linkText = text.substring(pos + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParenthesis)

                            withLink(LinkAnnotation.Url(url)) {
                                withStyle(
                                    SpanStyle(
                                        color = IntelliJColors.accent,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ) {
                                    append(linkText)
                                }
                            }
                            pos = closeParenthesis + 1
                        } else {
                            append(text[pos])
                            pos++
                        }
                    } else {
                        append(text[pos])
                        pos++
                    }
                }

                // Image ![alt](url) - show as placeholder
                text.startsWith("![", pos) -> {
                    val closeBracket = text.indexOf(']', pos + 2)
                    if (closeBracket > pos && text.getOrNull(closeBracket + 1) == '(') {
                        val closeParenthesis = text.indexOf(')', closeBracket + 2)
                        if (closeParenthesis > closeBracket) {
                            val altText = text.substring(pos + 2, closeBracket)
                            withStyle(
                                SpanStyle(
                                    fontStyle = FontStyle.Italic,
                                    color = IntelliJColors.textSecondary,
                                ),
                            ) {
                                append("[Image: $altText]")
                            }
                            pos = closeParenthesis + 1
                        } else {
                            append(text[pos])
                            pos++
                        }
                    } else {
                        append(text[pos])
                        pos++
                    }
                }

                else -> {
                    append(text[pos])
                    pos++
                }
            }
        }
    }

/**
 * Markdown AST nodes.
 */
private sealed interface MarkdownNode {
    data class Header(
        val level: Int,
        val content: String,
    ) : MarkdownNode

    data class Paragraph(
        val content: String,
    ) : MarkdownNode

    data class CodeBlock(
        val language: String,
        val code: String,
    ) : MarkdownNode

    data class BlockQuote(
        val content: String,
    ) : MarkdownNode

    data class ListItem(
        val content: String,
        val ordered: Boolean,
        val number: Int,
        val indent: Int,
    ) : MarkdownNode

    data object HorizontalRule : MarkdownNode
}

/**
 * Simple markdown parser.
 */
private fun parseMarkdown(text: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        when {
            // Empty line
            trimmedLine.isEmpty() -> {
                i++
            }

            // Code block
            trimmedLine.startsWith("```") -> {
                val language = trimmedLine.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                nodes.add(MarkdownNode.CodeBlock(language, codeLines.joinToString("\n")))
                i++ // Skip closing ```
            }

            // Header
            trimmedLine.startsWith("#") -> {
                val level = trimmedLine.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val content = trimmedLine.drop(level).trimStart()
                nodes.add(MarkdownNode.Header(level, content))
                i++
            }

            // Horizontal rule
            HORIZONTAL_RULE_REGEX.matches(trimmedLine) -> {
                nodes.add(MarkdownNode.HorizontalRule)
                i++
            }

            // Blockquote
            trimmedLine.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                nodes.add(MarkdownNode.BlockQuote(quoteLines.joinToString(" ")))
            }

            // Unordered list
            UNORDERED_LIST_REGEX.matchAt(line, 0) != null -> {
                val match = UNORDERED_LIST_REGEX.matchAt(line, 0)!!
                val indent = match.value.takeWhile { it.isWhitespace() }.length / 2
                val content = line.substring(match.range.last + 1).trim()
                nodes.add(MarkdownNode.ListItem(content, ordered = false, number = 0, indent = indent))
                i++
            }

            // Ordered list
            ORDERED_LIST_REGEX.matchAt(line, 0) != null -> {
                val match = ORDERED_LIST_REGEX.matchAt(line, 0)!!
                val indent = match.value.takeWhile { it.isWhitespace() }.length / 2
                val number = match.value.filter { it.isDigit() }.toIntOrNull() ?: 1
                val content = line.substring(match.range.last + 1).trim()
                nodes.add(MarkdownNode.ListItem(content, ordered = true, number = number, indent = indent))
                i++
            }

            // Paragraph
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size &&
                    lines[i].isNotBlank() &&
                    !lines[i].trim().startsWith("#") &&
                    !lines[i].trim().startsWith("```") &&
                    !lines[i].trim().startsWith(">") &&
                    !HORIZONTAL_RULE_REGEX.matches(lines[i].trim()) &&
                    UNORDERED_LIST_REGEX.matchAt(lines[i], 0) == null &&
                    ORDERED_LIST_REGEX.matchAt(lines[i], 0) == null
                ) {
                    paragraphLines.add(lines[i].trim())
                    i++
                }
                if (paragraphLines.isNotEmpty()) {
                    nodes.add(MarkdownNode.Paragraph(paragraphLines.joinToString(" ")))
                }
            }
        }
    }

    return nodes
}

private val HORIZONTAL_RULE_REGEX = Regex("^[-*_]{3,}$")
private val UNORDERED_LIST_REGEX = Regex("^\\s*[*+-]\\s")
private val ORDERED_LIST_REGEX = Regex("^\\s*\\d+\\.\\s")
