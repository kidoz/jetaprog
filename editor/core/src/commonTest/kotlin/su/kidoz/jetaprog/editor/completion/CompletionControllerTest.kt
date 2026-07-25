package su.kidoz.jetaprog.editor.completion

import su.kidoz.jetaprog.common.completion.CompletionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletionControllerTest {
    private val controller = CompletionController()

    private fun item(
        label: String,
        sortText: String? = null,
        preselect: Boolean = false,
    ) = CompletionItem(label = label, sortText = sortText, preselect = preselect)

    @Test
    fun serverRankingWinsOverLocalStringScoring() {
        // clangd ranks semantically and reports the order in sortText. Local scoring would
        // put the shortest prefix match first, which is not what the server decided.
        val items =
            listOf(
                item("valueOfSomethingLong", sortText = "000000"),
                item("val", sortText = "999999"),
            )

        val result = controller.filterItems(items, "val")

        assertEquals("valueOfSomethingLong", result.first().label)
    }

    @Test
    fun preselectStillOutranksServerOrder() {
        val items =
            listOf(
                item("first", sortText = "000000"),
                item("preselected", sortText = "999999", preselect = true),
            )

        assertEquals("preselected", controller.filterItems(items, "e").first().label)
    }

    @Test
    fun localScoringIsUsedWhenNoProviderRanks() {
        // In-process providers send no sortText; relevance ordering is all we have.
        val items = listOf(item("someValue"), item("val"))

        val result = controller.filterItems(items, "val")

        assertEquals("val", result.first().label)
    }

    @Test
    fun nonMatchingItemsAreDropped() {
        val items = listOf(item("alpha"), item("beta"))

        assertEquals(listOf("beta"), controller.filterItems(items, "bet").map { it.label })
    }

    @Test
    fun fuzzyCamelCaseMatchingStillWorks() {
        val items = listOf(item("fillMaxWidth"), item("unrelated"))

        assertEquals(listOf("fillMaxWidth"), controller.filterItems(items, "fmw").map { it.label })
    }

    @Test
    fun anEmptyFilterKeepsTheProvidersOwnOrder() {
        val items = listOf(item("zebra", sortText = "000"), item("apple", sortText = "111"))

        assertEquals(listOf("zebra", "apple"), controller.filterItems(items, "").map { it.label })
    }

    @Test
    fun filteringIsIdempotentOverTheRawSet() {
        // The ViewModel always filters the unfiltered set, so widening the prefix and then
        // narrowing it again must give the same answer as filtering once.
        val items = listOf(item("value"), item("valid"), item("other"))

        val narrow = controller.filterItems(items, "val")
        val widened = controller.filterItems(items, "v")

        assertEquals(2, narrow.size)
        assertTrue(widened.size >= narrow.size, "widening the prefix must not lose items")
    }

    @Test
    fun replacementRangeCoversTheIdentifierAroundTheCaret() {
        val content = "auto v = compute(value);"
        val caret = content.indexOf("value") + 3

        val (start, end) = controller.getReplacementRange(content, caret)

        assertEquals(content.indexOf("value"), start)
        assertEquals(content.indexOf("value") + "value".length, end)
    }
}
