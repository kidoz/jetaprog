package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus

/** Slash name of the session goal command. */
internal const val GOAL_COMMAND: String = "goal"

/** How many automatic continuation rounds run before the goal pauses. */
internal const val GOAL_ROUND_LIMIT: Int = 20

/** Shown when `/goal` is used in Plan mode. */
internal const val GOAL_PLAN_NOTICE: String = "Goal is not available in Plan mode. Switch modes first."

/** Shown when a goal is set while a turn is still running. */
internal const val GOAL_BUSY_NOTICE: String = "Finish the running task before setting a goal."

/** Shown when `/goal` has no objective and the session has no goal yet. */
internal const val GOAL_EMPTY_NOTICE: String = "No goal is set. Use /goal and a checkable objective."

/** Shown when replace is used without a new objective. */
internal const val GOAL_OBJECTIVE_NOTICE: String = "Add the new objective after /goal."

/** Shown when the automatic loop reaches [GOAL_ROUND_LIMIT]. */
internal const val GOAL_CAP_NOTICE: String = "The goal paused after 20 rounds. Resume to continue."

/** Shown when a saved active goal is opened again. */
internal const val GOAL_RESTORE_NOTICE: String = "The goal is paused. Resume to continue it."

/** Shown when a turn fails while a goal is active. */
internal const val GOAL_FAILED_NOTICE: String = "The goal paused because the turn failed."

/** Menu text for the built-in goal command. */
internal const val GOAL_COMMAND_DESCRIPTION: String = "View or set the goal for this session"

private const val GOAL_MET_PREFIX: String = "GOAL_MET:"
private const val GOAL_UNMET_PREFIX: String = "GOAL_UNMET:"
private const val EXECUTE_TOOL_KIND: String = "execute"
private const val EVIDENCE_CITATION_MIN: Int = 8
private const val FILE_NAME_MIN: Int = 3
private const val NEXT_ACTION_MAX: Int = 200
private const val CONTINUE_ACTION: String = "Continue the goal."
private const val MILLIS_PER_SECOND: Long = 1_000
private const val SECONDS_PER_MINUTE: Long = 60
private const val MINUTES_PER_HOUR: Long = 60
private const val HOURS_PER_DAY: Long = 24

/** What `/goal` is asking the session to do. */
internal sealed interface GoalCommand {
    /** Show the current goal. */
    data object Show : GoalCommand

    /** Hold the loop. */
    data object Pause : GoalCommand

    /** Continue a paused goal. */
    data object Resume : GoalCommand

    /** Remove the goal. */
    data object Clear : GoalCommand

    /**
     * Set or replace the objective.
     *
     * @property objective The new checkable outcome. Blank when the command omitted it.
     */
    data class Set(
        val objective: String,
    ) : GoalCommand
}

/** Outcome of the separate check that follows a work round. */
internal data class GoalVerdict(
    val met: Boolean,
    val nextAction: String,
)

/** Changed files and command output the check is allowed to cite. */
internal data class GoalEvidence(
    val files: List<String>,
    val commandOutputs: List<String>,
    val unfinishedPlanSteps: Int,
)

/**
 * Reads a composer message as a goal command.
 *
 * Returns null when the text is an ordinary prompt. `pause`, `resume`, and `clear`
 * count only when they are the whole command. Any other text is the objective.
 */
internal fun parseGoalCommand(input: String): GoalCommand? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return null
    val body = trimmed.removePrefix("/").trimStart()
    val token = body.substringBefore(' ').substringBefore('\n')
    if (!token.equals(GOAL_COMMAND, ignoreCase = true)) return null
    val rest = body.substring(token.length).trim()
    if (rest.isEmpty()) return GoalCommand.Show
    val verb = rest.substringBefore(' ').substringBefore('\n')
    val tail = rest.substring(verb.length).trim()
    return when {
        verb.equals("pause", ignoreCase = true) && tail.isEmpty() -> GoalCommand.Pause
        verb.equals("resume", ignoreCase = true) && tail.isEmpty() -> GoalCommand.Resume
        verb.equals("clear", ignoreCase = true) && tail.isEmpty() -> GoalCommand.Clear
        verb.equals("replace", ignoreCase = true) -> GoalCommand.Set(tail)
        else -> GoalCommand.Set(rest)
    }
}

/**
 * Why [command] cannot be applied, or null when it can.
 *
 * Setting a goal waits until the current turn finishes, and Plan mode has no
 * automatic continuation, so a goal cannot start there.
 */
