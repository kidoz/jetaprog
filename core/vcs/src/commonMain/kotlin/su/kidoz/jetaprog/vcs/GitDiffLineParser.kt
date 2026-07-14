package su.kidoz.jetaprog.vcs

/**
 * Parses `git diff -U0` output into per-line change markers.
 */
public object GitDiffLineParser {
    private val hunkHeader = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

    /**
     * Extracts per-line changes from a zero-context unified diff of a single file.
     *
     * Line indices in the result are 0-based positions in the new (current)
     * file contents.
     *
     * @param diffOutput Output of `git diff -U0 -- <path>`.
     * @return The per-line changes, ordered as they appear in the diff.
     */
    public fun parse(diffOutput: String): List<GitLineChange> {
        val changes = mutableListOf<GitLineChange>()

        for (line in diffOutput.lineSequence()) {
            val match = hunkHeader.find(line) ?: continue
            val oldCount = match.groupValues[2].toCountOrDefault()
            val newStart = match.groupValues[3].toInt()
            val newCount = match.groupValues[4].toCountOrDefault()

            when {
                newCount == 0 -> {
                    // Pure deletion: newStart is the line before the removed
                    // content (0 when removed from the top of the file).
                    changes.add(GitLineChange(line = (newStart - 1).coerceAtLeast(0), type = GitLineChangeType.DELETED))
                }

                oldCount == 0 -> {
                    repeat(newCount) { offset ->
                        changes.add(GitLineChange(line = newStart - 1 + offset, type = GitLineChangeType.ADDED))
                    }
                }

                else -> {
                    repeat(newCount) { offset ->
                        changes.add(GitLineChange(line = newStart - 1 + offset, type = GitLineChangeType.MODIFIED))
                    }
                }
            }
        }

        return changes
    }

    private fun String.toCountOrDefault(): Int = if (isEmpty()) 1 else toInt()
}
