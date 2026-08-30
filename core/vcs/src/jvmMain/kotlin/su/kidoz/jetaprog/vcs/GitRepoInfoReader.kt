package su.kidoz.jetaprog.vcs

import java.io.File

/**
 * The locally readable state of a Git repository, parsed straight from the
 * `.git` directory without invoking the `git` CLI.
 *
 * Mirrors IntelliJ's `GitRepoInfo`/`GitRepositoryReader` split: reading these
 * files is cheap enough to do on every repository refresh, while `git status`
 * remains the only source for working-copy changes and ahead/behind counts.
 *
 * @property currentBranch Short name of the checked-out branch, or null when
 *   detached / unknown.
 * @property isDetached Whether HEAD points at a commit instead of a branch.
 * @property headSha Commit sha HEAD resolves to, when readable.
 * @property branches All local branches with the checked-out one flagged.
 */
public data class GitLocalInfo(
    val currentBranch: String?,
    val isDetached: Boolean,
    val headSha: String?,
    val branches: List<GitBranch>,
)

/**
 * Reads repository state by parsing the `.git` directory: `HEAD` for the
 * checked-out ref (or detached sha), loose refs under `refs/heads` plus
 * `packed-refs` (loose entries win) for the branch list.
 *
 * Falls back to `git` CLI behaviour is intentionally left to callers; when
 * [read] returns null (no repository or unreadable layout) callers should use
 * their command-based path.
 */
public class GitRepoInfoReader(
    private val repositoryPath: String,
) {
    /**
     * Reads the current repository info, or null when the path is not a
     * recognizable Git repository.
     */
    public fun read(): GitLocalInfo? {
        val gitDir = resolveGitDir() ?: return null
        val head = gitDir.resolve("HEAD")
        if (!head.isFile) return null
        val headLine = head.readText().trim()
        if (headLine.isEmpty()) return null

        val branchShas = readBranchShas(gitDir)
        if (headLine.startsWith("ref: ")) {
            val refName = headLine.removePrefix("ref: ").trim()
            val branchName = refName.removePrefix("refs/heads/")
            val headSha = branchShas[branchName] ?: refSha(gitDir, refName)
            return GitLocalInfo(
                currentBranch = branchName,
                isDetached = false,
                headSha = headSha,
                branches =
                    branchShas.map { (name, sha) ->
                        GitBranch(name = name, isCurrent = name == branchName, sha = sha)
                    },
            )
        }

        // Detached HEAD: the line is a raw (possibly abbreviated) commit sha.
        return GitLocalInfo(
            currentBranch = null,
            isDetached = true,
            headSha = headLine,
            branches =
                branchShas.map { (name, sha) ->
                    GitBranch(name = name, isCurrent = false, sha = sha)
                },
        )
    }

    /** Resolves the `.git` directory, following the `gitdir:` file form. */
    private fun resolveGitDir(): File? {
        val dotGit = File(repositoryPath, ".git")
        if (dotGit.isDirectory) return dotGit
        if (!dotGit.isFile) return null
        val content = dotGit.readText().trim()
        if (!content.startsWith("gitdir:")) return null
        val gitDir = File(content.removePrefix("gitdir:").trim())
        return gitDir.takeIf { it.isDirectory }
    }

    /**
     * Local branch name → sha. Loose refs under `refs/heads` take precedence
     * over `packed-refs` entries, matching git's own resolution order.
     */
    private fun readBranchShas(gitDir: File): Map<String, String> {
        val shas = LinkedHashMap<String, String>()
        gitDir.resolve("packed-refs").takeIf { it.isFile }?.readLines()?.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("^")) return@forEach
            val sha = trimmed.substringBefore(' ')
            val ref = trimmed.substringAfter(' ', missingDelimiterValue = "")
            if (sha.isNotEmpty() && ref.startsWith("refs/heads/")) {
                shas[ref.removePrefix("refs/heads/")] = sha
            }
        }
        val looseRefs = gitDir.resolve("refs/heads")
        if (looseRefs.isDirectory) {
            looseRefs.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(looseRefs).invariantSeparatorsPath
                val sha = file.readText().trim()
                if (sha.isNotEmpty()) shas[name] = sha
            }
        }
        return shas
    }

    /** Reads a loose ref directly (e.g. the checked-out branch's sha). */
    private fun refSha(
        gitDir: File,
        refName: String,
    ): String? =
        gitDir
            .resolve(refName)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.ifEmpty { null }
}
