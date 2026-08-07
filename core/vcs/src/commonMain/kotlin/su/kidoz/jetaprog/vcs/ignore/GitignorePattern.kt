package su.kidoz.jetaprog.vcs.ignore

/**
 * A single compiled pattern line from a `.gitignore` file.
 *
 * Paths are matched relative to the directory holding the `.gitignore` file,
 * with `/` separators and no leading or trailing slash. A pattern that matches
 * a directory also matches everything below it, which is why [matches] answers
 * true for descendants of a matched path.
 */
public class GitignorePattern private constructor(
    /** The pattern as written in the file, minus the trailing whitespace git strips. */
    public val source: String,
    /** Whether a match re-includes the path (`!pattern`) instead of ignoring it. */
    public val negated: Boolean,
    /** Whether the pattern only matches directories (`pattern/`). */
    public val directoryOnly: Boolean,
    private val self: Regex,
    private val descendants: Regex,
) {
    /**
     * Returns true when this pattern matches [relativePath], either directly or
     * because it matches one of its ancestors.
     *
     * @param relativePath path relative to the pattern's base directory
     * @param isDirectory whether [relativePath] denotes a directory
     */
    public fun matches(
        relativePath: String,
        isDirectory: Boolean,
    ): Boolean =
        (self.matches(relativePath) && (isDirectory || !directoryOnly)) ||
            descendants.matches(relativePath)

    override fun toString(): String = source

    public companion object {
        /**
         * Parses one `.gitignore` line.
         *
         * @return the compiled pattern, or null for blank lines, comments and
         *   patterns whose glob syntax is unsupported.
         */
        public fun parse(line: String): GitignorePattern? {
            val source = stripTrailingWhitespace(line)
            if (source.isEmpty() || source.startsWith("#")) return null

            val negated = source.startsWith("!")
            var body = if (negated) source.substring(1) else source
            // Only the first '#' or '!' of a pattern may be backslash-escaped.
            if (body.startsWith("\\#") || body.startsWith("\\!")) body = body.substring(1)

            val directoryOnly = body.endsWith("/")
            if (directoryOnly) body = body.dropLast(1)
            // A pattern is anchored to its own directory as soon as it contains a
            // separator anywhere but at the end; otherwise it matches at any depth.
            val anchored = body.contains('/')
            body = body.removePrefix("/")
            if (body.isEmpty()) return null

            val prefix = if (anchored) "" else "(?:.*/)?"
            val glob = translateGlob(body) ?: return null
            return runCatching {
                GitignorePattern(
                    source = source,
                    negated = negated,
                    directoryOnly = directoryOnly,
                    self = Regex(prefix + glob),
                    descendants = Regex("$prefix$glob/.*"),
                )
            }.getOrNull()
        }

        /** Git strips trailing spaces unless the last one is escaped with a backslash. */
        private fun stripTrailingWhitespace(line: String): String {
            var end = line.length
            while (end > 0 && (line[end - 1] == ' ' || line[end - 1] == '\t')) {
                // An odd number of preceding backslashes escapes this space.
                var backslashes = 0
                while (end - 1 - backslashes > 0 && line[end - 2 - backslashes] == '\\') backslashes++
                if (backslashes % 2 == 1) break
                end--
            }
            return line.substring(0, end)
        }

        /**
         * Translates a gitignore glob into a regular expression body.
         *
         * @return null when the glob uses syntax this translator does not support.
         */
        private fun translateGlob(glob: String): String? {
            val regex = StringBuilder()
            var index = 0
            while (index < glob.length) {
                val rest = glob.length - index
                when {
                    // A `**` between separators matches zero or more directories.
                    glob.startsWith("**/", index) && (index == 0 || glob[index - 1] == '/') -> {
                        regex.append("(?:.*/)?")
                        index += 3
                    }

                    // A trailing `/**` matches everything inside the directory.
                    glob.startsWith("/**", index) && rest == 3 -> {
                        regex.append("/.*")
                        index += 3
                    }

                    glob[index] == '*' -> {
                        regex.append("[^/]*")
                        index++
                    }

                    glob[index] == '?' -> {
                        regex.append("[^/]")
                        index++
                    }

                    glob[index] == '[' -> {
                        val consumed = appendCharacterClass(glob, index, regex) ?: return null
                        index += consumed
                    }

                    glob[index] == '\\' && rest > 1 -> {
                        regex.appendEscaped(glob[index + 1])
                        index += 2
                    }

                    else -> {
                        regex.appendEscaped(glob[index])
                        index++
                    }
                }
            }
            return regex.toString()
        }

        /**
         * Appends the bracket expression starting at [start] to [regex].
         *
         * @return the number of characters consumed, or null when the class is
         *   unterminated or uses an unsupported POSIX class.
         */
        private fun appendCharacterClass(
            glob: String,
            start: Int,
            regex: StringBuilder,
        ): Int? {
            if (glob.startsWith("[[:", start)) return null
            val body = StringBuilder()
            var index = start + 1
            var negatedClass = false
            if (index < glob.length && (glob[index] == '!' || glob[index] == '^')) {
                negatedClass = true
                index++
            }
            // A ']' in the first position is a literal, not the terminator.
            if (index < glob.length && glob[index] == ']') {
                body.append("\\]")
                index++
            }
            while (index < glob.length && glob[index] != ']') {
                val char = glob[index]
                if (char == '\\' && index + 1 < glob.length) {
                    body.append('\\').append(glob[index + 1])
                    index += 2
                    continue
                }
                if (char == '[' || char == '&') body.append('\\')
                body.append(char)
                index++
            }
            if (index >= glob.length) return null
            if (body.isEmpty()) return null
            regex
                .append('[')
                .append(if (negatedClass) "^" else "")
                .append(body)
                .append(']')
            return index + 1 - start
        }

        /** Appends [char] as a literal, escaping it when it is a regex metacharacter. */
        private fun StringBuilder.appendEscaped(char: Char) {
            if (char in REGEX_METACHARACTERS) append('\\')
            append(char)
        }

        private const val REGEX_METACHARACTERS = "\\.^$|?*+()[]{}"
    }
}
