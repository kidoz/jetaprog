package su.kidoz.jetaprog.plugins.support

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lsp.protocol.LspDiagnostic
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspRange
import kotlin.test.Test
import kotlin.test.assertEquals

class LspTypeConvertersTest {
    @Test
    fun lspPositionKeepsItsZeroBasedLineAndCharacter() {
        assertEquals(TextPosition(18, 9), LspPosition(line = 18, character = 9).toTextPosition())
    }

    @Test
    fun textPositionKeepsItsZeroBasedLineAndColumn() {
        assertEquals(LspPosition(line = 18, character = 9), TextPosition(18, 9).toLspPosition())
    }

    @Test
    fun documentStartConvertsInBothDirections() {
        assertEquals(TextPosition(0, 0), LspPosition(line = 0, character = 0).toTextPosition())
        assertEquals(LspPosition(line = 0, character = 0), TextPosition(0, 0).toLspPosition())
    }

    @Test
    fun rangeRoundTripsUnchanged() {
        val range = LspRange(LspPosition(line = 39, character = 35), LspPosition(line = 39, character = 46))

        assertEquals(TextRange(TextPosition(39, 35), TextPosition(39, 46)), range.toTextRange())
        assertEquals(range, range.toTextRange().toLspRange())
    }

    @Test
    fun diagnosticRangeLandsOnTheReportedText() {
        val diagnostic =
            LspDiagnostic(
                range = LspRange(LspPosition(line = 18, character = 9), LspPosition(line = 18, character = 13)),
                message = "Use of undeclared identifier 'core'",
            )

        val converted = diagnostic.toLanguageDiagnostic()

        assertEquals(TextPosition(18, 9), converted.range.start)
        assertEquals(TextPosition(18, 13), converted.range.end)
    }
}
