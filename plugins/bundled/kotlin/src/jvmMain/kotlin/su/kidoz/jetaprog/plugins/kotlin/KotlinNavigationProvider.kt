package su.kidoz.jetaprog.plugins.kotlin

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Provides navigation features for Kotlin code.
 */
public class KotlinNavigationProvider(
    private val symbolIndex: KotlinSymbolIndex,
) {
    /**
     * Finds the definition of the symbol at the given position.
     *
     * @param filePath The file containing the reference.
     * @param position The position in the file.
     * @param fileContent The content of the file (for extracting the identifier).
     * @return The location of the definition, or null if not found.
     */
    public suspend fun goToDefinition(
        filePath: String,
        position: TextPosition,
        fileContent: String,
    ): SymbolLocation? {
        // Extract the identifier at the position
        val identifier = extractIdentifierAt(fileContent, position) ?: return null

        // First, check if cursor is on a declaration itself
        val symbolAtPosition = symbolIndex.getSymbolAt(filePath, position)
        if (symbolAtPosition != null && symbolAtPosition.name == identifier) {
            // Already on declaration, return it
            return SymbolLocation(
                filePath = symbolAtPosition.filePath,
                position = symbolAtPosition.nameRange.start,
                range = symbolAtPosition.nameRange,
            )
        }

        // Search for the symbol by name
        val candidates = symbolIndex.findByName(identifier)
        if (candidates.isEmpty()) return null

        // If there's only one match, return it
        if (candidates.size == 1) {
            return SymbolLocation(
                filePath = candidates.first().filePath,
                position = candidates.first().nameRange.start,
                range = candidates.first().nameRange,
            )
        }

        // Multiple matches - try to find the best one
        // Prefer symbols from the same file
        val sameFile = candidates.filter { it.filePath == filePath }
        if (sameFile.size == 1) {
            return SymbolLocation(
                filePath = sameFile.first().filePath,
                position = sameFile.first().nameRange.start,
                range = sameFile.first().nameRange,
            )
        }

        // Return the first match (could be improved with type analysis)
        return SymbolLocation(
            filePath = candidates.first().filePath,
            position = candidates.first().nameRange.start,
            range = candidates.first().nameRange,
        )
    }

    /**
     * Finds all references to a symbol.
     *
     * @param filePath The file containing the symbol.
     * @param position The position of the symbol.
     * @param projectRoot The project root to search in.
     * @return List of locations where the symbol is referenced.
     */
    public suspend fun findReferences(
        filePath: String,
        position: TextPosition,
        projectRoot: String,
    ): List<SymbolLocation> {
        // Get the symbol at position
        val symbol = symbolIndex.getSymbolAt(filePath, position) ?: return emptyList()

        // This is a simplified implementation - for real references,
        // we would need to parse all files and find usages
        val references = mutableListOf<SymbolLocation>()

        // Include the declaration itself
        references.add(
            SymbolLocation(
                filePath = symbol.filePath,
                position = symbol.nameRange.start,
                range = symbol.nameRange,
            ),
        )

        return references
    }

    /**
     * Gets the file outline (all symbols in a file).
     */
    public suspend fun getFileOutline(filePath: String): List<KotlinSymbol> =
        symbolIndex
            .getFileSymbols(filePath)
            .sortedBy { it.range.start.line }

    /**
     * Searches for symbols matching a query.
     */
    public suspend fun searchSymbols(
        query: String,
        limit: Int = 50,
    ): List<KotlinSymbol> = symbolIndex.search(query, limit)

    private fun extractIdentifierAt(
        content: String,
        position: TextPosition,
    ): String? {
        val lines = content.lines()
        if (position.line >= lines.size) return null

        val line = lines[position.line]
        if (position.column >= line.length) return null

        // Find identifier boundaries
        var start = position.column
        var end = position.column

        // Move start to beginning of identifier
        while (start > 0 && isIdentifierChar(line[start - 1])) {
            start--
        }

        // Move end to end of identifier
        while (end < line.length && isIdentifierChar(line[end])) {
            end++
        }

        if (start == end) return null

        return line.substring(start, end)
    }

    private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
