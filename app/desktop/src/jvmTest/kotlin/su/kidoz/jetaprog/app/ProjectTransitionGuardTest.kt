package su.kidoz.jetaprog.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectTransitionGuardTest {
    @Test
    fun cleanOpenAndCloseProceedImmediately() =
        runTest {
            val harness = TransitionHarness(dirty = false)

            harness.guard.openProject("/work/next")
            harness.guard.closeProject()

            assertEquals(listOf("/work/next"), harness.openedProjects)
            assertEquals(1, harness.closeCount)
            assertEquals(0, harness.saveCount)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun dirtyOpenWaitsAndCancelLeavesSessionUntouched() =
        runTest {
            val harness = TransitionHarness(dirty = true)

            harness.guard.openProject("/work/next")
            assertEquals(PendingProjectTransition.OpenProject("/work/next"), harness.guard.pending.value)

            harness.guard.cancelPending()

            assertTrue(harness.openedProjects.isEmpty())
            assertEquals(0, harness.closeCount)
            assertEquals(0, harness.saveCount)
            assertTrue(harness.dirty)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun discardResumesTheOriginallyRequestedOpen() =
        runTest {
            val harness = TransitionHarness(dirty = true)
            harness.guard.openProject("/work/original-request")

            val resumed = harness.guard.discardPending()

            assertNull(resumed)
            assertEquals(listOf("/work/original-request"), harness.openedProjects)
            assertEquals(0, harness.saveCount)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun dirtyCloseCanBeCancelledOrExplicitlyDiscarded() =
        runTest {
            val harness = TransitionHarness(dirty = true)
            harness.guard.closeProject()
            harness.guard.cancelPending()
            assertEquals(0, harness.closeCount)

            harness.guard.closeProject()
            harness.guard.discardPending()

            assertEquals(1, harness.closeCount)
            assertEquals(0, harness.saveCount)
        }

    @Test
    fun successfulSaveAllResumesOriginalOpen() =
        runTest {
            val harness = TransitionHarness(dirty = true, saveSucceeds = true)
            harness.guard.openProject("/work/created-project")

            val resumed = harness.guard.saveAllAndResumePending()

            assertNull(resumed)
            assertEquals(1, harness.saveCount)
            assertEquals(listOf("/work/created-project"), harness.openedProjects)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun successfulSaveAllClosesProject() =
        runTest {
            val harness = TransitionHarness(dirty = true, saveSucceeds = true)
            harness.guard.closeProject()

            val resumed = harness.guard.saveAllAndResumePending()

            assertNull(resumed)
            assertEquals(1, harness.saveCount)
            assertEquals(1, harness.closeCount)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun failedSaveAllDoesNotOpenProject() =
        runTest {
            val harness = TransitionHarness(dirty = true, saveSucceeds = false)
            harness.guard.openProject("/work/next")

            val resumed = harness.guard.saveAllAndResumePending()

            assertNull(resumed)
            assertEquals(1, harness.saveCount)
            assertTrue(harness.openedProjects.isEmpty())
            assertTrue(harness.dirty)
            assertNull(harness.guard.pending.value)
        }

    @Test
    fun failedSaveAllDoesNotCloseProject() =
        runTest {
            val harness = TransitionHarness(dirty = true, saveSucceeds = false)
            harness.guard.closeProject()

            val resumed = harness.guard.saveAllAndResumePending()

            assertNull(resumed)
            assertEquals(1, harness.saveCount)
            assertEquals(0, harness.closeCount)
            assertTrue(harness.dirty)
        }

    @Test
    fun cleanExitInvokesCallbackWithoutPrompt() {
        val harness = TransitionHarness(dirty = false)
        var exitCount = 0

        harness.guard.requestExit { exitCount++ }

        assertEquals(1, exitCount)
        assertNull(harness.guard.pending.value)
    }

    @Test
    fun dirtyExitCanBeCancelledWithoutInvokingCallback() {
        val harness = TransitionHarness(dirty = true)
        var exitCount = 0
        harness.guard.requestExit { exitCount++ }
        assertEquals(PendingProjectTransition.Exit, harness.guard.pending.value)

        harness.guard.cancelPending()

        assertEquals(0, exitCount)
        assertNull(harness.guard.pending.value)
    }

    @Test
    fun exitIsReturnedOnlyAfterDiscardOrSuccessfulSave() =
        runTest {
            val discardHarness = TransitionHarness(dirty = true)
            discardHarness.guard.requestExit { error("Exit must wait for confirmation") }
            assertIs<PendingProjectTransition.Exit>(discardHarness.guard.discardPending())

            val saveHarness = TransitionHarness(dirty = true, saveSucceeds = true)
            saveHarness.guard.requestExit { error("Exit must wait for Save All") }
            assertIs<PendingProjectTransition.Exit>(saveHarness.guard.saveAllAndResumePending())
            assertEquals(1, saveHarness.saveCount)

            val failedSaveHarness = TransitionHarness(dirty = true, saveSucceeds = false)
            failedSaveHarness.guard.requestExit { error("Exit must wait for Save All") }
            assertNull(failedSaveHarness.guard.saveAllAndResumePending())
            assertTrue(failedSaveHarness.dirty)
        }

    @Test
    fun aSecondRequestDoesNotReplaceThePendingTransition() =
        runTest {
            val harness = TransitionHarness(dirty = true)
            harness.guard.openProject("/work/first")

            harness.guard.closeProject()
            harness.guard.requestExit { error("A modal transition is already pending") }

            assertEquals(PendingProjectTransition.OpenProject("/work/first"), harness.guard.pending.value)
            harness.guard.discardPending()
            assertEquals(listOf("/work/first"), harness.openedProjects)
            assertEquals(0, harness.closeCount)
        }

    private class TransitionHarness(
        var dirty: Boolean,
        private val saveSucceeds: Boolean = true,
    ) {
        val openedProjects = mutableListOf<String>()
        var closeCount = 0
        var saveCount = 0

        val guard =
            ProjectTransitionGuard(
                hasUnsavedChanges = { dirty },
                saveAll = {
                    saveCount++
                    saveSucceeds
                },
                openProjectAction = { openedProjects += it },
                closeProjectAction = { closeCount++ },
            )
    }
}
