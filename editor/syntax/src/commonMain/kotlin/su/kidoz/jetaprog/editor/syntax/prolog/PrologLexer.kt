package su.kidoz.jetaprog.editor.syntax.prolog

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for Prolog source files as used by DotProlog (`.pl` programs and `.dpli` contracts).
 *
 * Covers ISO Prolog lexical syntax: `%` line comments, `/* ... */` block comments, quoted
 * atoms, double-quoted strings, `0'c` character codes, integers with radix prefixes,
 * floats, variables (uppercase or `_` start), and the standard operator set (`:-`, `?-`,
 * `-->`, arithmetic comparisons, ...). Atoms directly followed by `(` are highlighted as
 * predicate functors.
 */
public class PrologLexer : Lexer {
    override val languageId: String = "dotprolog"

    private companion object {
        /** Word operators and directive atoms with clause-level meaning. */
        val KEYWORDS =
            setOf(
                "is",
                "mod",
                "rem",
                "div",
                "xor",
                "not",
                "dynamic",
                "discontiguous",
                "multifile",
                "module",
                "use_module",
                "meta_predicate",
                "initialization",
                "include",
                "ensure_loaded",
            )

        val CONSTANTS = setOf("true", "false", "fail")

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',')

        /** Symbolic operators, longest first so alternation never stops at a prefix. */
        val OPERATORS =
            listOf(
                "=..",
                "-->",
                "@=<",
                "@>=",
                "=:=",
                "=\\=",
                "\\==",
                ":-",
                "?-",
                "->",
                "=<",
                ">=",
                "==",
                "\\=",
                "\\+",
                "**",
                "//",
                "<<",
                ">>",
                "@<",
                "@>",
                "=",
                "<",
                ">",
                "+",
                "-",
                "*",
                "/",
                "^",
                "!",
                ";",
                "|",
                "\\",
                "?",
                "@",
                ":",
            )
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

        val char = text[pos]

        return when {
            state.inBlockComment -> consumeBlockComment(text, pos, line, state, baseOffset, opening = false)
            char == '\n' -> Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            char.isWhitespace() -> consumeWhitespace(text, pos, line, baseOffset)
            char == '%' -> consumeLineComment(text, pos, line, baseOffset)
            text.startsWith("/*", pos) -> consumeBlockComment(text, pos, line, state, baseOffset, opening = true)
            text.startsWith("0'", pos) -> consumeCharacterCode(text, pos, line, baseOffset)
            char.isDigit() -> consumeNumber(text, pos, line, baseOffset)
            char == '\'' -> consumeQuoted(text, pos, line, baseOffset, '\'')
            char == '"' -> consumeQuoted(text, pos, line, baseOffset, '"')
            char == '_' || char.isUpperCase() -> consumeVariable(text, pos, line, baseOffset)
            char.isLetter() -> consumeAtom(text, pos, line, baseOffset)
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
        var length = 1
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
        opening: Boolean,
    ): Triple<Token, LexerState, Int> {
        var length = if (opening) 2 else 0
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
        return Triple(
            Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
            state.copy(inBlockComment = true),
            length,
        )
    }

    /**
     * Consumes a `0'c` character-code literal, including escaped forms like `0'\n`.
     */
    private fun consumeCharacterCode(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        if (pos + length < text.length) {
            length += if (text[pos + length] == '\\' && pos + length + 1 < text.length) 2 else 1
        }
        return Triple(Token(TokenType.CHARACTER, baseOffset + pos, length, line), LexerState.Initial, length)
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
                    while (pos + length < text.length && text[pos + length].isHexDigit()) {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }

                'o' -> {
                    length = 2
                    while (pos + length < text.length && text[pos + length] in '0'..'7') {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }

                'b' -> {
                    length = 2
                    while (pos + length < text.length && text[pos + length] in '0'..'1') {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }
            }
        }

        while (pos + length < text.length && text[pos + length].isDigit()) {
            length++
        }

        // A fraction only when a digit follows the dot, so clause-ending '5.' stays two tokens.
        if (pos + length + 1 < text.length && text[pos + length] == '.' && text[pos + length + 1].isDigit()) {
            length += 2
            while (pos + length < text.length && text[pos + length].isDigit()) {
                length++
            }
        }

        length += exponentLength(text, pos + length)

        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun exponentLength(
        text: String,
        start: Int,
    ): Int {
        if (start >= text.length || text[start].lowercaseChar() != 'e') return 0
        var length = 1
        if (start + length < text.length && (text[start + length] == '+' || text[start + length] == '-')) {
            length++
        }
        if (start + length >= text.length || !text[start + length].isDigit()) return 0
        while (start + length < text.length && text[start + length].isDigit()) {
            length++
        }
        return length
    }

    /**
     * Consumes a quoted atom (`'...'`) or string (`"..."`), honouring backslash escapes
     * and doubled-quote escapes (`''` / `""`).
     */
    private fun consumeQuoted(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
        quote: Char,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                char == quote && pos + length + 1 < text.length && text[pos + length + 1] == quote -> {
                    length += 2
                }

                char == quote -> {
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

    private fun consumeVariable(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
            length++
        }
        return Triple(Token(TokenType.PARAMETER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeAtom(
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
                word in KEYWORDS -> TokenType.KEYWORD
                word in CONSTANTS -> TokenType.CONSTANT
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
        for (op in OPERATORS) {
            if (text.startsWith(op, pos)) {
                return Triple(Token(TokenType.OPERATOR, baseOffset + pos, op.length, line), state, op.length)
            }
        }
        return Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), state, 1)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
