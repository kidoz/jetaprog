package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.WorkspaceChange
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Workspace review is available without recorded agent diffs and exposes only read-only inspection. */
@OptIn(ExperimentalTestApi::class)
class AgentWorkspaceReviewUiTest {
    @Test
    fun darkNarrowWorkspaceReview() = workspaceReview(dark = true)

    @Test
    fun lightWorkspaceReview() = workspaceReview(dark = false)

    private fun workspaceReview(dark: Boolean) =
        runComposeUiTest {
            val state =
                mutableStateOf(
                    AgentUiState(
                        workspacePath = "/project",
                        reviewVisible = true,
                        reviewScope = ReviewScope.WORKSPACE,
                        workspaceReview =
                            WorkspaceReviewState(
                                files =
                                    listOf(
                                        WorkspaceChange("manual.kt", 'M', 'M'),
                                        WorkspaceChange("generated.kt", '?', '?'),
                                    ),
                                revision = 0,
                                preExistingPaths = setOf("manual.kt"),
                            ),
                    ),
                )
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 760.dp)) {
                        AgentSurfaceContent(state.value, dispatch = { intent ->
                            intents += intent
                            if (intent is AgentIntent.SetReviewScope) {
                                state.value =
                                    state.value.copy(reviewScope = intent.scope)
                            }
                            if (intent is AgentIntent.PreviewWorkspaceChange) {
                                state.value =
                                    state.value.copy(
                                        workspaceReview =
                                            state.value.workspaceReview.copy(
                                                previewPath = intent.path,
                                                preview = "Untracked file · current contents\n\nGenerated output",
                                            ),
                                    )
                            }
                            if (intent == AgentIntent.RefreshWorkspaceReview) {
                                state.value =
                                    state.value.copy(
                                        workspaceReview =
                                            state.value.workspaceReview.copy(
                                                previewPath = null,
                                                preview = null,
                                            ),
                                    )
                            }
                        }, showRail = false)
                    }
                }
            }
            onNodeWithText("Workspace changes").assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToIndex(1)
            onNodeWithText("Changed before this task").performScrollTo().assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToIndex(2)
            onNodeWithText("Origin unknown").performScrollTo().assertIsDisplayed()
            onNodeWithText("Keep").assertDoesNotExist()
            onNodeWithText("Revert").assertDoesNotExist()
            onNodeWithContentDescription("Inspect generated.kt").performScrollTo().performClick()
            assertTrue(AgentIntent.PreviewWorkspaceChange("generated.kt") in intents)
            onNodeWithText("Preview · generated.kt").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-workspace-preview-${if (dark) "dark" else "light"}")
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNodeWithText("Refresh").performScrollTo().performClick()
            assertTrue(AgentIntent.RefreshWorkspaceReview in intents)
            onNodeWithText("Agent changes").performClick()
            onNodeWithText("No agent-reported changes. Check Workspace changes for other edits.").assertIsDisplayed()
            onNodeWithText("Workspace changes").performClick()
            onRoot().saveUiArtifacts("agent-workspace-review-${if (dark) "dark" else "light"}")
            runOnIdle { state.value = state.value.copy(workspaceRevision = 1, hasUnsavedEdits = true) }
            onNodeWithText(
                "Unsaved editor changes are not included in this disk snapshot.",
            ).performScrollTo().assertIsDisplayed()
            onNodeWithText(
                "Workspace may have changed. Refresh before reviewing the result.",
            ).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun failureLoadingAndEmptySnapshotsAreDistinct() =
        runComposeUiTest {
            val state = mutableStateOf(AgentUiState(reviewVisible = true, reviewScope = ReviewScope.WORKSPACE))
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(420.dp, 760.dp)) { AgentSurfaceContent(state.value, {}, false) }
                }
            }
            onNodeWithText("Starting state unavailable. Unreported changes have unknown origin.").assertIsDisplayed()
            onNodeWithText("Refresh to inspect workspace changes.").assertIsDisplayed()
            runOnIdle { state.value = state.value.copy(workspaceReview = WorkspaceReviewState(loading = true)) }
            onNodeWithText("Loading workspace review…").assertIsDisplayed()
            runOnIdle {
                state.value =
                    state.value.copy(workspaceReview = WorkspaceReviewState(error = "Git is unavailable", revision = 0))
            }
            onNodeWithText("Git is unavailable").assertIsDisplayed()
            onNodeWithText("No local Git changes.").assertDoesNotExist()
            runOnIdle { state.value = state.value.copy(workspaceReview = WorkspaceReviewState(revision = 0)) }
            onNodeWithText("No local Git changes.").assertIsDisplayed()
        }
}
