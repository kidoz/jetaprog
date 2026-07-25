package su.kidoz.jetaprog.app.quickfix

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.quickfix.QuickFix
import su.kidoz.jetaprog.editor.quickfix.QuickFixProvider
import su.kidoz.jetaprog.editor.quickfix.TextReplacement
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex

/**
 * Computes quick fixes for the caret position in a Kotlin file.
 *
 * Currently offers imports for unresolved names, resolved from the project
 * symbol index. Fixes are returned as plain document edits so the caller can
 * apply them through the editor's normal content update — which keeps them
 * undoable.
 */
public class KotlinQuickFixService(
    private val symbolIndex: KotlinSymbolIndex,
) : QuickFixProvider {
    /**
     * Returns the fixes available at [position] in [content].
     *
     * [content] is the live editor buffer, so fixes stay correct for unsaved edits.
     */
    override suspend fun quickFixes(
        filePath: String,
        content: String,
        position: TextPosition,
    ): List<QuickFix> {
        if (!isKotlinFile(filePath)) return emptyList()
        val identifier = identifierAt(content, position) ?: return emptyList()
        return importFixes(filePath, content, identifier)
    }

    /**
     * Offers an import for each project declaration matching [identifier] that
     * the file cannot already see.
     */
    private suspend fun importFixes(
        filePath: String,
        content: String,
        identifier: String,
    ): List<QuickFix> {
        val lines = content.lines()
        val currentPackage = packageOf(lines)
        val existingImports = importsIn(lines)

        val candidates =
            symbolIndex
                .findByName(identifier)
                // Only top-level declarations can be imported by simple name.
                .filter { it.parent == null }
                .filter { it.filePath != filePath }
                .map { it.fqName }
                .filter { fqName -> fqName.substringBeforeLast('.', "") != currentPackage }
                .filterNot { fqName -> isImported(fqName, existingImports) }
                .distinct()
                .sorted()

        if (candidates.isEmpty()) return emptyList()

        return candidates.map { fqName ->
            QuickFix(
                title = "Import $fqName",
                edits = listOf(importEdit(content, lines, fqName)),
            )
        }
    }

    /**
     * Builds the edit that inserts `import [fqName]`, keeping the import block
     * in lexicographic order so the result satisfies the project's ktlint rules.
     */
    private fun importEdit(
        content: String,
        lines: List<String>,
        fqName: String,
    ): TextReplacement {
        val statement = "import $fqName"
        val importLines = lines.withIndex().filter { (_, line) -> line.trimStart().startsWith("import ") }

        if (importLines.isNotEmpty()) {
            val successor = importLines.firstOrNull { (_, line) -> line.trim() > statement }
            val anchorLine = successor?.index ?: (importLines.last().index + 1)
            val offset = offsetOfLineStart(content, anchorLine)
            return TextReplacement(offset, offset, "$statement\n")
        }

        val packageLine = lines.indexOfFirst { it.trimStart().startsWith("package ") }
        if (packageLine >= 0) {
            // Insert after the package declaration, separated by a blank line.
            val offset = offsetOfLineStart(content, packageLine + 1)
            return TextReplacement(offset, offset, "\n$statement\n")
        }

        return TextReplacement(0, 0, "$statement\n\n")
    }

    private fun isImported(
        fqName: String,
        existingImports: List<String>,
    ): Boolean =
        existingImports.any { imported ->
            imported == fqName ||
                (imported.endsWith(".*") && fqName.substringBeforeLast('.', "") == imported.dropLast(2))
        }

    private fun packageOf(lines: List<String>): String =
        lines
            .firstOrNull { it.trimStart().startsWith("package ") }
            ?.trim()
            ?.removePrefix("package ")
            ?.trim()
            .orEmpty()

    private fun importsIn(lines: List<String>): List<String> =
        lines
            .filter { it.trimStart().startsWith("import ") }
            .map { it.trim().removePrefix("import ").trim() }

    /** Character offset where [line] begins; the end of content when past the last line. */
    private fun offsetOfLineStart(
        content: String,
        line: Int,
    ): Int {
        val lines = content.lines()
        if (line >= lines.size) return content.length
        return lines.take(line).sumOf { it.length + 1 }.coerceAtMost(content.length)
    }

    private fun isKotlinFile(filePath: String): Boolean = filePath.endsWith(".kt") || filePath.endsWith(".kts")

    private fun identifierAt(
        content: String,
        position: TextPosition,
    ): String? {
        val line = content.lines().getOrNull(position.line) ?: return null
        if (position.column > line.length) return null

        var start = position.column.coerceAtMost(line.length)
        var end = start
        while (start > 0 && line[start - 1].isIdentifierChar()) start--
        while (end < line.length && line[end].isIdentifierChar()) end++
        return line.substring(start, end).takeIf { it.isNotEmpty() }
    }

    private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'
}
