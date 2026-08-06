package su.kidoz.jetaprog.editor.completion.smart

/**
 * Infers the type expected at a completion position by reading the code around it.
 *
 * This is deliberately syntactic rather than semantic: it recognises the shapes where the
 * expected type is written down a few characters to the left, which covers the case smart
 * completion exists for — completing the right-hand side of an initialised declaration.
 *
 * Contexts that need real semantic knowledge (a function argument's parameter type, a
 * return statement's enclosing signature) yield [ExpectedTypeContext.None]; smart
 * completion then behaves like basic completion rather than guessing wrongly.
 */
public object ExpectedTypeInference {
    /** `val name: Type = ` / `var name: Type = ` */
    private val KOTLIN_DECLARATION =
        Regex("""\b(?:val|var)\s+\w+\s*:\s*([\w.]+(?:<[^<>]*>)?\??)\s*=\s*$""")

    /** `Type name = ` including `const`, pointers, references and one level of generics. */
    private val C_FAMILY_DECLARATION =
        Regex(
            """(?:^|[;{}(,])\s*(?:const\s+|constexpr\s+|static\s+|mutable\s+|volatile\s+|auto\s+)*""" +
                """([A-Za-z_][\w:]*(?:\s*<[^<>]*>)?(?:\s*[*&]+)?)\s+[*&]*\w+\s*=\s*$""",
        )

    /** Assignment to something already declared: the type is not written here. */
    private val BARE_ASSIGNMENT = Regex("""(?:^|[;{}])\s*[\w.\[\]>-]+\s*=\s*$""")

    /** Type names that carry no filtering value. */
    private val UNINFORMATIVE = setOf("auto", "var", "val", "const", "return", "decltype")

    /**
     * Infers the expected type at [offset] in [content].
     *
     * @return The inferred context, or [ExpectedTypeContext.None] when nothing reliable
     *   can be read from the surrounding text.
     */
    public fun inferAt(
        content: String,
        offset: Int,
    ): ExpectedTypeContext {
        val safe = offset.coerceIn(0, content.length)
        val statement = currentStatement(content, safe)
        if (statement.isBlank()) return ExpectedTypeContext.None

        KOTLIN_DECLARATION.find(statement)?.let { match ->
            return contextFor(match.groupValues[1])
        }
        C_FAMILY_DECLARATION.find(statement)?.let { match ->
            return contextFor(match.groupValues[1])
        }
        if (BARE_ASSIGNMENT.containsMatchIn(statement)) {
            // The variable exists but its type is not written here; resolving it needs the
            // language server, so report the context without a type.
            return ExpectedTypeContext(contextKind = TypeContextKind.Assignment)
        }

        return ExpectedTypeContext.None
    }

    /**
     * The text from the start of the current statement up to [offset].
     *
     * Statements are delimited by `;`, braces and line breaks, which is enough to keep a
     * declaration on an earlier line from being matched against a later caret.
     */
    private fun currentStatement(
        content: String,
        offset: Int,
    ): String {
        var start = offset
        while (start > 0) {
            val char = content[start - 1]
            if (char == ';' || char == '{' || char == '}' || char == '\n') break
            start--
        }
        return content.substring(start, offset)
    }

    private fun contextFor(rawType: String): ExpectedTypeContext {
        val normalised = rawType.trim().trimEnd('*', '&', ' ')
        if (normalised.isEmpty() || normalised in UNINFORMATIVE) {
            return ExpectedTypeContext(contextKind = TypeContextKind.Assignment)
        }
        return ExpectedTypeContext(
            expectedType = TypeInfo.parse(normalised),
            contextKind = TypeContextKind.Assignment,
        )
    }
}
