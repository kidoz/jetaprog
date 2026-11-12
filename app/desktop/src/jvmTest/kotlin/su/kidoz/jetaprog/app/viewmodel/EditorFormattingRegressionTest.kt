package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.support.formatters.CodeFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorFormattingRegressionTest {
    private class DelayedFormatter(
        private val formattedText: String,
    ) : CodeFormatter {
        override val languageId: LanguageId = LanguageId.KOTLIN
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        private val release = CountDownLatch(1)

        override fun format(
            content: String,
            options: FormattingOptions,
        ): FormattingResult = awaitResult()

        override fun formatRange(
            content: String,
            range: TextRange,
            options: FormattingOptions,
        ): FormattingResult = awaitResult()

        private fun awaitResult(): FormattingResult {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Timed out waiting to finish formatting" }
            finished.countDown()
            return FormattingResult.Success(formattedText, emptyList())
        }

        fun release() {
            release.countDown()
        }
    }

    private fun formattingTest(
        intent: EditorIntent,
        formattedText: String = "formatted",
        initialCursor: Cursor = Cursor.Zero,
        expectApplied: Boolean = false,
        whileFormatting: suspend (EditorViewModel) -> Unit = {},
        assertions: (EditorViewModel, Long) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fileSystem = mockk<FileSystem>()
        coEvery { fileSystem.readText("/a.kt", any()) } returns Result.success("one\ntwo\nthree\nfour\nfive")
        coEvery { fileSystem.readText("/b.kt", any()) } returns Result.success("other")
        val settingsService = mockk<SettingsService>()
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
        val formatter = DelayedFormatter(formattedText)
        val viewModel = EditorViewModel(fileSystem, settingsService)
        val registration = FormatterRegistry.register(formatter)
        try {
            viewModel.perform(EditorIntent.OpenFile("/a.kt"))
            if (initialCursor.anchor == null) {
                viewModel.perform(EditorIntent.MoveCursor(initialCursor.position))
            } else {
                val anchor = requireNotNull(initialCursor.anchor)
                viewModel.perform(
                    if (anchor <= initialCursor.position) {
                        EditorIntent.Select(TextRange(anchor, initialCursor.position))
                    } else {
                        EditorIntent.Select(TextRange(initialCursor.position, anchor), reversed = true)
                    },
                )
            }
            val caretSyncVersion = viewModel.state.value.caretSyncVersion

            viewModel.dispatch(intent)
            runCurrent()
            assertTrue(formatter.started.await(5, TimeUnit.SECONDS), "formatter did not start")
            whileFormatting(viewModel)
            formatter.release()
            assertTrue(formatter.finished.await(5, TimeUnit.SECONDS), "formatter did not finish")
            withContext(Dispatchers.Default) {}
            advanceUntilIdle()
            if (expectApplied) {
                withTimeout(5_000) { viewModel.state.first { it.content == formattedText } }
            }

            assertions(viewModel, caretSyncVersion)
        } finally {
            formatter.release()
            viewModel.dispose()
            registration.dispose()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun documentResultDoesNotOverwriteNewerTyping() =
        formattingTest(
            intent = EditorIntent.FormatDocument,
            whileFormatting = { it.perform(EditorIntent.UpdateContent("newer typing")) },
        ) { viewModel, _ ->
            assertEquals("newer typing", viewModel.state.value.content)
        }

    @Test
    fun documentResultIsNotInstalledIntoAnotherActiveDocument() =
        formattingTest(
            intent = EditorIntent.FormatDocument,
            whileFormatting = { it.perform(EditorIntent.OpenFile("/b.kt")) },
        ) { viewModel, _ ->
            assertEquals("other", viewModel.state.value.content)
            assertEquals(
                "/b.kt",
                viewModel.state.value.activeDocumentUri
                    ?.value
                    ?.removePrefix("file://"),
            )
        }

    @Test
    fun selectionResultDoesNotOverwriteNewerTyping() =
        formattingTest(
            intent = EditorIntent.FormatSelection(TextRange(TextPosition.Zero, TextPosition(0, 3))),
            whileFormatting = { it.perform(EditorIntent.UpdateContent("newer typing")) },
        ) { viewModel, _ ->
            assertEquals("newer typing", viewModel.state.value.content)
        }

    @Test
    fun selectionResultIsNotInstalledIntoAnotherActiveDocument() =
        formattingTest(
            intent = EditorIntent.FormatSelection(TextRange(TextPosition.Zero, TextPosition(0, 3))),
            whileFormatting = { it.perform(EditorIntent.OpenFile("/b.kt")) },
        ) { viewModel, _ ->
            assertEquals("other", viewModel.state.value.content)
            assertEquals(
                "/b.kt",
                viewModel.state.value.activeDocumentUri
                    ?.value
                    ?.removePrefix("file://"),
            )
        }

    @Test
    fun acceptedResultClampsCursorAndSelectionAndSynchronizesTheEditor() =
        formattingTest(
            intent = EditorIntent.FormatDocument,
            formattedText = "x",
            initialCursor = Cursor(position = TextPosition(4, 4), anchor = TextPosition(2, 2)),
            expectApplied = true,
        ) { viewModel, previousCaretSyncVersion ->
            assertEquals("x", viewModel.state.value.content)
            assertEquals(TextPosition(0, 1), viewModel.state.value.cursor.position)
            assertFalse(viewModel.state.value.cursor.hasSelection)
            assertTrue(viewModel.state.value.caretSyncVersion > previousCaretSyncVersion)
        }

    @Test
    fun acceptedResultPreservesBackwardSelectionDirection() =
        formattingTest(
            intent = EditorIntent.FormatDocument,
            formattedText = "ab\ncd",
            initialCursor = Cursor(position = TextPosition(1, 1), anchor = TextPosition(3, 3)),
            expectApplied = true,
        ) { viewModel, _ ->
            assertEquals(TextPosition(1, 1), viewModel.state.value.cursor.position)
            assertEquals(TextPosition(1, 2), viewModel.state.value.cursor.anchor)
            assertTrue(viewModel.state.value.cursor.hasSelection)
        }
}
