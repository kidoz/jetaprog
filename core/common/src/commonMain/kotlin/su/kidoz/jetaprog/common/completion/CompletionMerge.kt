package su.kidoz.jetaprog.common.completion

/**
 * Folds items that share a label into one item carrying the richest information.
 *
 * Providers overlap: the Kotlin index and the in-file PSI provider both offer a class
 * declared in the open file, and a language server offers it a third time with a
 * semantic rank, documentation and the edit it wants applied. Keeping only the first
 * item discarded all of that, so the server's ranking and import edits never reached
 * the popup. Order follows the first occurrence of each label.
 */
public fun List<CompletionItem>.mergeDuplicateLabels(): List<CompletionItem> {
    if (size < 2) return this
    val merged = LinkedHashMap<String, CompletionItem>(size)
    for (item in this) {
        merged[item.label] = merged[item.label]?.mergedWith(item) ?: item
    }
    return merged.values.toList()
}

/**
 * Combines this item with [other], which shares its label.
 *
 * The item carrying a server-side rank or an explicit edit range supplies the insertion
 * (text, snippet flag, ranges), since those were computed together; everything else is
 * filled from whichever item has it.
 */
public fun CompletionItem.mergedWith(other: CompletionItem): CompletionItem {
    val (base, extra) = if (!hasServerData && other.hasServerData) other to this else this to other
    return base.copy(
        kind = if (base.kind == CompletionItemKind.Text) extra.kind else base.kind,
        detail = base.detail ?: extra.detail,
        documentation = base.documentation ?: extra.documentation,
        sortText = base.sortText ?: extra.sortText,
        preselect = base.preselect || extra.preselect,
        additionalTextEdits = base.additionalTextEdits.ifEmpty { extra.additionalTextEdits },
        typeText = base.typeText ?: extra.typeText,
        tailText = base.tailText ?: extra.tailText,
        returnTypeName = base.returnTypeName ?: extra.returnTypeName,
        containerTypeName = base.containerTypeName ?: extra.containerTypeName,
        priority = maxOf(base.priority, extra.priority),
        tags = (base.tags + extra.tags).distinct(),
    )
}

private val CompletionItem.hasServerData: Boolean
    get() = sortText != null || range != null

/**
 * Fills in what [resolved] supplies lazily (documentation, detail, extra edits) and
 * marks this item as resolved so it is not asked for again.
 */
public fun CompletionItem.resolvedWith(resolved: CompletionItem): CompletionItem =
    copy(
        detail = resolved.detail ?: detail,
        documentation = resolved.documentation ?: documentation,
        additionalTextEdits = resolved.additionalTextEdits.ifEmpty { additionalTextEdits },
        resolveData = null,
    )
