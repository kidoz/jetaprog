package su.kidoz.jetaprog.lsp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalTextChangeTest {
    private fun apply(
        old: String,
        change: TextDocumentContentChangeEvent,
    ): String {
        val range = change.range!!

        fun offset(position: LspPosition): Int {
            var line = 0
            var index = 0
            while (line < position.line) {
                if (old[index] == '\n') line++
                index++
            }
            return index + position.character
        }
        return old.substring(0, offset(range.start)) + change.text + old.substring(offset(range.end))
    }

    @Test
    fun aTypedCharacterBecomesAOneCharacterInsertion() {
        val old = "fun main() {\n    val x = 1\n}\n"
        val new = "fun main() {\n    val xy = 1\n}\n"

        val change = IncrementalTextChange.between(old, new)

        assertEquals(LspRange(LspPosition(1, 9), LspPosition(1, 9)), change.range)
        assertEquals(0, change.rangeLength)
        assertEquals("y", change.text)
        assertEquals(new, apply(old, change))
    }

    @Test
    fun deletionsReplacementsAndRepeatedTextRoundTrip() {
        val cases =
            listOf(
                "abc\ndef\n" to "abc\nf\n",
                "aaaa" to "aaa",
                "aaa" to "aaaa",
                "hello world" to "hello brave world",
                "" to "new\ntext",
                "gone" to "",
                "same" to "same",
            )

        for ((old, new) in cases) {
            assertEquals(new, apply(old, IncrementalTextChange.between(old, new)), "$old -> $new")
        }
    }
}
