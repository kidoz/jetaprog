package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TextSearchViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)

    /** In-memory file contents served by [fileSystem]; writes land here too. */
    private val contents =
        mutableMapOf(
            "/proj/src/a.kt" to "val old = 1\n",
            "/proj/src/b.kt" to "no match\n",
        )
    private val fileSystem = stubFileSystem()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubFileSystem(): FileSystem {
        val fileSystem = mockk<FileSystem>()
        coEvery { fileSystem.listDirectory("/proj") } returns
            Result.success(listOf(fileEntry("src", "/proj/src", isDirectory = true)))
        coEvery { fileSystem.listDirectory("/proj/src") } returns
            Result.success(
                listOf(
                    fileEntry("a.kt", "/proj/src/a.kt", isFile = true),
                    fileEntry("b.kt", "/proj/src/b.kt", isFile = true),
                ),
            )
        coEvery { fileSystem.readText(any(), any()) } answers {
            Result.success(contents[arg<String>(0)] ?: "")
        }
        coEvery { fileSystem.writeText(any(), any(), any()) } answers {
            contents[arg<String>(0)] = arg<String>(1)
            Result.success(Unit)
        }
        return fileSystem
    }

    private fun fileEntry(
        name: String,
        path: String,
        isDirectory: Boolean = false,
        isFile: Boolean = false,
    ): FileEntry =
        FileEntry(
            name = name,
            path = path,
            isDirectory = isDirectory,
            isFile = isFile,
            isSymbolicLink = false,
            size = 10L,
            lastModified = 0L,
            isHidden = false,
        )

    @Test
    fun searchPopulatesPerFileResults() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)

            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()

            val state = viewModel.state.first { it.searched }
            assertEquals(1, state.totalMatches)
            assertEquals(listOf("/proj/src/a.kt"), state.results.map { it.filePath })
            viewModel.dispose()
        }

    @Test
    fun confirmReplaceAllRewritesMatchingFilesAndReportsSummary() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)
            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()
            viewModel.setReplacement("new")

            viewModel.confirmReplaceAll()

            val summary = viewModel.state.first { it.replaceSummary != null }.replaceSummary
            assertEquals(listOf("/proj/src/a.kt"), summary?.replacedPaths)
            assertEquals(1, summary?.occurrences)
            assertTrue(summary?.skippedPaths?.isEmpty() == true)
            assertEquals("val new = 1\n", contents["/proj/src/a.kt"])
            viewModel.dispose()
        }

    @Test
    fun skippedPathsAreReportedAndNotWritten() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)
            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()
            viewModel.setReplacement("new")

            viewModel.confirmReplaceAll(skipPaths = setOf("/proj/src/a.kt"))

            val summary = viewModel.state.first { it.replaceSummary != null }.replaceSummary
            assertEquals(emptyList(), summary?.replacedPaths)
            assertEquals(listOf("/proj/src/a.kt"), summary?.skippedPaths)
            assertEquals("val old = 1\n", contents["/proj/src/a.kt"], "skipped file must stay untouched")
            viewModel.dispose()
        }

    @Test
    fun replaceConfirmationClosesOnNewQuery() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)
            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()
            viewModel.state.first { it.searched }

            viewModel.requestReplaceAll()
            assertTrue(viewModel.state.value.awaitingReplaceConfirmation)

            viewModel.cancelReplaceAll()
            assertFalse(viewModel.state.value.awaitingReplaceConfirmation)

            viewModel.requestReplaceAll()
            viewModel.setQuery("old!")
            assertFalse(viewModel.state.value.awaitingReplaceConfirmation)
            viewModel.dispose()
        }

    @Test
    fun emptyQueryClearsResultsAndSummary() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)
            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()
            viewModel.setReplacement("new")
            viewModel.confirmReplaceAll()
            viewModel.state.first { it.replaceSummary != null }

            viewModel.setQuery("")

            val state = viewModel.state.value
            assertEquals(0, state.totalMatches)
            assertFalse(state.searched)
            assertNull(state.replaceSummary)
            viewModel.dispose()
        }

    @Test
    fun explicitSearchAndReplaceRecordPersistableHistories() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)
            viewModel.seedHistories(searches = listOf("seeded"), replacements = listOf("seeded-new"))

            viewModel.setQuery("old")
            scheduler.advanceUntilIdle()
            viewModel.setReplacement("new")
            viewModel.search()
            viewModel.confirmReplaceAll()
            viewModel.state.first { it.replaceSummary != null }

            val state = viewModel.state.value
            assertEquals(listOf("old", "seeded"), state.searchHistory)
            assertEquals(listOf("new", "seeded-new"), state.replaceHistory)
            viewModel.dispose()
        }

    @Test
    fun debouncedTypingDoesNotRecordSearchHistory() =
        runTest {
            val viewModel = TextSearchViewModel("/proj", fileSystem, testDispatcher)

            viewModel.setQuery("typed-not-executed")
            scheduler.advanceUntilIdle()

            assertTrue(
                viewModel.state.value.searchHistory
                    .isEmpty(),
            )
            viewModel.dispose()
        }
}
