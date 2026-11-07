package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assumptions.assumeFalse
import su.kidoz.jetaprog.app.ui.panels.parseUnifiedDiff
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.vcs.DefaultGitService
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import su.kidoz.jetaprog.vcs.GitStatusParser
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for the editor audit Git workflows. */
@OptIn(ExperimentalCoroutinesApi::class)
class GitWorkflowRegressionTest {
    private val repositories = mutableListOf<java.io.File>()

    @AfterTest
    fun cleanUp() {
        repositories.forEach { it.deleteRecursively() }
    }

    private fun repo(initialCommit: Boolean = true): java.io.File {
        val dir = Files.createTempDirectory("jetaprog-git-acceptance-").toFile()
        repositories += dir
        git(dir, "init", "-b", "main")
        git(dir, "config", "user.name", "UX Audit")
        git(dir, "config", "user.email", "ux-audit@example.invalid")
        if (initialCommit) {
            dir.resolve("tracked.txt").writeText("original\n")
            git(dir, "add", "tracked.txt")
            git(dir, "commit", "-m", "Create test fixture")
        }
        return dir
    }

    private fun git(
        dir: java.io.File,
        vararg args: String,
    ): String {
        val process = ProcessBuilder(listOf("git", "-C", dir.path) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        return output
    }

    private fun service(dir: java.io.File) = DefaultGitService(JvmProcessExecutor(), dir.path)

    @Test
    fun stagePlainFilenameRoundTrip() =
        runBlocking {
            val dir = repo()
            dir.resolve("tracked.txt").writeText("changed\n")
            val svc = service(dir)
            val change =
                svc
                    .status()
                    .getOrThrow()
                    .unstaged
                    .single()
            svc.stage(listOf(change.path)).getOrThrow()
            assertEquals(
                "tracked.txt",
                svc
                    .status()
                    .getOrThrow()
                    .staged
                    .single()
                    .path,
            )
            assertTrue(svc.diff("tracked.txt", true).getOrThrow().contains("+changed"))
            svc.unstage(listOf("tracked.txt")).getOrThrow()
            assertTrue(
                svc
                    .status()
                    .getOrThrow()
                    .staged
                    .isEmpty(),
            )
        }

    @Test
    fun stageSpacedFilenameReturnedByStatus() = checkFilename("space name.txt")

    @Test
    fun stageUnicodeFilenameReturnedByStatus() = checkFilename("кириллица.txt")

    @Test
    fun stageArrowFilenameReturnedByStatus() = checkFilename("a -> b.txt")

    @Test
    fun stageWhitespaceFilenameReturnedByStatus() = checkFilename(" leading\ntrailing ")

    @Test
    fun stageLiteralPathspecFilename() = checkFilename(":(glob)*.txt")

    @Test
    fun renamedDestinationPreservesLiteralArrow() =
        runBlocking {
            assumeFalse(System.getProperty("os.name").startsWith("Windows"))
            val dir = repo()
            git(dir, "mv", "tracked.txt", "renamed -> file.txt")
            val change =
                service(dir)
                    .status()
                    .getOrThrow()
                    .staged
                    .single()
            assertEquals("renamed -> file.txt", change.path)
            assertEquals(GitChangeType.RENAMED, change.type)
        }

    @Test
    fun rollbackFailureDoesNotDeleteUntrackedFiles() =
        runBlocking {
            val dir = repo()
            dir.resolve("new.txt").writeText("keep on failure")
            val result = service(dir).discard(listOf("missing.txt", "new.txt"))
            assertTrue(result.isFailure)
            assertTrue(dir.resolve("new.txt").exists())
        }

    @Test
    fun allUnmergedPairsProduceSingleConflictedEntry() {
        listOf("DD", "AU", "UD", "UA", "DU", "AA", "UU").forEach { pair ->
            val change = GitStatusParser.parse("## main\u0000$pair conflict.txt\u0000").changes.single()
            assertEquals(GitChangeType.CONFLICTED, change.type)
            assertTrue(!change.staged)
        }
    }

    @Test
    fun unequalChangeBlocksKeepContextAligned() {
        val (old, new) = parseUnifiedDiff("@@ -3,3 +8,4 @@\n before\n-old\n+new\n+extra\n after\n")
        assertEquals(listOf(3, 4, null, 5), old.map { it.number })
        assertEquals(listOf(8, 9, 10, 11), new.map { it.number })
        assertEquals("after", old.last().text)
        assertEquals("after", new.last().text)
        assertEquals(1, old[1].counterpart)
        assertEquals(null, new[2].counterpart)
    }

    @Test
    fun stageQuotedFilenameReturnedByStatus() = checkFilename("quoted\"name.txt")

    @Test
    fun stageTabbedFilenameReturnedByStatus() = checkFilename("tab\tname.txt")

    @Test
    fun refreshReconcilesStageChangesAndClearsDisappearedSelection() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var status = "## main\u0000 M tracked.txt\u0000"
            val executor =
                fakeExecutor { command ->
                    when (command.getOrNull(1)) {
                        "status" -> ProcessResult(0, status, "")
                        "diff" -> ProcessResult(0, if ("--cached" in command) "staged diff" else "working diff", "")
                        else -> normalResponse(command)
                    }
                }
            val vm = GitViewModel(executor, "/nonexistent-jetaprog-audit")
            try {
                advanceUntilIdle()
                vm.select(GitChange("tracked.txt", GitChangeType.MODIFIED, false))
                advanceUntilIdle()
                status = "## main\u0000M  tracked.txt\u0000"
                vm.refresh()
                advanceUntilIdle()
                assertTrue(
                    vm.state.value.selected
                        ?.staged == true,
                )
                assertEquals("staged diff", vm.state.value.diff)
                status = "## main\u0000"
                vm.refresh()
                advanceUntilIdle()
                assertEquals(null, vm.state.value.selected)
                assertEquals("", vm.state.value.diff)
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }

    private fun checkFilename(name: String) =
        runBlocking {
            assumeFalse(System.getProperty("os.name").startsWith("Windows") && name.any { it in "<>:\"|?*\n\t" })
            val dir = repo()
            dir.resolve(name).writeText("new content\n")
            val svc = service(dir)
            val path =
                svc
                    .status()
                    .getOrThrow()
                    .unstaged
                    .single()
                    .path
            assertEquals(name, path, "Git status must return the literal filesystem path")
            svc.stage(listOf(path)).getOrThrow()
            assertTrue(git(dir, "diff", "--cached", "--name-only").isNotBlank())
        }

    @Test
    fun untrackedFileCanBeReviewedBeforeStaging() =
        runBlocking {
            val dir = repo()
            dir.resolve("new.txt").writeText("review this content\n")
            assertTrue(service(dir).diff("new.txt", false).getOrThrow().contains("review this content"))
        }

    @Test
    fun unstageBeforeInitialCommitPreservesFile() =
        runBlocking {
            val dir = repo(false)
            dir.resolve("new.txt").writeText("keep me\n")
            val svc = service(dir)
            svc.stage(listOf("new.txt")).getOrThrow()
            svc.unstage(listOf("new.txt")).getOrThrow()
            assertEquals("keep me\n", dir.resolve("new.txt").readText())
            assertTrue(
                svc
                    .status()
                    .getOrThrow()
                    .staged
                    .isEmpty(),
            )
        }

    @Test
    fun mixedRollbackRestoresTrackedFile() =
        runBlocking {
            val dir = repo()
            dir.resolve("tracked.txt").writeText("changed\n")
            dir.resolve("new.txt").writeText("disposable\n")
            service(dir).discard(listOf("tracked.txt", "new.txt")).getOrThrow()
            assertEquals(
                "original\n",
                dir.resolve("tracked.txt").readText(),
                "A successful rollback must restore tracked content",
            )
        }

    @Test
    fun revertStagedAndUnstagedEditsAndNewFiles() =
        runBlocking {
            val dir = repo()
            val svc = service(dir)
            dir.resolve("tracked.txt").writeText("staged edit\n")
            dir.resolve("added.txt").writeText("new staged file\n")
            svc.stage(listOf("tracked.txt", "added.txt")).getOrThrow()
            dir.resolve("tracked.txt").writeText("unstaged edit\n")
            dir.resolve("untracked.txt").writeText("new untracked file\n")
            svc
                .discard(
                    svc
                        .status()
                        .getOrThrow()
                        .changes
                        .map { it.path },
                ).getOrThrow()
            assertEquals("original\n", dir.resolve("tracked.txt").readText())
            assertTrue(!dir.resolve("added.txt").exists())
            assertTrue(!dir.resolve("untracked.txt").exists())
            assertTrue(svc.status().getOrThrow().isClean)
        }

    @Test
    fun revertRenameRestoresOriginalPathAndRemovesDestination() =
        runBlocking {
            val dir = repo()
            git(dir, "mv", "tracked.txt", "renamed.txt")
            val svc = service(dir)
            val change =
                svc
                    .status()
                    .getOrThrow()
                    .changes
                    .single()
            assertEquals("tracked.txt", change.originalPath)
            svc.discard(listOf(change.path)).getOrThrow()
            assertEquals("original\n", dir.resolve("tracked.txt").readText())
            assertTrue(!dir.resolve("renamed.txt").exists())
            assertTrue(svc.status().getOrThrow().isClean)
        }

    @Test
    fun addAddConflictIsReportedAsConflict() {
        val status = GitStatusParser.parse("## main\nAA conflict.txt\n")
        assertTrue(
            status.changes.all {
                it.type == GitChangeType.CONFLICTED
            },
            "AA is an unmerged conflict, not two ordinary additions",
        )
    }

    @Test
    fun diffContextOccursOncePerSide() {
        val diff = "@@ -1,3 +1,3 @@\n before\n-old\n+new\n after\n"
        val result = parseUnifiedDiff(diff)
        assertEquals(3, result.first.size, "Before pane must contain exactly three lines")
        assertEquals(3, result.second.size)
    }

    @Test
    fun latestSelectionOwnsDiffWhenResponsesFinishOutOfOrder() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val executor =
                fakeExecutor { command ->
                    if (command.getOrNull(1) == "diff") {
                        val name = command.last()
                        withContext(NonCancellable) { delay(if (name == "slow.txt") 100 else 1) }
                        ProcessResult(0, "diff for $name", "")
                    } else {
                        normalResponse(command)
                    }
                }
            val vm = GitViewModel(executor, "/nonexistent-jetaprog-audit")
            try {
                advanceUntilIdle()
                vm.select(GitChange("slow.txt", GitChangeType.MODIFIED, false))
                runCurrent()
                vm.select(GitChange("fast.txt", GitChangeType.MODIFIED, false))
                advanceUntilIdle()
                assertEquals(
                    "fast.txt",
                    vm.state.value.selected
                        ?.path,
                )
                assertEquals("diff for fast.txt", vm.state.value.diff)
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun refreshReloadsSelectedDiff() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var content = "before refresh"
            val executor =
                fakeExecutor { command ->
                    if (command.getOrNull(1) == "diff") ProcessResult(0, content, "") else normalResponse(command)
                }
            val vm = GitViewModel(executor, "/nonexistent-jetaprog-audit")
            try {
                advanceUntilIdle()
                vm.select(GitChange("tracked.txt", GitChangeType.MODIFIED, false))
                advanceUntilIdle()
                content = "after refresh"
                vm.refresh()
                advanceUntilIdle()
                assertEquals("after refresh", vm.state.value.diff)
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun successfulCommitRefreshesStatusEvenWhenPushFails() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var committed = false
            val executor =
                fakeExecutor { command ->
                    when (command.getOrNull(1)) {
                        "status" -> {
                            ProcessResult(0, "## main\u0000" + if (committed) "" else "M  tracked.txt\u0000", "")
                        }

                        "commit" -> {
                            committed = true
                            ProcessResult(0, "committed", "")
                        }

                        "push" -> {
                            ProcessResult(128, "", "No configured push destination")
                        }

                        else -> {
                            normalResponse(command)
                        }
                    }
                }
            val vm = GitViewModel(executor, "/nonexistent-jetaprog-audit")
            try {
                advanceUntilIdle()
                vm.setCommitMessage("Commit audit change")
                vm.commitAndPush()
                advanceUntilIdle()
                assertTrue(committed)
                assertEquals("Committed locally, but push failed: No configured push destination", vm.state.value.error)
                assertTrue(
                    vm.state.value.staged
                        .isEmpty(),
                    "A completed commit must not remain listed as staged",
                )
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun busyOperationRejectsSecondMutation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val commands = mutableListOf<List<String>>()
            val executor =
                fakeExecutor { command ->
                    commands += command
                    if (command.getOrNull(1) == "commit") {
                        withContext(NonCancellable) { delay(100) }
                        ProcessResult(0, "committed", "")
                    } else {
                        stagedStatusResponse(command)
                    }
                }
            val vm = GitViewModel(executor, "/nonexistent-jetaprog-audit")
            try {
                advanceUntilIdle()
                vm.setCommitMessage("Commit audit change")
                vm.commit()
                runCurrent()
                vm.checkoutBranch("feature")
                advanceUntilIdle()

                assertEquals(1, commands.count { it.getOrNull(1) == "commit" })
                assertTrue(commands.none { it.getOrNull(1) == "checkout" })
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun coloredGitConfigurationDoesNotHideDiff() =
        runBlocking {
            val dir = repo()
            git(dir, "config", "color.ui", "always")
            dir.resolve("tracked.txt").writeText("changed\n")
            val svc = service(dir)

            val diff = svc.diff("tracked.txt", staged = false).getOrThrow()
            val (oldLines, newLines) = parseUnifiedDiff(diff)

            assertTrue(oldLines.any { it.text == "original" })
            assertTrue(newLines.any { it.text == "changed" })
            assertTrue(svc.lineStatus("tracked.txt").getOrThrow().isNotEmpty())
        }

    private fun fakeExecutor(response: suspend (List<String>) -> ProcessResult): ProcessExecutor {
        val executor = mockk<ProcessExecutor>()
        coEvery { executor.execute(any(), any(), any(), any()) } coAnswers { Result.success(response(firstArg())) }
        coEvery { executor.executeRaw(any()) } coAnswers { Result.success(response(firstArg<ProcessConfig>().command)) }
        return executor
    }

    private fun stagedStatusResponse(command: List<String>): ProcessResult =
        if (command.getOrNull(1) == "status") {
            ProcessResult(0, "## main\u0000M  tracked.txt\u0000", "")
        } else {
            normalResponse(command)
        }

    private fun normalResponse(command: List<String>): ProcessResult =
        ProcessResult(
            0,
            when (command.getOrNull(1)) {
                "rev-parse" -> "true"
                "status" -> "## main\u0000 M tracked.txt\u0000"
                "branch" -> "*\tmain\n"
                else -> ""
            },
            "",
        )
}
