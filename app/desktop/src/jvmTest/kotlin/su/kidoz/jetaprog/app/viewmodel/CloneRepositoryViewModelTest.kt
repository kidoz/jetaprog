package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryEffect
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryIntent
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the Clone Repository view model: validation, name derivation, and
 * the clone run driving state and effects through a mocked process executor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class CloneRepositoryViewModelTest {
    private val executor: ProcessExecutor = mockk()
    private val process: RunningProcess = mockk()
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempDir = Files.createTempDirectory("jetaprog-clone-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun shownViewModel(): CloneRepositoryViewModel {
        val viewModel = CloneRepositoryViewModel(executor, defaultDestination = tempDir.absolutePath)
        viewModel.dispatch(CloneRepositoryIntent.Show)
        return viewModel
    }

    @Test
    fun projectNameIsDerivedFromUrl() {
        val viewModel = CloneRepositoryViewModel(executor, defaultDestination = "/tmp")
        assertEquals(
            "sample",
            viewModel.deriveProjectName("https://github.com/user/sample.git", keepUserValue = false),
        )
        assertEquals(
            "sample",
            viewModel.deriveProjectName("git@host:user/sample/", keepUserValue = false),
        )
    }

    @Test
    fun showPrefillsDestination() {
        val viewModel = shownViewModel()
        assertTrue(viewModel.state.value.isVisible)
        assertEquals(tempDir.absolutePath, viewModel.state.value.destinationDirectory)
    }

    @Test
    fun blankUrlIsRejectedWithoutStartingProcess() =
        runTest {
            val viewModel = shownViewModel()
            viewModel.dispatch(CloneRepositoryIntent.Clone)

            val state = viewModel.state.value
            assertNotNull(state.error)
            assertFalse(state.isCloning)
        }

    @Test
    fun existingTargetFolderIsRejected() =
        runTest {
            val existingName = "taken"
            File(tempDir, existingName).mkdirs()
            val viewModel = shownViewModel()
            viewModel.dispatch(CloneRepositoryIntent.SetRepositoryUrl("https://example.com/user/$existingName.git"))
            viewModel.dispatch(CloneRepositoryIntent.Clone)

            val state = viewModel.state.value
            assertNotNull(state.error)
            assertFalse(state.isCloning)
        }

    @Test
    fun successfulCloneEmitsClonedEffect() =
        runTest {
            coEvery { executor.start(any()) } returns Result.success(process)
            every { process.output } returns
                flowOf(
                    ProcessOutput.Stderr("Cloning into 'sample'..."),
                    ProcessOutput.Stderr("Receiving objects: 100% (12/12), done."),
                    ProcessOutput.Exited(0),
                )
            coEvery { process.waitFor() } returns 0

            val viewModel = shownViewModel()
            viewModel.dispatch(CloneRepositoryIntent.SetRepositoryUrl("https://example.com/user/sample.git"))
            viewModel.dispatch(CloneRepositoryIntent.Clone)

            // Awaiting the effect also waits out the IO-dispatched clone.
            val effect = viewModel.effects.first()
            val cloned = effect as CloneRepositoryEffect.Cloned
            assertEquals(File(tempDir, "sample").absolutePath, cloned.projectPath)

            val state = viewModel.state.value
            assertFalse(state.isVisible)
            assertNull(state.error)
        }

    @Test
    fun failedCloneSurfacesError() =
        runTest {
            coEvery { executor.start(any()) } returns Result.success(process)
            every { process.output } returns
                flowOf(
                    ProcessOutput.Stderr("fatal: repository 'https://example.com/nope.git' not found"),
                    ProcessOutput.Exited(128),
                )
            coEvery { process.waitFor() } returns 128

            val viewModel = shownViewModel()
            viewModel.dispatch(CloneRepositoryIntent.SetRepositoryUrl("https://example.com/nope.git"))
            viewModel.dispatch(CloneRepositoryIntent.Clone)

            val state = viewModel.state.first { it.error != null }
            assertFalse(state.isCloning)
            assertTrue(state.error.orEmpty().contains("exit code 128"))
        }
}
