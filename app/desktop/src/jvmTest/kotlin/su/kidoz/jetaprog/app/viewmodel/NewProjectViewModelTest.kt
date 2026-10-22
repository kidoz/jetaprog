package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.project.ProjectLanguage
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectEffect
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectIntent
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewProjectViewModelTest {
    private val executor: ProcessExecutor = mockk()
    private lateinit var projectsDir: File
    private lateinit var viewModel: NewProjectViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectsDir = createTempDirectory("new-project-view-model").toFile()
        viewModel = NewProjectViewModel(JvmFileSystem(), executor)
        viewModel.dispatch(NewProjectIntent.Show)
        viewModel.dispatch(NewProjectIntent.SetProjectName("sample"))
        viewModel.dispatch(NewProjectIntent.SetProjectLocation(projectsDir.absolutePath))
        viewModel.dispatch(NewProjectIntent.SetLanguage(ProjectLanguage.RUST))
        viewModel.dispatch(NewProjectIntent.SetPackageName("com.example.sample"))
    }

    @AfterTest
    fun tearDown() {
        viewModel.dispose()
        Dispatchers.resetMain()
        projectsDir.deleteRecursively()
    }

    @Test
    fun successfulGitInitializationCompletesTheWizard() =
        runTest {
            coEvery { executor.execute(any(), any(), any(), any()) } returns
                Result.success(ProcessResult(0, "Initialized empty repository", ""))

            viewModel.dispatch(NewProjectIntent.Create)

            val effect = assertIs<NewProjectEffect.ProjectCreated>(viewModel.effects.first())
            assertEquals(File(projectsDir, "sample").absolutePath, effect.path)
            assertFalse(viewModel.state.value.isCreating)
            assertFalse(viewModel.state.value.isVisible)
            coVerify(exactly = 1) { executor.execute(listOf("git", "init"), effect.path, any(), any()) }
        }

    @Test
    fun gitFailureKeepsWizardOpenAndAllowsRetryWithoutGit() =
        runTest {
            coEvery { executor.execute(any(), any(), any(), any()) } returns
                Result.success(ProcessResult(128, "", "fatal: cannot create repository"))

            viewModel.dispatch(NewProjectIntent.Create)

            val error = assertIs<NewProjectEffect.ShowError>(viewModel.effects.first())
            assertContains(error.message, "Failed to initialize Git repository")
            assertContains(error.message, "fatal: cannot create repository")
            assertTrue(viewModel.state.value.isVisible)
            assertFalse(viewModel.state.value.isCreating)
            assertFalse(File(projectsDir, "sample").exists())

            viewModel.dispatch(NewProjectIntent.SetGitInit(false))
            viewModel.dispatch(NewProjectIntent.Create)

            val created = assertIs<NewProjectEffect.ProjectCreated>(viewModel.effects.first())
            assertTrue(File(created.path, "Cargo.toml").isFile)
            assertFalse(File(created.path, ".git").exists())
            assertFalse(viewModel.state.value.isVisible)
            coVerify(exactly = 1) { executor.execute(any(), any(), any(), any()) }
        }
}
