package su.kidoz.jetaprog.editor.syntax.vala

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the Vala programming language.
 *
 * Supports `.vala` source files and `.vapi` binding files.
 */
public class ValaLexer : Lexer {
    override val languageId: String = "vala"

    private companion object {
        val KEYWORDS =
            setOf(
                // Control flow
                "if",
                "else",
                "for",
                "foreach",
                "while",
                "do",
                "switch",
                "case",
                "default",
                "break",
                "continue",
                "return",
                "try",
                "catch",
                "finally",
                "throw",
                "throws",
                // Type declarations
                "class",
                "struct",
                "enum",
                "interface",
                "namespace",
                "delegate",
                "errordomain",
                // Type-related
                "new",
                "delete",
                "typeof",
                "sizeof",
                "is",
                "as",
                "in",
                "out",
                "ref",
                // Values
                "null",
                "true",
                "false",
                "this",
                "base",
                // Async
                "async",
                "yield",
                // Other
                "using",
                "var",
                "const",
                "lock",
                "global",
                "requires",
                "ensures",
                // Property/signal accessors
                "get",
                "set",
                "construct",
                "signal",
                "property",
                // Ownership
                "owned",
                "unowned",
                "weak",
                "dynamic",
            )

        val MODIFIERS =
            setOf(
                "public",
                "private",
                "protected",
                "internal",
                "static",
                "abstract",
                "virtual",
                "override",
                "sealed",
                "extern",
                "inline",
                "volatile",
            )

        val BUILTIN_TYPES =
            setOf(
                "void",
                "bool",
                "char",
                "uchar",
                "unichar",
                "int",
                "uint",
                "short",
                "ushort",
                "long",
                "ulong",
                "int8",
                "int16",
                "int32",
                "int64",
                "uint8",
                "uint16",
                "uint32",
                "uint64",
                "float",
                "double",
                "string",
                "size_t",
                "ssize_t",
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
                "<",
                ">",
                "<=",
                ">=",
                "??",
                "?.",
                "?",
                ":",
                "::",
                "..",
                "->",
                "=>",
                "&",
                "|",
                "^",
                "~",
                "<<",
                ">>",
                "&=",
                "|=",
                "^=",
                "<<=",
                ">>=",
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
            return consumeVerbatimStringContinuation(text, pos, line, baseOffset)
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

            // Verbatim strings (Vala uses @"...")
            text.startsWith("@\"", pos) -> {
                consumeVerbatimString(text, pos, line, baseOffset)
            }

            // Regular strings
            char == '"' -> {
                consumeString(text, pos, line, baseOffset)
            }

            // Character literals
            char == '\'' -> {
                consumeChar(text, pos, line, baseOffset)
            }

            // Preprocessor directives (#if, #else, #endif, etc.)
            char == '#' && isStartOfLine(text, pos) -> {
                consumePreprocessor(text, pos, line, baseOffset)
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

    private fun isStartOfLine(
        text: String,
        pos: Int,
    ): Boolean {
        if (pos == 0) return true
        for (i in pos - 1 downTo 0) {
            val c = text[i]
            if (c == '\n') return true
            if (!c.isWhitespace()) return false
        }
        return true
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
            if (closed) LexerState.Initial else state.copy(inBlockComment = true, blockCommentDepth = depth),
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

        return Triple(
            Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
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

    private fun consumeVerbatimString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2 // @"
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                // In verbatim strings, "" is an escaped quote
                text.startsWith("\"\"", pos + length) -> {
                    length += 2
                }

                char == '"' -> {
                    length++
                    return Triple(
                        Token(TokenType.STRING, baseOffset + pos, length, line),
                        LexerState.Initial,
                        length,
                    )
                }

                else -> {
                    length++
                }
            }
        }
        // Unterminated - continues on next line
        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true),
            length,
        )
    }

    private fun consumeVerbatimStringContinuation(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                text.startsWith("\"\"", pos + length) -> {
                    length += 2
                }

                char == '"' -> {
                    length++
                    return Triple(
                        Token(TokenType.STRING, baseOffset + pos, length, line),
                        LexerState.Initial,
                        length,
                    )
                }

                else -> {
                    length++
                }
            }
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

    private fun consumePreprocessor(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1 // #
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        return Triple(
            Token(TokenType.KEYWORD, baseOffset + pos, length, line),
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

                'o' -> {
                    length = 2
                    while (pos + length < text.length && text[pos + length] in '0'..'7') {
                        length++
                    }
                }

                else -> {
                    length = consumeDecimalNumber(text, pos)
                }
            }
        } else {
            length = consumeDecimalNumber(text, pos)
        }

        // Handle suffixes (L, U, F, etc.)
        if (pos + length < text.length) {
            val suffix = text[pos + length].lowercaseChar()
            if (suffix in listOf('l', 'u', 'f', 'd')) {
                length++
                // Handle UL or LU combinations
                if (pos + length < text.length) {
                    val nextSuffix = text[pos + length].lowercaseChar()
                    if ((suffix == 'u' && nextSuffix == 'l') || (suffix == 'l' && nextSuffix == 'u')) {
                        length++
                    }
                }
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
                word in KEYWORDS -> TokenType.KEYWORD

                word in MODIFIERS -> TokenType.MODIFIER

                word in BUILTIN_TYPES -> TokenType.TYPE

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
