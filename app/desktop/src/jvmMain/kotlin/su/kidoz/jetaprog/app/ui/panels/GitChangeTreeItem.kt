package su.kidoz.jetaprog.app.ui.panels

import su.kidoz.jetaprog.vcs.GitChange

internal sealed interface GitChangeTreeItem {
    val key: String
    val depth: Int

    data class Directory(
        val path: String,
        val name: String,
        override val depth: Int,
        val changes: List<GitChange>,
    ) : GitChangeTreeItem {
        override val key: String = "directory:$path"
    }

    data class File(
        val change: GitChange,
        override val depth: Int,
    ) : GitChangeTreeItem {
        override val key: String = "file:${change.staged}:${change.path}"
    }
}

internal fun buildGitChangeTreeItems(
    changes: List<GitChange>,
    collapsedDirectories: Set<String> = emptySet(),
): List<GitChangeTreeItem> {
    val root = MutableGitDirectory(path = "", name = "")
    changes.forEach(root::add)
    return buildList { root.flattenInto(this, depth = 0, collapsedDirectories) }
}

private class MutableGitDirectory(
    val path: String,
    val name: String,
) {
    private val directories = mutableMapOf<String, MutableGitDirectory>()
    private val files = mutableListOf<GitChange>()

    fun add(change: GitChange) {
        val segments =
            change.path
                .replace('\\', '/')
                .trim('/')
                .split('/')
                .filter(String::isNotEmpty)
        if (segments.size <= 1) {
            files += change
            return
        }

        var directory = this
        segments.dropLast(1).forEach { segment ->
            val childPath = if (directory.path.isEmpty()) segment else "${directory.path}/$segment"
            directory =
                directory.directories.getOrPut(segment) {
                    MutableGitDirectory(path = childPath, name = segment)
                }
        }
        directory.files += change
    }

    fun allChanges(): List<GitChange> =
        buildList {
            addAll(files)
            directories.values.forEach { addAll(it.allChanges()) }
        }

    fun flattenInto(
        destination: MutableList<GitChangeTreeItem>,
        depth: Int,
        collapsedDirectories: Set<String>,
    ) {
        directories.values.sortedBy { it.name.lowercase() }.forEach { directory ->
            destination +=
                GitChangeTreeItem.Directory(
                    path = directory.path,
                    name = directory.name,
                    depth = depth,
                    changes = directory.allChanges(),
                )
            if (directory.path !in collapsedDirectories) {
                directory.flattenInto(destination, depth + 1, collapsedDirectories)
            }
        }
        files
            .sortedWith(compareBy<GitChange>({ it.path.substringAfterLast('/').lowercase() }, { it.staged }))
            .forEach { destination += GitChangeTreeItem.File(change = it, depth = depth) }
    }
}
