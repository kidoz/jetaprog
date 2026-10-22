package su.kidoz.jetaprog.app.agent

import kotlinx.serialization.Serializable

/**
 * Whether the session goal is still being pursued.
 *
 * A reopened chat pauses an active goal so the loop does not resume on its own.
 */
@Serializable
public enum class AgentGoalStatus {
    /** The agent keeps working until the check passes. */
    ACTIVE,

    /** The loop is held. Resume continues it. */
    PAUSED,

    /** The check accepted evidence that the objective is met. */
    MET,
}

/**
 * The one objective for an agent session.
 *
 * Running and needs-you stay live. This record is what a saved session restores.
 *
 * @property objective The checkable outcome, such as a test command passing.
 * @property status Whether the loop is active, paused, or met.
 * @property startedAtEpochMillis When the objective was set.
 * @property elapsedMillis Time accumulated while the goal was active, frozen across pauses.
 * @property runningSinceEpochMillis Start of the current active stretch, or null when the clock is frozen.
 * @property iteration The work round currently in progress, starting at 1.
 */
@Serializable
public data class AgentGoal(
    val objective: String,
    val status: AgentGoalStatus = AgentGoalStatus.ACTIVE,
    val startedAtEpochMillis: Long = 0,
    val elapsedMillis: Long = 0,
    val runningSinceEpochMillis: Long? = null,
    val iteration: Int = 1,
)
