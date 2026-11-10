package su.kidoz.jetaprog.acp.protocol

/** Payload-free connection diagnostics safe to display without exposing prompts or tool output. */
public enum class AcpDiagnostic {
    MALFORMED_MESSAGE,
    UNSUPPORTED_OR_MALFORMED_UPDATE,
    NOTIFICATION_HANDLER_FAILED,
    INCOMING_OVERFLOW,
}

/** Connection counters; lengths count UTF-16 code units, not encoded network bytes. */
public data class AcpConnectionMetrics(
    val receivedMessages: Long = 0,
    val receivedChars: Long = 0,
    val queuedNotifications: Int = 0,
    val queuedChars: Long = 0,
    val peakQueuedNotifications: Int = 0,
    val issues: Long = 0,
)
