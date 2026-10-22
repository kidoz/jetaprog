package su.kidoz.jetaprog.app.ui.agent

/** Slash name of the worktree command. */
internal const val WORKTREE_COMMAND: String = "worktree"

/** Menu text for the built-in worktree command. */
internal const val WORKTREE_COMMAND_DESCRIPTION: String = "Start a second session in a worktree"

/** Shown when `/worktree` is used while a turn is running. */
internal const val WORKTREE_BUSY_NOTICE: String = "Finish the running task before opening a worktree."

/** Shown when the visible chat is already the worktree session. */
internal const val WORKTREE_ALREADY_NOTICE: String = "This chat is already in a worktree."

/** Shown when this session has no way to create a linked checkout. */
internal const val WORKTREE_UNAVAILABLE_NOTICE: String = "Worktrees are not available for this session."

/** Shown when a second `/worktree` arrives while the first checkout is still being created. */
internal const val WORKTREE_OPENING_NOTICE: String = "A worktree session is already opening."

/**
 * Reads a composer message as the worktree command.
 *
 * Only `/worktree` with no argument is the local command.
 */
internal fun parseWorktreeCommand(input: String): Boolean {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return false
    return trimmed.removePrefix("/").trim().equals(WORKTREE_COMMAND, ignoreCase = true)
}
