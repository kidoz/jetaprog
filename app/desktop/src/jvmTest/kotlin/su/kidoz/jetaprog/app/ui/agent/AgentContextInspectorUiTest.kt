package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AgentContextInspectorUiTest {
    private val sample =
        AgentUiState(
            workspacePath = "/projects/linked-checkout",
            composerInput = " Explain the selection ",
            composerAttachments =
                listOf(
                    ComposerAttachment("Reader.kt", "src/Reader.kt"),
                    ComposerAttachment("Reader.kt", "src/Reader.kt", "val answer = 42"),
                ),
            instructionPreview = ProjectInstructionPreview(text = "Use project conventions."),
        )

    @Test
    fun inspectorRendersCheckoutAndContextInBothThemes() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                show(sample, dark)
                onNodeWithText("/projects/linked-checkout").assertIsDisplayed()
                onNodeWithText("File reference only. File contents are not embedded in the prompt.").assertIsDisplayed()
                onNode(isRoot() and hasAnyDescendant(hasText("Context for next message")))
                    .saveUiArtifacts("agent-context-overview-${if (dark) "dark" else "light"}")
                onNodeWithText("Show selection in src/Reader.kt").performScrollTo().performClick()
                onNodeWithContentDescription(
                    "Preview: selection in src/Reader.kt",
                ).performScrollTo().assertTextEquals("val answer = 42")
                onNode(isRoot() and hasAnyDescendant(hasText("Context for next message")))
                    .saveUiArtifacts("agent-context-${if (dark) "dark" else "light"}")
                onNodeWithText("Show project instructions").performScrollTo().performClick()
                onNodeWithContentDescription("Preview: project instructions")
                    .performScrollTo()
                    .assertTextEquals("Use project conventions.")
                onNode(isRoot() and hasAnyDescendant(hasText("Context for next message")))
                    .saveUiArtifacts("agent-context-instructions-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun removingSelectionRetainsFileAndUpdatesOutgoingPreview() =
        runComposeUiTest {
            val intents = show(sample)
            onNodeWithContentDescription("Remove Reader.kt (selection) from context").performScrollTo().performClick()
            onNodeWithText("Attachments (1)").performScrollTo().assertIsDisplayed()
            onNodeWithText("Show outgoing text").performScrollTo().performClick()
            onNodeWithContentDescription("Preview: outgoing text").performScrollTo().assertTextEquals(
                "Project instructions (AGENTS.md):\nUse project conventions.\n\nContext:\nFile: src/Reader.kt\n\nExplain the selection",
            )
            assertEquals(listOf(AgentIntent.RemoveAttachment("src/Reader.kt", true)), intents)
        }

    @Test
    fun refreshAndCloseDispatchWithoutSending() =
        runComposeUiTest {
            val intents = show(sample)
            onNodeWithText("Refresh").performClick()
            onNodeWithText("Close").performClick()
            assertEquals(listOf(AgentIntent.RefreshContext, AgentIntent.HideContext), intents)
            onNodeWithText("Context for next message").assertDoesNotExist()
        }

    @Test
    fun loadingInstructionsDoesNotShowAnOutdatedOutgoingPreview() =
        runComposeUiTest {
            show(sample.copy(instructionPreview = ProjectInstructionPreview(loading = true, text = "Old snapshot")))
            onNodeWithText("Reading root instructions…").performScrollTo().assertIsDisplayed()
            onNodeWithText("Show outgoing text").assertDoesNotExist()
        }

    @Test
    fun localCommandsExplainWhyThereIsNoDirectPromptPreview() =
        runComposeUiTest {
            show(sample.copy(composerInput = "/review", composerAttachments = emptyList()))
            onNodeWithText(
                "This is a JetaProg command. It may start a separate workflow; its generated prompts are not previewed here.",
            ).performScrollTo().assertIsDisplayed()
            onNodeWithText("Show outgoing text").assertDoesNotExist()
        }

    @Test
    fun escapeClosesTheInspector() =
        runComposeUiTest {
            val intents = show(sample)
            onNodeWithText("Close").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Close").performKeyInput { pressKey(Key.Escape) }
            onNodeWithText("Context for next message").assertDoesNotExist()
            assertEquals(listOf(AgentIntent.HideContext), intents)
        }

    @Test
    fun composerOpensInspectorAndPreservesTheDraftOnClose() =
        runComposeUiTest {
            setContent {
                var state by remember { mutableStateOf(sample) }
                JetaProgTheme {
                    AgentSurfaceContent(state, dispatch = {
                        state =
                            when (it) {
                                AgentIntent.ShowContext -> state.copy(contextVisible = true)
                                AgentIntent.HideContext -> state.copy(contextVisible = false)
                                else -> state
                            }
                    }, showRail = false)
                }
            }
            onNodeWithContentDescription("Inspect next message context").performClick()
            onNodeWithText("Context for next message").assertIsDisplayed()
            onNodeWithText("Close").performClick()
            onNodeWithContentDescription("Agent prompt").assertTextEquals(sample.composerInput)
        }

    private fun ComposeUiTest.show(
        initial: AgentUiState,
        dark: Boolean = true,
    ): List<AgentIntent> {
        val intents = mutableListOf<AgentIntent>()
        setContent {
            var state by remember { mutableStateOf(initial.copy(contextVisible = true)) }
            JetaProgTheme(darkTheme = dark) {
                if (state.contextVisible) {
                    AgentContextInspector(state) { intent ->
                        intents += intent
                        when (intent) {
                            is AgentIntent.RemoveAttachment -> {
                                state =
                                    state.copy(
                                        composerAttachments =
                                            state.composerAttachments.filterNot {
                                                it.path == intent.path && (it.excerpt != null) == intent.selection
                                            },
                                    )
                            }

                            AgentIntent.HideContext -> {
                                state = state.copy(contextVisible = false)
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
        return intents
    }
}
