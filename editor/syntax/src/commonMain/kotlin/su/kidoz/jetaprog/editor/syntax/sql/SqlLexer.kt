package su.kidoz.jetaprog.editor.syntax.sql

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/** SQL dialects supported by the bundled lexer. */
public enum class SqlDialect(
    /** Language identifier used by the editor registry. */
    public val languageId: String,
) {
    /** Portable SQL syntax. */
    STANDARD("sql"),

    /** PostgreSQL syntax and lexical extensions. */
    POSTGRESQL("postgresql"),

    /** StarRocks SQL syntax and lexical extensions. */
    STARROCKS("starrocks"),
}

/**
 * Lexer for SQL with PostgreSQL and StarRocks dialect modes.
 *
 * The lexer deliberately classifies identifiers context-free. Schema-aware completion and
 * validation belong to a database connection provider, while this lexer remains useful offline.
 */
public class SqlLexer(
    private val dialect: SqlDialect = SqlDialect.STANDARD,
) : Lexer {
    override val languageId: String = dialect.languageId

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
        val dollarDelimiter = if (dialect == SqlDialect.POSTGRESQL) dollarDelimiterAt(text, position) else null
        val stringPrefixLength = stringPrefixLength(text, position)
        return when {
            character == '\n' -> {
                token(TokenType.NEWLINE, position, 1, line, state, baseOffset)
            }

            character.isWhitespace() -> {
                consumeWhitespace(text, position, line, baseOffset)
            }

            text.startsWith("--", position) -> {
                consumeLineComment(text, position, line, baseOffset, 2)
            }

            dialect == SqlDialect.STARROCKS && character == '#' -> {
                consumeLineComment(text, position, line, baseOffset, 1)
            }

            text.startsWith("/*", position) -> {
                consumeBlockComment(text, position, line, state, baseOffset, true)
            }

            dollarDelimiter != null -> {
                consumeDollarString(text, position, line, baseOffset, dollarDelimiter)
            }

            dialect == SqlDialect.POSTGRESQL && text.startsWith("U&\"", position, ignoreCase = true) -> {
                consumeQuotedIdentifier(text, position, line, baseOffset, 3, '"')
            }

            stringPrefixLength > 0 -> {
                consumeString(text, position, line, baseOffset, stringPrefixLength)
            }

            character == '\'' -> {
                consumeString(text, position, line, baseOffset, 1)
            }

            dialect == SqlDialect.STARROCKS && character == '"' -> {
                consumeString(text, position, line, baseOffset, 1, '"')
            }

            character == '"' -> {
                consumeQuotedIdentifier(text, position, line, baseOffset, 1, '"')
            }

            dialect == SqlDialect.STARROCKS && character == '`' -> {
                consumeQuotedIdentifier(text, position, line, baseOffset, 1, '`')
            }

            character.isDigit() || (character == '.' && text.getOrNull(position + 1)?.isDigit() == true) -> {
                consumeNumber(text, position, line, baseOffset)
            }

            character == '$' && text.getOrNull(position + 1)?.isDigit() == true -> {
                consumeParameter(text, position, line, baseOffset)
            }

            (character == ':' || character == '@') && text.getOrNull(position + 1).isIdentifierStart() -> {
                consumeParameter(text, position, line, baseOffset)
            }

            character == '?' && dialect != SqlDialect.POSTGRESQL -> {
                token(TokenType.PARAMETER, position, 1, line, state, baseOffset)
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
        prefixLength: Int,
    ): Triple<Token, LexerState, Int> {
        var length = prefixLength
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
        var length = if (opening) 2 else 0
        var depth = if (opening) 1 else state.blockCommentDepth.coerceAtLeast(1)
        while (position + length < text.length) {
            when {
                text.startsWith("/*", position + length) -> {
                    depth++
                    length += 2
                }

                text.startsWith("*/", position + length) -> {
                    depth--
                    length += 2
                    if (depth == 0) {
                        return token(TokenType.COMMENT_BLOCK, position, length, line, LexerState.Initial, baseOffset)
                    }
                }

                else -> {
                    length++
                }
            }
        }
        return token(
            TokenType.COMMENT_BLOCK,
            position,
            length,
            line,
            LexerState(inBlockComment = true, blockCommentDepth = depth),
            baseOffset,
        )
    }

    private fun consumeString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        openingLength: Int,
        quote: Char = '\'',
    ): Triple<Token, LexerState, Int> {
        val hasEscapePrefix =
            openingLength > 1 && isEscapeStringPrefix(text, position)
        val usesBackslashEscapes =
            hasEscapePrefix || dialect == SqlDialect.STARROCKS
        var length = openingLength
        while (position + length < text.length) {
            val character = text[position + length]
            when {
                usesBackslashEscapes && character == '\\' -> {
                    length += if (position + length + 1 < text.length) 2 else 1
                }

                character == quote && text.getOrNull(position + length + 1) == quote -> {
                    length += 2
                }

                character == quote -> {
                    length++
                    return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
                }

                else -> {
                    length++
                }
            }
        }
        val delimiter =
            when {
                quote == '"' -> DOUBLE_QUOTED_STRING
                usesBackslashEscapes -> ESCAPED_STRING
                else -> SINGLE_QUOTED_STRING
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

    private fun consumeDollarString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        delimiter: String,
    ): Triple<Token, LexerState, Int> {
        val closing = text.indexOf(delimiter, position + delimiter.length)
        return if (closing >= 0) {
            token(
                TokenType.STRING,
                position,
                closing + delimiter.length - position,
                line,
                LexerState.Initial,
                baseOffset,
            )
        } else {
            token(
                TokenType.STRING,
                position,
                text.length - position,
                line,
                LexerState(inMultilineString = true, stringDelimiter = delimiter),
                baseOffset,
            )
        }
    }

    private fun consumeMultilineString(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val delimiter = state.stringDelimiter
        if (delimiter.startsWith('$')) {
            val closing = text.indexOf(delimiter, position)
            return if (closing >= 0) {
                token(
                    TokenType.STRING,
                    position,
                    closing + delimiter.length - position,
                    line,
                    LexerState.Initial,
                    baseOffset,
                )
            } else {
                token(TokenType.STRING, position, text.length - position, line, state, baseOffset)
            }
        }

        val quote = if (delimiter == DOUBLE_QUOTED_STRING) '"' else '\''
        val syntheticPrefix = if (delimiter == ESCAPED_STRING) "E" else ""
        val syntheticText = syntheticPrefix + quote + text.substring(position)
        val prefixLength = syntheticPrefix.length + 1
        val (_, nextState, consumed) = consumeString(syntheticText, 0, line, 0, prefixLength, quote)
        val continuationLength = (consumed - prefixLength).coerceAtLeast(0)
        return Triple(
            Token(TokenType.STRING, baseOffset + position, continuationLength, line),
            nextState,
            continuationLength,
        )
    }

    private fun consumeQuotedIdentifier(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        openingLength: Int,
        quote: Char,
    ): Triple<Token, LexerState, Int> {
        var length = openingLength
        while (position + length < text.length) {
            val character = text[position + length]
            if (character == quote && text.getOrNull(position + length + 1) == quote) {
                length += 2
            } else if (character == quote) {
                length++
                break
            } else if (openingLength > 1 && character == '\\') {
                length += if (position + length + 1 < text.length) 2 else 1
            } else {
                length++
            }
        }
        return token(TokenType.IDENTIFIER, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeNumber(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        if (text.startsWith("0x", position, ignoreCase = true)) {
            length = 2
            while (text
                    .getOrNull(
                        position + length,
                    )?.let { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == '_' } ==
                true
            ) {
                length++
            }
        } else {
            while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
            if (text.getOrNull(position + length) == '.') {
                length++
                while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
            }
            if (text.getOrNull(position + length)?.lowercaseChar() == 'e') {
                val exponentStart = length
                length++
                if (text.getOrNull(position + length) in setOf('+', '-')) length++
                val digitsStart = length
                while (text.getOrNull(position + length)?.let { it.isDigit() || it == '_' } == true) length++
                if (length == digitsStart) length = exponentStart
            }
        }
        return token(TokenType.NUMBER, position, length.coerceAtLeast(1), line, LexerState.Initial, baseOffset)
    }

    private fun consumeParameter(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (text.getOrNull(position + length)?.let { it.isLetterOrDigit() || it == '_' || it == '$' } ==
            true
        ) {
            length++
        }
        return token(TokenType.PARAMETER, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeIdentifier(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (text.getOrNull(position + length).isIdentifierPart()) length++
        val value = text.substring(position, position + length).uppercase()
        val type =
            when {
                value in CONSTANTS -> TokenType.CONSTANT
                value in TYPES || value in dialectTypes() -> TokenType.TYPE
                value in KEYWORDS || value in dialectKeywords() -> TokenType.KEYWORD
                value in FUNCTIONS || nextNonWhitespace(text, position + length) == '(' -> TokenType.FUNCTION
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
        return if (operator != null) {
            token(TokenType.OPERATOR, position, operator.length, line, state, baseOffset)
        } else {
            token(TokenType.UNKNOWN, position, 1, line, state, baseOffset)
        }
    }

    private fun dollarDelimiterAt(
        text: String,
        position: Int,
    ): String? {
        if (text.getOrNull(position) != '$') return null
        var end = position + 1
        if (text.getOrNull(end) == '$') return "$$"
        if (!text.getOrNull(end).isIdentifierStart()) return null
        end++
        while (text.getOrNull(end)?.let { it.isLetterOrDigit() || it == '_' } == true) end++
        return if (text.getOrNull(end) == '$') text.substring(position, end + 1) else null
    }

    private fun stringPrefixLength(
        text: String,
        position: Int,
    ): Int {
        if (dialect == SqlDialect.POSTGRESQL && text.startsWith("U&'", position, ignoreCase = true)) return 3
        if (dialect == SqlDialect.POSTGRESQL && text.startsWith("E'", position, ignoreCase = true)) return 2
        if (text.startsWith("N'", position, ignoreCase = true)) return 2
        if (dialect == SqlDialect.STARROCKS &&
            (text.startsWith("X'", position, ignoreCase = true) || text.startsWith("B'", position, ignoreCase = true))
        ) {
            return 2
        }
        return 0
    }

    private fun isEscapeStringPrefix(
        text: String,
        position: Int,
    ): Boolean =
        text[position].equals('e', ignoreCase = true) ||
            text.startsWith("U&", position, ignoreCase = true)

    private fun dialectKeywords(): Set<String> =
        when (dialect) {
            SqlDialect.STANDARD -> emptySet()
            SqlDialect.POSTGRESQL -> POSTGRESQL_KEYWORDS
            SqlDialect.STARROCKS -> STARROCKS_KEYWORDS
        }

    private fun dialectTypes(): Set<String> =
        when (dialect) {
            SqlDialect.STANDARD -> emptySet()
            SqlDialect.POSTGRESQL -> POSTGRESQL_TYPES
            SqlDialect.STARROCKS -> STARROCKS_TYPES
        }

    private fun nextNonWhitespace(
        text: String,
        position: Int,
    ): Char? {
        var cursor = position
        while (text.getOrNull(cursor)?.isWhitespace() == true) cursor++
        return text.getOrNull(cursor)
    }

    private fun token(
        type: TokenType,
        position: Int,
        length: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> = Triple(Token(type, baseOffset + position, length, line), state, length)

    private fun Char?.isIdentifierStart(): Boolean {
        val character = this ?: return false
        return character.isLetter() || character == '_'
    }

    private fun Char?.isIdentifierPart(): Boolean {
        val character = this ?: return false
        return character.isLetterOrDigit() || character == '_' || character == '$'
    }

    private companion object {
        const val SINGLE_QUOTED_STRING = "sql-single-quoted"
        const val ESCAPED_STRING = "sql-escaped"
        const val DOUBLE_QUOTED_STRING = "sql-double-quoted"

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',', ';')
        val OPERATORS =
            listOf(
                "!~~*",
                "!~~",
                "!~*",
                "#>>",
                "->>",
                "<=>",
                "::",
                ":=",
                "=>",
                "<=",
                ">=",
                "<>",
                "!=",
                "||",
                "&&",
                "@>",
                "<@",
                "#>",
                "->",
                "~*",
                "!~",
                "~~*",
                "~~",
                "?|",
                "?&",
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "<",
                ">",
                "~",
                "^",
                "&",
                "|",
                "#",
                "?",
            )

        val CONSTANTS =
            setOf(
                "FALSE",
                "NULL",
                "TRUE",
                "UNKNOWN",
                "CURRENT_DATE",
                "CURRENT_TIME",
                "CURRENT_TIMESTAMP",
                "CURRENT_USER",
                "SESSION_USER",
                "SYSTEM_USER",
            )

        val TYPES =
            setOf(
                "BIGINT",
                "BINARY",
                "BIT",
                "BLOB",
                "BOOLEAN",
                "CHAR",
                "CHARACTER",
                "CLOB",
                "DATE",
                "DEC",
                "DECIMAL",
                "DOUBLE",
                "FLOAT",
                "INTEGER",
                "INTERVAL",
                "NATIONAL",
                "NCHAR",
                "NCLOB",
                "NUMERIC",
                "REAL",
                "SMALLINT",
                "TIME",
                "TIMESTAMP",
                "VARBINARY",
                "VARCHAR",
                "VARYING",
            )

        val KEYWORDS =
            setOf(
                "ADD",
                "ALL",
                "ALTER",
                "AND",
                "ANY",
                "AS",
                "ASC",
                "AUTHORIZATION",
                "BEGIN",
                "BETWEEN",
                "BOTH",
                "BY",
                "CALL",
                "CASCADE",
                "CASE",
                "CAST",
                "CHECK",
                "COLLATE",
                "COLUMN",
                "COMMIT",
                "CONSTRAINT",
                "CREATE",
                "CROSS",
                "CUBE",
                "CURRENT",
                "DATABASE",
                "DEFAULT",
                "DELETE",
                "DESC",
                "DISTINCT",
                "DROP",
                "ELSE",
                "END",
                "ESCAPE",
                "EXCEPT",
                "EXISTS",
                "FETCH",
                "FILTER",
                "FIRST",
                "FOLLOWING",
                "FOR",
                "FOREIGN",
                "FROM",
                "FULL",
                "FUNCTION",
                "GRANT",
                "GROUP",
                "GROUPING",
                "HAVING",
                "IF",
                "IN",
                "INDEX",
                "INNER",
                "INSERT",
                "INTERSECT",
                "INTO",
                "IS",
                "JOIN",
                "KEY",
                "LAST",
                "LATERAL",
                "LEADING",
                "LEFT",
                "LIKE",
                "LIMIT",
                "MATCH",
                "MATERIALIZED",
                "MERGE",
                "NATURAL",
                "NEXT",
                "NOT",
                "NULLS",
                "OFFSET",
                "ON",
                "ONLY",
                "OR",
                "ORDER",
                "OUTER",
                "OVER",
                "PARTITION",
                "PRECEDING",
                "PRIMARY",
                "PROCEDURE",
                "RANGE",
                "RECURSIVE",
                "REFERENCES",
                "REVOKE",
                "RIGHT",
                "ROLLBACK",
                "ROLLUP",
                "ROW",
                "ROWS",
                "SCHEMA",
                "SELECT",
                "SET",
                "SOME",
                "TABLE",
                "THEN",
                "TO",
                "TRAILING",
                "TRIGGER",
                "TRUNCATE",
                "UNBOUNDED",
                "UNION",
                "UNIQUE",
                "UPDATE",
                "USING",
                "VALUES",
                "VIEW",
                "WHEN",
                "WHERE",
                "WINDOW",
                "WITH",
            )

        val FUNCTIONS =
            setOf(
                "AVG",
                "COALESCE",
                "COUNT",
                "DENSE_RANK",
                "EXTRACT",
                "FIRST_VALUE",
                "LAG",
                "LAST_VALUE",
                "LEAD",
                "LOWER",
                "MAX",
                "MIN",
                "NULLIF",
                "RANK",
                "ROW_NUMBER",
                "SUBSTRING",
                "SUM",
                "TRIM",
                "UPPER",
            )

        val POSTGRESQL_KEYWORDS =
            setOf(
                "ANALYSE",
                "ANALYZE",
                "CONCURRENTLY",
                "CONFLICT",
                "COPY",
                "DO",
                "ILIKE",
                "INHERITS",
                "LANGUAGE",
                "LISTEN",
                "NOTIFY",
                "NOTHING",
                "OWNER",
                "PERFORM",
                "PLACING",
                "RETURNING",
                "SETOF",
                "STABLE",
                "UNLISTEN",
                "VACUUM",
                "VARIADIC",
                "VOLATILE",
            )

        val POSTGRESQL_TYPES =
            setOf(
                "BIGSERIAL",
                "BOX",
                "BYTEA",
                "CIDR",
                "CIRCLE",
                "INET",
                "JSON",
                "JSONB",
                "LINE",
                "LSEG",
                "MACADDR",
                "MACADDR8",
                "MONEY",
                "PATH",
                "PG_LSN",
                "POINT",
                "POLYGON",
                "SERIAL",
                "SMALLSERIAL",
                "TEXT",
                "TSQUERY",
                "TSVECTOR",
                "UUID",
                "XML",
            )

        val STARROCKS_KEYWORDS =
            setOf(
                "AGGREGATE",
                "ASYNC",
                "BROKER",
                "BUCKETS",
                "CATALOG",
                "COMPACTION",
                "DISTRIBUTED",
                "DUPLICATE",
                "ENGINE",
                "EVERY",
                "EXTERNAL",
                "GLOBAL",
                "HASH",
                "IMMEDIATE",
                "LOAD",
                "MANUAL",
                "OLAP",
                "PROPERTIES",
                "REFRESH",
                "REPLACE_IF_NOT_NULL",
                "ROUTINE",
                "SCHEDULE",
                "START",
                "TEMPORARY",
            )

        val STARROCKS_TYPES =
            setOf(
                "ARRAY",
                "BITMAP",
                "DATETIME",
                "DECIMAL32",
                "DECIMAL64",
                "DECIMAL128",
                "DECIMALV2",
                "HLL",
                "JSON",
                "LARGEINT",
                "MAP",
                "PERCENTILE",
                "STRING",
                "STRUCT",
                "TINYINT",
            )
    }
}
