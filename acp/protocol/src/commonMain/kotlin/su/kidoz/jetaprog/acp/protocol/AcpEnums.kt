package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable

/**
 * The reason a prompt turn finished, serialized as its wire string.
 *
 * Modeled as a string wrapper rather than an enum so that values introduced by
 * future protocol revisions deserialize without failing.
 */
@Serializable
@JvmInline
public value class StopReason(
    /** The raw wire value. */
    public val value: String,
) {
    public companion object {
        /** The turn completed normally. */
        public val END_TURN: StopReason = StopReason("end_turn")

        /** The model reached its maximum token budget. */
        public val MAX_TOKENS: StopReason = StopReason("max_tokens")

        /** The agent reached its maximum number of turn requests. */
        public val MAX_TURN_REQUESTS: StopReason = StopReason("max_turn_requests")

        /** The agent refused to continue. */
        public val REFUSAL: StopReason = StopReason("refusal")

        /** The turn was cancelled by the client. */
        public val CANCELLED: StopReason = StopReason("cancelled")
    }
}

/**
 * The category of a tool call, serialized as its wire string.
 */
@Serializable
@JvmInline
public value class ToolKind(
    /** The raw wire value. */
    public val value: String,
) {
    public companion object {
        /** Reads data, e.g. file contents. */
        public val READ: ToolKind = ToolKind("read")

        /** Edits or creates content. */
        public val EDIT: ToolKind = ToolKind("edit")

        /** Deletes content. */
        public val DELETE: ToolKind = ToolKind("delete")

        /** Moves or renames content. */
        public val MOVE: ToolKind = ToolKind("move")

        /** Searches for content. */
        public val SEARCH: ToolKind = ToolKind("search")

        /** Executes a command. */
        public val EXECUTE: ToolKind = ToolKind("execute")

        /** Records the agent's reasoning. */
        public val THINK: ToolKind = ToolKind("think")

        /** Fetches a remote resource. */
        public val FETCH: ToolKind = ToolKind("fetch")

        /** Any other kind. */
        public val OTHER: ToolKind = ToolKind("other")
    }
}

/**
 * The lifecycle status of a tool call, serialized as its wire string.
 */
@Serializable
@JvmInline
public value class ToolCallStatus(
    /** The raw wire value. */
    public val value: String,
) {
    public companion object {
        /** The tool call has been created but not started. */
        public val PENDING: ToolCallStatus = ToolCallStatus("pending")

        /** The tool call is running. */
        public val IN_PROGRESS: ToolCallStatus = ToolCallStatus("in_progress")

        /** The tool call finished successfully. */
        public val COMPLETED: ToolCallStatus = ToolCallStatus("completed")

        /** The tool call failed. */
        public val FAILED: ToolCallStatus = ToolCallStatus("failed")
    }
}

/**
 * The kind of a permission option offered to the client.
 */
@Serializable
@JvmInline
public value class PermissionOptionKind(
    /** The raw wire value. */
    public val value: String,
) {
    public companion object {
        /** Allow this single occurrence. */
        public val ALLOW_ONCE: PermissionOptionKind = PermissionOptionKind("allow_once")

        /** Allow this and future occurrences. */
        public val ALLOW_ALWAYS: PermissionOptionKind = PermissionOptionKind("allow_always")

        /** Reject this single occurrence. */
        public val REJECT_ONCE: PermissionOptionKind = PermissionOptionKind("reject_once")

        /** Reject this and future occurrences. */
        public val REJECT_ALWAYS: PermissionOptionKind = PermissionOptionKind("reject_always")
    }
}
