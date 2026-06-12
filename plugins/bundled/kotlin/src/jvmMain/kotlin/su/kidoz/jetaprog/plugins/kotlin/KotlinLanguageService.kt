package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
import su.kidoz.jetaprog.plugins.api.language.CompletionItemKind
import su.kidoz.jetaprog.plugins.api.language.Hover

/**
 * Language service for Kotlin providing IDE features.
 */
public class KotlinLanguageService : Disposable {
    private val symbolIndex = KotlinSymbolIndex()
    private val navigationProvider = KotlinNavigationProvider(symbolIndex)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initializes the service with a project root.
     */
    public fun initialize(projectRoot: String) {
        scope.launch {
            symbolIndex.indexDirectory(projectRoot)
        }
    }

    /**
     * Re-indexes a file after changes.
     */
    public fun onFileChanged(filePath: String) {
        scope.launch {
            symbolIndex.indexFile(filePath)
        }
    }

    /**
     * Navigates to the definition of a symbol.
     */
    public suspend fun goToDefinition(
        filePath: String,
        position: TextPosition,
        fileContent: String,
    ): SymbolLocation? = navigationProvider.goToDefinition(filePath, position, fileContent)

    /**
     * Finds all references to a symbol.
     */
    public suspend fun findReferences(
        filePath: String,
        position: TextPosition,
        projectRoot: String,
    ): List<SymbolLocation> = navigationProvider.findReferences(filePath, position, projectRoot)

    /**
     * Gets the file outline.
     */
    public suspend fun getFileOutline(filePath: String): List<KotlinSymbol> =
        navigationProvider.getFileOutline(filePath)

    /**
     * Searches for symbols.
     */
    public suspend fun searchSymbols(
        query: String,
        limit: Int = 50,
    ): List<KotlinSymbol> = navigationProvider.searchSymbols(query, limit)

    /**
     * Gets hover information for a position.
     */
    public suspend fun getHover(
        filePath: String,
        position: TextPosition,
    ): Hover? {
        val symbol = symbolIndex.getSymbolAt(filePath, position) ?: return null

        val codeContent =
            buildString {
                when (symbol.kind) {
                    SymbolKind.CLASS -> {
                        append("class ")
                    }

                    SymbolKind.INTERFACE -> {
                        append("interface ")
                    }

                    SymbolKind.OBJECT -> {
                        append("object ")
                    }

                    SymbolKind.ENUM -> {
                        append("enum class ")
                    }

                    SymbolKind.FUNCTION -> {
                        append("fun ")
                    }

                    SymbolKind.PROPERTY -> {
                        append("val ")
                    }

                    else -> {}
                }
                append(symbol.name)
                symbol.signature?.let { append(": $it") }
            }

        val contents =
            mutableListOf<MarkedString>(
                MarkedString.Code("kotlin", codeContent),
            )

        symbol.documentation?.let {
            contents.add(MarkedString.Markdown(it))
        }

        return Hover(
            contents = contents,
            range = symbol.nameRange,
        )
    }

    /**
     * Gets completion items at a position.
     */
    public suspend fun getCompletions(
        filePath: String,
        position: TextPosition,
        fileContent: String,
    ): List<CompletionItem> {
        // Extract partial identifier being typed
        val lines = fileContent.lines()
        if (position.line >= lines.size) return emptyList()

        val line = lines[position.line]
        val prefix =
            buildString {
                var col = position.column - 1
                while (col >= 0 && (line[col].isLetterOrDigit() || line[col] == '_')) {
                    insert(0, line[col])
                    col--
                }
            }

        if (prefix.isEmpty()) return emptyList()

        // Search for matching symbols
        val symbols = symbolIndex.search(prefix, 20)

        return symbols.map { symbol ->
            CompletionItem(
                label = symbol.name,
                kind =
                    when (symbol.kind) {
                        SymbolKind.CLASS, SymbolKind.OBJECT,
                        SymbolKind.ANNOTATION,
                        -> CompletionItemKind.Class

                        SymbolKind.INTERFACE -> CompletionItemKind.Interface

                        SymbolKind.ENUM -> CompletionItemKind.Enum

                        SymbolKind.FUNCTION -> CompletionItemKind.Function

                        SymbolKind.PROPERTY -> CompletionItemKind.Property

                        else -> CompletionItemKind.Text
                    },
                detail = symbol.fqName,
                documentation = symbol.documentation,
                insertText = symbol.name,
            )
        }
    }

    override fun dispose() {
        // Clean up
    }
}
