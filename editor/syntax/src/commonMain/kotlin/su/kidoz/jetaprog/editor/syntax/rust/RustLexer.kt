package su.kidoz.jetaprog.editor.syntax.rust

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the Rust programming language.
 */
public class RustLexer : Lexer {
    override val languageId: String = "rust"

    private companion object {
        val KEYWORDS =
            setOf(
                "as",
                "async",
                "await",
                "break",
                "const",
                "continue",
                "crate",
                "dyn",
                "else",
                "enum",
                "extern",
                "false",
                "fn",
                "for",
                "if",
                "impl",
                "in",
                "let",
                "loop",
                "match",
                "mod",
                "move",
                "mut",
                "pub",
                "ref",
                "return",
                "self",
                "Self",
                "static",
                "struct",
                "super",
                "trait",
                "true",
                "type",
                "unsafe",
                "use",
                "where",
                "while",
                // Reserved keywords
                "abstract",
                "become",
                "box",
                "do",
                "final",
                "macro",
                "override",
                "priv",
                "try",
                "typeof",
                "unsized",
                "virtual",
                "yield",
            )

        val MODIFIERS =
            setOf(
                "pub",
                "mut",
                "const",
                "static",
                "unsafe",
                "async",
                "extern",
            )

        val PRIMITIVE_TYPES =
            setOf(
                "bool",
                "char",
                "str",
                "u8",
                "u16",
                "u32",
                "u64",
                "u128",
                "usize",
                "i8",
                "i16",
                "i32",
                "i64",
                "i128",
                "isize",
                "f32",
                "f64",
            )

        val BUILTIN_MACROS =
            setOf(
                "println",
                "print",
                "eprintln",
                "eprint",
                "format",
                "panic",
                "assert",
                "assert_eq",
                "assert_ne",
                "debug_assert",
                "debug_assert_eq",
                "debug_assert_ne",
                "vec",
                "cfg",
                "env",
                "option_env",
                "concat",
                "line",
                "column",
                "file",
                "stringify",
                "include",
                "include_str",
                "include_bytes",
                "module_path",
                "todo",
                "unimplemented",
                "unreachable",
                "compile_error",
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
                "==",
                "!=",
                "<",
                ">",
                "<=",
                ">=",
                "&&",
                "||",
                "!",
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
                "..",
                "..=",
                "->",
                "=>",
                "::",
                "?",
                "@",
                "#",
                "$",
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
        if (state.inMultilineString) {
            return consumeRawStringContinuation(text, pos, line, state, baseOffset)
        }

        val char = text[pos]

        return when {
            char == '\n' -> {
                Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            }

            char.isWhitespace() -> {
                consumeWhitespace(text, pos, line, baseOffset)
            }

            text.startsWith("//", pos) -> {
                consumeLineComment(text, pos, line, baseOffset)
            }

            text.startsWith("/*", pos) -> {
                consumeBlockComment(text, pos, line, state, baseOffset)
            }

            text.startsWith("r#", pos) || text.startsWith("r\"", pos) -> {
                consumeRawString(text, pos, line, baseOffset)
            }

            text.startsWith("b\"", pos) -> {
                consumeByteString(text, pos, line, baseOffset)
            }

            text.startsWith("b'", pos) -> {
                consumeByteChar(text, pos, line, baseOffset)
            }

            char == '"' -> {
                consumeString(text, pos, line, baseOffset)
            }

            char == '\'' -> {
                consumeCharOrLifetime(text, pos, line, baseOffset)
            }

            char == '#' && pos + 1 < text.length && text[pos + 1] == '[' -> {
                consumeAttribute(
                    text,
                    pos,
                    line,
                    baseOffset,
                )
            }

            char == '#' && pos + 1 < text.length && text[pos + 1] == '!' -> {
                consumeInnerAttribute(
                    text,
                    pos,
                    line,
                    baseOffset,
                )
            }

            char.isDigit() -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            char.isLetter() || char == '_' -> {
                consumeIdentifier(text, pos, line, baseOffset)
            }

            char in BRACKETS -> {
                Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), state, 1)
            }

            char in PUNCTUATION -> {
                Triple(Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line), state, 1)
            }

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
        return Triple(Token(TokenType.WHITESPACE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeLineComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val isDoc = text.startsWith("///", pos) || text.startsWith("//!", pos)
        var length = if (isDoc) 3 else 2
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_LINE
        return Triple(Token(tokenType, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeBlockComment(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val isDoc = text.startsWith("/**", pos) || text.startsWith("/*!", pos)
        var length = if (isDoc && !text.startsWith("/***", pos)) 3 else 2
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

        val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            if (depth == 0) LexerState.Initial else state.copy(inBlockComment = true, blockCommentDepth = depth),
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

        return Triple(
            Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
            if (depth == 0) LexerState.Initial else state.copy(blockCommentDepth = depth),
            length,
        )
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

    private fun consumeRawString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var hashCount = 0
        var idx = pos + 1
        while (idx < text.length && text[idx] == '#') {
            hashCount++
            idx++
        }
        if (idx >= text.length || text[idx] != '"') {
            return Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), LexerState.Initial, 1)
        }

        var length = 2 + hashCount
        val endPattern = "\"" + "#".repeat(hashCount)

        while (pos + length < text.length) {
            if (text.startsWith(endPattern, pos + length)) {
                length += endPattern.length
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true, stringDelimiter = endPattern),
            length,
        )
    }

    private fun consumeRawStringContinuation(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        val endPattern = state.stringDelimiter

        while (pos + length < text.length) {
            if (text.startsWith(endPattern, pos + length)) {
                length += endPattern.length
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), state, length)
    }

    private fun consumeByteString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
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

    private fun consumeByteChar(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
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

    private fun consumeCharOrLifetime(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        if (pos + 1 < text.length && (text[pos + 1].isLetter() || text[pos + 1] == '_')) {
            var length = 2
            while (pos + length < text.length && (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')) {
                length++
            }
            if (pos + length < text.length && text[pos + length] == '\'') {
                length++
                return Triple(Token(TokenType.CHARACTER, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            return Triple(Token(TokenType.TYPE, baseOffset + pos, length, line), LexerState.Initial, length)
        }

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

    private fun consumeAttribute(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        var depth = 1
        while (pos + length < text.length && depth > 0) {
            when (text[pos + length]) {
                '[' -> depth++
                ']' -> depth--
            }
            length++
        }
        return Triple(Token(TokenType.ANNOTATION, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeInnerAttribute(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        if (pos + length < text.length && text[pos + length] == '[') {
            var depth = 1
            length++
            while (pos + length < text.length && depth > 0) {
                when (text[pos + length]) {
                    '[' -> depth++
                    ']' -> depth--
                }
                length++
            }
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
                    while ((pos + length < text.length) &&
                        (text[pos + length].isHexDigit() || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                'o' -> {
                    length = 2
                    while ((pos + length < text.length) &&
                        (text[pos + length] in '0'..'7' || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                'b' -> {
                    length = 2
                    while ((pos + length < text.length) &&
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

        while ((pos + length < text.length) && (text[pos + length].isLetter() || text[pos + length] == '_')) {
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

        val isMacro = pos + length < text.length && text[pos + length] == '!'
        if (isMacro) {
            length++
            return Triple(Token(TokenType.FUNCTION, baseOffset + pos, length, line), LexerState.Initial, length)
        }

        val type =
            when {
                word in PRIMITIVE_TYPES -> TokenType.TYPE
                word in KEYWORDS -> if (word in MODIFIERS) TokenType.MODIFIER else TokenType.KEYWORD
                word.first().isUpperCase() || (word == "self" && text.getOrNull(pos + length) == ':') -> TokenType.TYPE
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
