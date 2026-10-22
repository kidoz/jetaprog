package su.kidoz.jetaprog.app.ui.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentModeCycleTest {
    private val ask = ModeOption(id = "ask", name = "Ask")
    private val code = ModeOption(id = "code", name = "Code")
    private val plan = ModeOption(id = "plan", name = "Plan")

    @Test
    fun theNextAdvertisedModeWrapsAfterTheLast() {
        val modes = listOf(ask, code, plan)
        assertEquals("code", nextCycledModeId("ask", modes))
        assertEquals("plan", nextCycledModeId("code", modes))
        assertEquals("ask", nextCycledModeId("plan", modes))
    }

    @Test
    fun oneModeOrAnEmptyListDoesNotCycle() {
        assertNull(nextCycledModeId("ask", listOf(ask)))
        assertNull(nextCycledModeId(null, emptyList()))
        assertNull(nextCycledModeId("ask", listOf(ask, ask)))
    }

    @Test
    fun anUnknownModeStartsAtTheFirstAdvertisedMode() {
        val modes = listOf(ask, code)
        assertEquals("ask", nextCycledModeId(null, modes))
        assertEquals("ask", nextCycledModeId("other", modes))
    }
}
