package su.kidoz.jetaprog.app.agent

import kotlinx.serialization.Serializable

/**
 * A linked Git checkout that keeps a second agent session off the main working tree.
 *
 * @property path Absolute path of the linked checkout.
 * @property branch Branch created for that checkout.
 */
@Serializable
public data class AgentWorktree(
    val path: String,
    val branch: String,
)

/** Which checkout an agent session is editing. */
public enum class AgentCheckout {
    /** The project the user opened. */
    MAIN,

    /** A linked Git worktree. Edits stay out of [MAIN]. */
    WORKTREE,
}

/** Shown when the project is not a Git working tree. */
internal const val WORKTREE_NOT_GIT_NOTICE: String = "Open a Git repository before starting a worktree session."

/**
 * One file the worktree session is handing to the main checkout.
 *
 * @property relativePath Path under both checkouts, with `/` separators and no `..`.
 * @property exists Whether the file is present in the worktree.
 * @property content The worktree text, or null when [exists] is false.
 */
public data class AgentBroughtChange(
    val relativePath: String,
    val exists: Boolean,
    val content: String?,
)

/**
 * How a copy from the worktree into the main checkout finished.
 *
 * [refusal] is set when the main checkout could not take the files. The skip counts
 * are files left in place while any others were copied.
 *
 * @property imported Files written into the main checkout and opened for review.
 * @property skippedDirty Files left in place because an editor still has unsaved text.
 * @property skippedPending Files left in place because the main checkout is already reviewing them.
 * @property refusal Why the copy did not run, if the main checkout refused it.
 */
public data class AgentBringOutcome(
    val imported: Int = 0,
    val skippedDirty: Int = 0,
    val skippedPending: Int = 0,
    val refusal: String? = null,
)

/**
 * Creates the checkout for a second, concurrent agent session.
 */
public fun interface AgentWorktreeLauncher {
    /**
     * Creates one linked worktree for [projectPath].
     *
     * The checkout is outside the main working tree. A second call is the session's choice;
     * this function itself always creates.
     */
    public suspend fun create(projectPath: String): Result<AgentWorktree>
}
