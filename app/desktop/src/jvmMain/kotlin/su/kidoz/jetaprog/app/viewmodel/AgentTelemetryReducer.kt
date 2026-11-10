package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.acp.protocol.AcpPatch
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.app.ui.agent.AgentCompaction
import su.kidoz.jetaprog.app.ui.agent.AgentSessionTelemetry

/** Applies metadata without deriving execution or authorization from presentation events. */
internal fun AgentSessionTelemetry.applyTelemetry(update: SessionUpdate): AgentSessionTelemetry =
    when (update) {
        is SessionUpdate.SessionInfoUpdate -> {
            copy(
                title = update.title.applyTo(title),
                updatedAt = update.updatedAt.applyTo(updatedAt),
            )
        }

        is SessionUpdate.UsageUpdate -> {
            copy(
                usage =
                    "Context: ${update.used} / ${update.size} tokens" +
                        (update.cost?.let { " · Session cost: ${it.amount} ${it.currency}" } ?: ""),
            )
        }

        is SessionUpdate.Notice -> {
            copy(
                notices =
                    (
                        notices +
                            listOfNotNull("${update.severity}: ${update.title}", update.description).joinToString("\n")
                    ).takeLast(MAX_SESSION_NOTICES),
            )
        }

        is SessionUpdate.CompactionUpdate -> {
            withCompaction(update.compactionId) {
                it.copy(
                    status = update.status,
                    summary =
                        when (val patch = update.summary) {
                            AcpPatch.Absent -> {
                                it.summary
                            }

                            is AcpPatch.Value -> {
                                patch.value.orEmpty().joinToString(
                                    "",
                                ) { content -> content.displayText() }
                            }
                        },
                    error = update.error.applyTo(it.error),
                )
            }
        }

        is SessionUpdate.CompactionSummaryChunk -> {
            withCompaction(update.compactionId) {
                it.copy(summary = it.summary + update.content.displayText())
            }
        }

        else -> {
            this
        }
    }

internal fun isTelemetryUpdate(update: SessionUpdate): Boolean =
    update is SessionUpdate.SessionInfoUpdate || update is SessionUpdate.UsageUpdate ||
        update is SessionUpdate.Notice || update is SessionUpdate.CompactionUpdate ||
        update is SessionUpdate.CompactionSummaryChunk

private fun <T> AcpPatch<T>.applyTo(previous: T?): T? =
    when (this) {
        AcpPatch.Absent -> previous
        is AcpPatch.Value -> value
    }

private fun ContentBlock.displayText(): String =
    when (this) {
        is ContentBlock.Text -> text
        else -> "[Non-text summary content]"
    }

private fun AgentSessionTelemetry.withCompaction(
    id: String,
    change: (AgentCompaction) -> AgentCompaction,
): AgentSessionTelemetry {
    val previous = compactions.firstOrNull { it.id == id }
    val updated = change(previous ?: AgentCompaction(id))
    return copy(
        compactions =
            if (previous == null) {
                compactions + updated
            } else {
                compactions.map {
                    if (it.id == id) updated else it
                }
            },
    )
}

private const val MAX_SESSION_NOTICES = 10
