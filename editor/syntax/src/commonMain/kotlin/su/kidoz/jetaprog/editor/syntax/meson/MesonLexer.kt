package su.kidoz.jetaprog.editor.syntax.meson

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for Meson build system files.
 */
public class MesonLexer : Lexer {
    override val languageId: String = "meson"

    private companion object {
        val KEYWORDS =
            setOf(
                "if",
                "elif",
                "else",
                "endif",
                "foreach",
                "endforeach",
                "break",
                "continue",
                "and",
                "or",
                "not",
                "in",
            )

        val BUILTINS =
            setOf(
                "add_global_arguments",
                "add_global_link_arguments",
                "add_languages",
                "add_project_arguments",
                "add_project_link_arguments",
                "add_test_setup",
                "assert",
                "benchmark",
                "both_libraries",
                "build_target",
                "configuration_data",
                "configure_file",
                "custom_target",
                "declare_dependency",
                "dependency",
                "disabler",
                "environment",
                "error",
                "executable",
                "files",
                "find_library",
                "find_program",
                "generator",
                "get_option",
                "get_variable",
                "import",
                "include_directories",
                "install_data",
                "install_headers",
                "install_man",
                "install_subdir",
                "is_disabler",
                "is_variable",
                "jar",
                "join_paths",
                "library",
                "message",
                "project",
                "range",
                "run_command",
                "run_target",
                "set_variable",
                "shared_library",
                "shared_module",
                "static_library",
                "subdir",
                "subdir_done",
                "subproject",
                "summary",
                "test",
                "unset_variable",
                "vcs_tag",
                "warning",
            )

        val CONSTANTS =
            setOf(
                "true",
                "false",
                "meson",
                "host_machine",
                "build_machine",
                "target_machine",
            )

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',', ':')
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
            char == '\n' -> Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            char.isWhitespace() -> consumeWhitespace(text, pos, line, baseOffset)
            char == '#' -> consumeComment(text, pos, line, baseOffset)
            char == '\'' -> consumeString(text, pos, line, baseOffset, '\'')
            char == '"' -> consumeString(text, pos, line, baseOffset, '"')
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

    private fun consumeComment(
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

    private fun consumeString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
        quote: Char,
    ): Triple<Token, LexerState, Int> {
        // Check for multiline string '''...'''
        if (quote == '\'' && text.startsWith("'''", pos)) {
            return consumeMultilineString(text, pos, line, baseOffset)
        }

        var length = 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
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

    private fun consumeMultilineString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 3
        while (pos + length < text.length) {
            if (text.startsWith("'''", pos + length)) {
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

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        // Check for hex or octal
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

        // Decimal number
        while (pos + length < text.length && text[pos + length].isDigit()) {
            length++
        }

        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
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
                word in KEYWORDS -> TokenType.KEYWORD
                word in CONSTANTS -> TokenType.CONSTANT
                word in BUILTINS -> TokenType.FUNCTION
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
        val operators =
            listOf(
                "==",
                "!=",
                "<=",
                ">=",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "+",
                "-",
                "*",
                "/",
                "%",
                "<",
                ">",
                "=",
                "?",
            )

        for (op in operators) {
            if (text.startsWith(op, pos)) {
                return Triple(Token(TokenType.OPERATOR, baseOffset + pos, op.length, line), state, op.length)
            }
        }
        return Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), state, 1)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
