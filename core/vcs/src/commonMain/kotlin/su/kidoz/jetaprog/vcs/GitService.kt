package su.kidoz.jetaprog.vcs

import su.kidoz.jetaprog.platform.process.ProcessExecutor

/**
 * Read/write access to a Git working copy: status, diff, staging and commit.
 */
public interface GitService {
    /** Whether [repositoryPath] is inside a Git working tree. */
    public suspend fun isRepository(): Boolean

    /** Returns the current repository status. */
    public suspend fun status(): Result<GitRepositoryStatus>

    /**
     * Returns the unified diff for [path]; staged (index vs HEAD) when [staged],
     * otherwise the working-tree diff.
     */
    public suspend fun diff(
        path: String,
        staged: Boolean,
    ): Result<String>

    /** Stages [paths] (git add). */
    public suspend fun stage(paths: List<String>): Result<Unit>

    /** Unstages [paths] (git reset HEAD). */
    public suspend fun unstage(paths: List<String>): Result<Unit>

    /** Commits the staged changes with [message]; returns git's output. */
    public suspend fun commit(message: String): Result<String>

    /** Returns the most recent commits, newest first, up to [limit]. */
    public suspend fun log(limit: Int): Result<List<GitCommit>>

    /** Pushes the current branch to its upstream; returns git's output. */
    public suspend fun push(): Result<String>

    /** Fast-forwards the current branch from its upstream; returns git's output. */
    public suspend fun pull(): Result<String>
}

/**
 * [GitService] backed by the `git` CLI via a [ProcessExecutor].
 */
public class DefaultGitService(
    private val processExecutor: ProcessExecutor,
    private val repositoryPath: String,
) : GitService {
    override suspend fun isRepository(): Boolean {
        val result = run("rev-parse", "--is-inside-work-tree").getOrNull() ?: return false
        return result.isSuccess && result.stdout.trim() == "true"
    }

    override suspend fun status(): Result<GitRepositoryStatus> =
        run("status", "--porcelain=v1", "--branch").mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { "git status failed" })
            GitStatusParser.parse(result.stdout)
        }

    override suspend fun diff(
        path: String,
        staged: Boolean,
    ): Result<String> {
        val args =
            buildList {
                add("diff")
                if (staged) add("--cached")
                add("--")
                add(path)
            }
        return run(*args.toTypedArray()).mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { "git diff failed" })
            result.stdout
        }
    }

    override suspend fun stage(paths: List<String>): Result<Unit> = mutate("add", paths)

    override suspend fun unstage(paths: List<String>): Result<Unit> = mutate("reset", paths, "-q", "HEAD")

    override suspend fun commit(message: String): Result<String> =
        run("commit", "-m", message).mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { result.stdout.ifBlank { "git commit failed" } })
            result.stdout
        }

    override suspend fun log(limit: Int): Result<List<GitCommit>> =
        run("log", "-n", limit.toString(), "--pretty=format:${GitLogParser.PRETTY_FORMAT}").mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { "git log failed" })
            GitLogParser.parse(result.stdout)
        }

    override suspend fun push(): Result<String> =
        run("push").mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { result.stdout.ifBlank { "git push failed" } })
            result.stderr.ifBlank { result.stdout }
        }

    override suspend fun pull(): Result<String> =
        run("pull", "--ff-only").mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { result.stdout.ifBlank { "git pull failed" } })
            result.stdout.ifBlank { result.stderr }
        }

    private suspend fun mutate(
        command: String,
        paths: List<String>,
        vararg extraArgs: String,
    ): Result<Unit> {
        if (paths.isEmpty()) return Result.success(Unit)
        val args =
            buildList {
                add(command)
                addAll(extraArgs)
                add("--")
                addAll(paths)
            }
        return run(*args.toTypedArray()).mapCatching { result ->
            if (!result.isSuccess) error(result.stderr.ifBlank { "git $command failed" })
        }
    }

    private suspend fun run(vararg args: String) =
        processExecutor.execute(
            command = listOf("git", *args),
            workingDirectory = repositoryPath,
        )
}
