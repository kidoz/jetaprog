package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.LintService
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelLintTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val lintService = mockk<LintService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("fun main() {}")
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openFilePublishesLintDiagnostics() =
        runTest {
            every { lintService.getConfiguration() } returns LintConfiguration()
            coEvery { lintService.lintFile(any(), any(), any()) } returns listOf(lintResult("syntax error"))
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.OpenFile("/project/src/Main.kt"))
            val diagnostics = viewModel.state.first { it.diagnostics.isNotEmpty() }.diagnostics

            assertEquals("syntax error", diagnostics.single().message)
            assertEquals(DiagnosticSeverity.ERROR, diagnostics.single().severity)
            viewModel.dispose()
        }

    @Test
    fun typingReLintsWhenLintOnTypeEnabled() =
        runTest {
            every { lintService.getConfiguration() } returns LintConfiguration(lintOnType = true)
            coEvery { lintService.lintFile(any(), any(), any()) } returns listOf(lintResult("stale"))
            val viewModel = editorViewModel()
            viewModel.dispatch(EditorIntent.OpenFile("/project/src/Main.kt"))
            viewModel.state.first { it.diagnostics.isNotEmpty() }

            val editedContent = "fun main() { val x = 1 }"
            coEvery { lintService.lintFile(any(), any(), any()) } returns listOf(lintResult("fresh"))
            viewModel.dispatch(EditorIntent.UpdateContent(editedContent))
            val diagnostics =
                viewModel.state
                    .first { state -> state.diagnostics.any { it.message == "fresh" } }
                    .diagnostics

            assertEquals("fresh", diagnostics.single().message)
            coVerify { lintService.lintFile(any(), any(), editedContent) }
            viewModel.dispose()
        }

    @Test
    fun lintSkippedWhenDisabled() =
        runTest {
            every { lintService.getConfiguration() } returns LintConfiguration(enabled = false)
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.OpenFile("/project/src/Main.kt"))
            viewModel.state.first { it.tabs.isNotEmpty() }

            assertTrue(
                viewModel.state.value.diagnostics
                    .isEmpty(),
            )
            coVerify(exactly = 0) { lintService.lintFile(any(), any(), any()) }
            viewModel.dispose()
        }

    private fun editorViewModel(): EditorViewModel =
        EditorViewModel(
            fileSystem = fileSystem,
            settingsService = settingsService,
            lintService = lintService,
        )

    private fun lintResult(message: String): LintResult =
        LintResult(
            ruleId = LintRuleId.of("kotlin", "syntax-error"),
            message = message,
            range = TextRange(TextPosition(0, 0), TextPosition(0, 1)),
            severity = LintSeverity.ERROR,
            category = LintCategory.CORRECTNESS,
        )
}
