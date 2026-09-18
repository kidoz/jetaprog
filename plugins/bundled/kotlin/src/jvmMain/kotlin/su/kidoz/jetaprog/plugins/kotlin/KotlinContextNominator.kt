package su.kidoz.jetaprog.plugins.kotlin

/**
 * Nominates project source files that semantic analysis of one file should see.
 *
 * The Gradle classpath holds jars only, so a file analyzed on its own cannot
 * resolve anything declared in a sibling source file. This picks, from the
 * symbol index, the files declaring the identifiers the analyzed text mentions,
 * ranked by how many of those identifiers each file supplies.
 */
public class KotlinContextNominator(
    private val symbolIndex: KotlinSymbolIndex,
) {
    /**
     * Files (other than [filePath]) declaring identifiers used in [content],
     * most relevant first, at most [limit].
     */
    public suspend fun nominate(
        content: String,
        filePath: String,
        limit: Int,
    ): List<String> {
        val hits = mutableMapOf<String, Int>()
        for (identifier in identifiers(content)) {
            symbolIndex
                .findByName(identifier)
                .map { it.filePath }
                .distinct()
                .filter { it != filePath }
                .forEach { path -> hits[path] = (hits[path] ?: 0) + 1 }
        }
        return hits.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    /** Whether the index knows a declaration named [name] anywhere in the project. */
    public suspend fun declares(name: String): Boolean = symbolIndex.findByName(name).isNotEmpty()

    private fun identifiers(content: String): Set<String> =
        IDENTIFIER
            .findAll(content)
            .map { it.value }
            .filter { it.length >= MIN_IDENTIFIER_LENGTH && it !in KEYWORDS }
            .toSet()

    private companion object {
        val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
        const val MIN_IDENTIFIER_LENGTH = 2

        val KEYWORDS =
            setOf(
                "as",
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
                "interface",
                "is",
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
                "it",
            )
    }
}
