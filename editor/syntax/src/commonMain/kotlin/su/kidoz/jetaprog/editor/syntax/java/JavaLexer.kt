package su.kidoz.jetaprog.editor.syntax.java

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the Java programming language.
 */
public class JavaLexer : Lexer {
    override val languageId: String = "java"

    private companion object {
        val KEYWORDS =
            setOf(
                "abstract",
                "assert",
                "boolean",
                "break",
                "byte",
                "case",
                "catch",
                "char",
                "class",
                "const",
                "continue",
                "default",
                "do",
                "double",
                "else",
                "enum",
                "extends",
                "final",
                "finally",
                "float",
                "for",
                "goto",
                "if",
                "implements",
                "import",
                "instanceof",
                "int",
                "interface",
                "long",
                "native",
                "new",
                "package",
                "private",
                "protected",
                "public",
                "return",
                "short",
                "static",
                "strictfp",
                "super",
                "switch",
                "synchronized",
                "this",
                "throw",
                "throws",
                "transient",
                "try",
                "void",
                "volatile",
                "while",
                "true",
                "false",
                "null",
                // Java 10+
                "var",
                // Java 14+
                "record",
                "yield",
                // Java 15+
                "sealed",
                "permits",
                "non-sealed",
                // Java 17+
                "when",
            )

        val MODIFIERS =
            setOf(
                "abstract",
                "final",
                "native",
                "private",
                "protected",
                "public",
                "static",
                "strictfp",
                "synchronized",
                "transient",
                "volatile",
                "sealed",
                "non-sealed",
            )

        val PRIMITIVE_TYPES =
            setOf(
                "boolean",
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short",
                "void",
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
                "&",
                "|",
                "^",
                "~",
                "<<",
                ">>",
                ">>>",
                "&=",
                "|=",
                "^=",
                "<<=",
                ">>=",
                ">>>=",
                "?",
                ":",
                "::",
                "->",
                "@",
            )

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')

        val PUNCTUATION = setOf('.', ',', ';')
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

        if (state.inBlockComment) {
            return consumeBlockCommentContinuation(text, pos, line, state, baseOffset)
        }

        val char = text[pos]

        return when {
            char == '\n' -> Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            char.isWhitespace() -> consumeWhitespace(text, pos, line, baseOffset)
            text.startsWith("//", pos) -> consumeLineComment(text, pos, line, baseOffset)
            text.startsWith("/*", pos) -> consumeBlockComment(text, pos, line, state, baseOffset)
            text.startsWith("\"\"\"", pos) -> consumeTextBlock(text, pos, line, baseOffset)
            char == '"' -> consumeString(text, pos, line, baseOffset)
            char == '\'' -> consumeChar(text, pos, line, baseOffset)
            char == '@' -> consumeAnnotation(text, pos, line, baseOffset)
            char.isDigit() -> consumeNumber(text, pos, line, baseOffset)
            char.isLetter() || char == '_' -> consumeIdentifier(text, pos, line, baseOffset)
            char in BRACKETS -> Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), state, 1)
            char in PUNCTUATION -> Triple(Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line), state, 1)
            else -> consumeOperator(text, pos, line, state, baseOffset)
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
        return Triple(Token(TokenType.WHITESPACE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeLineComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        return Triple(Token(TokenType.COMMENT_LINE, baseOffset + pos, length, line), LexerState.Initial, length)
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

        while (pos + length < text.length) {
            if (text.startsWith("*/", pos + length)) {
                length += 2
                val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
                return Triple(Token(tokenType, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            state.copy(inBlockComment = true),
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
        while (pos + length < text.length) {
            if (text.startsWith("*/", pos + length)) {
                length += 2
                return Triple(
                    Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
                    LexerState.Initial,
                    length,
                )
            }
            length++
        }
        return Triple(Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line), state, length)
    }

    private fun consumeString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                char == '"' -> {
                    length++
                    break
                }

                char == '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }
        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeTextBlock(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 3
        while (pos + length < text.length) {
            if (text.startsWith("\"\"\"", pos + length)) {
                length += 3
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
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
        var length = 1
        if (pos + length < text.length && text[pos + length] == '\\') {
            length += 2
        } else if (pos + length < text.length) {
            length++
        }
        if (pos + length < text.length && text[pos + length] == '\'') {
            length++
        }
        return Triple(Token(TokenType.CHARACTER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeAnnotation(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
            length++
        }
        return Triple(Token(TokenType.ANNOTATION, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        if (pos + 1 < text.length && text[pos] == '0') {
            when (text[pos + 1].lowercaseChar()) {
                'x' -> {
                    length = 2
                    while (pos + length < text.length &&
                        (text[pos + length].isHexDigit() || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                'b' -> {
                    length = 2
                    while (pos + length < text.length &&
                        (text[pos + length] in '0'..'1' || text[pos + length] == '_')
                    ) {
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

        if (pos + length < text.length && text[pos + length].lowercaseChar() in listOf('l', 'f', 'd')) {
            length++
        }

        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeDecimalNumber(
        text: String,
        pos: Int,
    ): Int {
        var length = 0
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
            length++
        }
        if (pos + length < text.length && text[pos + length] == '.') {
            if (pos + length + 1 < text.length && text[pos + length + 1].isDigit()) {
                length++
                while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                    length++
                }
            }
        }
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
        while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
            length++
        }

        val word = text.substring(pos, pos + length)
        val type =
            when {
                word in PRIMITIVE_TYPES -> TokenType.TYPE
                word in KEYWORDS -> if (word in MODIFIERS) TokenType.MODIFIER else TokenType.KEYWORD
                word.first().isUpperCase() -> TokenType.TYPE
                pos + length < text.length && text[pos + length] == '(' -> TokenType.FUNCTION
                else -> TokenType.IDENTIFIER
            }

        return Triple(Token(type, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeOperator(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        for (op in OPERATORS.sortedByDescending { it.length }) {
            if (text.startsWith(op, pos)) {
                return Triple(Token(TokenType.OPERATOR, baseOffset + pos, op.length, line), state, op.length)
            }
        }
        return Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), state, 1)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
