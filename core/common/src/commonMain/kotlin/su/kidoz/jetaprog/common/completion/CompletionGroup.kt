package su.kidoz.jetaprog.common.completion

/**
 * Group/category for organizing completion items in the popup.
 *
 * Items within the same group are clustered together with visual separators.
 * Groups are displayed in the order defined by their [priority] (higher first).
 */
public data class CompletionGroup(
    /**
     * Unique identifier for this group.
     */
    val id: String,
    /**
     * Display name shown as a separator/header.
     */
    val displayName: String,
    /**
     * Sort priority (higher = appears first).
     */
    val priority: Int = 0,
)

/**
 * A completion item with its assigned group.
 */
public data class GroupedCompletionItem(
    /**
     * The completion item.
     */
    val item: CompletionItem,
    /**
     * The group this item belongs to, or null for ungrouped.
     */
    val group: CompletionGroup?,
)

/**
 * Well-known completion groups.
 */
public object CompletionGroups {
    /**
     * Recently used items.
     */
    public val RECENT: CompletionGroup = CompletionGroup("recent", "Recent", priority = 100)

    /**
     * Items from the local scope (local variables, parameters).
     */
    public val LOCAL: CompletionGroup = CompletionGroup("local", "Local", priority = 90)

    /**
     * Member items (methods, properties of current type).
     */
    public val MEMBERS: CompletionGroup = CompletionGroup("members", "Members", priority = 80)

    /**
     * Items from inherited/super types.
     */
    public val INHERITED: CompletionGroup = CompletionGroup("inherited", "Inherited", priority = 70)

    /**
     * Global items (top-level functions, classes).
     */
    public val GLOBAL: CompletionGroup = CompletionGroup("global", "Global", priority = 60)

    /**
     * Keywords.
     */
    public val KEYWORDS: CompletionGroup = CompletionGroup("keywords", "Keywords", priority = 50)

    /**
     * Snippets and live templates.
     */
    public val SNIPPETS: CompletionGroup = CompletionGroup("snippets", "Snippets", priority = 40)

    /**
     * Deprecated items.
     */
    public val DEPRECATED: CompletionGroup = CompletionGroup("deprecated", "Deprecated", priority = 10)
}

/**
 * Assigns groups to completion items based on their properties.
 */
public object CompletionGroupAssigner {
    /**
     * Assigns a group to a completion item.
     *
     * @param item The completion item
     * @param isRecent Whether the item was recently used
     * @return A [GroupedCompletionItem] with the assigned group
     */
    public fun assignGroup(
        item: CompletionItem,
        isRecent: Boolean = false,
    ): GroupedCompletionItem {
        val group =
            when {
                isRecent -> CompletionGroups.RECENT
                "deprecated" in item.tags -> CompletionGroups.DEPRECATED
                item.kind == CompletionItemKind.Keyword -> CompletionGroups.KEYWORDS
                item.kind == CompletionItemKind.Snippet -> CompletionGroups.SNIPPETS
                item.source == CompletionSource.LocalIndex -> CompletionGroups.LOCAL
                item.containerTypeName != null -> CompletionGroups.MEMBERS
                else -> CompletionGroups.GLOBAL
            }
        return GroupedCompletionItem(item, group)
    }

    /**
     * Groups and sorts completion items by their assigned groups.
     *
     * @param items The items to group
     * @param recentItems Set of item labels that are considered "recent"
     * @return Items sorted by group priority, then by item order within each group
     */
    public fun groupAndSort(
        items: List<CompletionItem>,
        recentItems: Set<String> = emptySet(),
    ): List<GroupedCompletionItem> {
        val grouped = items.map { assignGroup(it, it.label in recentItems) }
        return grouped.sortedByDescending { it.group?.priority ?: 0 }
    }
}
