package su.kidoz.jetaprog.editor.syntax.c

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the C programming language, targeting C23 (ISO/IEC 9899:2024).
 *
 * Recognises the C23 additions on top of C17: the unprefixed `bool`, `true`, `false`,
 * `nullptr`, `constexpr`, `typeof`/`typeof_unqual` and `_BitInt` keywords, `[[...]]`
 * attributes, binary literals, digit separators and the `#embed`/`#elifdef`/`#elifndef`
 * preprocessor directives.
 */
public class CLexer : Lexer {
    override val languageId: String = "c"

    private companion object {
        /** Keywords defined by C23, including the legacy underscore-prefixed spellings. */
        val KEYWORDS =
            setOf(
                "alignas",
                "alignof",
                "auto",
                "bool",
                "break",
                "case",
                "char",
                "const",
                "constexpr",
                "continue",
                "default",
                "do",
                "double",
                "else",
                "enum",
                "extern",
                "false",
                "float",
                "for",
                "goto",
                "if",
                "inline",
                "int",
                "long",
                "nullptr",
                "register",
                "restrict",
                "return",
                "short",
                "signed",
                "sizeof",
                "static",
                "static_assert",
                "struct",
                "switch",
                "thread_local",
                "true",
                "typedef",
                "typeof",
                "typeof_unqual",
                "union",
                "unsigned",
                "void",
                "volatile",
                "while",
                // Underscore-prefixed spellings kept for compatibility with C11/C17.
                "_Alignas",
                "_Alignof",
                "_Atomic",
                "_BitInt",
                "_Bool",
                "_Complex",
                "_Decimal32",
                "_Decimal64",
                "_Decimal128",
                "_Generic",
                "_Imaginary",
                "_Noreturn",
                "_Static_assert",
                "_Thread_local",
            )

        val MODIFIERS =
            setOf(
                "auto",
                "const",
                "constexpr",
                "extern",
                "inline",
                "register",
                "restrict",
                "static",
                "thread_local",
                "volatile",
                "_Atomic",
                "_Noreturn",
                "_Thread_local",
            )

        val PRIMITIVE_TYPES =
            setOf(
                "bool",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short",
                "signed",
                "unsigned",
                "void",
                "_BitInt",
                "_Bool",
                "_Complex",
                "_Decimal32",
                "_Decimal64",
                "_Decimal128",
                "_Imaginary",
                // Common standard-library typedefs.
                "char8_t",
                "char16_t",
                "char32_t",
                "wchar_t",
                "size_t",
                "ssize_t",
                "ptrdiff_t",
                "nullptr_t",
                "max_align_t",
                "intptr_t",
                "uintptr_t",
                "intmax_t",
                "uintmax_t",
                "int8_t",
                "int16_t",
                "int32_t",
                "int64_t",
                "uint8_t",
                "uint16_t",
                "uint32_t",
                "uint64_t",
                "FILE",
                "va_list",
            )

        val CONSTANTS = setOf("true", "false", "nullptr", "NULL")

        /** Preprocessor directives, including the C23 `#embed`, `#elifdef` and `#elifndef`. */
        val PREPROCESSOR_DIRECTIVES =
            setOf(
                "include",
                "embed",
                "define",
                "undef",
                "ifdef",
                "ifndef",
                "if",
                "else",
                "elif",
                "elifdef",
                "elifndef",
                "endif",
                "error",
                "warning",
                "pragma",
                "line",
            )

        val OPERATORS =
            setOf(
                "...",
                "<<=",
                ">>=",
                "->",
                "++",
                "--",
                "<<",
                ">>",
                "<=",
                ">=",
                "==",
                "!=",
                "&&",
                "||",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "&=",
                "|=",
                "^=",
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "<",
                ">",
                "!",
                "&",
                "|",
                "^",
                "~",
                "?",
                ":",
            )

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')

        val PUNCTUATION = setOf('.', ',', ';')

        /** Prefixes that may precede a string or character literal. */
        val LITERAL_PREFIXES = listOf("u8", "u", "U", "L")

        /** Integer and floating-point literal suffixes, longest first. */
        val NUMBER_SUFFIXES =
            listOf(
                "wb",
                "uwb",
                "wbu",
                "ull",
                "llu",
                "df",
                "dd",
                "dl",
                "ul",
                "lu",
                "ll",
                "u",
                "l",
                "f",
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

        if (state.inBlockComment) {
            return consumeBlockCommentContinuation(text, pos, line, state, baseOffset)
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

            char == '#' -> {
                consumePreprocessor(text, pos, line, baseOffset)
            }

            text.startsWith("[[", pos) -> {
                consumeAttribute(text, pos, line, baseOffset)
            }

            char == '"' -> {
                consumeString(text, pos, line, baseOffset)
            }

            char == '\'' -> {
                consumeChar(text, pos, line, baseOffset)
            }

            char.isDigit() -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            char == '.' && pos + 1 < text.length && text[pos + 1].isDigit() -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            char.isLetter() || char == '_' -> {
                consumeWord(text, pos, line, baseOffset)
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
            state.copy(inBlockComment = true, inDocComment = isDoc),
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
        val tokenType = if (state.inDocComment) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
        var length = 0
        while (pos + length < text.length) {
            if (text.startsWith("*/", pos + length)) {
                length += 2
                return Triple(Token(tokenType, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }
        return Triple(Token(tokenType, baseOffset + pos, length, line), state, length)
    }

    /**
     * Consumes a preprocessor directive such as `#include`, `#embed` or `#elifdef`.
     *
     * The `#` and the directive name form a single keyword token; the remainder of the
     * line is tokenised normally so that header names and macro bodies stay highlighted.
     */
    private fun consumePreprocessor(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length && text[pos + length] == ' ') {
            length++
        }
        val nameStart = pos + length
        var nameEnd = nameStart
        while (nameEnd < text.length && text[nameEnd].isLetter()) {
            nameEnd++
        }
        val directive = text.substring(nameStart, nameEnd)
        if (directive !in PREPROCESSOR_DIRECTIVES) {
            // A stray '#' or the '#'/'##' stringify and paste operators.
            val operatorLength = if (text.startsWith("##", pos)) 2 else 1
            return Triple(
                Token(TokenType.OPERATOR, baseOffset + pos, operatorLength, line),
                LexerState.Initial,
                operatorLength,
            )
        }
        val total = nameEnd - pos
        return Triple(Token(TokenType.KEYWORD, baseOffset + pos, total, line), LexerState.Initial, total)
    }

    /**
     * Consumes a C23 `[[attribute]]` sequence as a single annotation token.
     */
    private fun consumeAttribute(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        while (pos + length < text.length && !text.startsWith("]]", pos + length)) {
            if (text[pos + length] == '\n') {
                // Unterminated on this line: fall back to a bracket token.
                return Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), LexerState.Initial, 1)
            }
            length++
        }
        if (pos + length >= text.length) {
            return Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), LexerState.Initial, 1)
        }
        length += 2
        return Triple(Token(TokenType.ANNOTATION, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
        prefixLength: Int = 0,
    ): Triple<Token, LexerState, Int> {
        var length = prefixLength + 1
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

    private fun consumeChar(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
        prefixLength: Int = 0,
    ): Triple<Token, LexerState, Int> {
        var length = prefixLength + 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                char == '\'' -> {
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
        return Triple(Token(TokenType.CHARACTER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length =
            when {
                text.startsWith("0x", pos, ignoreCase = true) -> consumeRadixDigits(text, pos, 2) { it.isHexDigit() }
                text.startsWith("0b", pos, ignoreCase = true) -> consumeRadixDigits(text, pos, 2) { it in '0'..'1' }
                else -> consumeDecimalNumber(text, pos)
            }
        length += consumeSuffix(text, pos + length)
        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeRadixDigits(
        text: String,
        pos: Int,
        prefixLength: Int,
        isDigit: (Char) -> Boolean,
    ): Int {
        var length = prefixLength
        while (pos + length < text.length && (isDigit(text[pos + length]) || text[pos + length] == '\'')) {
            length++
        }
        // Hexadecimal floating literals: 0x1.8p3
        if (pos + length < text.length && text[pos + length] == '.') {
            length++
            while (pos + length < text.length && (text[pos + length].isHexDigit() || text[pos + length] == '\'')) {
                length++
            }
        }
        if (pos + length < text.length && text[pos + length].lowercaseChar() == 'p') {
            length++
            if (pos + length < text.length && (text[pos + length] == '+' || text[pos + length] == '-')) {
                length++
            }
            while (pos + length < text.length && text[pos + length].isDigit()) {
                length++
            }
        }
        return length
    }

    private fun consumeDecimalNumber(
        text: String,
        pos: Int,
    ): Int {
        var length = 0
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '\'')) {
            length++
        }
        if (pos + length < text.length && text[pos + length] == '.') {
            length++
            while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '\'')) {
                length++
            }
        }
        if (pos + length < text.length && text[pos + length].lowercaseChar() == 'e') {
            length++
            if (pos + length < text.length && (text[pos + length] == '+' || text[pos + length] == '-')) {
                length++
            }
            while (pos + length < text.length && text[pos + length].isDigit()) {
                length++
            }
        }
        return length
    }

    /**
     * Consumes a literal suffix such as `u`, `ll`, `wb` (C23 `_BitInt`) or `df` (decimal float).
     */
    private fun consumeSuffix(
        text: String,
        pos: Int,
    ): Int {
        val remaining = text.length - pos
        if (remaining <= 0) return 0
        for (suffix in NUMBER_SUFFIXES) {
            if (text.startsWith(suffix, pos, ignoreCase = true)) {
                val end = pos + suffix.length
                val boundary = end >= text.length || !(text[end].isLetterOrDigit() || text[end] == '_')
                if (boundary) return suffix.length
            }
        }
        return 0
    }

    /**
     * Consumes an identifier, keyword, or a prefixed string/character literal such as `u8"x"`.
     */
    private fun consumeWord(
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

        if (word in LITERAL_PREFIXES && pos + length < text.length) {
            when (text[pos + length]) {
                '"' -> return consumeString(text, pos, line, baseOffset, prefixLength = length)
                '\'' -> return consumeChar(text, pos, line, baseOffset, prefixLength = length)
                else -> Unit
            }
        }

        val type = classifyWord(word, text, pos + length)
        return Triple(Token(type, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun classifyWord(
        word: String,
        text: String,
        end: Int,
    ): TokenType {
        val next = text.getOrNull(end)
        return when {
            word in CONSTANTS -> TokenType.CONSTANT

            word in PRIMITIVE_TYPES -> TokenType.TYPE

            word in KEYWORDS -> if (word in MODIFIERS) TokenType.MODIFIER else TokenType.KEYWORD

            next == '(' -> TokenType.FUNCTION

            word.endsWith("_t") -> TokenType.TYPE

            // Uppercase identifiers are macros or enumeration constants by convention.
            word.length > 1 && word.all { it.isUpperCase() || it.isDigit() || it == '_' } -> TokenType.CONSTANT

            else -> TokenType.IDENTIFIER
        }
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
