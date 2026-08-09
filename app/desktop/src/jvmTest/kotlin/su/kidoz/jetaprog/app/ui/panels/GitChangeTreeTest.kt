package su.kidoz.jetaprog.app.ui.panels

import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitChangeTreeTest {
    @Test
    fun groupsNestedPathsWithDirectoriesBeforeFiles() {
        val items =
            buildGitChangeTreeItems(
                listOf(
                    change("README.md"),
                    change("src/Main.kt"),
                    change("src/test/MainTest.kt"),
                    change("docs/guide.md"),
                ),
            )

        assertEquals(
            listOf(
                "directory:docs",
                "file:false:docs/guide.md",
                "directory:src",
                "directory:src/test",
                "file:false:src/test/MainTest.kt",
                "file:false:src/Main.kt",
                "file:false:README.md",
            ),
            items.map { it.key },
        )
        assertEquals(listOf(0, 1, 0, 1, 2, 1, 0), items.map { it.depth })
    }

    @Test
    fun hidesEveryDescendantOfCollapsedDirectory() {
        val items =
            buildGitChangeTreeItems(
                changes = listOf(change("src/main/App.kt"), change("src/test/AppTest.kt"), change("README.md")),
                collapsedDirectories = setOf("src"),
            )

        assertEquals(listOf("directory:src", "file:false:README.md"), items.map { it.key })
        val directory = assertIs<GitChangeTreeItem.Directory>(items.first())
        assertEquals(2, directory.changes.size)
    }

    @Test
    fun preservesStagedAndUnstagedEntriesForPartiallyStagedFile() {
        val items =
            buildGitChangeTreeItems(
                listOf(
                    change("src/Main.kt", staged = true),
                    change("src/Main.kt", staged = false),
                ),
            )

        assertEquals(
            listOf("directory:src", "file:false:src/Main.kt", "file:true:src/Main.kt"),
            items.map { it.key },
        )
        assertEquals(
            StageSelection.INDETERMINATE,
            assertIs<GitChangeTreeItem.Directory>(items.first()).changes.stageSelection(),
        )
    }

    @Test
    fun reportsAggregateStageSelection() {
        assertEquals(StageSelection.UNCHECKED, emptyList<GitChange>().stageSelection())
        assertEquals(StageSelection.UNCHECKED, listOf(change("a.kt")).stageSelection())
        assertEquals(StageSelection.CHECKED, listOf(change("a.kt", staged = true)).stageSelection())
        assertEquals(
            StageSelection.INDETERMINATE,
            listOf(change("a.kt", staged = true), change("b.kt")).stageSelection(),
        )
    }

    private fun change(
        path: String,
        staged: Boolean = false,
    ): GitChange = GitChange(path = path, type = GitChangeType.MODIFIED, staged = staged)
}
