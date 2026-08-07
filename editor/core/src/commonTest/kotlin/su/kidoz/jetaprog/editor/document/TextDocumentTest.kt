package su.kidoz.jetaprog.editor.document

import su.kidoz.jetaprog.common.text.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for document position conversion and line endings. */
public class TextDocumentTest {
    @Test
    public fun crlfPositionsRoundTripWithoutCountingCarriageReturn() {
        val document = document("first\r\nsecond\r\nthird", LineEnding.CRLF)

        assertEquals(7, document.positionToOffset(TextPosition(1, 0)))
        assertEquals(10, document.positionToOffset(TextPosition(1, 3)))
        assertEquals(TextPosition(1, 3), document.offsetToPosition(10))
        assertEquals(TextPosition(2, 0), document.offsetToPosition(15))
    }

    @Test
    public fun positionColumnIsClampedToTargetLine() {
        val document = document("a\r\nbb", LineEnding.CRLF)

        assertEquals(1, document.positionToOffset(TextPosition(0, 50)))
        assertEquals(5, document.positionToOffset(TextPosition(1, 50)))
    }

    private fun document(
        content: String,
        lineEnding: LineEnding,
    ): TextDocument =
        TextDocument(
            uri = DocumentUri.file("/tmp/test.txt"),
            languageId = LanguageId.PLAIN_TEXT,
            version = 1,
            content = content,
            fileName = "test.txt",
            lineEnding = lineEnding,
        )
}
