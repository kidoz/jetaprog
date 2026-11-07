package su.kidoz.jetaprog.app.mcp

import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy

/**
 * A sensitive MCP tool action, mapped onto the agent permission kinds.
 */
public enum class McpSensitiveAction {
    /** Writing a project file or changing a breakpoint. Uses the Edit policy. */
    EDIT,

    /** Running Gradle or controlling a debug session. Uses the Run policy. */
    RUN,
}

/**
 * Applies the project's agent permission policy to an MCP tool call.
 *
 * Ask suspends in [ask] until the user allows or denies the action. Auto allows it.
 * Never denies it. A missing policy asks.
 *
 * @param permissions The current per-kind policies for the open project.
 * @param ask Shows the approval and returns the user's decision.
 */
public class McpActionAuthorizer(
    private val permissions: suspend () -> Map<PermissionKind, PermissionPolicy>,
    private val ask: suspend (String) -> Boolean,
) {
    /**
     * Whether [action] may proceed. [description] is the text shown when the policy asks.
     */
    public suspend fun allow(
        action: McpSensitiveAction,
        description: String,
    ): Boolean {
        val kind =
            when (action) {
                McpSensitiveAction.EDIT -> PermissionKind.EDIT
                McpSensitiveAction.RUN -> PermissionKind.RUN
            }
        return when (permissions()[kind] ?: PermissionPolicy.ASK) {
            PermissionPolicy.AUTO -> true
            PermissionPolicy.NEVER -> false
            PermissionPolicy.ASK -> ask(description)
        }
    }
}
