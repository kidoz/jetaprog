package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind

/**
 * Symbol extractor for Prolog sources (`.pl`, and DotProlog `.dpli` contracts).
 *
 * Prolog has no declarations in the usual sense: a predicate is defined by the
 * set of clauses whose heads share a name and arity. Each predicate is indexed
 * once, at its first clause, with the arity in the signature (`parent/2`, or
 * `greeting//0` for DCG rules). A `:- module(Name, ...)` directive becomes a
 * module symbol that contains the file's predicates.
 *
 * Clause heads are recognized at column 0 only, which is the universal layout
 * convention and keeps indented body goals out of the index.
 */
public class PrologSymbolExtractor : SymbolExtractor {
    override val languageId: String = "dotprolog"
    override val supportedExtensions: Set<String> = setOf("pl", "dpli")

    override fun extractSymbols(
        content: String,
        filePath: String,
    ): List<IndexedSymbol> {
        val symbols = mutableListOf<IndexedSymbol>()
        val seen = mutableSetOf<String>()
        var module: String? = null
        var offset = 0
        var inBlockComment = false

        for ((lineIndex, line) in content.split('\n').withIndex()) {
            val lineOffset = offset
            offset += line.length + 1

            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                continue
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/")
                continue
            }
            if (line.isBlank() || line.startsWith("%")) continue

            MODULE_DIRECTIVE.find(line)?.let { match ->
                val nameGroup = match.groups[1] ?: return@let
                val name = nameGroup.value.trim('\'')
                module = name
                symbols +=
                    IndexedSymbol(
                        name = name,
                        qualifiedName = name,
                        kind = NavigationSymbolKind.MODULE,
                        filePath = filePath,
                        offset = lineOffset + nameGroup.range.first,
                        nameLength = nameGroup.value.length,
                        line = lineIndex,
                        column = nameGroup.range.first,
                        languageId = languageId,
                    )
                return@let
            }
            if (line.startsWith(":-")) continue

            val head = CLAUSE_HEAD.find(line) ?: continue
            val nameGroup = head.groups[1] ?: continue
            val rawName = nameGroup.value
            val name = rawName.trim('\'')
            val afterName = line.substring(nameGroup.range.last + 1).trimStart()
            val (arity, rest) =
                if (afterName.startsWith("(")) {
                    val (count, endIndex) = countArguments(afterName)
                    count to afterName.substring(endIndex).trimStart()
                } else {
                    0 to afterName
                }
            if (!isClauseTail(rest)) continue

            val isDcg = rest.startsWith("-->")
            val signature = if (isDcg) "$name//$arity" else "$name/$arity"
            if (!seen.add(signature)) continue

            symbols +=
                IndexedSymbol(
                    name = name,
                    qualifiedName = listOfNotNull(module, signature).joinToString("."),
                    kind = NavigationSymbolKind.FUNCTION,
                    filePath = filePath,
                    offset = lineOffset + nameGroup.range.first,
                    nameLength = rawName.length,
                    line = lineIndex,
                    column = nameGroup.range.first,
                    containerName = module,
                    signature = signature,
                    languageId = languageId,
                )
        }
        return symbols
    }

    /**
     * Counts top-level arguments of the parenthesized list starting at [text]'s
     * first character, returning the count and the index just past the closing
     * parenthesis. A head whose arguments continue on the next line is counted
     * as far as this line goes.
     */
    private fun countArguments(text: String): Pair<Int, Int> {
        var depth = 0
        var count = 0
        var sawArgument = false
        var quote: Char? = null
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quote != null -> {
                    if (char == '\\') {
                        index++
                    } else if (char == quote) {
                        quote = null
                    }
                }

                char == '\'' || char == '"' -> {
                    quote = char
                }

                char in "([{" -> {
                    depth++
                }

                char in ")]}" -> {
                    depth--
                    if (depth == 0) return (if (sawArgument) count + 1 else 0) to index + 1
                }

                char == ',' && depth == 1 -> {
                    count++
                }

                !char.isWhitespace() && depth == 1 -> {
                    sawArgument = true
                }
            }
            index++
        }
        return (if (sawArgument) count + 1 else 0) to text.length
    }

    /** What may follow a clause head: a body, a DCG body, the terminating dot, or a line break. */
    private fun isClauseTail(rest: String): Boolean =
        rest.isEmpty() || rest.startsWith(":-") || rest.startsWith("-->") || rest.startsWith(".")

    private companion object {
        val MODULE_DIRECTIVE = Regex("""^:-\s*module\(\s*([a-z][A-Za-z0-9_]*|'(?:[^'\\]|\\.)*')""")
        val CLAUSE_HEAD = Regex("""^([a-z][A-Za-z0-9_]*|'(?:[^'\\]|\\.)*')""")
    }
}
