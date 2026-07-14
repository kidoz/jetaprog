package su.kidoz.jetaprog.editor.syntax.kotlin

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the Kotlin programming language.
 */
public class KotlinLexer : Lexer {
    override val languageId: String = "kotlin"

    private companion object {
        val KEYWORDS =
            setOf(
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while",
                "by",
                "catch",
                "constructor",
                "delegate",
                "dynamic",
                "field",
                "file",
                "finally",
                "get",
                "import",
                "init",
                "param",
                "property",
                "receiver",
                "set",
                "setparam",
                "where",
                "actual",
                "annotation",
                "companion",
                "const",
                "crossinline",
                "data",
                "enum",
                "expect",
                "external",
                "final",
                "infix",
                "inline",
                "inner",
                "internal",
                "lateinit",
                "noinline",
                "open",
                "operator",
                "out",
                "override",
                "private",
                "protected",
                "public",
                "reified",
                "sealed",
                "suspend",
                "tailrec",
                "value",
                "vararg",
                "it",
            )

        val MODIFIERS =
            setOf(
                "abstract",
                "annotation",
                "companion",
                "const",
                "crossinline",
                "data",
                "enum",
                "expect",
                "external",
                "final",
                "infix",
                "inline",
                "inner",
                "internal",
                "lateinit",
                "noinline",
                "open",
                "operator",
                "out",
                "override",
                "private",
                "protected",
                "public",
                "reified",
                "sealed",
                "suspend",
                "tailrec",
                "value",
                "vararg",
                "actual",
            )

        val OPERATORS =
            setOf(
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "++",
                "--",
                "&&",
                "||",
                "!",
                "==",
                "!=",
                "===",
                "!==",
                "<",
                ">",
                "<=",
                ">=",
                "?:",
                "?.",
                "?",
                "!!",
                "::",
                "..",
                "->",
                "=>",
                "@",
                ":",
                ";",
            )

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')

        val PUNCTUATION = setOf('.', ',', ';', ':')
    }

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var line = 0
        var state = LexerState.Initial

        while (pos < text.length) {
            val (token, newState, consumed) = nextToken(text, pos, line, state)
            if (token != null && token.type != TokenType.WHITESPACE && token.type != TokenType.NEWLINE) {
                tokens.add(token)
            }
            // Count newlines
            for (i in pos until pos + consumed) {
                if (text[i] == '\n') line++
            }
            pos += consumed
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

        while (pos < text.length) {
            val (token, newState, consumed) = nextToken(text, pos, lineNumber, currentState, startOffset)
            if (token != null && token.type != TokenType.WHITESPACE) {
                tokens.add(token)
            }
            pos += consumed
            currentState = newState
        }

        return tokens to currentState
    }

