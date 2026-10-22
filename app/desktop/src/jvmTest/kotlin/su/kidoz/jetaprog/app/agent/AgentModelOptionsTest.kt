package su.kidoz.jetaprog.app.agent

import su.kidoz.jetaprog.acp.protocol.ModelInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentModelOptionsTest {
    @Test
    fun claudePrefixIsDroppedFromTheCompactName() {
        val option = ModelInfo(modelId = "claude-opus-4-8", name = "Claude Opus 4.8").toModelOption()

        assertEquals("claude-opus-4-8", option.id)
        assertEquals("Claude Opus 4.8", option.displayName)
        assertEquals("Opus 4.8", option.shortName)
    }

    @Test
    fun codexModelNamesKeepTheirEffortSuffix() {
        val option = ModelInfo(modelId = "gpt-6.1-sol[high]", name = "6.1 Sol (high)").toModelOption()

        assertEquals("6.1 Sol (high)", option.shortName)
    }

    @Test
    fun blankNameFallsBackToTheModelId() {
        val option = ModelInfo(modelId = "custom-model", name = " ").toModelOption()

        assertEquals("custom-model", option.shortName)
    }
}
