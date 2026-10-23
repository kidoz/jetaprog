package su.kidoz.jetaprog.app.ui.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Evidence state of a command; tool completion alone does not establish process success. */
internal enum class CheckStatus(
    val label: String,
) {
    RUNNING("Running"),
    PASSED("Passed"),
    FAILED("Failed"),
    STALE("Stale"),
    UNVERIFIED("Unverified"),
}

/** One command and its locally observed evidence state. */
internal data class AgentCheck(
    val tool: Block.Tool,
    val status: CheckStatus,
    val turnId: String = "",
)

/**
 * Recognizes explicit top-level process exit codes only. ACP raw output is adapter-specific;
 * prose, nested results, booleans, and numeric strings are deliberately not interpreted.
 */
internal fun commandExitCode(output: JsonElement?): Int? {
    val fields = output as? JsonObject ?: return null
    val code = (fields["exitCode"] ?: fields["exit_code"]) as? JsonPrimitive ?: return null
    return if (code.isString) null else code.intOrNull
}

/** Derives command evidence without interpreting output text as a test result. */
internal fun agentChecks(
    turns: List<Turn>,
    workspaceRevision: Long,
    isStreaming: Boolean,
    hasUnsavedEdits: Boolean = false,
): List<AgentCheck> =
    turns.filterIsInstance<Turn.Agent>().flatMap { turn ->
        turn.blocks.filterIsInstance<Block.Tool>().filter { it.kind == "execute" }.map { tool ->
            val status =
                when {
                    tool.status == ToolStatus.RUNNING && isStreaming && turn === turns.lastOrNull() -> {
                        CheckStatus.RUNNING
                    }

                    tool.checkRevision == null -> {
                        CheckStatus.UNVERIFIED
                    }

                    hasUnsavedEdits || tool.checkRevision != workspaceRevision -> {
                        CheckStatus.STALE
                    }

                    tool.status == ToolStatus.FAILED -> {
                        CheckStatus.FAILED
                    }

                    tool.status != ToolStatus.OK || tool.exitCode == null -> {
                        CheckStatus.UNVERIFIED
                    }

                    tool.exitCode != 0 -> {
                        CheckStatus.FAILED
                    }

                    else -> {
                        CheckStatus.PASSED
                    }
                }
            AgentCheck(tool, status, turn.id)
        }
    }

/** Compact overview; failures and uncertain evidence take precedence over a successful command. */
internal fun checkSummary(checks: List<AgentCheck>): String {
    if (checks.isEmpty()) return "Not run"
    val priority =
        listOf(CheckStatus.RUNNING, CheckStatus.FAILED, CheckStatus.STALE, CheckStatus.UNVERIFIED, CheckStatus.PASSED)
    val latest = checks.groupBy { it.tool.command ?: "${it.turnId}:${it.tool.id}" }.values.map { it.last() }
    return priority.first { status -> latest.any { it.status == status } }.label
}

/** Explanation displayed with the original command output. */
internal fun checkExplanation(check: AgentCheck): String =
    when (check.status) {
        CheckStatus.RUNNING -> {
            "Waiting for process completion."
        }

        CheckStatus.PASSED -> {
            "Exit code 0 reported by the agent. This confirms command success, not goal completion."
        }

        CheckStatus.FAILED -> {
            check.tool.exitCode?.let { "Exit code $it reported by the agent." }
                ?: "The tool reported failure."
        }

        CheckStatus.STALE -> {
            "Workspace changed or has unsaved edits. Run it again to check the current files."
        }

        CheckStatus.UNVERIFIED -> {
            "No confirmed process result. A launch acknowledgement is not a passed check."
        }
    }

/** Whether a disk event can change check inputs; generated output and IDE bookkeeping are excluded. */
internal fun isCheckInput(
    workspace: String,
    path: String,
): Boolean {
    val root = workspace.replace('\\', '/').trimEnd('/') + "/"
    val normalized = path.replace('\\', '/')
    if (!normalized.startsWith(root)) return false
    val directories = normalized.removePrefix(root).split('/').dropLast(1)
    return directories.none { it in CHECK_OUTPUT_DIRECTORIES }
}

private val CHECK_OUTPUT_DIRECTORIES = setOf(".git", ".gradle", ".jetaprog", "build", "out", "target", "node_modules")
