package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.vcs.DefaultGitService
import su.kidoz.jetaprog.vcs.GitBranch
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitCommit
import su.kidoz.jetaprog.vcs.GitLineChange
import su.kidoz.jetaprog.vcs.GitService

/** State of the Git panel. */
public data class GitState(
    /** Whether the project is a Git repository. */
    val isRepository: Boolean = true,
    /** The current branch name. */
    val branch: String? = null,
    /** The local branches. */
    val branches: List<GitBranch> = emptyList(),
    /** Commits ahead of upstream. */
    val ahead: Int = 0,
    /** Commits behind upstream. */
    val behind: Int = 0,
    /** Staged changes. */
    val staged: List<GitChange> = emptyList(),
    /** Unstaged changes. */
    val unstaged: List<GitChange> = emptyList(),
    /** The change whose diff is shown. */
    val selected: GitChange? = null,
    /** The unified diff of [selected]. */
    val diff: String = "",
    /** Recent commit history, newest first. */
    val commitLog: List<GitCommit> = emptyList(),
    /** The pending commit message. */
    val commitMessage: String = "",
    /** Whether a git operation is in progress. */
    val isBusy: Boolean = false,
    /** The most recent error, if any. */
    val error: String? = null,
)

/**
 * Drives the in-IDE Git workflow: status, diff, stage/unstage and commit.
 *
 * @param processExecutor used to invoke the `git` CLI.
 * @param projectPath the repository working directory.
 */
public class GitViewModel(
    processExecutor: ProcessExecutor,
    private val projectPath: String,
) : Disposable {
    private val service: GitService = DefaultGitService(processExecutor, projectPath)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(GitState())

    /** The observable panel state. */
    public val state: StateFlow<GitState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reloads repository status. */
    public fun refresh() {
        scope.launch {
            if (!service.isRepository()) {
                _state.update { it.copy(isRepository = false) }
                return@launch
            }
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .status()
                .onSuccess { status ->
                    _state.update {
                        it.copy(
                            isRepository = true,
                            isBusy = false,
                            branch = status.branch,
                            ahead = status.ahead,
                            behind = status.behind,
                            staged = status.staged,
                            unstaged = status.unstaged,
                        )
                    }
                    service
                        .log(LOG_LIMIT)
                        .onSuccess { log -> _state.update { it.copy(commitLog = log) } }
                    service
                        .branches()
                        .onSuccess { branches -> _state.update { it.copy(branches = branches) } }
                }.onFailure { error -> fail(error) }
        }
    }

    /** Selects a change and loads its diff. */
    public fun select(change: GitChange) {
        _state.update { it.copy(selected = change, diff = "") }
        scope.launch {
            service
                .diff(change.path, change.staged)
                .onSuccess { diff -> _state.update { it.copy(diff = diff) } }
                .onFailure { error -> fail(error) }
        }
    }

    /** Stages [change]. */
    public fun stage(change: GitChange) {
        runThenRefresh { service.stage(listOf(change.path)) }
    }

    /** Unstages [change]. */
    public fun unstage(change: GitChange) {
        runThenRefresh { service.unstage(listOf(change.path)) }
    }

    /** Stages [change] if unstaged, otherwise unstages it. */
    public fun toggleStage(change: GitChange) {
        if (change.staged) unstage(change) else stage(change)
    }

    /** Stages all changes when [staged], otherwise unstages all staged changes. */
    public fun setAllStaged(staged: Boolean) {
        val paths =
            if (staged) {
                _state.value.unstaged.map { it.path }
            } else {
                _state.value.staged.map { it.path }
            }
        if (paths.isEmpty()) return
        runThenRefresh { if (staged) service.stage(paths) else service.unstage(paths) }
    }

    /** Updates the commit message. */
    public fun setCommitMessage(message: String) {
        _state.update { it.copy(commitMessage = message) }
    }

    /** Commits the staged changes with the current message. */
    public fun commit() {
        val message = _state.value.commitMessage.trim()
        if (message.isEmpty() || _state.value.staged.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .commit(message)
                .onSuccess {
                    _state.update { it.copy(commitMessage = "", diff = "", selected = null) }
                    refresh()
                }.onFailure { error -> fail(error) }
        }
    }

    /** Commits the staged changes, then pushes to the upstream. */
    public fun commitAndPush() {
        val message = _state.value.commitMessage.trim()
        if (message.isEmpty() || _state.value.staged.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .commit(message)
                .onSuccess {
                    _state.update { it.copy(commitMessage = "", diff = "", selected = null) }
                    service
                        .push()
                        .onSuccess { refresh() }
                        .onFailure { error -> fail(error) }
                }.onFailure { error -> fail(error) }
        }
    }

    /** Pushes the current branch to its upstream. */
    public fun push() {
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .push()
                .onSuccess { refresh() }
                .onFailure { error -> fail(error) }
        }
    }

    /** Updates (fast-forwards) the current branch from its upstream. */
    public fun pull() {
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .pull()
                .onSuccess { refresh() }
                .onFailure { error -> fail(error) }
        }
    }

    /** Checks out the branch [name]. */
    public fun checkoutBranch(name: String) {
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .checkout(name)
                .onSuccess { refresh() }
                .onFailure { error -> fail(error) }
        }
    }

    /** Creates the branch [name] and checks it out. */
    public fun createBranch(name: String) {
        val branchName = name.trim()
        if (branchName.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            service
                .createBranch(branchName)
                .onSuccess { refresh() }
                .onFailure { error -> fail(error) }
        }
    }

    /**
     * Returns the per-line working-tree changes of [path] relative to HEAD,
     * or an empty list when unavailable (e.g. untracked file, not a repository).
     */
    public suspend fun lineChanges(path: String): List<GitLineChange> =
        service.lineStatus(path).getOrDefault(emptyList())

    private fun runThenRefresh(action: suspend () -> Result<Unit>) {
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            action()
                .onSuccess { refresh() }
                .onFailure { error -> fail(error) }
        }
    }

    private fun fail(error: Throwable) {
        _state.update { it.copy(isBusy = false, error = error.message ?: "Git operation failed") }
    }

    override fun dispose() {
        scope.cancel()
    }

    private companion object {
        const val LOG_LIMIT = 50
    }
}
