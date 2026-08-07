package su.kidoz.jetaprog.vcs.ignore

/**
 * The parsed contents of one `.gitignore` file (or any file using the same
 * syntax, such as `.git/info/exclude`).
 */
public class GitignoreFile(
    /** The patterns in file order; the last one that matches decides. */
    public val patterns: List<GitignorePattern>,
) {
    /** Whether this file contains no usable pattern. */
    public val isEmpty: Boolean get() = patterns.isEmpty()

    /**
     * Applies the patterns to [relativePath], which must be relative to the
     * directory holding this file.
     *
     * @param isDirectory whether [relativePath] denotes a directory
     * @return true when ignored, false when explicitly re-included, and null
     *   when no pattern applies — leaving the decision to an outer file.
     */
    public fun match(
        relativePath: String,
        isDirectory: Boolean,
    ): Boolean? {
        // Last match wins, so scanning backwards lets the first hit decide.
        for (index in patterns.indices.reversed()) {
            val pattern = patterns[index]
            if (pattern.matches(relativePath, isDirectory)) return !pattern.negated
        }
        return null
    }

    public companion object {
        /** A file with no patterns; matches nothing. */
        public val EMPTY: GitignoreFile = GitignoreFile(emptyList())

        /** Parses the [content] of a `.gitignore` file. */
        public fun parse(content: String): GitignoreFile {
            val patterns =
                content
                    .split('\n')
                    .mapNotNull { line -> GitignorePattern.parse(line.removeSuffix("\r")) }
            return if (patterns.isEmpty()) EMPTY else GitignoreFile(patterns)
        }
    }
}
