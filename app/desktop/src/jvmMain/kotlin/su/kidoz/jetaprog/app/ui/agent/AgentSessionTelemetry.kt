package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.Immutable

/** Agent-reported session information, independent of prompt lifecycle and permissions. */
@Immutable
public data class AgentSessionTelemetry(
    val title: String? = null,
    val updatedAt: String? = null,
    val usage: String? = null,
    val notices: List<String> = emptyList(),
    val compactions: List<AgentCompaction> = emptyList(),
)

/** ID-addressed context maintenance, separate from tool execution. */
@Immutable
public data class AgentCompaction(
    val id: String,
    val status: String = "in_progress",
    val summary: String = "",
    val error: String? = null,
)
