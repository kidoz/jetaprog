package su.kidoz.jetaprog.vcs

/**
 * Parses `git log` output produced with a unit-separator (`%x1f`) delimited
 * pretty format into [GitCommit]s.
 *
 * Expected per-line format:
 * `%H<US>%h<US>%s<US>%an<US>%ae<US>%ar<US>%D`
 */
public object GitLogParser {
    private const val UNIT_SEPARATOR_CODE = 0x1F

    /** ASCII unit separator used as the field delimiter in the pretty format. */
    public val fieldSeparator: String = Char(UNIT_SEPARATOR_CODE).toString()

    /** The `--pretty=format:` string that produces parseable output. */
    public const val PRETTY_FORMAT: String =
        "%H%x1f%h%x1f%s%x1f%an%x1f%ae%x1f%ar%x1f%D"

    private const val MIN_FIELDS = 6
    private const val REFS_INDEX = 6
    private const val HEAD_PREFIX = "HEAD -> "

    /** Parses [output] into commits, skipping any malformed lines. */
    public fun parse(output: String): List<GitCommit> =
        output
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(fieldSeparator)
                if (parts.size < MIN_FIELDS) return@mapNotNull null
                GitCommit(
                    hash = parts[0],
                    shortHash = parts[1],
                    message = parts[2],
                    author = parts[3],
                    authorEmail = parts[4],
                    relativeDate = parts[5],
                    refs = parts.getOrNull(REFS_INDEX).parseRefs(),
                )
            }.toList()

    private fun String?.parseRefs(): List<String> =
        this
            ?.split(", ")
            ?.map { it.removePrefix(HEAD_PREFIX).trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}
