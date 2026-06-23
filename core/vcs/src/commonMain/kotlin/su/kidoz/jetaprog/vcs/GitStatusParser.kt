package su.kidoz.jetaprog.vcs

/**
 * Parses the output of `git status --porcelain=v1 --branch` into a
 * [GitRepositoryStatus]. Pure and platform-independent.
 */
public object GitStatusParser {
    private val aheadRegex = Regex("""ahead (\d+)""")
    private val behindRegex = Regex("""behind (\d+)""")

    /**
     * Parses porcelain v1 status [output].
     */
    public fun parse(output: String): GitRepositoryStatus {
        var branch: String? = null
        var ahead = 0
        var behind = 0
        val changes = mutableListOf<GitChange>()

        for (line in output.split('\n')) {
            if (line.isEmpty()) continue
            if (line.startsWith("## ")) {
                val header = line.removePrefix("## ")
                branch = parseBranch(header)
                ahead = aheadRegex
                    .find(header)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
                behind = behindRegex
                    .find(header)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
                continue
            }
            if (line.length < MIN_ENTRY_LENGTH) continue
            parseEntry(line, changes)
        }

        return GitRepositoryStatus(branch = branch, ahead = ahead, behind = behind, changes = changes)
    }

    private fun parseBranch(header: String): String? {
        val withoutTracking = header.substringBefore("...").substringBefore(" ")
        return when {
            withoutTracking.isEmpty() -> null
            header.startsWith("No commits yet on ") -> header.removePrefix("No commits yet on ").substringBefore(" ")
            withoutTracking == "HEAD" -> null
            else -> withoutTracking
        }
    }

    private fun parseEntry(
        line: String,
        changes: MutableList<GitChange>,
    ) {
        val index = line[0]
        val workTree = line[1]
        val rawPath = line.substring(MIN_ENTRY_LENGTH)
        val path = rawPath.substringAfter(" -> ", rawPath).trim()

        if (index == '?' && workTree == '?') {
            changes += GitChange(path, GitChangeType.UNTRACKED, staged = false)
            return
        }
        if (index != ' ') {
            changes += GitChange(path, changeType(index), staged = true)
        }
        if (workTree != ' ') {
            changes += GitChange(path, changeType(workTree), staged = false)
        }
    }

    private fun changeType(code: Char): GitChangeType =
        when (code) {
            'A' -> GitChangeType.ADDED
            'M' -> GitChangeType.MODIFIED
            'D' -> GitChangeType.DELETED
            'R' -> GitChangeType.RENAMED
            'C' -> GitChangeType.COPIED
            'U' -> GitChangeType.CONFLICTED
            else -> GitChangeType.UNKNOWN
        }

    private const val MIN_ENTRY_LENGTH = 3
}
