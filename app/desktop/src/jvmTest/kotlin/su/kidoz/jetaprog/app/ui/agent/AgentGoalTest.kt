package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing, evidence check, and labels for the session goal. */
class AgentGoalTest {
    @Test
    fun slashCommandReadsShowPauseAndAReplacementObjective() {
        assertNull(parseGoalCommand("Explain the goal"))
        assertEquals(GoalCommand.Show, parseGoalCommand("/goal"))
        assertEquals(GoalCommand.Pause, parseGoalCommand("  /goal pause"))
        assertEquals(GoalCommand.Resume, parseGoalCommand("/goal resume"))
        assertEquals(GoalCommand.Clear, parseGoalCommand("/goal clear"))
        assertEquals(GoalCommand.Set("Make ./gradlew test pass"), parseGoalCommand("/goal Make ./gradlew test pass"))
        assertEquals(GoalCommand.Set("Make tests pass"), parseGoalCommand("/goal replace Make tests pass"))
        assertEquals(GoalCommand.Set(""), parseGoalCommand("/goal replace"))
        assertEquals(GoalCommand.Set("pause the build"), parseGoalCommand("/goal pause the build"))
    }

    @Test
    fun settingAGoalWaitsForPlanModeAndARunningTurn() {
        val plan = ModeOption(id = "plan", name = "Plan")
        val set = GoalCommand.Set("Make tests pass")
        assertEquals(GOAL_PLAN_NOTICE, goalRefusal(set, isStreaming = false, mode = plan))
        assertEquals(GOAL_BUSY_NOTICE, goalRefusal(set, isStreaming = true, mode = ModeOption("code", "Code")))
        assertEquals(GOAL_OBJECTIVE_NOTICE, goalRefusal(GoalCommand.Set("  "), isStreaming = false, mode = null))
        assertNull(goalRefusal(GoalCommand.Pause, isStreaming = true, mode = plan))
        assertTrue(isPlanMode(plan))
        assertTrue(isPlanMode(ModeOption(id = "plan", name = "Plan mode")))
        assertFalse(isPlanMode(ModeOption(id = "code", name = "Code")))
        assertFalse(isPlanMode(null))
    }

    @Test
    fun goalCommandIsListedOnce() {
        val commands =
            slashCommands(
                listOf(
                    AgentCommand("review", "Review the diff"),
                    AgentCommand("goal", "Agent goal"),
                ),
            )
        assertEquals(listOf(GOAL_COMMAND, "review", "worktree"), commands.map { it.name })
        assertEquals(GOAL_COMMAND_DESCRIPTION, commands.first().description)
        assertEquals("Review the diff", commands[1].description)
    }

    @Test
    fun aConclusiveReplyDoesNotMeetTheGoal() {
        val verdict = judgeGoal("All done. The goal is met.", evidence())
        assertFalse(verdict.met)
        assertEquals("Continue the goal.", verdict.nextAction)
    }

    @Test
    fun aCitedChangeMeetsTheGoalOnlyWhenThePlanIsFinished() {
        val files = evidence(files = listOf("Reader.kt"))
        assertTrue(judgeGoal("GOAL_MET: Reader.kt", files).met)
        assertTrue(
            judgeGoal(
                "I think so\nGOAL_MET: BUILD SUCCESSFUL",
                evidence(outputs = listOf("BUILD SUCCESSFUL")),
            ).met,
        )
        val unfinished =
            judgeGoal(
                "GOAL_MET: Reader.kt",
                files.copy(unfinishedPlanSteps = 1),
            )
        assertFalse(unfinished.met)
        assertEquals("Finish the remaining plan steps.", unfinished.nextAction)
        assertFalse(judgeGoal("GOAL_MET: done", files).met)
        assertEquals("run the tests", judgeGoal("GOAL_UNMET: run the tests", evidence()).nextAction)
        assertFalse(judgeGoal("GOAL_MET: Reader.kt\nThanks", files).met)
    }

    @Test
    fun evidenceIsDiffsAndCommandOutput() {
        val turns =
            listOf(
                Turn.Agent(
                    id = "a",
                    timeLabel = "",
                    blocks =
                        listOf(
                            Block.Text(id = "t", text = "I finished everything"),
                            Block.Diff(
                                id = "d",
                                fileName = "Reader.kt",
                                path = "src",
                                added = 1,
                                removed = 1,
                                lines = emptyList(),
                                decision = DiffDecision.PROPOSED,
                                absolutePath = "/proj/src/Reader.kt",
                                oldText = "old",
                            ),
                            Block.Tool(
                                id = "run",
                                name = "Test",
                                kind = "execute",
                                args = "./gradlew test",
                                status = ToolStatus.OK,
                                result = "BUILD SUCCESSFUL",
                                expanded = false,
                            ),
                        ),
                ),
            )
        val evidence =
            goalEvidence(
                turns,
                plan = listOf(PlanStep("Read", PlanStepStatus.COMPLETED), PlanStep("Fix", PlanStepStatus.PENDING)),
            )
        assertEquals(listOf("Reader.kt", "/proj/src/Reader.kt"), evidence.files)
        assertEquals(listOf("BUILD SUCCESSFUL"), evidence.commandOutputs)
        assertEquals(1, evidence.unfinishedPlanSteps)
    }

    @Test
    fun elapsedLabelFreezesWhilePausedAndASavedActiveGoalPauses() {
        val active =
            AgentGoal(
                objective = "Make tests pass",
                status = AgentGoalStatus.ACTIVE,
                elapsedMillis = 0,
                runningSinceEpochMillis = 0,
                iteration = 3,
            )
        assertEquals("Active · 5m · Iteration 3", goalMetaLabel(active, 5 * 60_000))
        val paused =
            active.copy(
                status = AgentGoalStatus.PAUSED,
                elapsedMillis = 45_000,
                runningSinceEpochMillis = null,
            )
        assertEquals("45s", goalElapsedLabel(paused, 9_000_000))
        assertEquals(AgentGoalStatus.PAUSED, restoredGoal(active).status)
        assertEquals(GOAL_RESTORE_NOTICE, restoredGoalNotice(active))
        val met = active.copy(status = AgentGoalStatus.MET)
        assertEquals(AgentGoalStatus.MET, restoredGoal(met).status)
        assertNull(restoredGoalNotice(met))
    }
}

private fun evidence(
    files: List<String> = emptyList(),
    outputs: List<String> = emptyList(),
): GoalEvidence = GoalEvidence(files = files, commandOutputs = outputs, unfinishedPlanSteps = 0)
