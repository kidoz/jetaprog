package su.kidoz.jetaprog.editor.syntax.highlighting

import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Represents an ARGB color value.
 */
@JvmInline
public value class SyntaxColor(
    public val argb: Long,
) {
    public constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) : this(
        ((alpha.toLong() and 0xFF) shl 24) or
            ((red.toLong() and 0xFF) shl 16) or
            ((green.toLong() and 0xFF) shl 8) or
            (blue.toLong() and 0xFF),
    )

    public val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    public val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    public val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    public val blue: Int get() = (argb and 0xFF).toInt()

    public companion object {
        /**
         * Creates a color from a hex string (e.g., "#FF5500" or "FF5500").
         */
        public fun fromHex(hex: String): SyntaxColor {
            val cleanHex = hex.removePrefix("#")
            val value = cleanHex.toLong(16)
            return if (cleanHex.length == 6) {
                SyntaxColor(0xFF000000L or value)
            } else {
                SyntaxColor(value)
            }
        }
    }
}

/**
 * Style for a token including color and font styles.
 */
public data class TokenStyle(
    val foreground: SyntaxColor,
    val italic: Boolean = false,
    val bold: Boolean = false,
    val underline: Boolean = false,
)

/**
 * A complete syntax highlighting theme.
 */
public interface SyntaxTheme {
    /** Theme name for display. */
    public val name: String

    /** Whether this is a dark theme. */
    public val isDark: Boolean

    /** Default foreground color for text. */
    public val defaultForeground: SyntaxColor

    /** Background color for the editor. */
    public val background: SyntaxColor

    /** Line number color. */
    public val lineNumber: SyntaxColor

    /** Current line highlight color. */
    public val currentLineHighlight: SyntaxColor

    /** Selection background color. */
    public val selection: SyntaxColor

    /** Cursor color. */
    public val cursor: SyntaxColor

    /**
     * Gets the style for a specific token type.
     */
    public fun styleFor(tokenType: TokenType): TokenStyle
}

/**
 * Dark theme inspired by VS Code Dark+.
 */
public object DarkSyntaxTheme : SyntaxTheme {
    override val name: String = "Dark+"
    override val isDark: Boolean = true
    override val defaultForeground: SyntaxColor = SyntaxColor.fromHex("D7DBE0")
    override val background: SyntaxColor = SyntaxColor.fromHex("1E1E1E")
    override val lineNumber: SyntaxColor = SyntaxColor.fromHex("5A5D63")
    override val currentLineHighlight: SyntaxColor = SyntaxColor.fromHex("20242B")
    override val selection: SyntaxColor = SyntaxColor.fromHex("2D4A6B")
    override val cursor: SyntaxColor = SyntaxColor.fromHex("AEAFAD")

    private val keyword = TokenStyle(SyntaxColor.fromHex("569CD6"))
    private val modifier = TokenStyle(SyntaxColor.fromHex("569CD6"))
    private val string = TokenStyle(SyntaxColor.fromHex("CE9178"))
    private val number = TokenStyle(SyntaxColor.fromHex("B5CEA8"))
    private val comment = TokenStyle(SyntaxColor.fromHex("6A9955"), italic = true)
    private val annotation = TokenStyle(SyntaxColor.fromHex("DCDCAA"))
    private val type = TokenStyle(SyntaxColor.fromHex("4EC9B0"))
    private val function = TokenStyle(SyntaxColor.fromHex("DCDCAA"))
    private val property = TokenStyle(SyntaxColor.fromHex("9CDCFE"))
    private val operator = TokenStyle(SyntaxColor.fromHex("D7DBE0"))
    private val default = TokenStyle(SyntaxColor.fromHex("D7DBE0"))

    override fun styleFor(tokenType: TokenType): TokenStyle =
        when (tokenType) {
            TokenType.KEYWORD -> keyword
            TokenType.MODIFIER -> modifier
            TokenType.STRING, TokenType.STRING_ESCAPE, TokenType.STRING_TEMPLATE -> string
            TokenType.NUMBER -> number
            TokenType.CHARACTER -> string
            TokenType.COMMENT_LINE, TokenType.COMMENT_BLOCK, TokenType.COMMENT_DOC -> comment
            TokenType.ANNOTATION -> annotation
            TokenType.TYPE, TokenType.TAG -> type
            TokenType.FUNCTION -> function
            TokenType.PROPERTY, TokenType.PARAMETER -> property
            TokenType.IDENTIFIER, TokenType.TEXT -> default
            TokenType.OPERATOR -> operator
            TokenType.PUNCTUATION, TokenType.BRACKET -> operator
            TokenType.CONSTANT -> number
            TokenType.WHITESPACE, TokenType.NEWLINE, TokenType.UNKNOWN -> default
        }
}

/**
 * Light theme inspired by VS Code Light+.
 */
public object LightSyntaxTheme : SyntaxTheme {
    override val name: String = "Light+"
    override val isDark: Boolean = false
    override val defaultForeground: SyntaxColor = SyntaxColor.fromHex("000000")
    override val background: SyntaxColor = SyntaxColor.fromHex("FFFFFF")
    override val lineNumber: SyntaxColor = SyntaxColor.fromHex("237893")
    override val currentLineHighlight: SyntaxColor = SyntaxColor.fromHex("F0F0F0")
    override val selection: SyntaxColor = SyntaxColor.fromHex("ADD6FF")
    override val cursor: SyntaxColor = SyntaxColor.fromHex("000000")

    private val keyword = TokenStyle(SyntaxColor.fromHex("0000FF"))
    private val modifier = TokenStyle(SyntaxColor.fromHex("0000FF"))
    private val string = TokenStyle(SyntaxColor.fromHex("A31515"))
    private val number = TokenStyle(SyntaxColor.fromHex("098658"))
    private val comment = TokenStyle(SyntaxColor.fromHex("008000"), italic = true)
    private val annotation = TokenStyle(SyntaxColor.fromHex("795E26"))
    private val type = TokenStyle(SyntaxColor.fromHex("267F99"))
    private val function = TokenStyle(SyntaxColor.fromHex("795E26"))
    private val property = TokenStyle(SyntaxColor.fromHex("001080"))
    private val operator = TokenStyle(SyntaxColor.fromHex("000000"))
    private val default = TokenStyle(SyntaxColor.fromHex("000000"))

    override fun styleFor(tokenType: TokenType): TokenStyle =
        when (tokenType) {
            TokenType.KEYWORD -> keyword
            TokenType.MODIFIER -> modifier
            TokenType.STRING, TokenType.STRING_ESCAPE, TokenType.STRING_TEMPLATE -> string
            TokenType.NUMBER -> number
            TokenType.CHARACTER -> string
            TokenType.COMMENT_LINE, TokenType.COMMENT_BLOCK, TokenType.COMMENT_DOC -> comment
            TokenType.ANNOTATION -> annotation
            TokenType.TYPE, TokenType.TAG -> type
            TokenType.FUNCTION -> function
            TokenType.PROPERTY, TokenType.PARAMETER -> property
            TokenType.IDENTIFIER, TokenType.TEXT -> default
            TokenType.OPERATOR -> operator
            TokenType.PUNCTUATION, TokenType.BRACKET -> operator
            TokenType.CONSTANT -> number
            TokenType.WHITESPACE, TokenType.NEWLINE, TokenType.UNKNOWN -> default
        }
}
