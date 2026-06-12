package su.kidoz.jetaprog.editor.syntax.xml

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for XML files (including Maven pom.xml).
 */
public class XmlLexer : Lexer {
    override val languageId: String = "xml"

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

        // Handle block comment continuation
        if (state.inBlockComment) {
            return consumeCommentContinuation(text, pos, line, state, baseOffset)
        }

        val char = text[pos]

        return when {
            char == '\n' -> Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            char.isWhitespace() -> consumeWhitespace(text, pos, line, baseOffset)
            text.startsWith("<!--", pos) -> consumeComment(text, pos, line, state, baseOffset)
            text.startsWith("<![CDATA[", pos) -> consumeCdata(text, pos, line, baseOffset)
            text.startsWith("<?", pos) -> consumeProcessingInstruction(text, pos, line, baseOffset)
            text.startsWith("<!", pos) -> consumeDeclaration(text, pos, line, baseOffset)
            text.startsWith("</", pos) -> consumeCloseTag(text, pos, line, baseOffset)
            char == '<' -> consumeOpenTag(text, pos, line, baseOffset)
            char == '&' -> consumeEntity(text, pos, line, baseOffset)
            else -> consumeText(text, pos, line, baseOffset)
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

    private fun consumeComment(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 4 // <!--

        while (pos + length < text.length) {
            if (text.startsWith("-->", pos + length)) {
                length += 3
                return Triple(
                    Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
                    LexerState.Initial,
                    length,
                )
            }
            length++
        }

        // Comment continues on next line
        return Triple(
            Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
            state.copy(inBlockComment = true),
            length,
        )
    }

    private fun consumeCommentContinuation(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        while (pos + length < text.length) {
            if (text.startsWith("-->", pos + length)) {
                length += 3
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

    private fun consumeCdata(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 9 // <![CDATA[

        while (pos + length < text.length) {
            if (text.startsWith("]]>", pos + length)) {
                length += 3
                break
            }
            length++
        }

        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeProcessingInstruction(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2 // <?

        while (pos + length < text.length) {
            if (text.startsWith("?>", pos + length)) {
                length += 2
                break
            }
            length++
        }

        return Triple(Token(TokenType.KEYWORD, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeDeclaration(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2 // <!

        while (pos + length < text.length) {
            if (text[pos + length] == '>') {
                length++
                break
            }
            length++
        }

        return Triple(Token(TokenType.KEYWORD, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeOpenTag(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val tokens = mutableListOf<Token>()
        var length = 1 // <
        var totalLength = 1

        // Get tag name
        while (pos + length < text.length &&
            (text[pos + length].isLetterOrDigit() || text[pos + length] in listOf(':', '-', '_', '.'))
        ) {
            length++
        }

        // Return tag as a single token for now
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '>' -> {
                    length++
                    break
                }

                text.startsWith("/>", pos + length) -> {
                    length += 2
                    break
                }

                else -> {
                    length++
                }
            }
        }

        return Triple(Token(TokenType.TAG, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeCloseTag(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2 // </

        while (pos + length < text.length && text[pos + length] != '>') {
            length++
        }

        if (pos + length < text.length && text[pos + length] == '>') {
            length++
        }

        return Triple(Token(TokenType.TAG, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeEntity(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1 // &

        while (pos + length < text.length && text[pos + length] != ';' && !text[pos + length].isWhitespace()) {
            length++
            if (length > 10) break // Entities shouldn't be too long
        }

        if (pos + length < text.length && text[pos + length] == ';') {
            length++
            return Triple(Token(TokenType.CONSTANT, baseOffset + pos, length, line), LexerState.Initial, length)
        }

        // Not a valid entity, treat as text
        return Triple(Token(TokenType.TEXT, baseOffset + pos, 1, line), LexerState.Initial, 1)
    }

    private fun consumeText(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        while (pos + length < text.length) {
            val char = text[pos + length]
            if (char == '<' || char == '&' || char == '\n') {
                break
            }
            length++
        }

        if (length == 0) {
            length = 1
        }

        return Triple(Token(TokenType.TEXT, baseOffset + pos, length, line), LexerState.Initial, length)
    }
}
