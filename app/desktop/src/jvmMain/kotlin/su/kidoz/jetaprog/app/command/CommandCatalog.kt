package su.kidoz.jetaprog.app.command

import su.kidoz.jetaprog.plugins.api.PluginManifest

/**
 * A command as shown in the command palette.
 *
 * @property id The command identifier passed to
 *   [su.kidoz.jetaprog.plugins.api.services.CommandService.executeCommand].
 * @property title Human-readable action name.
 * @property category Optional grouping shown before the title, e.g. `C++`.
 */
public data class PaletteCommand(
    val id: String,
    val title: String,
    val category: String? = null,
) {
    /** The label shown in the palette, e.g. `C++: Build`. */
    public val displayName: String
        get() = category?.takeIf { it.isNotBlank() }?.let { "$it: $title" } ?: title
}

/**
 * Builds and filters the list of commands offered by the command palette.
 *
 * Command identifiers come from the runtime registry, so anything a plugin registers is
 * reachable. Titles and categories come from plugin manifests when declared; commands
 * registered without a manifest contribution fall back to a name derived from the id.
 */
public object CommandCatalog {
    /**
     * Merges registered command ids with the metadata declared in plugin manifests.
     *
     * @param registeredIds Ids currently registered with the command service.
     * @param manifests Manifests of the installed plugins.
     * @return Commands sorted by display name.
     */
    public fun build(
        registeredIds: Collection<String>,
        manifests: Collection<PluginManifest>,
    ): List<PaletteCommand> {
        val declared =
            manifests
                .flatMap { manifest -> manifest.contributes.commands }
                .associateBy { it.command }

        return registeredIds
            .distinct()
            .map { id ->
                val contribution = declared[id]
                PaletteCommand(
                    id = id,
                    title = contribution?.title ?: humanize(id),
                    category = contribution?.category ?: id.substringBefore('.', missingDelimiterValue = ""),
                )
            }.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Filters and ranks [commands] against [query].
     *
     * An empty query returns everything unchanged so the palette doubles as a
     * browsable list of what the installed plugins can do.
     */
    public fun filter(
        commands: List<PaletteCommand>,
        query: String,
    ): List<PaletteCommand> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return commands

        return commands
            .mapNotNull { command -> score(command, trimmed)?.let { command to it } }
            .sortedWith(compareBy({ it.second }, { it.first.displayName.lowercase() }))
            .map { it.first }
    }

    /**
     * Ranks a single command; lower is better, null means no match.
     */
    internal fun score(
        command: PaletteCommand,
        query: String,
    ): Int? {
        val name = command.displayName.lowercase()
        val id = command.id.lowercase()
        val needle = query.lowercase()

        return when {
            name == needle || id == needle -> EXACT
            name.startsWith(needle) -> NAME_PREFIX
            command.title.lowercase().startsWith(needle) -> TITLE_PREFIX
            id.startsWith(needle) -> ID_PREFIX
            name.contains(needle) -> NAME_SUBSTRING
            id.contains(needle) -> ID_SUBSTRING
            isSubsequence(name, needle) -> SUBSEQUENCE
            isSubsequence(id, needle) -> SUBSEQUENCE + 1
            else -> null
        }
    }

    /**
     * Whether every character of [needle] appears in [haystack] in order, which is what
     * makes abbreviations like `cppb` match `C++: Build`.
     */
    internal fun isSubsequence(
        haystack: String,
        needle: String,
    ): Boolean {
        if (needle.isEmpty()) return true
        var index = 0
        for (char in haystack) {
            if (char == needle[index]) {
                index++
                if (index == needle.length) return true
            }
        }
        return false
    }

    /**
     * Derives a readable title from a command id, e.g. `cpp.writeClangdConfig`
     * becomes `Write Clangd Config`.
     */
    internal fun humanize(id: String): String {
        val leaf = id.substringAfterLast('.')
        if (leaf.isEmpty()) return id

        val spaced =
            buildString {
                leaf.forEachIndexed { index, char ->
                    if (index > 0 && char.isUpperCase() && !leaf[index - 1].isUpperCase()) {
                        append(' ')
                    }
                    append(char)
                }
            }
        return spaced.replaceFirstChar { it.uppercase() }
    }

    private const val EXACT = 0
    private const val NAME_PREFIX = 1
    private const val TITLE_PREFIX = 2
    private const val ID_PREFIX = 3
    private const val NAME_SUBSTRING = 4
    private const val ID_SUBSTRING = 5
    private const val SUBSEQUENCE = 6
}
