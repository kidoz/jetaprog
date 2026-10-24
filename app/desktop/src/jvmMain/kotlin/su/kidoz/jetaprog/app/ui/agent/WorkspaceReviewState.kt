package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.Immutable
import su.kidoz.jetaprog.app.agent.WorkspaceChange

/** The source of files presented by the review surface. */
public enum class ReviewScope {
    /** Edits explicitly reported by the current agent conversation. */
    AGENT,

    /** Git's staged, unstaged and untracked changes in this checkout. */
    WORKSPACE,
}

/** Read-only workspace scan and preview. Baseline paths are session-local and never inferred on restore. */
@Immutable
public data class WorkspaceReviewState(
    /** Paths returned by the last successful Git status scan. */
    val files: List<WorkspaceChange> = emptyList(),
    /** Whether a scan or preview is pending. */
    val loading: Boolean = false,
    /** Scan or preview failure, shown instead of a misleading empty result. */
    val error: String? = null,
    /** Workspace revision when the scan began; null means not scanned. */
    val revision: Long? = null,
    /** Paths already dirty before this conversation's first prompt; null means unavailable. */
    val preExistingPaths: Set<String>? = null,
    /** File whose preview is displayed. */
    val previewPath: String? = null,
    /** Read-only current diff or untracked contents. */
    val preview: String? = null,
)

/** File-level evidence is deliberately non-exclusive and does not assign ownership to all lines. */
internal fun workspaceChangeOrigin(
    file: WorkspaceChange,
    baseline: Set<String>?,
    agentPaths: Set<String>,
): String {
    val evidence = mutableListOf<String>()
    if (baseline?.let { file.path in it || file.originalPath in it } == true) evidence += "Changed before this task"
    if (file.path in agentPaths || file.originalPath in agentPaths) evidence += "Agent-reported edits"
    if (evidence.isEmpty()) evidence += "Origin unknown"
    return evidence.joinToString(" · ")
}
