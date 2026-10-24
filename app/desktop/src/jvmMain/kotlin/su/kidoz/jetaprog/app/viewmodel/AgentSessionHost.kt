package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.common.Disposable

/**
 * The main agent session and, once opened, one worktree session.
 *
 * Both stay alive so a turn in the hidden checkout can keep running. The surface shows [active].
 *
 * @param main The session for the project the user opened.
 * @param openWorktree Builds the session that edits [AgentWorktree.path]. Called once.
 */
public class AgentSessionHost(
    public val main: AgentSessionViewModel,
    private val openWorktree: (AgentWorktree) -> AgentSessionViewModel,
) : Disposable {
    private var worktreeSession: AgentSessionViewModel? = null
    private val worktreeFlow = MutableStateFlow<AgentSessionViewModel?>(null)
    private val activeFlow = MutableStateFlow(main)

    /** The linked session, after `/worktree` has created it. */
    public val worktree: StateFlow<AgentSessionViewModel?> = worktreeFlow.asStateFlow()

    /** The session the agent surface should show. */
    public val active: StateFlow<AgentSessionViewModel> = activeFlow.asStateFlow()

    /**
     * Opens [tree] as the visible session.
     *
     * A later call switches back to the session already created for the first tree.
     */
    public fun attach(tree: AgentWorktree) {
        val session = worktreeSession ?: openWorktree(tree).also { worktreeSession = it }
        worktreeFlow.value = session
        show(session)
    }

    /** Shows the main checkout without stopping the worktree session. */
    public fun showMain() {
        show(main)
    }

    /** Shows the worktree session when one is open. */
    public fun showWorktree() {
        worktreeSession?.let(::show)
    }

    /** Saves both checkouts' active conversations before project shutdown completes. */
    public suspend fun shutdown() {
        try {
            worktreeSession?.shutdown()
        } finally {
            worktreeSession = null
            main.shutdown()
        }
    }

    override fun dispose() {
        worktreeSession?.dispose()
        worktreeSession = null
        main.dispose()
    }

    private fun show(target: AgentSessionViewModel) {
        val current = activeFlow.value
        if (current !== target && current.state.value.docked != target.state.value.docked) {
            val intent =
                if (current.state.value.docked) {
                    AgentIntent.DockToToolWindow
                } else {
                    AgentIntent.ExpandToPerspective
                }
            target.dispatch(intent)
        }
        activeFlow.value = target
    }
}
