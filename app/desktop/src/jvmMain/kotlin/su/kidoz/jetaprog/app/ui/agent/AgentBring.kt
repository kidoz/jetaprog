package su.kidoz.jetaprog.app.ui.agent

/** Slash name of the command that copies a worktree back into the main checkout. */
internal const val BRING_COMMAND: String = "bring"

/** Menu text for the built-in bring command. */
internal const val BRING_COMMAND_DESCRIPTION: String = "Bring worktree changes into the main checkout"

/** Shown when `/bring` is used in the main checkout. */
internal const val BRING_FROM_WORKTREE_NOTICE: String = "Bring changes back from the worktree chat."

/** Shown when `/bring` is used while the worktree turn is running. */
internal const val BRING_BUSY_NOTICE: String = "Finish the running task before bringing changes back."

/** Shown when the main checkout is still in a turn. */
internal const val BRING_MAIN_BUSY_NOTICE: String =
    "Finish the running task in the main checkout before bringing changes back."

/** Shown when the worktree has no file changes to copy. */
internal const val BRING_EMPTY_NOTICE: String = "No worktree changes to bring back."

/** Shown when this session has no main checkout to receive the files. */
internal const val BRING_UNAVAILABLE_NOTICE: String = "This chat cannot bring changes back."

/** Shown when an unsaved editor blocks a file. */
internal const val BRING_DIRTY_NOTICE: String =
    "Save open editors before bringing the rest of the worktree changes back."

/** Shown when the main checkout is already reviewing a file. */
internal const val BRING_PENDING_NOTICE: String =
    "Review the main checkout's changes before bringing these files back."

/** Shown when a file cannot be read or written. */
internal const val BRING_FAILED_NOTICE: String = "Could not bring changes back."

/**
 * Reads a composer message as the bring command.
 *
 * Only `/bring` with no argument is the local command.
 */
internal fun parseBringCommand(input: String): Boolean {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return false
    return trimmed.removePrefix("/").trim().equals(BRING_COMMAND, ignoreCase = true)
}

/**
 * Relative paths the worktree session changed and did not dismiss, in first-seen order.
 *
 * A path is omitted when every hunk for it was dismissed, or when it sits outside [projectPath].
 */
internal fun bringableRelativePaths(
    projectPath: String,
    turns: List<Turn>,
): List<String> {
    val decisions = linkedMapOf<String, MutableList<DiffDecision>>()
    turns.filterIsInstance<Turn.Agent>().forEach { turn ->
        turn.blocks.filterIsInstance<Block.Diff>().forEach { diff ->
            val relative = relativeCheckoutPath(projectPath, diff.absolutePath) ?: return@forEach
            decisions.getOrPut(relative) { mutableListOf() }.add(diff.decision)
        }
    }
    return decisions
        .filterValues { items -> items.any { it != DiffDecision.REJECTED } }
        .keys
        .toList()
}

/**
 * Whether [path] is a relative checkout path with no parent traversal.
 */
internal fun isSafeRelative(path: String): Boolean {
    if (path.isBlank() || path.startsWith("/") || path.contains('\\')) return false
    return path.split('/').none { part -> part.isEmpty() || part == "." || part == ".." }
}

/**
 * The path of [path] under [projectPath], or null when it is outside that checkout.
 */
internal fun relativeCheckoutPath(
    projectPath: String,
    path: String,
): String? {
    val root = projectPath.trimEnd('/', '\\').replace('\\', '/')
    if (root.isBlank()) return null
    val normalized = path.replace('\\', '/')
    val absolute = if (isAbsoluteCheckoutPath(normalized)) normalized else "$root/$normalized"
    val prefix = "$root/"
    if (!absolute.startsWith(prefix)) return null
    val relative = absolute.removePrefix(prefix)
    if (!isSafeRelative(relative)) return null
    return relative
}

private fun isAbsoluteCheckoutPath(path: String): Boolean =
    path.startsWith("/") || (path.length >= 2 && path[0].isLetter() && path[1] == ':')
