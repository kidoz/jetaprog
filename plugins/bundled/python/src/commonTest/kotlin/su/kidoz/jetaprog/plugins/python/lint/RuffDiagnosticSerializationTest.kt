package su.kidoz.jetaprog.plugins.python.lint

import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.common.text.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class RuffDiagnosticSerializationTest {
    @Test
    fun ruffOutputKeepsTheNoqaRowField() {
        val output =
            """
            {
                "code": "F401",
                "message": "Unused import",
                "filename": "main.py",
                "location": {"row": 2, "column": 1},
                "end_location": {"row": 2, "column": 10},
                "noqa_row": 2
            }
            """.trimIndent()

        val diagnostic = Json.decodeFromString<RuffDiagnostic>(output)

        assertEquals(2, diagnostic.noqaRow)
        assertEquals(
            Json.parseToJsonElement(output),
            Json.encodeToJsonElement(RuffDiagnostic.serializer(), diagnostic),
        )

        val result = RuffOutputParser.parse("[$output]", "file:///main.py").single()
        assertEquals("Unused import", result.message)
        assertEquals(TextPosition(1, 0), result.range.start)
    }
}
