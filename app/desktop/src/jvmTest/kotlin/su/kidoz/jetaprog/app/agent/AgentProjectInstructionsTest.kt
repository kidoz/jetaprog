package su.kidoz.jetaprog.app.agent

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.agent.AGENTS_MAX_CHARS
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentProjectInstructionsTest {
    private val fileSystem = mockk<FileSystem>()

    @Test
    fun missingEmptyUnreadableAndTruncatedInstructionsAreExplicit() =
        runTest {
            coEvery { fileSystem.exists("/project/AGENTS.md") } returns false
            assertNotNull(loadProjectInstructions(fileSystem, "/project").notice)
            coEvery { fileSystem.exists("/project/AGENTS.md") } returns true
            coEvery { fileSystem.readText("/project/AGENTS.md", any()) } returns Result.success(" \n")
            val empty = loadProjectInstructions(fileSystem, "/project")
            assertNull(empty.text)
            assertFalse(empty.truncated)
            assertEquals("Root AGENTS.md is empty.", empty.notice)
            coEvery { fileSystem.readText("/project/AGENTS.md", any()) } returns
                Result.failure(IllegalStateException("unreadable"))
            val failed = loadProjectInstructions(fileSystem, "/project")
            assertNull(failed.text)
            assertTrue(failed.notice!!.contains("Could not read"))
            coEvery { fileSystem.readText("/project/AGENTS.md", any()) } returns
                Result.success("x".repeat(AGENTS_MAX_CHARS + 1))
            val capped = loadProjectInstructions(fileSystem, "/project/")
            assertTrue(capped.truncated)
            assertEquals(AGENTS_MAX_CHARS, capped.text!!.length)
        }

    @Test
    fun cancellationIsNotPresentedAsAnUnreadableFile() =
        runTest {
            coEvery { fileSystem.exists(any()) } throws CancellationException("closed inspector")
            assertFailsWith<CancellationException> { loadProjectInstructions(fileSystem, "/project") }
        }
}
