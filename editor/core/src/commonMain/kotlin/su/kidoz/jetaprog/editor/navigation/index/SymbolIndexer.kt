package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind

/**
 * Interface for extracting symbols from source files.
 *
 * Implementations are language-specific and parse source code to identify
 * declarations like classes, functions, variables, etc.
 *
 * Inspired by IntelliJ IDEA's StubElementTypeHolder and VS Code's DocumentSymbolProvider.
 */
public interface SymbolExtractor {
    /**
     * The language ID this extractor handles (e.g., "kotlin", "java", "typescript").
     */
    public val languageId: String

    /**
     * File extensions this extractor supports (e.g., ["kt", "kts"]).
     */
    public val supportedExtensions: Set<String>

    /**
     * Extract symbols from source code.
     *
     * @param content The source code content
     * @param filePath The file path (for creating IndexedSymbol instances)
     * @return List of extracted symbols
     */
    public fun extractSymbols(
        content: String,
        filePath: String,
    ): List<IndexedSymbol>
}

/**
 * Registry of symbol extractors for different languages.
 */
public class SymbolExtractorRegistry {
    private val extractorsByLanguage = mutableMapOf<String, SymbolExtractor>()
    private val extractorsByExtension = mutableMapOf<String, SymbolExtractor>()

    /**
     * Register a symbol extractor.
     */
    public fun register(extractor: SymbolExtractor) {
        extractorsByLanguage[extractor.languageId] = extractor
        for (ext in extractor.supportedExtensions) {
            extractorsByExtension[ext.lowercase()] = extractor
        }
    }

    /**
     * Get extractor by language ID.
     */
    public fun getByLanguage(languageId: String): SymbolExtractor? = extractorsByLanguage[languageId]

    /**
     * Get extractor by file extension.
     */
    public fun getByExtension(extension: String): SymbolExtractor? = extractorsByExtension[extension.lowercase()]

    /**
     * Get extractor for a file path.
     */
    public fun getForFile(filePath: String): SymbolExtractor? {
        val ext = filePath.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) getByExtension(ext) else null
    }

    /**
     * Get all registered language IDs.
     */
    public fun getRegisteredLanguages(): Set<String> = extractorsByLanguage.keys.toSet()
}

/**
 * Manages symbol indexing for a project.
 *
 * Coordinates symbol extraction and index updates when files change.
 */
public class SymbolIndexer(
    private val index: SymbolIndex,
    private val extractorRegistry: SymbolExtractorRegistry = SymbolExtractorRegistry(),
) {
    /**
     * Index a single file.
     *
     * @param filePath The file path
     * @param content The file content
     * @return Number of symbols indexed, or 0 if no extractor found
     */
    public fun indexFile(
        filePath: String,
        content: String,
    ): Int {
        val extractor = extractorRegistry.getForFile(filePath) ?: return 0
        val symbols = extractor.extractSymbols(content, filePath)
        index.indexFile(filePath, symbols)
        return symbols.size
    }

    /**
     * Index multiple files.
     *
     * @param files Map of file path to content
     * @return Total number of symbols indexed
     */
    public fun indexFiles(files: Map<String, String>): Int {
        var total = 0
        for ((path, content) in files) {
            total += indexFile(path, content)
        }
        return total
    }

    /**
     * Remove a file from the index.
     */
    public fun removeFile(filePath: String) {
        index.removeFile(filePath)
    }

    /**
     * Register a symbol extractor.
     */
    public fun registerExtractor(extractor: SymbolExtractor) {
        extractorRegistry.register(extractor)
    }

    /**
     * Check if a file can be indexed.
     */
    public fun canIndex(filePath: String): Boolean = extractorRegistry.getForFile(filePath) != null

    /**
     * Get the underlying index.
     */
    public fun getIndex(): SymbolIndex = index
}

/**
 * A simple regex-based symbol extractor for common patterns.
 *
 * This provides basic symbol extraction using regular expressions.
 * For more accurate parsing, use language-specific extractors or tree-sitter.
 */
