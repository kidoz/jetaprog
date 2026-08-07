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
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.CodeAction
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.plugins.api.services.WorkspaceEdit
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the code-action path into the quick-fix popup.
 *
 * No bundled provider produces code actions yet, so these tests stand in for a
 * language server and pin the contract the editor relies on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelQuickFixTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val languageRegistry = mockk<LanguageRegistry>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("fun main() {}\n")
        coEvery { fileSystem.writeText(any(), any(), any()) } returns Result.success(Unit)
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun openEditor(): EditorViewModel {
        val viewModel =
            EditorViewModel(
                fileSystem = fileSystem,
                settingsService = settingsService,
                languageRegistry = languageRegistry,
            )
        viewModel.dispatch(EditorIntent.OpenFile("/project/src/Main.kt"))
        viewModel.state.first { it.tabs.isNotEmpty() }
        return viewModel
    }

    @Test
    fun codeActionsAppearAsQuickFixes() =
        runTest {
            val viewModel = openEditor()
            val uri =
                viewModel.state.value.activeDocumentUri!!
                    .value
            coEvery { languageRegistry.provideCodeActions(any(), any(), any()) } returns
                listOf(
                    CodeAction(
                        title = "Add missing braces",
                        edit =
                            WorkspaceEdit(
                                changes =
                                    mapOf(
                                        uri to
                                            listOf(
                                                TextEdit(
                                                    range = TextRange(TextPosition(0, 0), TextPosition(0, 3)),
                                                    newText = "val",
                                                ),
                                            ),
                                    ),
                            ),
                    ),
                )

            viewModel.dispatch(EditorIntent.RequestQuickFixes)
            val state = viewModel.state.first { it.quickFixState.isVisible }

            assertEquals(listOf("Add missing braces"), state.quickFixState.fixes.map { it.title })

            viewModel.dispatch(EditorIntent.ApplyQuickFix(0))
            val applied = viewModel.state.first { !it.quickFixState.isVisible }
            assertEquals("val main() {}\n", applied.content, "the action's edit must be applied")
            viewModel.dispose()
        }

    @Test
    fun multiFileCodeActionsAreApplied() =
        runTest {
            val viewModel = openEditor()
            val uri =
                viewModel.state.value.activeDocumentUri!!
                    .value
            val edit = TextEdit(range = TextRange(TextPosition(0, 0), TextPosition(0, 3)), newText = "val")
            coEvery { languageRegistry.provideCodeActions(any(), any(), any()) } returns
                listOf(
                    CodeAction(
                        title = "Rename across files",
                        edit =
                            WorkspaceEdit(
                                changes = mapOf(uri to listOf(edit), "file:///other/File.kt" to listOf(edit)),
                            ),
                    ),
                )

            viewModel.dispatch(EditorIntent.RequestQuickFixes)
            viewModel.state.first { it.quickFixState.isVisible }
            viewModel.dispatch(EditorIntent.ApplyQuickFix(0))
            viewModel.state.first { !it.quickFixState.isVisible }

            assertEquals("val main() {}\n", viewModel.state.value.content)
            coVerify { fileSystem.writeText("/other/File.kt", "val main() {}\n", any()) }
            viewModel.dispose()
        }

    @Test
    fun quickFixPopupClosesWithoutApplying() =
        runTest {
            val viewModel = openEditor()
            val uri =
                viewModel.state.value.activeDocumentUri!!
                    .value
            coEvery { languageRegistry.provideCodeActions(any(), any(), any()) } returns
                listOf(
                    CodeAction(
                        title = "Some fix",
                        edit =
                            WorkspaceEdit(
                                changes =
                                    mapOf(
                                        uri to
                                            listOf(
                                                TextEdit(
                                                    range = TextRange(TextPosition(0, 0), TextPosition(0, 3)),
                                                    newText = "val",
                                                ),
                                            ),
                                    ),
                            ),
                    ),
                )
            viewModel.dispatch(EditorIntent.RequestQuickFixes)
            viewModel.state.first { it.quickFixState.isVisible }

            viewModel.dispatch(EditorIntent.DismissQuickFixes)

            assertFalse(viewModel.state.value.quickFixState.isVisible)
            assertTrue(
                viewModel.state.value.content
                    .startsWith("fun main()"),
                "content must be unchanged",
            )
            viewModel.dispose()
        }

    @Test
    fun caretLandsAfterAnAcceptedCompletion() =
        runTest {
            coEvery { fileSystem.readText(any(), any()) } returns Result.success("val x = wid\n")
            val viewModel = openEditor()
            viewModel.dispatch(EditorIntent.MoveCursor(TextPosition(0, 11)))

            viewModel.dispatch(
                EditorIntent.ApplyCompletion(
                    CompletionItem(label = "widget", insertText = "widget"),
                ),
            )

            val state = viewModel.state.first { it.content.contains("widget") }
            assertEquals("val x = widget\n", state.content)
            // Caret sits after the inserted word, ready for the next keystroke.
            assertEquals(TextPosition(0, 14), state.cursor.position)
            viewModel.dispose()
        }
}