    private fun nextToken(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int = 0,
    ): Triple<Token?, LexerState, Int> {
        if (pos >= text.length) return Triple(null, state, 0)

        // Handle continuation of multi-line constructs
        if (state.inBlockComment) {
            return consumeBlockCommentContinuation(text, pos, line, state, baseOffset)
        }
        if (state.inMultilineString) {
            return consumeMultilineStringContinuation(text, pos, line, baseOffset)
        }

        val char = text[pos]

        return when {
            // Whitespace
            char == '\n' -> {
                Triple(
                    Token(TokenType.NEWLINE, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            char.isWhitespace() -> {
                consumeWhitespace(text, pos, line, baseOffset)
            }

            // Comments
            text.startsWith("//", pos) -> {
                consumeLineComment(text, pos, line, baseOffset)
            }

            text.startsWith("/*", pos) -> {
                consumeBlockComment(text, pos, line, state, baseOffset)
            }

            // Strings
            text.startsWith("\"\"\"", pos) -> {
                consumeMultilineString(text, pos, line, baseOffset)
            }

            char == '"' -> {
                consumeString(text, pos, line, baseOffset)
            }

            char == '\'' -> {
                consumeChar(text, pos, line, baseOffset)
            }

            // Annotations
            char == '@' -> {
                consumeAnnotation(text, pos, line, baseOffset)
            }

            // Numbers
            char.isDigit() -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            // Identifiers and keywords
            char.isLetter() || char == '_' -> {
                consumeIdentifier(text, pos, line, baseOffset)
            }

            // Brackets
            char in BRACKETS -> {
                Triple(
                    Token(TokenType.BRACKET, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            // Punctuation
            char in PUNCTUATION -> {
                Triple(
                    Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            // Operators (check multi-char operators first)
            else -> {
                consumeOperator(text, pos, line, state, baseOffset)
            }
        }
    }

    private fun consumeWhitespace(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length && text[pos + length].isWhitespace() && text[pos + length] != '\n') {
            length++
        }
        return Triple(
            Token(TokenType.WHITESPACE, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeLineComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2 // "//"
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        return Triple(
            Token(TokenType.COMMENT_LINE, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeBlockComment(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val isDoc = text.startsWith("/**", pos) && !text.startsWith("/***", pos)
        var length = if (isDoc) 3 else 2
        var depth = 1

        while (pos + length < text.length && depth > 0) {
            when {
                text.startsWith("/*", pos + length) -> {
                    depth++
                    length += 2
                }

                text.startsWith("*/", pos + length) -> {
                    depth--
                    length += 2
                }

                else -> {
                    length++
                }
            }
        }

        val closed = depth == 0
        val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK

        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            if (closed) {
                LexerState.Initial
            } else {
                state.copy(inBlockComment = true, blockCommentDepth = depth, inDocComment = isDoc)
            },
            length,
        )
    }

    private fun consumeBlockCommentContinuation(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        var depth = state.blockCommentDepth

        while (pos + length < text.length && depth > 0) {
            when {
                text.startsWith("/*", pos + length) -> {
                    depth++
                    length += 2
                }

                text.startsWith("*/", pos + length) -> {
                    depth--
                    length += 2
                }

                else -> {
                    length++
                }
            }
        }

        val closed = depth == 0
        val tokenType = if (state.inDocComment) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK

        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            if (closed) LexerState.Initial else state.copy(blockCommentDepth = depth),
            length,
        )
    }

    private fun consumeString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1 // Opening quote
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                // Escape sequence
                char == '"' -> {
                    length++
                    break
                }

                char == '\n' -> {
                    break
                }

                // Unterminated string
                else -> {
                    length++
                }
            }
        }
        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeMultilineString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 3 // Opening """
        while (pos + length < text.length) {
            if (text.startsWith("\"\"\"", pos + length)) {
                length += 3
                return Triple(
                    Token(TokenType.STRING, baseOffset + pos, length, line),
                    LexerState.Initial,
                    length,
                )
            }
            length++
        }
        // Unterminated
        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true),
            length,
        )
    }

    private fun consumeMultilineStringContinuation(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length) {
            if (text.startsWith("\"\"\"", pos + length)) {
                length += 3
                return Triple(
                    Token(TokenType.STRING, baseOffset + pos, length, line),
                    LexerState.Initial,
                    length,
                )
            }
            length++
        }
        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true),
            length,
        )
    }

    private fun consumeChar(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1 // Opening quote
        if (pos + length < text.length && text[pos + length] == '\\') {
            length += 2 // Escape sequence
        } else if (pos + length < text.length) {
            length++ // Character
        }
        if (pos + length < text.length && text[pos + length] == '\'') {
            length++ // Closing quote
        }
        return Triple(
            Token(TokenType.CHARACTER, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeAnnotation(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1 // @
        // Handle use-site targets like @file: @get: @set:
        while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
            length++
        }
        // Handle annotation with colon (use-site target)
        if (pos + length < text.length && text[pos + length] == ':') {
            length++
            // Consume the actual annotation name after the colon
            while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
                length++
            }
        }
        return Triple(
            Token(TokenType.ANNOTATION, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        // Check for hex, binary, or octal
        if (pos + 1 < text.length && text[pos] == '0') {
            when (text[pos + 1].lowercaseChar()) {
                'x' -> {
                    length = 2
                    while (pos + length < text.length && text[pos + length].isHexDigit()) {
                        length++
                    }
                }

                'b' -> {
                    length = 2
                    while (pos + length < text.length && text[pos + length] in '0'..'1') {
                        length++
                    }
                }

                else -> {
                    // Regular number starting with 0
                    length = consumeDecimalNumber(text, pos)
                }
            }
        } else {
            length = consumeDecimalNumber(text, pos)
        }

        // Handle suffixes (L, F, f, etc.)
        if (pos + length < text.length && text[pos + length].lowercaseChar() in listOf('l', 'f', 'u')) {
            length++
            // Handle UL or uL
            if (pos + length < text.length && text[pos + length].lowercaseChar() == 'l') {
                length++
            }
        }

        return Triple(
            Token(TokenType.NUMBER, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeDecimalNumber(
        text: String,
        pos: Int,
    ): Int {
        var length = 0
        // Integer part
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
            length++
        }
        // Decimal part
        if (pos + length < text.length && text[pos + length] == '.') {
            if (pos + length + 1 < text.length && text[pos + length + 1].isDigit()) {
                length++ // .
                while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                    length++
                }
            }
        }
        // Exponent part
        if (pos + length < text.length && text[pos + length].lowercaseChar() == 'e') {
            length++
            if (pos + length < text.length && text[pos + length] in listOf('+', '-')) {
                length++
            }
            while (pos + length < text.length && text[pos + length].isDigit()) {
                length++
            }
        }
        return length
    }

    private fun consumeIdentifier(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length &&
            (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')
        ) {
            length++
        }

        val word = text.substring(pos, pos + length)
        val type =
            when {
                word in KEYWORDS -> if (word in MODIFIERS) TokenType.MODIFIER else TokenType.KEYWORD

                word.first().isUpperCase() -> TokenType.TYPE

                // Check if followed by ( - indicates function call
                pos + length < text.length && text[pos + length] == '(' -> TokenType.FUNCTION

                else -> TokenType.IDENTIFIER
            }

        return Triple(
            Token(type, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeOperator(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        // Try multi-character operators first (longer ones)
        for (op in OPERATORS.sortedByDescending { it.length }) {
            if (text.startsWith(op, pos)) {
                return Triple(
                    Token(TokenType.OPERATOR, baseOffset + pos, op.length, line),
                    state,
                    op.length,
                )
            }
        }

        // Single unknown character
        return Triple(
            Token(TokenType.UNKNOWN, baseOffset + pos, 1, line),
            state,
            1,
        )
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
