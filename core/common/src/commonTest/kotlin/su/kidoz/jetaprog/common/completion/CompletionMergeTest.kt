package su.kidoz.jetaprog.common.completion

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionMergeTest {
    private val range = TextRange(TextPosition(0, 0), TextPosition(0, 3))

    @Test
    fun serverItemKeepsItsEditAndGainsNativeDocumentation() {
        val native = CompletionItem(label = "Greeter", kind = CompletionItemKind.Class, documentation = "KDoc")
        val server =
            CompletionItem(
                label = "Greeter",
                insertText = "com.example.Greeter",
                range = range,
                sortText = "0001",
                additionalTextEdits = listOf(TextEditData(range, "import com.example.Greeter\n")),
            )

        val merged = listOf(native, server).mergeDuplicateLabels().single()

        assertEquals("com.example.Greeter", merged.insertText)
        assertEquals(range, merged.range)
        assertEquals("0001", merged.sortText)
        assertEquals(1, merged.additionalTextEdits.size)
        assertEquals("KDoc", merged.documentation)
        assertEquals(CompletionItemKind.Class, merged.kind)
    }

    @Test
    fun firstNativeItemWinsAndIsEnrichedByLaterOnes() {
        val fromIndex = CompletionItem(label = "greet", kind = CompletionItemKind.Function, detail = "fun greet()")
        val fromPsi = CompletionItem(label = "greet", kind = CompletionItemKind.Function, documentation = "Says hi")

        val merged = listOf(fromIndex, fromPsi).mergeDuplicateLabels().single()

        assertEquals("fun greet()", merged.detail)
        assertEquals("Says hi", merged.documentation)
    }

    @Test
    fun orderFollowsFirstOccurrenceAndDistinctLabelsAreUntouched() {
        val items =
            listOf(
                CompletionItem(label = "b"),
                CompletionItem(label = "a"),
                CompletionItem(label = "b", sortText = "1"),
            )

        assertEquals(listOf("b", "a"), items.mergeDuplicateLabels().map { it.label })
    }
}
