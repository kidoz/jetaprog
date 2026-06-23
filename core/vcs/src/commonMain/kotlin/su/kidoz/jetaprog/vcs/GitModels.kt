package su.kidoz.jetaprog.vcs

/** The kind of change to a file in the working tree or index. */
public enum class GitChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNTRACKED,
    CONFLICTED,
    UNKNOWN,
}

/**
 * A single changed file, either staged (in the index) or unstaged (in the
 * working tree). A file modified in both appears as two changes.
 */
public data class GitChange(
    /** Repository-relative file path. */
    val path: String,
    /** The kind of change. */
    val type: GitChangeType,
    /** Whether this change is staged (in the index). */
    val staged: Boolean,
)

/**
 * A snapshot of repository status.
 */
public data class GitRepositoryStatus(
    /** The current branch name, or null when detached/unknown. */
    val branch: String?,
    /** Commits ahead of the upstream, if known. */
    val ahead: Int = 0,
    /** Commits behind the upstream, if known. */
    val behind: Int = 0,
    /** All staged and unstaged changes. */
    val changes: List<GitChange> = emptyList(),
) {
    /** Staged changes (in the index). */
    public val staged: List<GitChange> get() = changes.filter { it.staged }

    /** Unstaged changes (working tree, including untracked). */
    public val unstaged: List<GitChange> get() = changes.filterNot { it.staged }

    /** Whether the working tree is clean. */
    public val isClean: Boolean get() = changes.isEmpty()
}
