package su.kidoz.jetaprog.editor.folding

import su.kidoz.jetaprog.editor.document.LanguageId

/** A complete dependency block; offsets exclude its final line ending and lines are zero-based. */
public data class DependencyFold(
    val start: Int,
    val end: Int,
    val startLine: Int,
    val endLine: Int,
    val label: String,
    val declarationCount: Int,
    /** Stable within the document when lines are inserted before a block. */
    val key: String,
)

/** Conservatively identifies static import/include blocks in the declaration header of a file. */
public object DependencyFolding {
    /** Finds blocks spanning at least two lines, preserving comments and conditional boundaries. */
    public fun detect(
        content: String,
        language: LanguageId,
    ): List<DependencyFold> {
        val id = language.value.lowercase()
        if (id !in supportedLanguages) return emptyList()
        val lines = content.split('\n')
        val offsets = IntArray(lines.size)
        for (index in 1 until lines.size) offsets[index] = offsets[index - 1] + lines[index - 1].length + 1
        val result = mutableListOf<DependencyFold>()
        var start = -1
        var end = -1
        var count = 0
        var label = "imports"

        fun flush() {
            if (start >= 0 && end > start) {
                result +=
                    DependencyFold(
                        offsets[start],
                        offsets[end] + lines[end].trimEnd('\r').length,
                        start,
                        end,
                        label,
                        count,
                        "$label:${result.size}",
                    )
            }
            start = -1
            count = 0
        }
        var index = 0
        var commentDepth = 0
        var docQuote: String? = null
        while (index < lines.size) {
            val line = lines[index].trim().removePrefix("\uFEFF")
            if (docQuote != null) {
                if (line.contains(docQuote)) docQuote = null
                flush()
                index++
                continue
            }
            if (commentDepth > 0 || line.startsWith("/*")) {
                commentDepth += Regex("/\\*").findAll(line).count() - Regex("\\*/").findAll(line).count()
                commentDepth = commentDepth.coerceAtLeast(0)
                flush()
                index++
                continue
            }
            if (id == "python" && (line.startsWith("\"\"\"") || line.startsWith("'''"))) {
                val quote = line.take(3)
                if (!line.drop(3).contains(quote)) docQuote = quote
                flush()
                index++
                continue
            }
            val kind = dependencyKind(line, id)
            if (kind != null) {
                val last = declarationEnd(lines, index, id) ?: break
                if (start >= 0 && label != kind) flush()
                if (start < 0) {
                    start = index
                    label = kind
                }
                end = last
                count++
                index = last + 1
            } else if (line.isEmpty()) {
                index++
            } else {
                flush()
                if (!isHeaderLine(line, id)) break
                index++
            }
        }
        flush()
        return result
    }

    private fun dependencyKind(
        line: String,
        language: String,
    ): String? =
        when {
            language in setOf("c", "cpp", "objective-c", "objective-cpp") &&
                Regex("^#\\s*(include|import)\\s*[<\"]").containsMatchIn(line) -> "includes"

            language == "python" && (
                line.startsWith(
                    "import ",
                ) || Regex("^from\\s+.+\\s+import\\b").containsMatchIn(line)
            ) -> "imports"

            language == "rust" && line.startsWith("use ") -> "imports"

            language == "csharp" && Regex("^(global\\s+)?using\\s+(?!var\\b|static\\s+new\\b)").containsMatchIn(line) &&
                !line.contains('(') -> "imports"

            language !in setOf("c", "cpp", "python", "rust", "csharp") &&
                Regex("^import(?:\\s+|[\"'])").containsMatchIn(line) &&
                (language == "go" || !line.substringAfter("import").trimStart().startsWith('(')) -> "imports"

            else -> null
        }

    private fun isHeaderLine(
        line: String,
        language: String,
    ): Boolean =
        line.startsWith("//") || line.startsWith("#!") || line.startsWith("package ") ||
            line.startsWith("@file:") ||
            (language == "python" && line.startsWith('#')) ||
            (language == "csharp" && (line.startsWith("namespace ") && line.endsWith(';'))) ||
            (language in setOf("c", "cpp", "objective-c", "objective-cpp") && line.startsWith('#'))

    private fun declarationEnd(
        lines: List<String>,
        start: Int,
        language: String,
    ): Int? {
        var depth = 0
        for (index in start until lines.size) {
            val line =
                if (language ==
                    "python"
                ) {
                    lines[index].substringBefore('#')
                } else {
                    lines[index].substringBefore("//")
                }
            if (line.contains("/*") && !line.substringAfter("/*").contains("*/")) return null
            var quote: Char? = null
            var escaped = false
            val code =
                buildString {
                    for (char in line) {
                        if (escaped) {
                            escaped = false
                            continue
                        }
                        if (quote != null) {
                            if (char == '\\') {
                                escaped = true
                            } else if (char == quote) {
                                quote = null
                            }
                        } else if (char == '\'' || char == '"') {
                            quote = char
                        } else {
                            append(char)
                        }
                    }
                }
            // Never hide another statement sharing an import's source line.
            if (Regex(";\\s*\\S").containsMatchIn(code)) return null
            depth += code.count { it in "({[" } - code.count { it in ")}]" }
            if (depth < 0) return null
            if (depth == 0 && !line.trimEnd().endsWith('\\') && !line.trimEnd().endsWith(',')) return index
        }
        return null
    }

    private val supportedLanguages =
        setOf(
            "kotlin",
            "java",
            "javascript",
            "typescript",
            "javascriptreact",
            "typescriptreact",
            "python",
            "go",
            "swift",
            "rust",
            "csharp",
            "c",
            "cpp",
            "objective-c",
            "objective-cpp",
        )
}
