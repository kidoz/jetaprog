package su.kidoz.jetaprog.editor.completion.smart

import su.kidoz.jetaprog.common.completion.CompletionItem

/**
 * Narrows a completion list to the items whose type fits the position being completed.
 *
 * This runs *after* the normal providers, so the language server still sources and ranks
 * the candidates and smart mode only decides which of them survive. Sourcing separately
 * would throw away the server's semantic knowledge, which is the thing worth keeping.
 */
public object SmartCompletionFilter {
    /**
     * Filters [items] to those compatible with [context], best match first.
     *
     * Returns the input unchanged when no expected type could be inferred, or when nothing
     * matches — an empty popup is worse than an unfiltered one.
     *
     * @return The surviving items, and whether any narrowing actually happened.
     */
    public fun apply(
        items: List<CompletionItem>,
        context: ExpectedTypeContext,
    ): SmartFilterResult {
        val expected = context.expectedType ?: return SmartFilterResult(items, narrowed = false)

        val scored =
            items.mapNotNull { item ->
                val type = typeOf(item) ?: return@mapNotNull null
                val score = context.compatibilityScore(type)
                if (score > 0 || context.isCompatible(type)) item to score else null
            }

        if (scored.isEmpty()) return SmartFilterResult(items, narrowed = false)

        val ranked =
            scored
                .sortedWith(
                    compareByDescending<Pair<CompletionItem, Int>> { it.second }
                        .thenBy { it.first.sortText ?: it.first.label },
                ).map { it.first }

        return SmartFilterResult(ranked, narrowed = true, expectedTypeName = expected.name)
    }

    /**
     * The type an item would contribute if accepted.
     *
     * Language servers report this in different fields; clangd puts a function's return
     * type or a variable's type in `detail`.
     */
    private fun typeOf(item: CompletionItem): TypeInfo? {
        val raw =
            item.returnTypeName?.takeIf { it.isNotBlank() }
                ?: item.typeText?.takeIf { it.isNotBlank() }
                ?: item.detail?.takeIf { it.isNotBlank() }
                ?: return null

        // clangd's detail for a function is just the return type, but for some kinds it is
        // a whole signature; take the leading type token in that case.
        val cleaned =
            raw
                .substringBefore('(')
                .trim()
                .removePrefix("const ")
                .trim()
        if (cleaned.isEmpty()) return null
        return TypeInfo.parse(cleaned)
    }
}

/**
 * Outcome of a smart-completion narrowing pass.
 *
 * @property items The items to show.
 * @property narrowed Whether the list was actually filtered by type.
 * @property expectedTypeName The inferred type, for display in the popup.
 */
public data class SmartFilterResult(
    val items: List<CompletionItem>,
    val narrowed: Boolean,
    val expectedTypeName: String? = null,
)
