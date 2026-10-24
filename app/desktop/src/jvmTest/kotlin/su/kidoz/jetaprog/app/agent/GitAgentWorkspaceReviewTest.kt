package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.agent.workspaceChangeOrigin
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Git coverage for changes that agents do not report through edit tools. */
class GitAgentWorkspaceReviewTest {
    private val executor = JvmProcessExecutor()

    @Test
    fun rawOutputPreservesFramingAndWhitespace() =
        runTest {
            val value = "before\r\nafter \t"
            val result =
                executor
                    .executeRaw(
                        ProcessConfig(
                            listOf("git", "-c", "review.value=$value", "config", "--null", "--get", "review.value"),
                        ),
                    ).getOrThrow()
            assertEquals(0, result.exitCode)
            assertEquals("$value\u0000", result.stdout)
        }

    @Test
    fun parserPreservesUnusualPathsAndRenameSources() {
        assertEquals(
            listOf(
                WorkspaceChange("new\r\nname.kt", 'R', ' ', "old\tname.kt"),
                WorkspaceChange("space [a].kt ", '?', '?'),
            ),
            parseWorkspaceStatus("R  new\r\nname.kt\u0000old\tname.kt\u0000?? space [a].kt \u0000"),
        )
        assertFailsWith<IllegalArgumentException> { parseWorkspaceStatus("R  incomplete\u0000") }
    }

    @Test
    fun stagedWorkingUntrackedDeletedAndRenamedFilesAreInspectable() =
        runTest {
            val root = createTempDirectory("workspace-review").toFile()
            try {
                git(root, listOf("init"))
                File(root, "mixed.txt").writeText("original\n")
                File(root, "removed.txt").writeText("delete\n")
                File(root, "old.txt").writeText("rename\n")
                File(root, ".gitignore").writeText("ignored/\n")
                git(root, listOf("add", "."))
                git(
                    root,
                    listOf(
                        "-c",
                        "user.name=Test",
                        "-c",
                        "user.email=test@example.invalid",
                        "-c",
                        "commit.gpgsign=false",
                        "commit",
                        "-m",
                        "Initial",
                    ),
                )
                File(root, "mixed.txt").writeText("staged\n")
                git(root, listOf("add", "mixed.txt"))
                File(root, "mixed.txt").writeText("working\n")
                File(root, "removed.txt").delete()
                git(root, listOf("mv", "old.txt", "renamed.txt"))
                File(root, "scripts").mkdir()
                File(root, "scripts/space [a].kt").writeText("script output\n")
                File(root, "ignored").mkdir()
                File(root, "ignored/hidden.txt").writeText("ignored")
                val source = GitAgentWorkspaceReviewSource(executor, root.path)
                val changes = source.changes().getOrThrow().associateBy { it.path }
                assertEquals(setOf("mixed.txt", "removed.txt", "renamed.txt", "scripts/space [a].kt"), changes.keys)
                assertEquals(WorkspaceChange("mixed.txt", 'M', 'M'), changes.getValue("mixed.txt"))
                assertEquals("old.txt", changes.getValue("renamed.txt").originalPath)
                val preview = source.preview(changes.getValue("mixed.txt")).getOrThrow()
                assertTrue(preview.contains("+staged"))
                assertTrue(preview.contains("+working"))
                assertTrue(preview.contains("Staged changes"))
                assertTrue(preview.contains("Working-tree changes"))
                assertTrue(source.preview(changes.getValue("removed.txt")).getOrThrow().contains("-delete"))
                assertTrue(source.preview(changes.getValue("renamed.txt")).getOrThrow().contains("rename from old.txt"))
                assertTrue(
                    source.preview(changes.getValue("scripts/space [a].kt")).getOrThrow().endsWith("script output\n"),
                )
                assertEquals(changes.values.toList(), source.changes().getOrThrow())
                val nested = GitAgentWorkspaceReviewSource(executor, File(root, "scripts").path)
                assertEquals(listOf("space [a].kt"), nested.changes().getOrThrow().map { it.path })
                assertTrue(
                    nested.preview(nested.changes().getOrThrow().single()).getOrThrow().contains("script output"),
                )
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun binaryLargeAndOutsidePathsDoNotProduceMisleadingTextPreviews() =
        runTest {
            val root = createTempDirectory("workspace-preview").toFile()
            try {
                val source = GitAgentWorkspaceReviewSource(executor, root.path)
                File(root, "binary").writeBytes(byteArrayOf(1, 0, 2))
                File(root, "large").writeText("a".repeat(1_000_001))
                assertEquals(
                    "Binary file. No text preview.",
                    source.preview(WorkspaceChange("binary", '?', '?')).getOrThrow(),
                )
                assertTrue(source.preview(WorkspaceChange("large", '?', '?')).getOrThrow().contains("too large"))
                assertTrue(source.preview(WorkspaceChange("../outside", '?', '?')).isFailure)
                assertTrue(source.changes().isFailure)
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun originEvidenceAllowsMixedEditsWithoutGuessingOwnership() {
        val file = WorkspaceChange("new.kt", 'R', 'M', "old.kt")
        assertEquals(
            "Changed before this task · Agent-reported edits",
            workspaceChangeOrigin(file, setOf("old.kt"), setOf("new.kt")),
        )
        assertEquals("Origin unknown", workspaceChangeOrigin(file, emptySet(), emptySet()))
        assertEquals("Origin unknown", workspaceChangeOrigin(file, null, emptySet()))
        assertFalse(workspaceChangeOrigin(file, null, setOf("other.kt")).contains("Agent"))
    }

    private suspend fun git(
        root: File,
        args: List<String>,
    ) {
        val result =
            executor
                .execute(
                    listOf("git") + args,
                    workingDirectory = root.path,
                    timeoutMillis = 10_000,
                ).getOrThrow()
        check(result.isSuccess) { result.stderr }
    }
}
