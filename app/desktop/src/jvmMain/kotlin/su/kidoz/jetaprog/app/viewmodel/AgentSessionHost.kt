package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.agent.AgentCheckoutState
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.common.Disposable

/**
 * The main agent session and, once opened, one worktree session.
 *
 * Both stay alive so a turn in the hidden checkout can keep running. The surface shows [active].
 *
 * @param main The session for the project the user opened.
 * @param loadCheckouts Loads and validates the saved pair without creating a checkout.
 * @param saveCheckouts Persists tab changes for this project.
 * @param openWorktree Builds the session that edits [AgentWorktree.path]. Called once.
 */
public class AgentSessionHost(
    public val main: AgentSessionViewModel,
    private val loadCheckouts: suspend () -> Result<AgentCheckoutState?> = { Result.success(null) },
    private val saveCheckouts: suspend (AgentCheckoutState) -> Unit = {},
    private val openWorktree: (AgentWorktree) -> AgentSessionViewModel,
) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val saveMutex = Mutex()
    private var restoreJob: Deferred<Unit>? = null
    private var tree: AgentWorktree? = null
    private var revision = 0L
    private var changed = false
    private var closed = false
    private var worktreeSession: AgentSessionViewModel? = null
    private val worktreeFlow = MutableStateFlow<AgentSessionViewModel?>(null)
    private val activeFlow = MutableStateFlow(main)
    private val restoringFlow = MutableStateFlow(true)

    /** Prevents the agent surface from connecting the wrong checkout during restoration. */
    public val restoring: StateFlow<Boolean> = restoringFlow.asStateFlow()

    /** The linked session, after creation or restoration. */
    public val worktree: StateFlow<AgentSessionViewModel?> = worktreeFlow.asStateFlow()

    /** The session the agent surface should show. */
    public val active: StateFlow<AgentSessionViewModel> = activeFlow.asStateFlow()

    /** Reopens validated tabs once, without replaying prompts or creating a new worktree. */
    public suspend fun restore() {
        if (closed) return
        val job =
            restoreJob ?: scope
                .async {
                    try {
                        val startedAt = revision
                        main.awaitInitialization()
                        val result = loadCheckouts()
                        if (closed || startedAt != revision) return@async
                        val saved =
                            result.getOrElse {
                                main.reportCheckoutRestoreFailure(
                                    it.message ?: "Saved worktree could not be restored. Main checkout is open.",
                                )
                                return@async
                            } ?: return@async
                        val restoredTree = saved.worktree ?: return@async
                        tree = restoredTree
                        main.rememberLinkedWorktree(restoredTree)
                        val session = openWorktree(restoredTree)
                        worktreeSession = session
                        session.awaitInitialization()
                        if (closed || startedAt != revision) return@async
                        worktreeFlow.value = session
                        if (saved.worktreeActive) show(session)
                    } finally {
                        restoringFlow.value = false
                    }
                }.also { restoreJob = it }
        job.await()
    }

    /**
     * Opens [tree] as the visible session.
     *
     * A later call switches back to the session already created for the first tree.
     */
    public fun attach(tree: AgentWorktree) {
        if (closed) return
        revision++
        val session =
            worktreeSession ?: openWorktree(tree).also {
                worktreeSession = it
                this.tree = tree
                main.rememberLinkedWorktree(tree)
            }
        worktreeFlow.value = session
        show(session)
        persistTabs()
    }

    /** Shows the main checkout without stopping the worktree session. */
    public fun showMain() {
        if (closed) return
        revision++
        show(main)
        persistTabs()
    }

    /** Shows the worktree session when one is open. */
    public fun showWorktree() {
        if (closed) return
        worktreeSession?.let {
            revision++
            show(it)
            persistTabs()
        }
    }

    /** Saves both checkouts' active conversations before project shutdown completes. */
    public suspend fun shutdown() {
        if (closed) return
        closed = true
        val snapshot = checkoutState()
        scope.cancel()
        try {
            if (changed) withContext(NonCancellable) { saveMutex.withLock { saveCheckouts(snapshot) } }
        } finally {
            try {
                worktreeSession?.shutdown()
            } finally {
                worktreeSession = null
                main.shutdown()
            }
        }
    }

    override fun dispose() {
        closed = true
        scope.cancel()
        worktreeSession?.dispose()
        worktreeSession = null
        main.dispose()
    }

    private fun checkoutState(): AgentCheckoutState = AgentCheckoutState(tree, activeFlow.value !== main)

    private fun persistTabs() {
        changed = true
        scope.launch { saveMutex.withLock { saveCheckouts(checkoutState()) } }
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
