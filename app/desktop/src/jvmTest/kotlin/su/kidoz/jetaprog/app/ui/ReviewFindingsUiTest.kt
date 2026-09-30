package su.kidoz.jetaprog.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentSurfaceContent
import su.kidoz.jetaprog.app.ui.agent.AgentUiState
import su.kidoz.jetaprog.app.ui.components.NotificationOverlay
import su.kidoz.jetaprog.app.ui.navigation.searchResultPath
import su.kidoz.jetaprog.app.ui.panels.CommitArea
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.ui.welcome.ActionBar
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.RunConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for the constrained layouts observed in the desktop review. */
@OptIn(ExperimentalTestApi::class)
class ReviewFindingsUiTest {
    @Test
    fun welcomeSearchAndActionsFitTheDefaultContentWidth() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(490.dp, 200.dp)) { ActionBar("", {}) }
                    }
                }
                onRoot().saveUiArtifacts("welcome-actions-$dark")
                listOf("Search projects", "New Project", "Open", "Clone Repository").forEach {
                    onNodeWithText(it).assertUnclippedText()
                }
            }
        }
    }

    @Test
    fun executionControlsStayVisibleAtDefaultWindowWidth() {
        val configuration =
            RunConfiguration(
                id = ConfigurationId("desktop"),
                name = "A very long desktop run configuration",
                type = ConfigurationType.GRADLE,
                settings = ConfigurationSettings.Gradle(":app:desktop:run"),
            )
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(800.dp, 100.dp)) {
                            MainToolbar(
                                projectName = "A project with a very long name",
                                canGoBack = false,
                                canGoForward = false,
                                onNavigateBack = {},
                                onNavigateForward = {},
                                onSearchEverywhere = {},
                                branchName = "feature/a-long-branch-name",
                                branches = emptyList(),
                                ahead = 0,
                                onUpdate = {},
                                onPush = {},
                                onCheckoutBranch = {},
                                onCreateBranch = {},
                                onDeleteBranch = {},
                                onRenameBranch = { _, _ -> },
                                configurationState =
                                    ConfigurationState(
                                        listOf(configuration),
                                        activeConfigurationId = configuration.id,
                                    ),
                                onSelectConfiguration = {},
                                onRunConfiguration = {},
                                onDebugConfiguration = {},
                                onStopConfiguration = {},
                                onEditConfigurations = {},
                                onCreateRecommended = {},
                            )
                        }
                    }
                }
                listOf("Run", "Debug", "Stop", "Search Everywhere", "Update", "Push").forEach {
                    val bounds = onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.width > 0 && bounds.right <= 800, "$it is outside the toolbar: $bounds")
                }
                onRoot().saveUiArtifacts("toolbar-$dark")
            }
        }
    }

    @Test
    fun commitLabelsFitDefaultSidebar() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(250.dp, 300.dp)) {
                            CommitArea("Fix layout", false, false, true, {}, {}, {}, {})
                        }
                    }
                }
                onRoot().saveUiArtifacts("commit-actions-$dark")
                onNodeWithText("Commit").assertUnclippedText()
                onNodeWithText("Commit and Push").assertUnclippedText()
            }
        }
    }

    @Test
    fun agentPromptsRemainReachableAndConnectionErrorsCanBeRetried() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val intents = mutableListOf<AgentIntent>()
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(750.dp, 480.dp)) {
                            AgentSurfaceContent(
                                state = AgentUiState(connection = AgentConnection.ERROR, error = "Invalid params"),
                                dispatch = { intents += it },
                                showRail = true,
                            )
                        }
                    }
                }
                onNodeWithText("Write tests for the selected file").performScrollTo()
                onRoot().saveUiArtifacts("agent-error-and-prompts-$dark")
                onNodeWithText("Write tests for the selected file").assertUnclippedText()
                onNodeWithText("Retry").assertIsDisplayed().performClick()
                runOnIdle { assertEquals(2, intents.count { it == AgentIntent.EnsureConnected }) }
            }
        }
    }

    @Test
    fun notificationDetailsRetainTheFullError() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val center = NotificationCenter()
                val error = "Gradle import failed. " + "Diagnostic detail. ".repeat(40)
                center.error("Gradle sync failed", error)
                setContent {
                    JetaProgTheme(
                        darkTheme = dark,
                    ) { Box(Modifier.size(800.dp, 600.dp)) { NotificationOverlay(center) } }
                }
                onNodeWithText("Show details").performClick()
                onAllNodesWithText(error).assertCountEquals(2)
                onRoot().saveUiArtifacts("notification-details-$dark")
                onNodeWithText("Close").performClick()
                onNodeWithText("Show details").assertIsDisplayed()
            }
        }
    }

    @Test
    fun searchPathsDistinguishModulesAndPreserveExternalPaths() {
        assertEquals(
            "core/settings/build.gradle.kts",
            searchResultPath("/project/core/settings/build.gradle.kts", "/project"),
        )
        assertEquals(
            "app/desktop/build.gradle.kts",
            searchResultPath("/project/app/desktop/build.gradle.kts", "/project"),
        )
        assertEquals("/project-other/build.gradle.kts", searchResultPath("/project-other/build.gradle.kts", "/project"))
        assertEquals("core/build.gradle.kts", searchResultPath("C:\\project\\core\\build.gradle.kts", "C:\\project"))
    }

    private fun SemanticsNodeInteraction.assertUnclippedText() {
        assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(layout.didOverflowHeight, "Text height was clipped: ${layout.layoutInput.text}")
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line), "Label was truncated: ${layout.layoutInput.text}")
                // Skia may report fractional glyph bounds beyond its rounded integer width.
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
    }
}
