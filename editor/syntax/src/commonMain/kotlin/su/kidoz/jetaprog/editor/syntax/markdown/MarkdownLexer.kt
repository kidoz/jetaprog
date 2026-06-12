package su.kidoz.jetaprog.editor.syntax.markdown

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for Markdown files.
 *
 * Supports:
 * - Headers (# to ######)
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Code blocks (``` ... ```)
 * - Inline code (`code`)
 * - Links [text](url)
 * - Images ![alt](url)
 * - Lists (-, *, +, numbered)
 * - Blockquotes (>)
 * - Horizontal rules (---, ***, ___)
 */
public class MarkdownLexer : Lexer {
    override val languageId: String = "markdown"

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        val lines = text.split('\n')
        var offset = 0
        var state = LexerState.Initial

        for ((lineNum, line) in lines.withIndex()) {
            val (lineTokens, newState) = tokenizeLine(line, lineNum, offset, state)
            tokens.addAll(lineTokens)
            offset += line.length + 1 // +1 for newline
            state = newState
        }

        return TokenList(tokens)
    }

    override fun tokenizeLine(
        text: String,
        lineNumber: Int,
        startOffset: Int,
        state: LexerState,
    ): Pair<List<Token>, LexerState> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var currentState = state

        // Handle code block continuation
        if (currentState.inMultilineString) {
            val result = handleCodeBlockLine(text, lineNumber, startOffset, currentState)
            return result
        }

        // Check for code block start
        if (text.trimStart().startsWith("```")) {
            val indent = text.length - text.trimStart().length
            val codeBlockStart = text.indexOf("```")
            val langEnd = text.length

            // Entire line is code block delimiter
            tokens.add(Token(TokenType.STRING, startOffset, text.length, lineNumber))

            // Check if there's a language identifier
            val afterFence = text.substring(codeBlockStart + 3).trim()
            val newState =
                if (afterFence.isNotEmpty()) {
                    LexerState(inMultilineString = true, stringDelimiter = "```")
                } else {
                    LexerState(inMultilineString = true, stringDelimiter = "```")
                }

            return tokens to newState
        }

        // Check for header at start of line
        if (text.startsWith("#")) {
            val headerMatch = HEADER_REGEX.matchAt(text, 0)
            if (headerMatch != null) {
                tokens.add(Token(TokenType.TAG, startOffset, text.length, lineNumber))
                return tokens to LexerState.Initial
            }
        }

        // Check for blockquote
        if (text.trimStart().startsWith(">")) {
            tokens.add(Token(TokenType.COMMENT_LINE, startOffset, text.length, lineNumber))
            return tokens to LexerState.Initial
        }

        // Check for horizontal rule
        if (HORIZONTAL_RULE_REGEX.matches(text)) {
            tokens.add(Token(TokenType.OPERATOR, startOffset, text.length, lineNumber))
            return tokens to LexerState.Initial
        }

        // Check for list item at start of line
        val listMatch = LIST_ITEM_REGEX.matchAt(text, 0)
        if (listMatch != null) {
            val listMarkerLength = listMatch.value.length
            tokens.add(Token(TokenType.PUNCTUATION, startOffset, listMarkerLength, lineNumber))
            pos = listMarkerLength
        }

        // Process inline elements
        while (pos < text.length) {
            val result = processInlineElement(text, pos, lineNumber, startOffset)
            if (result != null) {
                val (token, consumed) = result
                tokens.add(token)
                pos += consumed
            } else {
                // Find next special character or end of line
                val nextSpecial = findNextSpecialChar(text, pos)
                if (nextSpecial > pos) {
                    tokens.add(Token(TokenType.TEXT, startOffset + pos, nextSpecial - pos, lineNumber))
                    pos = nextSpecial
                } else if (nextSpecial == pos) {
                    // Single character that's not part of any pattern
                    tokens.add(Token(TokenType.TEXT, startOffset + pos, 1, lineNumber))
                    pos++
                } else {
                    // Rest of line is plain text
                    tokens.add(Token(TokenType.TEXT, startOffset + pos, text.length - pos, lineNumber))
                    break
                }
            }
        }

        return tokens to LexerState.Initial
    }

    private fun handleCodeBlockLine(
        text: String,
        lineNumber: Int,
        startOffset: Int,
        state: LexerState,
    ): Pair<List<Token>, LexerState> {
        val tokens = mutableListOf<Token>()

        // Check for code block end
        if (text.trimStart().startsWith("```")) {
            tokens.add(Token(TokenType.STRING, startOffset, text.length, lineNumber))
            return tokens to LexerState.Initial
        }

        // Inside code block - treat as string/code
        if (text.isNotEmpty()) {
            tokens.add(Token(TokenType.STRING, startOffset, text.length, lineNumber))
        }

        return tokens to state
    }

    private fun processInlineElement(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
    ): Pair<Token, Int>? {
        if (pos >= text.length) return null

        val char = text[pos]

        return when {
            // Inline code
            char == '`' -> {
                processInlineCode(text, pos, lineNumber, startOffset)
            }

            // Bold (**text** or __text__)
            (char == '*' && text.getOrNull(pos + 1) == '*') ||
                (char == '_' && text.getOrNull(pos + 1) == '_') -> {
                processBold(text, pos, lineNumber, startOffset, char)
            }

            // Italic (*text* or _text_) - only if not followed by same char
            (char == '*' || char == '_') &&
                text.getOrNull(pos + 1) != char -> {
                processItalic(text, pos, lineNumber, startOffset, char)
            }

            // Link [text](url)
            char == '[' -> {
                processLink(text, pos, lineNumber, startOffset)
            }

            // Image ![alt](url)
            char == '!' && text.getOrNull(pos + 1) == '[' -> {
                processImage(text, pos, lineNumber, startOffset)
            }

            // Auto-link <url>
            char == '<' -> {
                processAutoLink(text, pos, lineNumber, startOffset)
            }

            else -> {
                null
            }
        }
    }

    private fun processInlineCode(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
    ): Pair<Token, Int>? {
        var endPos = pos + 1
        while (endPos < text.length && text[endPos] != '`') {
            endPos++
        }

        return if (endPos < text.length) {
            val length = endPos - pos + 1
            Token(TokenType.STRING, startOffset + pos, length, lineNumber) to length
        } else {
            null
        }
    }

    private fun processBold(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
        delimiter: Char,
    ): Pair<Token, Int>? {
        val pattern = "$delimiter$delimiter"
        val endPos = text.indexOf(pattern, pos + 2)

        return if (endPos > pos + 2) {
            val length = endPos - pos + 2
            Token(TokenType.KEYWORD, startOffset + pos, length, lineNumber) to length
        } else {
            null
        }
    }

    private fun processItalic(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
        delimiter: Char,
    ): Pair<Token, Int>? {
        var endPos = pos + 1
        while (endPos < text.length) {
            if (text[endPos] == delimiter && (endPos + 1 >= text.length || text[endPos + 1] != delimiter)) {
                break
            }
            endPos++
        }

        return if (endPos < text.length && endPos > pos + 1) {
            val length = endPos - pos + 1
            Token(TokenType.MODIFIER, startOffset + pos, length, lineNumber) to length
        } else {
            null
        }
    }

    private fun processLink(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
    ): Pair<Token, Int>? {
        // Find closing ]
        val closeBracket = text.indexOf(']', pos + 1)
        if (closeBracket < 0) return null

        // Check for (url) immediately after
        if (closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') return null

        val closeParenthesis = text.indexOf(')', closeBracket + 2)
        if (closeParenthesis < 0) return null

        val length = closeParenthesis - pos + 1
        return Token(TokenType.FUNCTION, startOffset + pos, length, lineNumber) to length
    }

    private fun processImage(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
    ): Pair<Token, Int>? {
        // Find closing ]
        val closeBracket = text.indexOf(']', pos + 2)
        if (closeBracket < 0) return null

        // Check for (url) immediately after
        if (closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') return null

        val closeParenthesis = text.indexOf(')', closeBracket + 2)
        if (closeParenthesis < 0) return null

        val length = closeParenthesis - pos + 1
        return Token(TokenType.ANNOTATION, startOffset + pos, length, lineNumber) to length
    }

    private fun processAutoLink(
        text: String,
        pos: Int,
        lineNumber: Int,
        startOffset: Int,
    ): Pair<Token, Int>? {
        val closeAngle = text.indexOf('>', pos + 1)
        if (closeAngle < 0) return null

        val content = text.substring(pos + 1, closeAngle)
        // Check if it looks like a URL or email
        if (content.contains("://") || content.contains("@")) {
            val length = closeAngle - pos + 1
            return Token(TokenType.FUNCTION, startOffset + pos, length, lineNumber) to length
        }

        return null
    }

    private fun findNextSpecialChar(
        text: String,
        pos: Int,
    ): Int {
        for (i in pos until text.length) {
            when (text[i]) {
                '`', '*', '_', '[', '!', '<' -> return i
            }
        }
        return -1
    }

    private companion object {
        val HEADER_REGEX = Regex("^#{1,6}\\s")
        val LIST_ITEM_REGEX = Regex("^\\s*([*+-]|\\d+\\.)\\s")
        val HORIZONTAL_RULE_REGEX = Regex("^\\s*([-*_])\\1{2,}\\s*$")
    }
}
