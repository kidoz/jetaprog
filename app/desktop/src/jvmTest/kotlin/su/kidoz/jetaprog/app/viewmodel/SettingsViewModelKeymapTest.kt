package su.kidoz.jetaprog.app.viewmodel

import androidx.compose.ui.input.key.Key
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.keymap.CommandActions
import su.kidoz.jetaprog.app.keymap.KeyboardShortcut
import su.kidoz.jetaprog.app.keymap.toSpec
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsState
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the Keymap settings intents: adding a custom shortcut creates a
 * pending change, resetting drops it, and loading seeds the keymap section.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelKeymapTest {
    private val settingsService = mockk<SettingsService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWith(loaded: AllSettings = AllSettings()): SettingsViewModel {
        every { settingsService.getCurrentSettings() } returns loaded
        return SettingsViewModel(settingsService)
    }

    @Test
    fun setShortcutCreatesPendingKeymapChange() =
        runTest {
            val viewModel = viewModelWith()
            viewModel.dispatch(SettingsIntent.LoadSettings(AllSettings()))
            val spec = KeyboardShortcut(Key.K, ctrl = true, shift = true).toSpec()

            viewModel.dispatch(SettingsIntent.SetShortcut(CommandActions.COMMAND_PALETTE, spec))

            val state = viewModel.state.value
            assertTrue(state.hasUnsavedChanges)
            assertEquals(
                spec,
                state.pendingChanges
                    ?.keymap
                    ?.customShortcuts
                    ?.get(CommandActions.COMMAND_PALETTE),
            )
            assertEquals(
                spec,
                state.effectiveSettings.keymap.customShortcuts[CommandActions.COMMAND_PALETTE],
            )
            viewModel.dispose()
        }

    @Test
    fun resetShortcutDropsTheOverrideAndKeepsOthers() =
        runTest {
            val viewModel = viewModelWith()
            viewModel.dispatch(SettingsIntent.LoadSettings(AllSettings()))
            val first = KeyboardShortcut(Key.P, ctrl = true).toSpec()
            val second = KeyboardShortcut(Key.R, alt = true).toSpec()
            viewModel.dispatch(SettingsIntent.SetShortcut(CommandActions.COMMAND_PALETTE, first))
            viewModel.dispatch(SettingsIntent.SetShortcut("navigation.rename", second))

            viewModel.dispatch(SettingsIntent.ResetShortcut(CommandActions.COMMAND_PALETTE))

            val custom = viewModel.state.value.effectiveSettings.keymap.customShortcuts
            assertFalse(custom.containsKey(CommandActions.COMMAND_PALETTE))
            assertEquals(second, custom["navigation.rename"])
            viewModel.dispose()
        }

    @Test
    fun loadingSettingsSeedsKeymapState() =
        runTest {
            val viewModel = SettingsViewModel(settingsService)
            val persisted =
                AllSettings(
                    keymap =
                        su.kidoz.jetaprog.settings.model.KeymapSettings(
                            customShortcuts =
                                mapOf("navigation.rename" to KeyboardShortcut(Key.F2, shift = true).toSpec()),
                        ),
                )

            viewModel.dispatch(SettingsIntent.LoadSettings(persisted))

            val state: SettingsState = viewModel.state.value
            assertEquals(
                persisted.keymap,
                state.keymap,
            )
            assertFalse(state.hasUnsavedChanges)
            viewModel.dispose()
        }
}
