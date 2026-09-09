package su.kidoz.jetaprog.app.navigation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.navigation.NavigationEffect
import su.kidoz.jetaprog.app.ui.navigation.NavigationIntent
import su.kidoz.jetaprog.app.ui.navigation.NavigationViewModel
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.NavigationTarget
import su.kidoz.jetaprog.editor.navigation.StructureItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Alt+Down / Alt+Up, Go to Implementation and Go to Type Declaration had default
 * shortcuts but no handler, so pressing them did nothing.
 */
class NavigationViewModelShortcutsTest {
    private val navigationService = mockk<NavigationService>()
    private val file = "/w/Greeter.kt"

    private fun target(
        name: String,
        line: Int,
        kind: NavigationSymbolKind = NavigationSymbolKind.METHOD,
        path: String = file,
    ) = NavigationTarget(
        name = name,
        qualifiedName = name,
        kind = kind,
        filePath = path,
        position = TextPosition(line, 4),
    )

    private val structure =
        listOf(
            StructureItem(
                target = target("Greeter", 1, NavigationSymbolKind.CLASS),
                children =
                    listOf(
                        StructureItem(target = target("greet", 3)),
                        StructureItem(target = target("name", 5, NavigationSymbolKind.PROPERTY)),
                        StructureItem(target = target("wave", 8)),
                    ),
            ),
        )

    @Test
    fun nextAndPreviousMethodStepThroughFunctionsInTheStructure() =
        runTest {
            coEvery { navigationService.getFileStructure(file) } returns structure
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.GoToNextMethod(file, line = 3))
            val next = viewModel.effects.first() as NavigationEffect.NavigateTo
            assertEquals(8, next.line)

            viewModel.processIntent(NavigationIntent.GoToPreviousMethod(file, line = 8))
            val previous = viewModel.effects.first() as NavigationEffect.NavigateTo
            assertEquals(3, previous.line)

            viewModel.processIntent(NavigationIntent.GoToNextMethod(file, line = 8))
            assertTrue(viewModel.effects.first() is NavigationEffect.ShowNotification)
        }

    @Test
    fun singleImplementationNavigatesAndSeveralOpenAChooser() =
        runTest {
            coEvery { navigationService.getImplementations(file, TextPosition(3, 0)) } returns
                listOf(target("Impl", 20, path = "/w/Impl.kt"))
            coEvery { navigationService.getImplementations(file, TextPosition(4, 0)) } returns
                listOf(target("A", 2, path = "/w/A.kt"), target("B", 9, path = "/w/B.kt"))
            coEvery { navigationService.getImplementations(file, TextPosition(5, 0)) } returns emptyList()
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.GoToImplementation(file, 3, 0))
            val jump = viewModel.effects.first() as NavigationEffect.NavigateTo
            assertEquals("/w/Impl.kt", jump.filePath)

            viewModel.processIntent(NavigationIntent.GoToImplementation(file, 4, 0))
            assertTrue(viewModel.state.value.isUsagesPopupVisible)
            assertEquals(
                2,
                viewModel.state.value.usagesResult
                    ?.totalCount,
            )

            viewModel.processIntent(NavigationIntent.GoToImplementation(file, 5, 0))
            assertTrue(viewModel.effects.first() is NavigationEffect.ShowNotification)
        }

    @Test
    fun refreshHistoryReflectsWhatTheServiceCanDo() =
        runTest {
            coEvery { navigationService.canGoBack() } returns true
            coEvery { navigationService.canGoForward() } returns false
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.RefreshHistory)

            assertTrue(viewModel.state.value.canGoBack)
            assertEquals(false, viewModel.state.value.canGoForward)
        }

    @Test
    fun typeDeclarationNavigatesToTheTypeOrReportsNone() =
        runTest {
            coEvery { navigationService.getTypeDefinition(file, TextPosition(3, 0)) } returns
                target("Greeting", 12, NavigationSymbolKind.CLASS)
            coEvery { navigationService.getTypeDefinition(file, TextPosition(4, 0)) } returns null
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.GoToTypeDeclaration(file, 3, 0))
            assertEquals(12, (viewModel.effects.first() as NavigationEffect.NavigateTo).line)

            viewModel.processIntent(NavigationIntent.GoToTypeDeclaration(file, 4, 0))
            assertTrue(viewModel.effects.first() is NavigationEffect.ShowNotification)
        }
}
