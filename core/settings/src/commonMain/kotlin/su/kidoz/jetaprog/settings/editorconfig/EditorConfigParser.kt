package su.kidoz.jetaprog.settings.editorconfig

/**
 * Represents a parsed `.editorconfig` section.
 *
 * @param pattern The file glob pattern (e.g., "*.kt", "[*.{java,kt}]")
 * @param properties The key-value properties for this section
 */
public data class EditorConfigSection(
    val pattern: String,
    val properties: Map<String, String>,
)

/**
 * Represents a complete parsed `.editorconfig` file.
 *
 * @param isRoot Whether this is a root editorconfig (stops upward search)
 * @param sections The sections in the file
 */
public data class EditorConfigFile(
    val isRoot: Boolean,
    val sections: List<EditorConfigSection>,
)

/**
 * Resolved editor configuration for a specific file path.
 *
 * Contains the merged properties from all matching `.editorconfig` sections
 * in the directory hierarchy, from closest to furthest (or root).
 */
public data class ResolvedEditorConfig(
    /**
     * Indent style: "tab" or "space".
     */
    val indentStyle: String? = null,
    /**
     * Indent size (number of spaces, or "tab" to use tab_width).
     */
    val indentSize: Int? = null,
    /**
     * Tab width in columns.
     */
    val tabWidth: Int? = null,
    /**
     * Line ending style: "lf", "cr", or "crlf".
     */
    val endOfLine: String? = null,
    /**
     * File encoding (e.g., "utf-8", "latin1").
     */
    val charset: String? = null,
    /**
     * Whether to trim trailing whitespace on save.
     */
    val trimTrailingWhitespace: Boolean? = null,
    /**
     * Whether to insert a final newline at end of file.
     */
    val insertFinalNewline: Boolean? = null,
    /**
     * Maximum line length (0 = off).
     */
    val maxLineLength: Int? = null,
)

/**
 * Parses `.editorconfig` files following the EditorConfig specification.
 *
 * Supports the standard properties:
 * - `root`, `indent_style`, `indent_size`, `tab_width`
 * - `end_of_line`, `charset`, `trim_trailing_whitespace`
 * - `insert_final_newline`, `max_line_length`
 *
 * @see <a href="https://editorconfig.org">EditorConfig Specification</a>
 */
public object EditorConfigParser {
    /**
     * Parses an `.editorconfig` file content.
     *
     * @param content The raw file content
     * @return The parsed editorconfig file
     */
    public fun parse(content: String): EditorConfigFile {
        val lines = content.lines()
        var isRoot = false
        val sections = mutableListOf<EditorConfigSection>()
        var currentPattern: String? = null
        var currentProperties = mutableMapOf<String, String>()

        for (rawLine in lines) {
            val line = rawLine.trim()

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue

            // Section header
            if (line.startsWith("[") && line.endsWith("]")) {
                // Save previous section
                if (currentPattern != null) {
                    sections.add(EditorConfigSection(currentPattern, currentProperties.toMap()))
                }
                currentPattern = line.substring(1, line.length - 1)
                currentProperties = mutableMapOf()
                continue
            }

            // Key = value
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim().lowercase()
                val value = line.substring(eqIdx + 1).trim()

                if (currentPattern == null && key == "root") {
                    isRoot = value.equals("true", ignoreCase = true)
                } else if (currentPattern != null) {
                    currentProperties[key] = value
                }
            }
        }

        // Save last section
        if (currentPattern != null) {
            sections.add(EditorConfigSection(currentPattern, currentProperties.toMap()))
        }

        return EditorConfigFile(isRoot, sections)
    }

    /**
     * Resolves the editor configuration for a specific file.
     *
     * @param fileName The file name (e.g., "MyClass.kt")
     * @param configFiles List of parsed editorconfig files, ordered from closest to root
     * @return The resolved configuration with merged properties
     */
    public fun resolve(
        fileName: String,
        configFiles: List<EditorConfigFile>,
    ): ResolvedEditorConfig {
        val merged = mutableMapOf<String, String>()

        for (config in configFiles) {
            for (section in config.sections) {
                if (matchesPattern(fileName, section.pattern)) {
                    // First-seen wins (closest config takes precedence)
                    for ((key, value) in section.properties) {
                        merged.putIfAbsent(key, value)
                    }
                }
            }
            if (config.isRoot) break
        }

        return ResolvedEditorConfig(
            indentStyle = merged["indent_style"],
            indentSize = merged["indent_size"]?.toIntOrNull(),
            tabWidth = merged["tab_width"]?.toIntOrNull(),
            endOfLine = merged["end_of_line"],
            charset = merged["charset"],
            trimTrailingWhitespace = merged["trim_trailing_whitespace"]?.toBooleanStrictOrNull(),
            insertFinalNewline = merged["insert_final_newline"]?.toBooleanStrictOrNull(),
            maxLineLength = merged["max_line_length"]?.toIntOrNull(),
        )
    }

    /**
     * Simple glob matching for editorconfig patterns.
     *
     * Supports: `*` (any chars except /), `**` (any chars), `?` (single char),
     * `{a,b}` (alternatives), `[abc]` (character class).
     */
    private fun matchesPattern(
        fileName: String,
        pattern: String,
    ): Boolean {
        // Handle common patterns directly
        if (pattern == "*") return true

        // Simple extension matching: *.ext
        if (pattern.startsWith("*.") && !pattern.contains('/')) {
            val ext = pattern.substring(2)
            return fileName.endsWith(".$ext", ignoreCase = true)
        }

        // Brace expansion: *.{kt,java}
        if (pattern.contains("{") && pattern.contains("}")) {
            val prefix = pattern.substringBefore("{")
            val suffix = pattern.substringAfter("}")
            val alternatives = pattern.substringAfter("{").substringBefore("}").split(",")
            return alternatives.any { alt ->
                matchesPattern(fileName, "$prefix$alt$suffix")
            }
        }

        // Exact match
        if (pattern == fileName) return true

        // Convert to regex for complex patterns
        return try {
            val regex = globToRegex(pattern)
            regex.matches(fileName)
        } catch (_: Exception) {
            false
        }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*")
                        i++
                    } else {
                        sb.append("[^/]*")
                    }
                }

                '?' -> {
                    sb.append("[^/]")
                }

                '.' -> {
                    sb.append("\\.")
                }

                '[' -> {
                    sb.append('[')
                }

                ']' -> {
                    sb.append(']')
                }

                else -> {
                    sb.append(glob[i])
                }
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
