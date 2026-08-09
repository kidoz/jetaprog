package su.kidoz.jetaprog.editor.syntax.csharp

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/** Lexer for modern C# source, including verbatim and raw string literals. */
public class CSharpLexer : Lexer {
    override val languageId: String = LANGUAGE_ID

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var position = 0
        var line = 0
        var state = LexerState.Initial

        while (position < text.length) {
            val (token, nextState, consumed) = nextToken(text, position, line, state)
            if (token != null && token.type != TokenType.WHITESPACE && token.type != TokenType.NEWLINE) {
                tokens += token
            }
            repeat(consumed) { offset ->
                if (text[position + offset] == '\n') line++
            }
            position += consumed
            state = nextState
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
        var position = 0
        var currentState = state

        while (position < text.length) {
            val (token, nextState, consumed) =
                nextToken(text, position, lineNumber, currentState, startOffset)
            if (token != null && token.type != TokenType.WHITESPACE) tokens += token
            position += consumed
            currentState = nextState
        }

        return tokens to currentState
    }

    private fun nextToken(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int = 0,
    ): Triple<Token?, LexerState, Int> {
        if (state.inBlockComment) return consumeBlockComment(text, position, line, state, baseOffset, false)
        if (state.inMultilineString) return consumeMultilineString(text, position, line, state, baseOffset)

        val character = text[position]
        val rawStringPrefixLength = rawStringPrefixLength(text, position)
        return when {
            character == '\n' -> {
                token(TokenType.NEWLINE, position, 1, line, state, baseOffset)
            }

            character.isWhitespace() -> {
                consumeWhitespace(text, position, line, baseOffset)
            }

            text.startsWith("///", position) -> {
                consumeLineComment(text, position, line, baseOffset, TokenType.COMMENT_DOC)
            }

            text.startsWith("//", position) -> {
                consumeLineComment(text, position, line, baseOffset, TokenType.COMMENT_LINE)
            }

            text.startsWith("/**", position) -> {
                consumeBlockComment(text, position, line, state, baseOffset, true, true)
            }

            text.startsWith("/*", position) -> {
                consumeBlockComment(text, position, line, state, baseOffset, true)
            }

            rawStringPrefixLength > 0 -> {
                consumeRawString(text, position, line, baseOffset, rawStringPrefixLength)
            }

            text.startsWith("$@\"", position) || text.startsWith("@$\"", position) -> {
                consumeVerbatimString(text, position, line, baseOffset, 3)
            }

            text.startsWith("@\"", position) -> {
                consumeVerbatimString(text, position, line, baseOffset, 2)
            }

            text.startsWith("$\"", position) -> {
                consumeQuotedLiteral(text, position, line, baseOffset, 2, '"', TokenType.STRING)
            }

            character == '"' -> {
                consumeQuotedLiteral(text, position, line, baseOffset, 1, character, TokenType.STRING)
            }

            character == '\'' -> {
                consumeQuotedLiteral(text, position, line, baseOffset, 1, character, TokenType.CHARACTER)
            }

            character == '#' -> {
                consumeDirective(text, position, line, baseOffset)
            }

            character.isDigit() -> {
                consumeNumber(text, position, line, baseOffset)
            }

            character.isIdentifierStart() || (character == '@' && text.getOrNull(position + 1).isIdentifierStart()) -> {
                consumeIdentifier(text, position, line, baseOffset)
            }

            character in BRACKETS -> {
                token(TokenType.BRACKET, position, 1, line, state, baseOffset)
            }

            character in PUNCTUATION -> {
                token(TokenType.PUNCTUATION, position, 1, line, state, baseOffset)
            }

            else -> {
                consumeOperator(text, position, line, state, baseOffset)
            }
        }
    }

