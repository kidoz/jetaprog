package su.kidoz.jetaprog.plugins.kotlin.lint

/** An import whose name is never mentioned in the rest of the file. */
public data class UnusedImport(
    /** Fully qualified name being imported. */
    val fqName: String,
    /** Zero-based line the import sits on. */
    val line: Int,
    /** Inclusive start offset of the import line. */
    val startOffset: Int,
    /** Exclusive end offset, including the trailing newline so removal leaves no blank line. */
    val endOffset: Int,
)

/**
 * Finds imports that appear unused in a Kotlin file.
 *
 * Detection is deliberately conservative — an import counts as used when its
 * simple name appears anywhere outside the import block, including inside
 * comments and strings. That yields false negatives rather than offering to
 * delete an import the file actually needs.
 *
 * Star imports are never reported, since what they bring in cannot be seen from
 * the file alone.
 */
public object KotlinUnusedImports {
    /** Returns the unused imports of [content], in file order. */
    public fun find(content: String): List<UnusedImport> {
        val lines = content.lines()
        val importLines = lines.withIndex().filter { (_, line) -> line.trimStart().startsWith("import ") }
        if (importLines.isEmpty()) return emptyList()

        val body =
            lines
                .filterIndexed { index, _ -> importLines.none { it.index == index } }
                .joinToString("\n")

        return importLines.mapNotNull { (index, rawLine) ->
            val statement = rawLine.trim().removePrefix("import ").trim()
            if (statement.endsWith(".*")) return@mapNotNull null

            // `import a.b.C as D` binds the alias, not the original name.
            val visibleName = statement.substringAfterLast(" as ").substringAfterLast('.')
            if (visibleName.isEmpty() || mentions(body, visibleName)) return@mapNotNull null

            val start = lines.take(index).sumOf { it.length + 1 }
            UnusedImport(
                fqName = statement.substringBefore(" as ").trim(),
                line = index,
                startOffset = start,
                // Consume the newline so removing the import does not leave a gap.
                endOffset = (start + rawLine.length + 1).coerceAtMost(content.length),
            )
        }
    }

    private fun mentions(
        body: String,
        name: String,
    ): Boolean = Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(body)
}
