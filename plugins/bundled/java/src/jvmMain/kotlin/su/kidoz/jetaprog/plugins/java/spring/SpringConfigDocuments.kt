package su.kidoz.jetaprog.plugins.java.spring

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.services.TextDocument

/**
 * The dotted-key context at a cursor position in a YAML configuration document.
 */
internal data class YamlKeyContext(
    /** Dotted path of enclosing keys, e.g. `spring.datasource`; empty at the top level. */
    val parentPath: String,
    /** The (possibly partial) key segment being typed on the current line. */
    val token: String,
)

/**
 * Shared key-extraction logic for Spring configuration files
 * (`application.properties` / `application.yml`).
 */
internal object SpringConfigDocuments {
    fun isPropertiesFile(document: TextDocument): Boolean = document.fileName.endsWith(".properties")

    /**
     * The full dotted key on the given line of a properties file, or null for
     * comments and blank lines.
     */
    fun propertiesKeyAt(
        document: TextDocument,
        line: Int,
    ): String? {
        val text = document.getLine(line).trim()
        if (text.isEmpty() || text.startsWith("#") || text.startsWith("!")) return null
        return text
            .substringBefore('=')
            .substringBefore(':')
            .trim()
            .ifEmpty { null }
    }

    /**
     * The key context at the cursor in a YAML document, or null when the cursor is in
     * a value, comment, or sequence item.
     */
    fun yamlKeyContext(
        document: TextDocument,
        position: TextPosition,
    ): YamlKeyContext? {
        val line = document.getLine(position.line)
        val beforeCursor = line.substring(0, position.column.coerceIn(0, line.length))
        if (':' in beforeCursor) return null
        val token = beforeCursor.trimStart()
        if (token.startsWith("#") || token.startsWith("-")) return null
        val indent = beforeCursor.length - token.length
        return YamlKeyContext(parentPath = enclosingYamlPath(document, position.line, indent), token = token)
    }

    /**
     * The full dotted key the given YAML line defines, or null when the line is not a
     * key line.
     */
    fun yamlKeyAt(
        document: TextDocument,
        line: Int,
    ): String? {
        val text = document.getLine(line)
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-") || ':' !in trimmed) return null
        val key = trimmed.substringBefore(':').trim()
        if (key.isEmpty()) return null
        val indent = text.length - text.trimStart().length
        val parent = enclosingYamlPath(document, line, indent)
        return if (parent.isEmpty()) key else "$parent.$key"
    }

    /**
     * Walks up from [line] collecting the keys of enclosing mappings (lines with a
     * smaller indent that end in a colon).
     */
    private fun enclosingYamlPath(
        document: TextDocument,
        line: Int,
        indent: Int,
    ): String {
        val parents = mutableListOf<String>()
        var currentIndent = indent
        for (lineNumber in line - 1 downTo 0) {
            if (currentIndent == 0) break
            val candidate = document.getLine(lineNumber)
            val trimmed = candidate.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val candidateIndent = candidate.length - candidate.trimStart().length
            if (candidateIndent < currentIndent && trimmed.endsWith(":")) {
                parents.add(0, trimmed.removeSuffix(":").trim())
                currentIndent = candidateIndent
            }
        }
        return parents.joinToString(".")
    }
}
