package su.kidoz.jetaprog.app.project

import su.kidoz.jetaprog.vcs.ignore.GitignoreMatcher
import java.io.File

/**
 * Builds a [GitignoreMatcher] for [projectPath] that reads the rule files from
 * disk.
 *
 * Unreadable files are treated as absent: a project that is not a Git working
 * copy — or one whose `.gitignore` cannot be read — simply ignores nothing.
 */
public fun projectGitignoreMatcher(projectPath: String): GitignoreMatcher =
    GitignoreMatcher(projectPath) { path ->
        val file = File(path)
        if (file.isFile) file.runCatching { readText() }.getOrNull() else null
    }