internal fun goalRefusal(
    command: GoalCommand,
    isStreaming: Boolean,
    mode: ModeOption?,
): String? {
    if (command !is GoalCommand.Set) return null
    if (isPlanMode(mode)) return GOAL_PLAN_NOTICE
    if (isStreaming) return GOAL_BUSY_NOTICE
    if (command.objective.isBlank()) return GOAL_OBJECTIVE_NOTICE
    return null
}

/** Whether [mode] is the session's plan mode. */
internal fun isPlanMode(mode: ModeOption?): Boolean {
    if (mode == null) return false
    return mode.id.equals("plan", ignoreCase = true) || mode.name.equals("plan", ignoreCase = true)
}

/**
 * Puts the built-in goal, review, and worktree commands first.
 *
 * A goal advertised by the agent is dropped. A review command keeps the agent's own description.
 */
internal fun slashCommands(agentCommands: List<AgentCommand>): List<AgentCommand> {
    val advertised = agentCommands.associateBy { it.name.lowercase() }
    val builtIn =
        listOf(
            AgentCommand(name = GOAL_COMMAND, description = GOAL_COMMAND_DESCRIPTION),
            advertised[REVIEW_COMMAND] ?: AgentCommand(name = REVIEW_COMMAND, description = REVIEW_COMMAND_DESCRIPTION),
            AgentCommand(name = WORKTREE_COMMAND, description = WORKTREE_COMMAND_DESCRIPTION),
        )
    val taken = setOf(GOAL_COMMAND, REVIEW_COMMAND, WORKTREE_COMMAND)
    return builtIn + agentCommands.filterNot { it.name.lowercase() in taken }
}

/** Prompt sent for the first work round. The transcript shows the objective itself. */
internal fun goalWorkPrompt(objective: String): String =
    "Work toward this goal: $objective\n" +
        "Use changed files, command output, and test results as evidence. " +
        "A plan or a summary is not enough."

/** Prompt for the separate check. The transcript shows [goalCheckLabel]. */
internal fun goalCheckPrompt(objective: String): String =
    "Check whether this goal is met: $objective\n" +
        "Use only changed files, command output, and test results. " +
        "A plan, a checklist, or a conclusive summary does not count. " +
        "If any plan step is unfinished, the goal is not met.\n" +
        "End with exactly one line:\n" +
        "$GOAL_MET_PREFIX <citation of a changed file, command output, or test result>\n" +
        "or\n" +
        "$GOAL_UNMET_PREFIX <the single next action>"

/** Prompt for the next work round after the check says the goal is unmet. */
internal fun goalContinuePrompt(
    objective: String,
    nextAction: String,
): String =
    "The goal is not met yet: $objective\n" +
        "Next action: $nextAction\n" +
        "Continue the work. A plan or a summary is not enough."

/** Prompt used when the user resumes a paused goal. */
internal fun goalResumePrompt(objective: String): String =
    "Resume work toward this goal: $objective\n" +
        "The goal is not met yet. Continue from the current project state."

/** Transcript line for a check round. */
internal fun goalCheckLabel(iteration: Int): String = "Iteration $iteration · Checking the goal"

/** Transcript line for the work round that follows an unmet check. */
internal fun goalContinueLabel(iteration: Int): String = "Iteration $iteration · Goal not met, task continues"

/** Transcript line for an explicit resume. */
internal fun goalResumeLabel(iteration: Int): String = "Iteration $iteration · Resumed"

/**
 * Decides the check from evidence already on the surface.
 *
 * A last line of `GOAL_MET` counts only when its citation names a changed file
 * or quotes command output, and every plan step is finished. Any other reply,
 * including a conclusive summary, is unmet.
 */
internal fun judgeGoal(
    reply: String,
    evidence: GoalEvidence,
): GoalVerdict {
    val line =
        reply
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            .orEmpty()
    if (evidence.unfinishedPlanSteps > 0) {
        return GoalVerdict(met = false, nextAction = "Finish the remaining plan steps.")
    }
    if (!line.startsWith(GOAL_MET_PREFIX)) {
        val action =
            if (line.startsWith(GOAL_UNMET_PREFIX)) {
                line.removePrefix(GOAL_UNMET_PREFIX).trim().ifBlank { CONTINUE_ACTION }
            } else {
                CONTINUE_ACTION
            }
        return GoalVerdict(met = false, nextAction = action.take(NEXT_ACTION_MAX))
    }
    val citation = line.removePrefix(GOAL_MET_PREFIX).trim()
    if (!citationMatches(citation, evidence)) {
        return GoalVerdict(
            met = false,
            nextAction = "The check cited no changed file, command output, or test result.",
        )
    }
    return GoalVerdict(met = true, nextAction = "")
}