    private fun consumeWhitespace(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (text.getOrNull(position + length)?.let { it.isWhitespace() && it != '\n' } == true) length++
        return token(TokenType.WHITESPACE, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeLineComment(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        type: TokenType,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        while (text.getOrNull(position + length)?.let { it != '\n' } == true) length++
        return token(type, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeBlockComment(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
        opening: Boolean,
        documentation: Boolean = state.inDocComment,
    ): Triple<Token, LexerState, Int> {
        var length = if (opening) 2 else 0
        while (position + length < text.length) {
            if (text.startsWith("*/", position + length)) {
                length += 2
                val type = if (documentation) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
                return token(type, position, length, line, LexerState.Initial, baseOffset)
            }
            length++
        }
        val type = if (documentation) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
        return token(
            type,
            position,
            length,
            line,
            state.copy(inBlockComment = true, inDocComment = documentation),
            baseOffset,
        )
    }

    private fun consumeQuotedLiteral(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        prefixLength: Int,
        quote: Char,
        type: TokenType,
    ): Triple<Token, LexerState, Int> {
        var length = prefixLength
        while (position + length < text.length) {
            when (text[position + length]) {
                '\\' -> {
                    length += if (position + length + 1 < text.length) 2 else 1
                }

                quote -> {
                    length++
                    break
                }

                '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }
        return token(type, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeVerbatimString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        prefixLength: Int,
    ): Triple<Token, LexerState, Int> {
        var length = prefixLength
        while (position + length < text.length) {
            if (text.startsWith("\"\"", position + length)) {
                length += 2
            } else if (text[position + length] == '"') {
                length++
                return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
            } else {
                length++
            }
        }
        return token(
            TokenType.STRING,
            position,
            length,
            line,
            LexerState(inMultilineString = true, stringDelimiter = VERBATIM_STRING_DELIMITER),
            baseOffset,
        )
    }

    private fun consumeRawString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        prefixLength: Int,
    ): Triple<Token, LexerState, Int> {
        val quoteStart = position + text.drop(position).takeWhile { it == '$' }.length
        val delimiter = text.drop(quoteStart).takeWhile { it == '"' }
        var length = prefixLength
        while (position + length < text.length) {
            if (text.startsWith(delimiter, position + length)) {
                length += delimiter.length
                return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
            }
            length++
        }
        return token(
            TokenType.STRING,
            position,
            length,
            line,
            LexerState(inMultilineString = true, stringDelimiter = delimiter),
            baseOffset,
        )
    }

    private fun consumeMultilineString(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val delimiter = state.stringDelimiter
        var length = 0
        while (position + length < text.length) {
            if (delimiter == VERBATIM_STRING_DELIMITER && text.startsWith("\"\"", position + length)) {
                length += 2
            } else if (text.startsWith(delimiter.removePrefix("@"), position + length)) {
                length += delimiter.removePrefix("@").length
                return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
            } else {
                length++
            }
        }
        return token(TokenType.STRING, position, length, line, state, baseOffset)
    }

    private fun consumeDirective(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (text.getOrNull(position + length)?.isLetter() == true) length++
        return token(TokenType.KEYWORD, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeNumber(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val prefix = text.drop(position).take(2).lowercase()
        var length = if (prefix in NUMBER_PREFIXES) 2 else 0
        if (length > 0) {
            while (text.getOrNull(position + length)?.isBasedDigitOrSeparator(prefix) == true) length++
        } else {
            while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
            if (text.getOrNull(position + length) == '.') {
                length++
                while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
            }
            if (text.getOrNull(position + length) == 'e' || text.getOrNull(position + length) == 'E') {
                length++
                if (text.getOrNull(position + length) == '+' || text.getOrNull(position + length) == '-') length++
                while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
            }
        }
        while (text.getOrNull(position + length) in NUMBER_SUFFIXES) length++
        return token(TokenType.NUMBER, position, length.coerceAtLeast(1), line, LexerState.Initial, baseOffset)
    }

    private fun consumeIdentifier(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = if (text[position] == '@') 1 else 0
        while (text.getOrNull(position + length).isIdentifierPart()) length++
        val word = text.substring(position, position + length).removePrefix("@")
        val type =
            when {
                word in CONSTANTS -> TokenType.CONSTANT
                word in TYPES -> TokenType.TYPE
                word in MODIFIERS -> TokenType.MODIFIER
                word in KEYWORDS -> TokenType.KEYWORD
                text.getOrNull(position + length) == '(' -> TokenType.FUNCTION
                word.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
                else -> TokenType.IDENTIFIER
            }
        return token(type, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeOperator(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val operator = OPERATORS.firstOrNull { text.startsWith(it, position) }
        return if (operator == null) {
            token(TokenType.UNKNOWN, position, 1, line, state, baseOffset)
        } else {
            token(TokenType.OPERATOR, position, operator.length, line, state, baseOffset)
        }
    }

    private fun rawStringPrefixLength(
        text: String,
        position: Int,
    ): Int {
        val dollarCount = text.drop(position).takeWhile { it == '$' }.length
        val quoteCount = text.drop(position + dollarCount).takeWhile { it == '"' }.length
        return if (quoteCount >= MIN_RAW_STRING_QUOTES) dollarCount + quoteCount else 0
    }

    private fun token(
        type: TokenType,
        position: Int,
        length: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> = Triple(Token(type, baseOffset + position, length, line), state, length)

    private fun Char?.isIdentifierStart(): Boolean = this != null && (isLetter() || this == '_')

    private fun Char?.isIdentifierPart(): Boolean = this != null && (isLetterOrDigit() || this == '_')

    private fun Char.isBasedDigitOrSeparator(prefix: String): Boolean =
        this == '_' ||
            when (prefix) {
                "0x" -> isDigit() || lowercaseChar() in 'a'..'f'
                else -> this == '0' || this == '1'
            }

    private companion object {
        const val LANGUAGE_ID = "csharp"
        const val VERBATIM_STRING_DELIMITER = "@\""
        const val MIN_RAW_STRING_QUOTES = 3
        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',', ';', ':')
        val NUMBER_PREFIXES = setOf("0x", "0b")
        val NUMBER_SUFFIXES = setOf('f', 'F', 'd', 'D', 'm', 'M', 'u', 'U', 'l', 'L')
        val CONSTANTS = setOf("true", "false", "null")
        val TYPES =
            setOf(
                "bool",
                "byte",
                "char",
                "decimal",
                "double",
                "dynamic",
                "float",
                "int",
                "long",
                "nint",
                "nuint",
                "object",
                "sbyte",
                "short",
                "string",
                "uint",
                "ulong",
                "ushort",
                "void",
            )
        val MODIFIERS =
            setOf(
                "abstract",
                "async",
                "const",
                "extern",
                "file",
                "internal",
                "new",
                "override",
                "partial",
                "private",
                "protected",
                "public",
                "readonly",
                "required",
                "sealed",
                "static",
                "unsafe",
                "virtual",
                "volatile",
            )
        val KEYWORDS =
            setOf(
                "add",
                "alias",
                "and",
                "as",
                "ascending",
                "await",
                "base",
                "break",
                "by",
                "case",
                "catch",
                "checked",
                "class",
                "continue",
                "default",
                "delegate",
                "descending",
                "do",
                "else",
                "enum",
                "equals",
                "event",
                "explicit",
                "finally",
                "fixed",
                "for",
                "foreach",
                "from",
                "get",
                "global",
                "goto",
                "group",
                "if",
                "implicit",
                "in",
                "init",
                "interface",
                "into",
                "is",
                "join",
                "let",
                "lock",
                "managed",
                "nameof",
                "namespace",
                "not",
                "notnull",
                "on",
                "operator",
                "or",
                "orderby",
                "out",
                "params",
                "record",
                "ref",
                "remove",
                "return",
                "scoped",
                "select",
                "set",
                "sizeof",
                "stackalloc",
                "struct",
                "switch",
                "this",
                "throw",
                "try",
                "typeof",
                "unchecked",
                "unmanaged",
                "using",
                "value",
                "var",
                "when",
                "where",
                "while",
                "with",
                "yield",
            )
        val OPERATORS =
            setOf(
                ">>>=",
                "<<=",
                ">>=",
                "??=",
                "=>",
                "??",
                "?.",
                "?[",
                "++",
                "--",
                "&&",
                "||",
                "==",
                "!=",
                "<=",
                ">=",
                "<<",
                ">>>",
                ">>",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "&=",
                "|=",
                "^=",
                "..",
                "+",
                "-",
                "*",
                "/",
                "%",
                "&",
                "|",
                "^",
                "!",
                "~",
                "=",
                "<",
                ">",
                "?",
            ).sortedByDescending(String::length)
    }
}
