package su.kidoz.jetaprog.app.ui.panels

/** A single side of a parsed unified diff. */
internal enum class DiffKind { CONTEXT, ADD, DELETE }

internal data class DiffLine(
    val number: Int?,
    val sign: Char,
    val text: String,
    val kind: DiffKind,
    /** Index of the paired line on the opposite side for intraline diffing. */
    val counterpart: Int? = null,
)

/** Parses aligned rows, retaining independent line numbers and gaps on the shorter side. */
internal fun parseUnifiedDiff(diff: String): Pair<List<DiffLine>, List<DiffLine>> {
    val old = mutableListOf<DiffLine>()
    val new = mutableListOf<DiffLine>()
    val deleted = mutableListOf<DiffLine>()
    val added = mutableListOf<DiffLine>()
    val gap = DiffLine(null, ' ', "", DiffKind.CONTEXT)

    fun flushChanges() {
        repeat(maxOf(deleted.size, added.size)) { index ->
            val paired = index < deleted.size && index < added.size
            val row = old.size
            old += deleted.getOrNull(index)?.copy(counterpart = if (paired) row else null) ?: gap
            new += added.getOrNull(index)?.copy(counterpart = if (paired) row else null) ?: gap
        }
        deleted.clear()
        added.clear()
    }
    var oldNumber = 0
    var newNumber = 0
    var inHunk = false
    val header = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    for (line in diff.lineSequence()) {
        val match = header.find(line)
        if (match != null) {
            flushChanges()
            oldNumber = match.groupValues[1].toInt()
            newNumber = match.groupValues[2].toInt()
            inHunk = true
        } else if (inHunk && line.isNotEmpty()) {
            when (line[0]) {
                '-' -> {
                    deleted += DiffLine(oldNumber++, '-', line.drop(1), DiffKind.DELETE)
                }

                '+' -> {
                    added += DiffLine(newNumber++, '+', line.drop(1), DiffKind.ADD)
                }

                ' ' -> {
                    flushChanges()
                    old += DiffLine(oldNumber++, ' ', line.drop(1), DiffKind.CONTEXT)
                    new += DiffLine(newNumber++, ' ', line.drop(1), DiffKind.CONTEXT)
                }

                '\\' -> {
                    // Git's no-final-newline marker is metadata, not a source line.
                    continue
                }

                else -> {
                    flushChanges()
                    inHunk = false
                }
            }
        }
    }
    flushChanges()
    return old to new
}