/**
 * Collects the diffs and finished command output in [turns].
 *
 * Agent prose is omitted. A plan step that is not completed blocks a met verdict.
 */
internal fun goalEvidence(
    turns: List<Turn>,
    plan: List<PlanStep>,
): GoalEvidence {
    val files = mutableListOf<String>()
    val outputs = mutableListOf<String>()
    turns.filterIsInstance<Turn.Agent>().forEach { turn ->
        turn.blocks.forEach { block ->
            if (block is Block.Diff) {
                if (block.fileName.isNotBlank()) files += block.fileName
                if (block.absolutePath.isNotBlank()) files += block.absolutePath
            } else if (block is Block.Tool && block.kind == EXECUTE_TOOL_KIND) {
                val result = block.result
                if (!result.isNullOrBlank()) outputs += result
            }
        }
    }
    return GoalEvidence(
        files = files.distinct(),
        commandOutputs = outputs,
        unfinishedPlanSteps = plan.count { it.status != PlanStepStatus.COMPLETED },
    )
}

/** Elapsed time shown on the card, including the stretch since the goal was last resumed. */
internal fun elapsedMillis(
    goal: AgentGoal,
    now: Long,
): Long {
    val running = goal.runningSinceEpochMillis ?: return goal.elapsedMillis.coerceAtLeast(0)
    return (goal.elapsedMillis + (now - running).coerceAtLeast(0)).coerceAtLeast(0)
}

/** Short elapsed label: `45s`, `5m`, `2h`, or `1d`. */
internal fun goalElapsedLabel(
    goal: AgentGoal,
    now: Long,
): String {
    val seconds = elapsedMillis(goal, now) / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        seconds < SECONDS_PER_MINUTE -> "${seconds}s"
        minutes < MINUTES_PER_HOUR -> "${minutes}m"
        hours < HOURS_PER_DAY -> "${hours}h"
        else -> "${days}d"
    }
}

/** Status word shown beside the elapsed time. */
internal fun goalStatusLabel(status: AgentGoalStatus): String =
    when (status) {
        AgentGoalStatus.ACTIVE -> "Active"
        AgentGoalStatus.PAUSED -> "Paused"
        AgentGoalStatus.MET -> "Met"
    }

/** One line of status, elapsed time, and iteration for the goal card. */
internal fun goalMetaLabel(
    goal: AgentGoal,
    now: Long,
): String = "${goalStatusLabel(goal.status)} · ${goalElapsedLabel(goal, now)} · Iteration ${goal.iteration}"

/** Freezes the clock so a saved goal does not keep a live timestamp. */
internal fun persistedGoal(
    goal: AgentGoal,
    now: Long,
): AgentGoal =
    goal.copy(
        elapsedMillis = elapsedMillis(goal, now),
        runningSinceEpochMillis = null,
    )

/**
 * Goal placed on a reopened session.
 *
 * An active loop becomes paused. The user resumes it explicitly.
 */
internal fun restoredGoal(goal: AgentGoal): AgentGoal =
    if (goal.status == AgentGoalStatus.ACTIVE) {
        goal.copy(status = AgentGoalStatus.PAUSED, runningSinceEpochMillis = null)
    } else {
        goal.copy(runningSinceEpochMillis = null)
    }

/** Notice for a goal that was still active when the session was saved. */
internal fun restoredGoalNotice(goal: AgentGoal?): String? =
    if (goal?.status == AgentGoalStatus.ACTIVE) GOAL_RESTORE_NOTICE else null

private fun citationMatches(
    citation: String,
    evidence: GoalEvidence,
): Boolean {
    if (citation.length < EVIDENCE_CITATION_MIN) return false
    if (evidence.files.any { name -> name.length >= FILE_NAME_MIN && citation.contains(name) }) return true
    return evidence.commandOutputs.any { output ->
        val body = output.trim()
        body.length >= EVIDENCE_CITATION_MIN && (body.contains(citation) || citation.contains(body))
    }
}
