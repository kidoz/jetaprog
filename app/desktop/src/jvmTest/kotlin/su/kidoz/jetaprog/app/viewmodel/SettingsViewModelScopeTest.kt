package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.settings.SettingsScope
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.Theme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for the settings scope handling: switching scope must drop in-flight
 * edits (they belong to the scope being edited) and load the raw values of the
 * newly selected scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SettingsViewModelScopeTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun switchingScopeDropsPendingEditsAndLoadsRawScopeValues() =
        runTest {
            val service = mockk<SettingsService>()
            val workspaceRaw =
                AllSettings.DEFAULT.copy(appearance = AppearanceSettings.DEFAULT.copy(theme = Theme.SYSTEM))
            coEvery { service.getRawSettings(SettingsScope.WORKSPACE) } returns workspaceRaw
            val viewModel = SettingsViewModel(service)

            // Make an edit while in the default USER scope, then switch.
            viewModel.dispatch(SettingsIntent.SetTheme(Theme.LIGHT))
            viewModel.dispatch(SettingsIntent.SetScope(SettingsScope.WORKSPACE))

            val state = viewModel.state.first { it.activeScope == SettingsScope.WORKSPACE && !it.isLoading }
            assertEquals(SettingsScope.WORKSPACE, state.activeScope)
            assertNull(state.pendingChanges)
            assertFalse(state.hasUnsavedChanges)
            // The form now shows the raw values of the WORKSPACE scope.
            assertEquals(Theme.SYSTEM, state.appearance.theme)
        }

    @Test
    fun openingTheDialogLoadsRawUserScopeValues() =
        runTest {
            val service = mockk<SettingsService>()
            val userRaw =
                AllSettings.DEFAULT.copy(appearance = AppearanceSettings.DEFAULT.copy(fontSize = 15))
            coEvery { service.getRawSettings(SettingsScope.USER) } returns userRaw
            val viewModel = SettingsViewModel(service)

            viewModel.dispatch(SettingsIntent.Show)

            val state = viewModel.state.first { !it.isLoading }
            assertEquals(15, state.appearance.fontSize)
        }
}
