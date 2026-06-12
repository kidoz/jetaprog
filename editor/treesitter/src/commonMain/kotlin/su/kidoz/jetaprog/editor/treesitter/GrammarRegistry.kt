package su.kidoz.jetaprog.editor.treesitter

/**
 * Registry for Tree-sitter language grammars.
 *
 * Provides information about available grammars and their configurations.
 * The actual grammar loading is handled by platform-specific implementations.
 */
public object GrammarRegistry {
    /**
     * Information about a Tree-sitter grammar.
     */
    public data class GrammarInfo(
        val languageId: String,
        val displayName: String,
        val fileExtensions: List<String>,
        val highlightQueryFile: String? = null,
    )

    /**
     * Built-in grammar definitions.
     *
     * These are grammars that Tree-sitter supports out of the box.
     * Actual grammar libraries must be available at runtime.
     */
    private val builtInGrammars: Map<String, GrammarInfo> =
        mapOf(
            "kotlin" to
                GrammarInfo(
                    languageId = "kotlin",
                    displayName = "Kotlin",
                    fileExtensions = listOf("kt", "kts"),
                ),
            "java" to
                GrammarInfo(
                    languageId = "java",
                    displayName = "Java",
                    fileExtensions = listOf("java"),
                ),
            "python" to
                GrammarInfo(
                    languageId = "python",
                    displayName = "Python",
                    fileExtensions = listOf("py", "pyi"),
                ),
            "rust" to
                GrammarInfo(
                    languageId = "rust",
                    displayName = "Rust",
                    fileExtensions = listOf("rs"),
                ),
            "go" to
                GrammarInfo(
                    languageId = "go",
                    displayName = "Go",
                    fileExtensions = listOf("go"),
                ),
            "javascript" to
                GrammarInfo(
                    languageId = "javascript",
                    displayName = "JavaScript",
                    fileExtensions = listOf("js", "mjs", "cjs"),
                ),
            "typescript" to
                GrammarInfo(
                    languageId = "typescript",
                    displayName = "TypeScript",
                    fileExtensions = listOf("ts", "mts", "cts"),
                ),
            "tsx" to
                GrammarInfo(
                    languageId = "tsx",
                    displayName = "TypeScript JSX",
                    fileExtensions = listOf("tsx"),
                ),
            "cpp" to
                GrammarInfo(
                    languageId = "cpp",
                    displayName = "C++",
                    fileExtensions = listOf("cpp", "cc", "cxx", "hpp", "hxx", "h"),
                ),
            "c" to
                GrammarInfo(
                    languageId = "c",
                    displayName = "C",
                    fileExtensions = listOf("c", "h"),
                ),
            "c-sharp" to
                GrammarInfo(
                    languageId = "c_sharp",
                    displayName = "C#",
                    fileExtensions = listOf("cs"),
                ),
            "json" to
                GrammarInfo(
                    languageId = "json",
                    displayName = "JSON",
                    fileExtensions = listOf("json"),
                ),
            "yaml" to
                GrammarInfo(
                    languageId = "yaml",
                    displayName = "YAML",
                    fileExtensions = listOf("yaml", "yml"),
                ),
            "toml" to
                GrammarInfo(
                    languageId = "toml",
                    displayName = "TOML",
                    fileExtensions = listOf("toml"),
                ),
            "markdown" to
                GrammarInfo(
                    languageId = "markdown",
                    displayName = "Markdown",
                    fileExtensions = listOf("md", "markdown"),
                ),
            "html" to
                GrammarInfo(
                    languageId = "html",
                    displayName = "HTML",
                    fileExtensions = listOf("html", "htm"),
                ),
            "css" to
                GrammarInfo(
                    languageId = "css",
                    displayName = "CSS",
                    fileExtensions = listOf("css"),
                ),
            "sql" to
                GrammarInfo(
                    languageId = "sql",
                    displayName = "SQL",
                    fileExtensions = listOf("sql"),
                ),
            "bash" to
                GrammarInfo(
                    languageId = "bash",
                    displayName = "Bash",
                    fileExtensions = listOf("sh", "bash"),
                ),
            "dockerfile" to
                GrammarInfo(
                    languageId = "dockerfile",
                    displayName = "Dockerfile",
                    fileExtensions = listOf("dockerfile", "Dockerfile"),
                ),
        )

    private val customGrammars = mutableMapOf<String, GrammarInfo>()

    /**
     * Get grammar info for a language.
     */
    public fun getGrammarInfo(languageId: String): GrammarInfo? =
        customGrammars[languageId] ?: builtInGrammars[languageId]

    /**
     * Get grammar info by file extension.
     */
    public fun getGrammarForExtension(extension: String): GrammarInfo? {
        val ext = extension.removePrefix(".")
        return allGrammars().values.find { ext in it.fileExtensions }
    }

    /**
     * Register a custom grammar.
     */
    public fun registerGrammar(info: GrammarInfo) {
        customGrammars[info.languageId] = info
    }

    /**
     * Get all available grammars.
     */
    public fun allGrammars(): Map<String, GrammarInfo> = builtInGrammars + customGrammars

    /**
     * Get all supported language IDs.
     */
    public fun supportedLanguages(): Set<String> = allGrammars().keys

    /**
     * Check if a language is supported.
     */
    public fun isSupported(languageId: String): Boolean = languageId in allGrammars()
}
