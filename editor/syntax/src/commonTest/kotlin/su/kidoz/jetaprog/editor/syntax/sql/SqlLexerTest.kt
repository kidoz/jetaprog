package su.kidoz.jetaprog.editor.syntax.sql

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlLexerTest {
    @Test
    fun `recognises portable SQL tokens case insensitively`() {
        val lexer = SqlLexer()
        val source = "select count(*) from users where active = TRUE and score >= 1.5e+2;"

        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "select"))
        assertEquals(TokenType.FUNCTION, typeOf(lexer, source, "count"))
        assertEquals(TokenType.CONSTANT, typeOf(lexer, source, "TRUE"))
        assertEquals(TokenType.OPERATOR, typeOf(lexer, source, ">="))
        assertEquals(TokenType.NUMBER, typeOf(lexer, source, "1.5e+2"))
    }

    @Test
    fun `carries nested comments across lines`() {
        val lexer = SqlLexer(SqlDialect.POSTGRESQL)
        val (_, firstState) = lexer.tokenizeLine("/* outer /* nested", 0, 0, LexerState.Initial)

        assertTrue(firstState.inBlockComment)
        assertEquals(2, firstState.blockCommentDepth)

        val (tokens, finalState) = lexer.tokenizeLine("*/ end */ SELECT", 1, 19, firstState)
        assertEquals(TokenType.COMMENT_BLOCK, tokens.first().type)
        assertEquals(TokenType.KEYWORD, tokens.last().type)
        assertTrue(!finalState.inBlockComment)
    }

    @Test
    fun `supports PostgreSQL dollar strings parameters and operators`() {
        val lexer = SqlLexer(SqlDialect.POSTGRESQL)
        val (_, stringState) = lexer.tokenizeLine("DO ${'$'}body${'$'}", 0, 0, LexerState.Initial)
        assertTrue(stringState.inMultilineString)
        assertEquals("${'$'}body${'$'}", stringState.stringDelimiter)

        val (tokens, finalState) =
            lexer.tokenizeLine("RETURN ${'$'}1::jsonb ->> 'name'; ${'$'}body${'$'};", 1, 10, stringState)
        assertEquals(TokenType.STRING, tokens.first().type)
        assertTrue(!finalState.inMultilineString)

        val source = "SELECT $1::jsonb ->> 'name' FROM U&\"data\" RETURNING id"
        assertEquals(TokenType.PARAMETER, typeOf(lexer, source, "$1"))
        assertEquals(TokenType.OPERATOR, typeOf(lexer, source, "::"))
        assertEquals(TokenType.OPERATOR, typeOf(lexer, source, "->>"))
        assertEquals(TokenType.IDENTIFIER, typeOf(lexer, source, "U&\"data\""))
        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "RETURNING"))
    }

    @Test
    fun `supports StarRocks identifiers strings types and DDL keywords`() {
        val lexer = SqlLexer(SqlDialect.STARROCKS)
        val source =
            """
            CREATE TABLE `events` (payload JSON, amount DECIMAL128) ENGINE=OLAP
            DUPLICATE KEY(id) DISTRIBUTED BY HASH(id) BUCKETS 8 PROPERTIES ("replication_num"="1");
            """.trimIndent()

        assertEquals(TokenType.IDENTIFIER, typeOf(lexer, source, "`events`"))
        assertEquals(TokenType.TYPE, typeOf(lexer, source, "JSON"))
        assertEquals(TokenType.TYPE, typeOf(lexer, source, "DECIMAL128"))
        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "DISTRIBUTED"))
        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "BUCKETS"))
        assertEquals(TokenType.STRING, typeOf(lexer, source, "\"replication_num\""))
        assertEquals(TokenType.IDENTIFIER, typeOf(SqlLexer(), "DISTRIBUTED", "DISTRIBUTED"))
    }

    private fun typeOf(
        lexer: SqlLexer,
        source: String,
        tokenText: String,
    ): TokenType {
        val token = lexer.tokenize(source).firstOrNull { source.substring(it.start, it.end) == tokenText }
        assertNotNull(token, "expected token '$tokenText' in: $source")
        return token.type
    }
}
