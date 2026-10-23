package su.kidoz.jetaprog.app.ui.agent

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Command results must be explicit, current, and finished before they count as evidence. */
class AgentChecksTest {
    @Test
    fun launchAcknowledgementAndSuccessProseAreUnverified() {
        val tool = command(exitCode = null).copy(result = "Started Gradle task: test\nBUILD SUCCESSFUL")
        assertEquals(CheckStatus.UNVERIFIED, check(tool).status)
        assertEquals("Not run", checkSummary(emptyList()))
        assertNull(commandExitCode(Json.parseToJsonElement("""{"output":"exitCode: 0"}""")))
        assertNull(commandExitCode(Json.parseToJsonElement("""{"exitCode":"0"}""")))
        assertEquals(0, commandExitCode(Json.parseToJsonElement("""{"exitCode":0}""")))
        assertEquals(1, commandExitCode(Json.parseToJsonElement("""{"exit_code":1}""")))
    }

    @Test
    fun failedAndIncompleteCommandsCannotPass() {
        assertEquals(CheckStatus.FAILED, check(command(exitCode = 1)).status)
        assertEquals(CheckStatus.FAILED, check(command().copy(status = ToolStatus.FAILED)).status)
        assertEquals(CheckStatus.UNVERIFIED, check(command().copy(status = ToolStatus.RUNNING)).status)
        assertEquals(CheckStatus.RUNNING, check(command().copy(status = ToolStatus.RUNNING), streaming = true).status)
        assertEquals(CheckStatus.PASSED, check(command()).status)
    }

    @Test
    fun changesDuringOrAfterACommandInvalidateItsEvidence() {
        val completed = command()
        assertEquals(CheckStatus.STALE, check(completed, revision = 1).status)
        assertEquals(CheckStatus.UNVERIFIED, check(completed.copy(checkRevision = null)).status)
        val output = goalEvidence(turns(completed), emptyList(), workspaceRevision = 1)
        assertEquals(emptyList(), output.commandOutputs)
    }

    @Test
    fun aFailedRerunSupersedesEarlierSuccessfulOutput() {
        val old = command().copy(id = "old")
        val failed = command(exitCode = 1).copy(id = "new", result = "BUILD FAILED")
        val evidence = goalEvidence(turns(old, failed), emptyList())
        assertEquals(emptyList(), evidence.commandOutputs)
        assertFalse(judgeGoal("GOAL_MET: BUILD SUCCESSFUL", evidence).met)
        assertFalse(judgeGoal("GOAL_MET: Reader.kt", evidence.copy(files = listOf("Reader.kt"))).met)
    }

    @Test
    fun oldInterruptedToolsDoNotBecomeRunningWhenAnotherTurnStarts() {
        val old = turns(command().copy(status = ToolStatus.RUNNING))
        val current = old + Turn.Agent("next", "now", emptyList())
        assertEquals(CheckStatus.UNVERIFIED, agentChecks(current, 0, isStreaming = true).single().status)
    }

    @Test
    fun unsavedEditsAndHiddenSourceFilesInvalidateEvidence() {
        assertEquals(CheckStatus.STALE, agentChecks(turns(command()), 0, false, hasUnsavedEdits = true).single().status)
        assertEquals(emptyList(), goalEvidence(turns(command()), emptyList(), hasUnsavedEdits = true).commandOutputs)
        assertEquals(true, isCheckInput("/project", "/project/.github/workflows/check.yml"))
        assertEquals(true, isCheckInput("/project", "/project/src/Main.kt"))
        assertEquals(false, isCheckInput("/project", "/project/build/results.xml"))
        assertEquals(false, isCheckInput("/project", "/project/.jetaprog/agent-history/1.json"))
        assertEquals(false, isCheckInput("/project", "/project-other/src/Main.kt"))
    }

    private fun check(
        tool: Block.Tool,
        revision: Long = 0,
        streaming: Boolean = false,
    ): AgentCheck = agentChecks(turns(tool), revision, streaming).single()

    private fun turns(vararg tools: Block.Tool): List<Turn> = listOf(Turn.Agent("turn", "now", tools.toList()))

    private fun command(exitCode: Int? = 0): Block.Tool =
        Block.Tool(
            "run",
            "Test",
            "execute",
            "./gradlew test",
            ToolStatus.OK,
            "BUILD SUCCESSFUL",
            false,
            exitCode = exitCode,
            checkRevision = 0,
        )
}
