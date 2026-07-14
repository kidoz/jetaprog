package su.kidoz.jetaprog.app.terminal

/**
 * RGB color used by a terminal cell style.
 *
 * Components use the inclusive `0..255` range.
 */
public data class TerminalColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

/**
 * Visual attributes applied to terminal cells.
 */
public data class TerminalCellStyle(
    val foreground: TerminalColor? = null,
    val background: TerminalColor? = null,
    val isBold: Boolean = false,
    val isDim: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderlined: Boolean = false,
    val isInverse: Boolean = false,
    val isStrikethrough: Boolean = false,
)

/**
 * Consecutive terminal cells that share styling and hyperlink metadata.
 */
public data class TerminalStyledSegment(
    val text: String,
    val cellStart: Int,
    val cellWidth: Int,
    val style: TerminalCellStyle,
    val hyperlink: String? = null,
)

/**
 * Render-ready terminal row with its plain-text representation.
 */
public data class TerminalStyledLine(
    val plainText: String,
    val segments: List<TerminalStyledSegment>,
)
