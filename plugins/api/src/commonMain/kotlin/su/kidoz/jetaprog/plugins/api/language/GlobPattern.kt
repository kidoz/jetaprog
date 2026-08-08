package su.kidoz.jetaprog.plugins.api.language

/**
 * A compiled glob pattern for matching file paths.
 *
 * Supported syntax:
 * - `*` matches any number of characters within a path segment
 * - `**` matches any number of characters across path segments
 * - `?` matches exactly one character within a path segment
 * - `{a,b}` matches any of the comma-separated alternatives
 *
 * All other characters match literally. Matching is case-sensitive.
 */
public class GlobPattern(
    /** The glob pattern source text. */
    public val glob: String,
) {
    private val regex: Regex = Regex(toRegexPattern(glob))

    /**
     * Returns true when the given path matches this pattern.
     */
    public fun matches(path: String): Boolean = regex.matches(path)

    private fun toRegexPattern(glob: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < glob.length) {
            index += appendToken(builder, glob, index)
        }
        return builder.toString()
    }

    private fun appendToken(
        builder: StringBuilder,
        glob: String,
        index: Int,
    ): Int =
        when (val char = glob[index]) {
            '*' -> {
                if (glob.getOrNull(index + 1) == '*') {
                    // "**/" also matches zero directories; a bare "**" matches anything.
                    if (glob.getOrNull(index + 2) == '/') {
                        builder.append("(?:.*/)?")
                        THREE_CHARS
                    } else {
                        builder.append(".*")
                        TWO_CHARS
                    }
                } else {
                    builder.append("[^/]*")
                    ONE_CHAR
                }
            }

            '?' -> {
                builder.append("[^/]")
                ONE_CHAR
            }

            '{' -> {
                appendAlternation(builder, glob, index)
            }

            else -> {
                builder.append(Regex.escape(char.toString()))
                ONE_CHAR
            }
        }

    private fun appendAlternation(
        builder: StringBuilder,
        glob: String,
        index: Int,
    ): Int {
        val end = glob.indexOf('}', startIndex = index)
        if (end < 0) {
            builder.append(Regex.escape("{"))
            return ONE_CHAR
        }
        val alternatives =
            glob
                .substring(index + 1, end)
                .split(',')
                .joinToString("|") { Regex.escape(it) }
        builder.append("(?:").append(alternatives).append(')')
        return end - index + 1
    }

    private companion object {
        const val ONE_CHAR = 1
        const val TWO_CHARS = 2
        const val THREE_CHARS = 3
    }
}
