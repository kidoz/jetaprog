package su.kidoz.jetaprog.editor.completion.smart

import su.kidoz.jetaprog.common.completion.CompletionItem

/**
 * Interface for completion providers that support smart (type-aware) completion.
 *
 * Inspired by IntelliJ IDEA's CompletionContributor pattern.
 * Providers can contribute completion items and optionally provide type information
 * for smart filtering.
 */
public interface SmartCompletionProvider {
    /**
     * The priority of this provider (higher values are processed first).
     */
    public val priority: Int get() = 0

    /**
     * Whether this provider is applicable for the given request.
     */
    public fun isApplicable(request: SmartCompletionRequest): Boolean = true

    /**
     * Provide completion items for the request.
     *
     * @param request The completion request with context
     * @param collector Collector to add items to
     */
    public suspend fun provideCompletions(
        request: SmartCompletionRequest,
        collector: CompletionCollector,
    )

    /**
     * Optionally provide type context for the completion location.
     *
     * This is called before provideCompletions to determine expected types.
     * If multiple providers return type context, they are merged.
     *
     * @param request The completion request
     * @return Expected type context, or null if unknown
     */
    public suspend fun resolveTypeContext(request: SmartCompletionRequest): ExpectedTypeContext? = null
}

/**
 * Collector for completion items with type information.
 */
public interface CompletionCollector {
    /**
     * Add a completion item with optional type information.
     *
     * @param item The completion item
     * @param returnType The return/result type of this item (for smart filtering)
     */
    public fun add(
        item: CompletionItem,
        returnType: TypeInfo? = null,
    )

    /**
     * Add multiple items.
     */
    public fun addAll(items: Iterable<CompletionItem>) {
        items.forEach { add(it) }
    }

    /**
     * Add an item with priority override.
     */
    public fun addWithPriority(
        item: CompletionItem,
        priority: Int,
        returnType: TypeInfo? = null,
    )

    /**
     * Check if collection should stop (e.g., limit reached).
     */
    public fun shouldStop(): Boolean = false
}

/**
 * Registry of smart completion providers.
 */
public class SmartCompletionProviderRegistry {
    private val providers = mutableListOf<SmartCompletionProvider>()

    /**
     * Register a completion provider.
     */
    public fun register(provider: SmartCompletionProvider) {
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }

    /**
     * Unregister a completion provider.
     */
    public fun unregister(provider: SmartCompletionProvider) {
        providers.remove(provider)
    }

    /**
     * Get all applicable providers for a request.
     */
    public fun getApplicableProviders(request: SmartCompletionRequest): List<SmartCompletionProvider> =
        providers.filter { it.isApplicable(request) }

    /**
     * Get all registered providers.
     */
    public fun getAllProviders(): List<SmartCompletionProvider> = providers.toList()

    /**
     * Clear all providers.
     */
    public fun clear() {
        providers.clear()
    }
}

/**
 * A provider that contributes language keywords.
 */
public abstract class KeywordCompletionProvider : SmartCompletionProvider {
    override val priority: Int get() = 100

    /**
     * Get keywords for the language.
     */
    protected abstract fun getKeywords(): List<String>

    /**
     * Get the completion item kind for keywords.
     */
    protected open fun getKeywordKind(): su.kidoz.jetaprog.common.completion.CompletionItemKind =
        su.kidoz.jetaprog.common.completion.CompletionItemKind.Keyword

    override suspend fun provideCompletions(
        request: SmartCompletionRequest,
        collector: CompletionCollector,
    ) {
        // Keywords are only relevant for basic completion
        if (request.mode != CompletionMode.Basic) return

        val prefix = request.prefix.lowercase()
        for (keyword in getKeywords()) {
            if (prefix.isEmpty() || keyword.lowercase().startsWith(prefix)) {
                collector.add(
                    CompletionItem(
                        label = keyword,
                        kind = getKeywordKind(),
                        insertText = keyword,
                        source = su.kidoz.jetaprog.common.completion.CompletionSource.Keywords,
                    ),
                )
            }
        }
    }
}

/**
 * Kotlin keyword completion provider.
 */
public class KotlinKeywordProvider : KeywordCompletionProvider() {
    override fun getKeywords(): List<String> =
        listOf(
            // Hard keywords
            "as",
            "as?",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "!in",
            "interface",
            "is",
            "!is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while",
            // Soft keywords
            "by",
            "catch",
            "constructor",
            "delegate",
            "dynamic",
            "field",
            "file",
            "finally",
            "get",
            "import",
            "init",
            "param",
            "property",
            "receiver",
            "set",
            "setparam",
            "value",
            "where",
            // Modifier keywords
            "abstract",
            "actual",
            "annotation",
            "companion",
            "const",
            "crossinline",
            "data",
            "enum",
            "expect",
            "external",
            "final",
            "infix",
            "inline",
            "inner",
            "internal",
            "lateinit",
            "noinline",
            "open",
            "operator",
            "out",
            "override",
            "private",
            "protected",
            "public",
            "reified",
            "sealed",
            "suspend",
            "tailrec",
            "vararg",
        )
}

/**
 * Provider that suggests symbols from the local index.
 */
public class IndexBasedCompletionProvider(
    private val symbolIndex: su.kidoz.jetaprog.editor.navigation.index.SymbolIndex,
) : SmartCompletionProvider {
    override val priority: Int get() = 50

    override suspend fun provideCompletions(
        request: SmartCompletionRequest,
        collector: CompletionCollector,
    ) {
        val matches =
            symbolIndex.findByPattern(
                pattern = request.prefix,
                scope = su.kidoz.jetaprog.editor.navigation.SearchScope.PROJECT,
                limit = request.limit,
            )

        for (match in matches) {
            val symbol = match.symbol
            val item =
                CompletionItem(
                    label = symbol.name,
                    kind = mapSymbolKindToCompletionKind(symbol.kind),
                    detail = symbol.containerName?.let { "from $it" },
                    insertText = symbol.name,
                    filterText = symbol.name,
                    sortText = symbol.name,
                    typeText = symbol.signature,
                    containerTypeName = symbol.containerName,
                    source = su.kidoz.jetaprog.common.completion.CompletionSource.LocalIndex,
                    priority = match.score,
                )

            // Try to parse type info from signature
            val returnType = symbol.signature?.let { TypeInfo.parse(it) }
            collector.add(item, returnType)
        }
    }

    private fun mapSymbolKindToCompletionKind(
        kind: su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind,
    ): su.kidoz.jetaprog.common.completion.CompletionItemKind =
        when (kind) {
            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.CLASS -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Class
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.INTERFACE -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Interface
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.FUNCTION -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Function
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.METHOD -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Method
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.PROPERTY -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Property
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.FIELD -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Field
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.VARIABLE -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Variable
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.CONSTANT -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Constant
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.ENUM -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Enum
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.ENUM_MEMBER -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.EnumMember
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.STRUCT -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Struct
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.MODULE -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Module
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.TYPE_PARAMETER -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.TypeParameter
            }

            su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind.TYPE_ALIAS -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.TypeParameter
            }

            else -> {
                su.kidoz.jetaprog.common.completion.CompletionItemKind.Text
            }
        }
}
