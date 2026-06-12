package su.kidoz.jetaprog.common.completion

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Represents an import statement that should be added when a completion item is applied.
 *
 * @param importStatement The full import statement (e.g., "import kotlin.collections.List")
 * @param packageName The package to import from
 * @param symbolName The symbol being imported
 */
public data class AutoImportData(
    val importStatement: String,
    val packageName: String,
    val symbolName: String,
)

/**
 * Service for resolving and applying auto-import when completion items are selected.
 *
 * When a user selects a completion item from a non-imported package, this service
 * determines the appropriate import statement and applies it to the document.
 */
public interface AutoImportResolver {
    /**
     * Resolves import data for a completion item.
     *
     * @param item The completion item that was selected
     * @param languageId The language of the document
     * @return The auto-import data, or null if no import is needed
     */
    public fun resolveImport(
        item: CompletionItem,
        languageId: String,
    ): AutoImportData?

    /**
     * Checks if the given import already exists in the document.
     *
     * @param content The document content
     * @param importData The import to check
     * @return true if the import already exists
     */
    public fun isAlreadyImported(
        content: String,
        importData: AutoImportData,
    ): Boolean

    /**
     * Finds the position where a new import should be inserted.
     *
     * @param content The document content
     * @param languageId The language ID
     * @return The line number where the import should be inserted
     */
    public fun findImportInsertPosition(
        content: String,
        languageId: String,
    ): Int
}

/**
 * Default auto-import resolver supporting Kotlin, Java, Python, and Rust.
 */
public class DefaultAutoImportResolver : AutoImportResolver {
    override fun resolveImport(
        item: CompletionItem,
        languageId: String,
    ): AutoImportData? {
        val containerType = item.containerTypeName ?: return null

        // Only auto-import for types that come from external packages
        if (containerType.isEmpty() || !containerType.contains('.')) return null

        val packageName = containerType.substringBeforeLast('.')
        val importStatement =
            when (languageId) {
                "kotlin", "java" -> "import $containerType.${item.label}"
                "python" -> "from $packageName import ${item.label}"
                "rust" -> "use ${containerType.replace('.', ':')}::${item.label};"
                else -> return null
            }

        return AutoImportData(
            importStatement = importStatement,
            packageName = packageName,
            symbolName = item.label,
        )
    }

    override fun isAlreadyImported(
        content: String,
        importData: AutoImportData,
    ): Boolean = content.lines().any { it.trim() == importData.importStatement }

    override fun findImportInsertPosition(
        content: String,
        languageId: String,
    ): Int {
        val lines = content.lines()
        var lastImportLine = -1
        var packageLine = -1

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            when (languageId) {
                "kotlin", "java" -> {
                    if (trimmed.startsWith("package ")) packageLine = index
                    if (trimmed.startsWith("import ")) lastImportLine = index
                }

                "python" -> {
                    if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                        lastImportLine = index
                    }
                }

                "rust" -> {
                    if (trimmed.startsWith("use ")) lastImportLine = index
                }
            }
        }

        return when {
            lastImportLine >= 0 -> lastImportLine + 1
            packageLine >= 0 -> packageLine + 2
            else -> 0
        }
    }
}

/**
 * [InsertHandler] that automatically adds import statements when completing
 * items from non-imported packages.
 *
 * @param resolver The auto-import resolver to use
 */
public class AutoImportInsertHandler(
    private val resolver: AutoImportResolver = DefaultAutoImportResolver(),
) : InsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: CompletionItem,
    ) {
        val importData = resolver.resolveImport(item, context.languageId) ?: return

        if (resolver.isAlreadyImported(context.documentContent, importData)) return

        val insertLine = resolver.findImportInsertPosition(context.documentContent, context.languageId)
        context.insertTextAt(TextPosition(insertLine, 0), "${importData.importStatement}\n")
    }
}
