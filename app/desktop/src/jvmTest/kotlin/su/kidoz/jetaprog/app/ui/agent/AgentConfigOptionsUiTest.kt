package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AgentConfigOptionsUiTest {
    @Test
    fun booleanCheckboxUsesConfirmedStateAndSupportsKeyboardChangesInBothThemes() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val intents = mutableListOf<AgentIntent>()
                val option =
                    AgentConfigOption(
                        "compact",
                        "Auto compact",
                        "",
                        emptyList(),
                        description = "Compact the conversation automatically",
                        booleanValue = false,
                    )
                val state =
                    mutableStateOf(
                        AgentUiState(
                            connection = AgentConnection.CONNECTED,
                            configOptions = listOf(option),
                        ),
                    )
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(264.dp, 650.dp)) {
                            AgentSurfaceContent(state.value, { intents += it }, showRail = false)
                        }
                    }
                }
                onNodeWithContentDescription("Session settings").performClick()
                onNodeWithContentDescription("Auto compact: Off").performClick()
                onNodeWithText("Compact the conversation automatically").assertIsDisplayed()
                val checkbox = onNodeWithContentDescription("Auto compact")
                checkbox.assertIsOff()
                checkbox.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                checkbox.performKeyInput { pressKey(Key.Enter) }
                assertEquals(
                    listOf(AgentIntent.SetBooleanConfigOption("compact", true)),
                    intents.filterIsInstance<AgentIntent.SetBooleanConfigOption>(),
                )
                onNodeWithContentDescription("Session settings").performClick()
                onNodeWithContentDescription("Auto compact: Off").assertIsDisplayed()
                runOnIdle { state.value = state.value.copy(changingConfigId = "compact") }
                onNodeWithContentDescription("Session settings").assertIsNotEnabled()
                runOnIdle {
                    state.value =
                        state.value.copy(
                            changingConfigId = null,
                            configOptions = listOf(option.copy(booleanValue = true)),
                        )
                }
                onNodeWithContentDescription("Session settings").performClick()
                onNodeWithContentDescription("Auto compact: On").performClick()
                onNodeWithContentDescription("Auto compact").assertIsOn()
                onAllNodes(isRoot())[1].saveUiArtifacts("agent-config-boolean-$dark")
                onNodeWithContentDescription("Auto compact").performClick()
                assertEquals(
                    AgentIntent.SetBooleanConfigOption("compact", false),
                    intents.filterIsInstance<AgentIntent.SetBooleanConfigOption>().last(),
                )
            }
        }
    }

    @Test
    fun agentConfigurationReplacesLegacyControlsAndExposesGroupedKeyboardChoices() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val intents = mutableListOf<AgentIntent>()
                val state =
                    mutableStateOf(
                        AgentUiState(
                            connection = AgentConnection.CONNECTED,
                            agent = AgentOption("claude", "Claude"),
                            composerInput = "Keep this draft",
                            configOptions =
                                listOf(
                                    AgentConfigOption(
                                        "engine",
                                        "Model",
                                        "fast",
                                        listOf(
                                            AgentConfigValue(
                                                "fast",
                                                "A very long model name with extended context",
                                                group = "Available models",
                                            ),
                                            AgentConfigValue(
                                                "deep",
                                                "Deep",
                                                "More detailed analysis",
                                                "Available models",
                                            ),
                                        ),
                                        "Choose the session model",
                                        "model",
                                    ),
                                    AgentConfigOption(
                                        "effort",
                                        "Effort",
                                        "medium",
                                        listOf(AgentConfigValue("medium", "Medium")),
                                    ),
                                    AgentConfigOption("extra", "Extra", "off", listOf(AgentConfigValue("off", "Off"))),
                                ),
                            mode = ModeOption("legacy", "Legacy"),
                            availableModels = listOf(ModelOption("old", "Old model", "Old model")),
                            model = ModelOption("old", "Old model", "Old model"),
                        ),
                    )
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(264.dp, 650.dp)) {
                            AgentSurfaceContent(state.value, { intents += it }, showRail = false)
                        }
                    }
                }
                onNodeWithContentDescription("Session mode: Legacy").assertDoesNotExist()
                onNodeWithContentDescription("Model: Old model").assertDoesNotExist()
                val settings = onNodeWithContentDescription("Session settings")
                settings.assertIsDisplayed()
                val bounds = settings.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= 0 && bounds.right <= 264, "Settings must fit entirely inside the dock")
                settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                settings.performKeyInput { pressKey(Key.Enter) }
                onNodeWithContentDescription("Effort: Medium").assertIsDisplayed()
                onNodeWithContentDescription("Extra: Off").assertIsDisplayed()
                onAllNodes(isRoot())[1].saveUiArtifacts("agent-config-overview-$dark")
                val model = onNodeWithContentDescription("Model: A very long model name with extended context")
                model.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                model.performKeyInput { pressKey(Key.Enter) }
                onNodeWithText("Available models").assertIsDisplayed()
                onNodeWithText("Choose the session model").assertIsDisplayed()
                onNodeWithText("More detailed analysis").assertIsDisplayed()
                onAllNodes(isRoot())[1].saveUiArtifacts("agent-config-options-$dark")
                onNodeWithText("Deep").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                onNodeWithText("Deep").performKeyInput { pressKey(Key.Enter) }
                assertEquals(
                    listOf(AgentIntent.SetConfigOption("engine", "deep")),
                    intents.filterIsInstance<AgentIntent.SetConfigOption>(),
                )
                runOnIdle { state.value = state.value.copy(changingConfigId = "engine") }
                onNodeWithContentDescription("Session settings").assertIsNotEnabled()
                runOnIdle { state.value = state.value.copy(changingConfigId = null, configOptions = emptyList()) }
                onNodeWithContentDescription("Session settings").assertDoesNotExist()
            }
        }
    }
}