public abstract class RegexSymbolExtractor(
    override val languageId: String,
    override val supportedExtensions: Set<String>,
) : SymbolExtractor {
    /**
     * Pattern definitions for this language.
     */
    protected abstract val patterns: List<SymbolPattern>

    override fun extractSymbols(
        content: String,
        filePath: String,
    ): List<IndexedSymbol> {
        val symbols = mutableListOf<IndexedSymbol>()
        val lines = content.lines()
        var offset = 0
        var containerStack = ArrayDeque<String>()

        for ((lineIndex, line) in lines.withIndex()) {
            for (pattern in patterns) {
                val matcher = pattern.regex.findAll(line)
                for (match in matcher) {
                    val name = match.groups[pattern.nameGroup]?.value ?: continue
                    val symbolOffset = offset + (match.groups[pattern.nameGroup]?.range?.first ?: 0)

                    // Track container scope
                    if (pattern.opensScope) {
                        containerStack.addLast(name)
                    }

                    val containerName =
                        if (containerStack.isNotEmpty() && !pattern.opensScope) {
                            containerStack.lastOrNull()
                        } else if (containerStack.size > 1) {
                            containerStack.dropLast(1).lastOrNull()
                        } else {
                            null
                        }

                    val signature =
                        if (pattern.signatureGroup > 0 && pattern.signatureGroup < match.groups.size) {
                            match.groups[pattern.signatureGroup]?.value
                        } else {
                            null
                        }

                    symbols.add(
                        IndexedSymbol(
                            name = name,
                            qualifiedName = buildQualifiedName(containerStack, name, pattern.opensScope),
                            kind = pattern.kind,
                            filePath = filePath,
                            offset = symbolOffset,
                            nameLength = name.length,
                            containerName = containerName,
                            signature = signature,
                            languageId = languageId,
                        ),
                    )
                }
            }

            // Track scope closing (simplified: count braces)
            val openBraces = line.count { it == '{' }
            val closeBraces = line.count { it == '}' }
            repeat((closeBraces - openBraces).coerceAtLeast(0)) {
                if (containerStack.isNotEmpty()) {
                    containerStack.removeLast()
                }
            }

            offset += line.length + 1 // +1 for newline
        }

        return symbols
    }

    private fun buildQualifiedName(
        containerStack: ArrayDeque<String>,
        name: String,
        opensScope: Boolean,
    ): String {
        val parts =
            if (opensScope) {
                containerStack.dropLast(1) + name
            } else {
                containerStack.toList() + name
            }
        return parts.joinToString(".")
    }
}

/**
 * A pattern for extracting symbols.
 */
public data class SymbolPattern(
    /**
     * The regular expression to match.
     */
    val regex: Regex,
    /**
     * The capture group index for the symbol name (1-based).
     */
    val nameGroup: Int = 1,
    /**
     * The symbol kind.
     */
    val kind: NavigationSymbolKind,
    /**
     * Whether this symbol opens a new scope (e.g., class, namespace).
     */
    val opensScope: Boolean = false,
    /**
     * Optional capture group for signature/type info.
     */
    val signatureGroup: Int = 0,
)

/**
 * Kotlin symbol extractor using regex patterns.
 */
