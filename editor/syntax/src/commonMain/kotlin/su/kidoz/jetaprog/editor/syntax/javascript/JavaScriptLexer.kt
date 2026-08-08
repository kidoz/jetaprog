package su.kidoz.jetaprog.editor.syntax.javascript

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/** Lexer for modern JavaScript source, including JSX-compatible punctuation. */
public open class JavaScriptLexer protected constructor(
    final override val languageId: String,
    private val typeScript: Boolean,
) : Lexer {
    /** Creates a JavaScript lexer. */
    public constructor() : this(JAVASCRIPT_LANGUAGE_ID, false)

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
        if (state.inMultilineString) return consumeTemplate(text, position, line, baseOffset, continuation = true)

        val character = text[position]
        return when {
            character == '\n' -> {
                token(TokenType.NEWLINE, position, 1, line, state, baseOffset)
            }

            character.isWhitespace() -> {
                consumeWhitespace(text, position, line, baseOffset)
            }

            text.startsWith("//", position) -> {
                consumeLineComment(text, position, line, baseOffset)
            }

            text.startsWith("/*", position) -> {
                consumeBlockComment(text, position, line, state, baseOffset, true)
            }

            character == '\'' || character == '"' -> {
                consumeString(text, position, line, baseOffset, character)
            }

            character == '`' -> {
                consumeTemplate(text, position, line, baseOffset, continuation = false)
            }

            character == '@' -> {
                consumeDecorator(text, position, line, baseOffset)
            }

            character == '#' && text.getOrNull(position + 1).isIdentifierStart() -> {
                consumeIdentifier(text, position, line, baseOffset, privateIdentifier = true)
            }

            character.isDigit() ||
                (character == '.' && text.getOrNull(position + 1)?.isDigit() == true) -> {
                consumeNumber(text, position, line, baseOffset)
            }

            character.isIdentifierStart() -> {
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
    ): Triple<Token, LexerState, Int> {
        var length = 2
        while (text.getOrNull(position + length)?.let { it != '\n' } == true) length++
        return token(TokenType.COMMENT_LINE, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeBlockComment(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
        opening: Boolean,
    ): Triple<Token, LexerState, Int> {
        val isDoc = if (opening) text.startsWith("/**", position) else state.inDocComment
        var length = if (opening) 2 else 0
        while (position + length < text.length) {
            if (text.startsWith("*/", position + length)) {
                length += 2
                return token(commentType(isDoc), position, length, line, LexerState.Initial, baseOffset)
            }
            length++
        }
        return token(
            commentType(isDoc),
            position,
            length,
            line,
            LexerState(inBlockComment = true, inDocComment = isDoc),
            baseOffset,
        )
    }

    private fun consumeString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        quote: Char,
    ): Triple<Token, LexerState, Int> {
        var length = 1
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
        return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeTemplate(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        continuation: Boolean,
    ): Triple<Token, LexerState, Int> {
        var length = if (continuation) 0 else 1
        while (position + length < text.length) {
            when (text[position + length]) {
                '\\' -> {
                    length += if (position + length + 1 < text.length) 2 else 1
                }

                '`' -> {
                    length++
                    return token(TokenType.STRING_TEMPLATE, position, length, line, LexerState.Initial, baseOffset)
                }

                else -> {
                    length++
                }
            }
        }
        return token(
            TokenType.STRING_TEMPLATE,
            position,
            length,
            line,
            LexerState(inMultilineString = true, stringDelimiter = "`"),
            baseOffset,
        )
    }

    private fun consumeDecorator(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (text.getOrNull(position + length).isIdentifierPart()) length++
        return token(TokenType.ANNOTATION, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeNumber(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length: Int
        val prefix = text.drop(position).take(2).lowercase()
        if (prefix in setOf("0x", "0b", "0o")) {
            length = 2
            val validDigit: (Char) -> Boolean =
                when (prefix) {
                    "0x" -> { character -> (character.isDigit() || character.lowercaseChar() in 'a'..'f') }
                    "0b" -> { character -> (character == '0' || character == '1') }
                    else -> { character -> character in '0'..'7' }
                }
            while (text.getOrNull(position + length)?.let { (validDigit(it) || it == '_') } == true) length++
        } else {
            length = if (text[position] == '.') 1 else 0
            while (text.getOrNull(position + length)?.let { (it.isDigit() || it == '_') } == true) length++
            if (text.getOrNull(position + length) == '.') {
                length++
                while (text.getOrNull(position + length)?.let { (it.isDigit() || it == '_') } == true) length++
            }
            if (text.getOrNull(position + length) in setOf('e', 'E')) {
                length++
                if (text.getOrNull(position + length) in setOf('+', '-')) length++
                while (text.getOrNull(position + length)?.let { (it.isDigit() || it == '_') } == true) length++
            }
        }
        if (text.getOrNull(position + length) == 'n') length++
        return token(
            TokenType.NUMBER,
            position,
            length.coerceAtLeast(1),
            line,
            LexerState.Initial,
            baseOffset,
        )
    }

    private fun consumeIdentifier(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        privateIdentifier: Boolean = false,
    ): Triple<Token, LexerState, Int> {
        var length = if (privateIdentifier) 1 else 0
        while (text.getOrNull(position + length).isIdentifierPart()) length++
        val word = text.substring(position, position + length)
        val normalized = word.removePrefix("#")
        val isKeyword = normalized in JAVASCRIPT_KEYWORDS || (typeScript && normalized in TYPESCRIPT_KEYWORDS)
        val type =
            when {
                normalized in CONSTANTS -> TokenType.CONSTANT
                typeScript && normalized in TYPESCRIPT_TYPES -> TokenType.TYPE
                typeScript && normalized in TYPESCRIPT_MODIFIERS -> TokenType.MODIFIER
                isKeyword -> TokenType.KEYWORD
                text.getOrNull(position + length) == '(' -> TokenType.FUNCTION
                normalized.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
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

    private fun token(
        type: TokenType,
        position: Int,
        length: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> = Triple(Token(type, baseOffset + position, length, line), state, length)

    private fun commentType(isDoc: Boolean): TokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK

    private fun Char?.isIdentifierStart(): Boolean = this != null && (isLetter() || this == '_' || this == '$')

    private fun Char?.isIdentifierPart(): Boolean = this != null && (isLetterOrDigit() || this == '_' || this == '$')

    private companion object {
        const val JAVASCRIPT_LANGUAGE_ID = "javascript"
        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',', ';', ':')
        val CONSTANTS = setOf("true", "false", "null", "undefined", "NaN", "Infinity")
        val JAVASCRIPT_KEYWORDS =
            setOf(
                "async",
                "await",
                "break",
                "case",
                "catch",
                "class",
                "const",
                "continue",
                "debugger",
                "default",
                "delete",
                "do",
                "else",
                "export",
                "extends",
                "finally",
                "for",
                "from",
                "function",
                "get",
                "if",
                "import",
                "in",
                "instanceof",
                "let",
                "new",
                "of",
                "return",
                "set",
                "static",
                "super",
                "switch",
                "this",
                "throw",
                "try",
                "typeof",
                "var",
                "void",
                "while",
                "with",
                "yield",
            )
        val TYPESCRIPT_KEYWORDS =
            setOf(
                "any",
                "as",
                "asserts",
                "constructor",
                "declare",
                "enum",
                "implements",
                "infer",
                "interface",
                "is",
                "keyof",
                "module",
                "namespace",
                "never",
                "object",
                "out",
                "satisfies",
                "symbol",
                "type",
                "undefined",
                "unique",
                "unknown",
            )
        val TYPESCRIPT_MODIFIERS =
            setOf("abstract", "accessor", "declare", "override", "private", "protected", "public", "readonly")
        val TYPESCRIPT_TYPES =
            setOf("any", "bigint", "boolean", "never", "number", "object", "string", "symbol", "unknown", "void")
        val OPERATORS =
            setOf(
                ">>>=",
                "**=",
                "&&=",
                "||=",
                "??=",
                "===",
                "!==",
                ">>>",
                "<<=",
                ">>=",
                "=>",
                "**",
                "&&",
                "||",
                "??",
                "?.",
                "++",
                "--",
                "==",
                "!=",
                "<=",
                ">=",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "&=",
                "|=",
                "^=",
                "<<",
                ">>",
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "!",
                "<",
                ">",
                "&",
                "|",
                "^",
                "~",
                "?",
            ).sortedByDescending(String::length)
    }
}

/** Lexer for TypeScript and TSX source. */
public class TypeScriptLexer : JavaScriptLexer(TYPESCRIPT_LANGUAGE_ID, true) {
    private companion object {
        const val TYPESCRIPT_LANGUAGE_ID = "typescript"
    }
}
