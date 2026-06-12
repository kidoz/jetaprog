package su.kidoz.jetaprog.settings.search

/**
 * An entry in the settings search index.
 *
 * @param path The settings path (e.g., "editor.tabSize")
 * @param displayName The human-readable display name (e.g., "Tab Size")
 * @param description A description of the setting
 * @param category The settings category (e.g., "editor")
 * @param keywords Additional search keywords
 */
public data class SettingsSearchEntry(
    val path: String,
    val displayName: String,
    val description: String,
    val category: String,
    val keywords: List<String> = emptyList(),
) {
    /**
     * All searchable text combined for matching.
     */
    val searchableText: String =
        "$displayName $description ${keywords.joinToString(" ")} $path".lowercase()
}

/**
 * Search result for a settings search query.
 */
public data class SettingsSearchResult(
    /**
     * The matched entry.
     */
    val entry: SettingsSearchEntry,
    /**
     * The relevance score (higher = more relevant).
     */
    val score: Int,
)

/**
 * Full-text search index for IDE settings.
 *
 * Indexes all settings fields with their display names, descriptions, and
 * keywords to support search/filter in the Settings dialog.
 */
public class SettingsSearchIndex {
    private val entries = mutableListOf<SettingsSearchEntry>()

    init {
        buildDefaultIndex()
    }

    /**
     * Searches the index with the given query.
     *
     * @param query The search query
     * @return Matching entries sorted by relevance
     */
    public fun search(query: String): List<SettingsSearchResult> {
        if (query.isBlank()) return emptyList()

        val terms = query.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()

        return entries
            .mapNotNull { entry ->
                val score = computeScore(entry, terms)
                if (score > 0) SettingsSearchResult(entry, score) else null
            }.sortedByDescending { it.score }
    }

    /**
     * Returns all entries in a category.
     */
    public fun getByCategory(category: String): List<SettingsSearchEntry> = entries.filter { it.category == category }

    /**
     * Adds a custom entry to the index (for plugin-contributed settings).
     */
    public fun addEntry(entry: SettingsSearchEntry) {
        entries.add(entry)
    }

    private fun computeScore(
        entry: SettingsSearchEntry,
        terms: List<String>,
    ): Int {
        var totalScore = 0
        val searchText = entry.searchableText
        val displayLower = entry.displayName.lowercase()

        for (term in terms) {
            val termScore =
                when {
                    // Exact match on display name
                    displayLower == term -> 100

                    // Display name starts with term
                    displayLower.startsWith(term) -> 80

                    // Display name contains term
                    displayLower.contains(term) -> 60

                    // Path contains term
                    entry.path.lowercase().contains(term) -> 40

                    // Any searchable text contains term
                    searchText.contains(term) -> 20

                    // No match
                    else -> return 0
                }
            totalScore += termScore
        }

        return totalScore
    }

    @Suppress("LongMethod")
    private fun buildDefaultIndex() {
        // Appearance settings
        addEntry(
            SettingsSearchEntry(
                "appearance.theme",
                "Theme",
                "Color theme for the IDE",
                "appearance",
                listOf("dark", "light", "color", "scheme"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "appearance.fontFamily",
                "Font Family",
                "Editor font family",
                "appearance",
                listOf("font", "typeface", "monospace"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "appearance.fontSize",
                "Font Size",
                "Editor font size in points",
                "appearance",
                listOf("font", "size", "text"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "appearance.uiScale",
                "UI Scale",
                "Scale factor for the user interface",
                "appearance",
                listOf("zoom", "dpi", "scale", "size"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "appearance.iconTheme",
                "Icon Theme",
                "Icon theme for the IDE",
                "appearance",
                listOf("icons", "visual"),
            ),
        )

        // Editor settings
        addEntry(
            SettingsSearchEntry(
                "editor.tabSize",
                "Tab Size",
                "Number of spaces per tab",
                "editor",
                listOf("indent", "spaces", "tab", "width"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.useTabs",
                "Use Tabs",
                "Use tabs instead of spaces for indentation",
                "editor",
                listOf("indent", "tab", "spaces"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.showLineNumbers",
                "Show Line Numbers",
                "Display line numbers in the gutter",
                "editor",
                listOf("line", "numbers", "gutter"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.showMinimap",
                "Show Minimap",
                "Display minimap on the right side",
                "editor",
                listOf("minimap", "overview", "preview"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.wordWrap",
                "Word Wrap",
                "Wrap long lines at the viewport edge",
                "editor",
                listOf("wrap", "line", "break", "soft"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.showWhitespace",
                "Show Whitespace",
                "Show whitespace characters",
                "editor",
                listOf("whitespace", "spaces", "tabs", "invisible"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.highlightCurrentLine",
                "Highlight Current Line",
                "Highlight the line the cursor is on",
                "editor",
                listOf("highlight", "current", "line", "cursor"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.bracketMatching",
                "Bracket Matching",
                "Highlight matching brackets",
                "editor",
                listOf("bracket", "parenthesis", "brace", "matching"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.autoCloseBrackets",
                "Auto Close Brackets",
                "Automatically close brackets and quotes",
                "editor",
                listOf("auto", "close", "bracket", "pair"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.autoSave",
                "Auto Save",
                "Automatically save files after a delay",
                "editor",
                listOf("auto", "save", "file"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.autoSaveDelayMs",
                "Auto Save Delay",
                "Delay in milliseconds before auto-save",
                "editor",
                listOf("auto", "save", "delay", "time"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.trimTrailingWhitespace",
                "Trim Trailing Whitespace",
                "Remove trailing whitespace on save",
                "editor",
                listOf("trim", "whitespace", "trailing", "save"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.insertFinalNewline",
                "Insert Final Newline",
                "Ensure file ends with a newline on save",
                "editor",
                listOf("newline", "final", "end", "save"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "editor.maxLineLength",
                "Max Line Length",
                "Ruler column for maximum line length",
                "editor",
                listOf("line", "length", "ruler", "column", "margin"),
            ),
        )

        // Languages
        addEntry(
            SettingsSearchEntry(
                "languages.defaults.encoding",
                "Default Encoding",
                "Default file encoding",
                "languages",
                listOf("encoding", "charset", "utf"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "languages.completionPreference",
                "Completion Provider",
                "Preferred completion provider (Native, LSP, or Hybrid)",
                "languages",
                listOf("completion", "lsp", "native", "hybrid", "autocomplete"),
            ),
        )

        // Tools
        addEntry(
            SettingsSearchEntry(
                "tools.buildSystems.autoDetect",
                "Auto Detect Build System",
                "Automatically detect project build system",
                "tools",
                listOf("build", "gradle", "maven", "cargo", "detect"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "tools.mcpServers",
                "MCP Servers",
                "Model Context Protocol server configurations",
                "tools",
                listOf("mcp", "ai", "server", "model"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "tools.externalTools",
                "External Tools",
                "Configure external tools and commands",
                "tools",
                listOf("external", "tools", "command"),
            ),
        )

        // Plugins
        addEntry(
            SettingsSearchEntry(
                "plugins.updatePolicy",
                "Plugin Update Policy",
                "How plugins are updated",
                "plugins",
                listOf("update", "plugin", "automatic"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "plugins.allowPrerelease",
                "Allow Prerelease Plugins",
                "Allow installation of prerelease plugin versions",
                "plugins",
                listOf("prerelease", "beta", "plugin"),
            ),
        )
        addEntry(
            SettingsSearchEntry(
                "plugins.disabledPlugins",
                "Disabled Plugins",
                "List of disabled plugins",
                "plugins",
                listOf("disabled", "plugin", "enable"),
            ),
        )
    }
}