public class KotlinSymbolExtractor :
    RegexSymbolExtractor(
        languageId = "kotlin",
        supportedExtensions = setOf("kt", "kts"),
    ) {
    override val patterns: List<SymbolPattern> =
        listOf(
            // Package declaration
            SymbolPattern(
                regex = Regex("""^\s*package\s+([\w.]+)"""),
                kind = NavigationSymbolKind.PACKAGE,
            ),
            // Class declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?(?:abstract\s+|open\s+|sealed\s+|data\s+|inline\s+|value\s+)*class\s+(\w+)""",
                    ),
                kind = NavigationSymbolKind.CLASS,
                opensScope = true,
            ),
            // Interface declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?(?:fun\s+)?interface\s+(\w+)"""),
                kind = NavigationSymbolKind.INTERFACE,
                opensScope = true,
            ),
            // Object declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?(?:companion\s+)?object\s+(\w+)""",
                    ),
                kind = NavigationSymbolKind.OBJECT,
                opensScope = true,
            ),
            // Enum declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?enum\s+class\s+(\w+)"""),
                kind = NavigationSymbolKind.ENUM,
                opensScope = true,
            ),
            // Function declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?(?:suspend\s+|inline\s+|infix\s+|operator\s+|tailrec\s+)*fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(""",
                    ),
                kind = NavigationSymbolKind.FUNCTION,
            ),
            // Property declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|internal\s+|protected\s+)?(?:const\s+|lateinit\s+)?(?:val|var)\s+(\w+)\s*[:\=]""",
                    ),
                kind = NavigationSymbolKind.PROPERTY,
            ),
            // Type alias
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|internal\s+)?typealias\s+(\w+)\s*="""),
                kind = NavigationSymbolKind.TYPE_ALIAS,
            ),
        )
}

/**
 * TypeScript/JavaScript symbol extractor using regex patterns.
 */
public class TypeScriptSymbolExtractor :
    RegexSymbolExtractor(
        languageId = "typescript",
        supportedExtensions = setOf("ts", "tsx", "js", "jsx"),
    ) {
    override val patterns: List<SymbolPattern> =
        listOf(
            // Class declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?(?:abstract\s+)?class\s+(\w+)"""),
                kind = NavigationSymbolKind.CLASS,
                opensScope = true,
            ),
            // Interface declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?interface\s+(\w+)"""),
                kind = NavigationSymbolKind.INTERFACE,
                opensScope = true,
            ),
            // Type alias
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?type\s+(\w+)\s*="""),
                kind = NavigationSymbolKind.TYPE_ALIAS,
            ),
            // Enum declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?(?:const\s+)?enum\s+(\w+)"""),
                kind = NavigationSymbolKind.ENUM,
                opensScope = true,
            ),
            // Function declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?(?:async\s+)?function\s+(\w+)\s*[<(]"""),
                kind = NavigationSymbolKind.FUNCTION,
            ),
            // Arrow function const
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?const\s+(\w+)\s*=\s*(?:async\s+)?(?:\([^)]*\)|[^=])\s*=>"""),
                kind = NavigationSymbolKind.FUNCTION,
            ),
            // Const/let/var declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+(\w+)\s*[:\=]"""),
                kind = NavigationSymbolKind.VARIABLE,
            ),
            // Namespace
            SymbolPattern(
                regex = Regex("""^\s*(?:export\s+)?(?:declare\s+)?namespace\s+(\w+)"""),
                kind = NavigationSymbolKind.NAMESPACE,
                opensScope = true,
            ),
        )
}

/**
 * Java symbol extractor using regex patterns.
 */
public class JavaSymbolExtractor :
    RegexSymbolExtractor(
        languageId = "java",
        supportedExtensions = setOf("java"),
    ) {
    override val patterns: List<SymbolPattern> =
        listOf(
            // Package declaration
            SymbolPattern(
                regex = Regex("""^\s*package\s+([\w.]+)\s*;"""),
                kind = NavigationSymbolKind.PACKAGE,
            ),
            // Class declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|protected\s+)?(?:abstract\s+|final\s+|static\s+)*class\s+(\w+)""",
                    ),
                kind = NavigationSymbolKind.CLASS,
                opensScope = true,
            ),
            // Interface declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?interface\s+(\w+)"""),
                kind = NavigationSymbolKind.INTERFACE,
                opensScope = true,
            ),
            // Enum declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?enum\s+(\w+)"""),
                kind = NavigationSymbolKind.ENUM,
                opensScope = true,
            ),
            // Method declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+|final\s+|abstract\s+|synchronized\s+)*(?:\w+(?:<[^>]+>)?)\s+(\w+)\s*\(""",
                    ),
                kind = NavigationSymbolKind.METHOD,
            ),
            // Field declaration
            SymbolPattern(
                regex =
                    Regex(
                        """^\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+|final\s+)*(?:\w+(?:<[^>]+>)?)\s+(\w+)\s*[;=]""",
                    ),
                kind = NavigationSymbolKind.FIELD,
            ),
            // Annotation declaration
            SymbolPattern(
                regex = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?@interface\s+(\w+)"""),
                kind = NavigationSymbolKind.ANNOTATION,
                opensScope = true,
            ),
        )
}
