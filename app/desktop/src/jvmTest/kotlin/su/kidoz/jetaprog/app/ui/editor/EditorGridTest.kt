package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editor draws overlays (bracket match, caret line, inlay hints) on a character grid
 * laid over the text, and maps pointer positions back through the same grid.
 *
 * Both used to be computed from a whole-pixel character advance, so the error accumulated
 * with the column and the overlay slid off its glyph further along the line.
 */
class EditorGridTest {
    /** A realistic fractional advance: 14sp JetBrains Mono is not a whole number of pixels. */
    private val charWidth = 8.4f
    private val lineHeight = 21

    @Test
    fun overlayStaysOnItsGlyphAcrossALongLine() {
        // Column of the brace in "class Widget final {".
        val column = 19
        val trueX = column * charWidth

        val fixed = (column * charWidth).roundToInt()
        val roundedAdvance = column * charWidth.roundToInt()

        // Rounding the advance first drifts by 0.4px per column: 7.6px by the time it
        // reaches the brace, most of a character, which is what put the highlight beside
        // the glyph instead of on it.
        assertTrue(
            abs(roundedAdvance - trueX) > charWidth / 2,
            "expected the old maths to drift more than half a character",
        )
        assertTrue(abs(fixed - trueX) <= 0.5f, "rounding once should stay within half a pixel")
    }

    @Test
    fun pointerMapsBackToTheColumnUnderIt() {
        val lines = listOf("class Widget final {", "public:")

        // Middle of the glyph in the brace column on line 0.
        val pointer = Offset(x = 19 * charWidth + charWidth / 2, y = lineHeight / 2f)
        val position =
            pointerTextPosition(
                pointer = pointer,
                lines = lines,
                scrollY = 0,
                scrollX = 0,
                lineHeightPx = lineHeight,
                charWidthPx = charWidth,
            )

        assertEquals(0, position?.line)
        assertEquals(19, position?.column)
    }

    @Test
    fun pointerMapsToTheCorrectLineFarDownTheFile() {
        val lines = List(40) { "int value$it = $it;" }
        val line = 30

        val pointer = Offset(x = charWidth / 2, y = line * lineHeight + lineHeight / 2f)
        val position =
            pointerTextPosition(
                pointer = pointer,
                lines = lines,
                scrollY = 0,
                scrollX = 0,
                lineHeightPx = lineHeight,
                charWidthPx = charWidth,
            )

        assertEquals(line, position?.line)
        assertEquals(0, position?.column)
    }

    @Test
    fun pointerOffTheEndOfALineHitsNothing() {
        val lines = listOf("ab")

        val position =
            pointerTextPosition(
                pointer = Offset(x = 40 * charWidth, y = lineHeight / 2f),
                lines = lines,
                scrollY = 0,
                scrollX = 0,
                lineHeightPx = lineHeight,
                charWidthPx = charWidth,
            )

        assertEquals(null, position)
    }

    @Test
    fun offsetToPositionHandlesMultipleLines() {
        val text = "class Widget final {\npublic:\n};\nint main() {"

        assertEquals(0, offsetToPosition(text, 0).line)
        assertEquals(19, offsetToPosition(text, 19).column)
        // Start of the closing brace on the third line.
        assertEquals(2, offsetToPosition(text, text.indexOf("};")).line)
        assertEquals(0, offsetToPosition(text, text.indexOf("};")).column)
    }
}
