package su.kidoz.jetaprog.app.agent

/**
 * A linked Git checkout that keeps a second agent session off the main working tree.
 *
 * @property path Absolute path of the linked checkout.
 * @property branch Branch created for that checkout.
 */
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
