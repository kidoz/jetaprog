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

/**
 * A local branch in the repository.
 */
public data class GitBranch(
    /** Branch name (short ref, e.g. "main"). */
    val name: String,
    /** Whether this branch is currently checked out. */
    val isCurrent: Boolean,
)

/** The kind of change to a single line relative to HEAD. */
public enum class GitLineChangeType {
    ADDED,
    MODIFIED,
    DELETED,
}

/**
 * A per-line change in a file's working tree relative to HEAD.
 *
 * For [GitLineChangeType.DELETED] the [line] is the line in the current file
 * next to which content was removed.
 */
public data class GitLineChange(
    /** Line index in the current file contents (0-based). */
    val line: Int,
    /** The kind of change. */
    val type: GitLineChangeType,
)

/**
 * A single entry in the commit history.
 */
public data class GitCommit(
    /** Full commit hash. */
    val hash: String,
    /** Abbreviated commit hash. */
    val shortHash: String,
    /** Commit subject line. */
    val message: String,
    /** Author display name. */
    val author: String,
    /** Author email address. */
    val authorEmail: String,
    /** Human-readable relative date (e.g. "2 hours ago"). */
    val relativeDate: String,
    /** Branch and tag names pointing at this commit. */
    val refs: List<String> = emptyList(),
)
