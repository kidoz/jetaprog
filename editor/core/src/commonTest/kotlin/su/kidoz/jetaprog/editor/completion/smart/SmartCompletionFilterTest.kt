package su.kidoz.jetaprog.editor.completion.smart

import su.kidoz.jetaprog.common.completion.CompletionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartCompletionFilterTest {
    private fun item(
        label: String,
        detail: String? = null,
        returnTypeName: String? = null,
    ) = CompletionItem(label = label, detail = detail, returnTypeName = returnTypeName)

    private fun expecting(type: String) =
        ExpectedTypeContext(
            expectedType = TypeInfo.parse(type),
            contextKind = TypeContextKind.Assignment,
        )

    @Test
    fun keepsOnlyItemsOfTheExpectedType() {
        val items =
            listOf(
                item("count", detail = "int"),
                item("name", detail = "std::string"),
                item("total", detail = "int"),
            )

        val result = SmartCompletionFilter.apply(items, expecting("int"))

        assertTrue(result.narrowed)
        assertEquals(listOf("count", "total"), result.items.map { it.label })
        assertEquals("int", result.expectedTypeName)
    }

    @Test
    fun readsTheTypeFromAFunctionSignatureDetail() {
        // clangd reports some completions with a full signature rather than a bare type.
        val items = listOf(item("square", detail = "int (int x)"), item("greet", detail = "void ()"))

        val result = SmartCompletionFilter.apply(items, expecting("int"))

        assertEquals(listOf("square"), result.items.map { it.label })
    }

    @Test
    fun prefersAnExplicitReturnTypeOverDetail() {
        val items = listOf(item("value", detail = "not a type", returnTypeName = "int"))

        assertEquals(listOf("value"), SmartCompletionFilter.apply(items, expecting("int")).items.map { it.label })
    }

    @Test
    fun exactMatchesOutrankMerelyAssignableOnes() {
        val items =
            listOf(
                item("assignable", detail = "Derived"),
                item("exact", detail = "int"),
            )

        val result = SmartCompletionFilter.apply(items, expecting("int"))

        assertEquals("exact", result.items.first().label)
    }

    @Test
    fun leavesTheListAloneWhenNoTypeWasInferred() {
        val items = listOf(item("a", detail = "int"), item("b", detail = "float"))

        val result = SmartCompletionFilter.apply(items, ExpectedTypeContext.None)

        assertFalse(result.narrowed)
        assertEquals(items, result.items)
    }

    @Test
    fun fallsBackToTheFullListWhenNothingMatches() {
        // An empty popup is worse than an unfiltered one.
        val items = listOf(item("name", detail = "std::string"))

        val result = SmartCompletionFilter.apply(items, expecting("int"))

        assertFalse(result.narrowed)
        assertEquals(items, result.items)
    }

    @Test
    fun itemsWithNoTypeInformationAreNotGuessedAt() {
        val items = listOf(item("known", detail = "int"), item("mystery"))

        val result = SmartCompletionFilter.apply(items, expecting("int"))

        assertEquals(listOf("known"), result.items.map { it.label })
    }

    @Test
    fun constQualifiersDoNotBlockAMatch() {
        val items = listOf(item("limit", detail = "const int"))

        assertTrue(SmartCompletionFilter.apply(items, expecting("int")).narrowed)
    }
}
