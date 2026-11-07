package su.kidoz.jetaprog.app.mcp

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * One MCP action waiting for Allow or Deny.
 *
 * @property title Short heading for the dialog.
 * @property message What the caller wants to do.
 */
public class McpApprovalRequest internal constructor(
    public val title: String,
    public val message: String,
    private val onDecision: (Boolean) -> Unit,
) {
    private val decided = AtomicBoolean(false)

    /** The user allowed the action. */
    public fun allow() {
        decide(true)
    }

    /** The user denied the action, or dismissed the dialog. */
    public fun deny() {
        decide(false)
    }

    private fun decide(allowed: Boolean) {
        if (decided.compareAndSet(false, true)) onDecision(allowed)
    }
}

/**
 * Queue of MCP approval dialogs. Tool handlers suspend in [confirm]; the shell
 * renders [requests] and completes the visible one.
 *
 * A newer request replaces an unanswered one. The replaced request is denied.
 */
public class McpApprovalRequests {
    private val mutableRequests =
        MutableSharedFlow<McpApprovalRequest?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val pending = AtomicReference<McpApprovalRequest?>(null)

    /** The request currently awaiting an answer, or null. */
    public val requests: SharedFlow<McpApprovalRequest?> = mutableRequests

    /**
     * Shows [message] and suspends until the user allows or denies it.
     * Dismissal denies the action. Cancelling the caller dismisses the dialog.
     */
    public suspend fun confirm(
        title: String,
        message: String,
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            val slot = AtomicReference<McpApprovalRequest?>(null)
            val request =
                McpApprovalRequest(title, message) { allowed ->
                    val current = slot.get()
                    if (current != null && pending.compareAndSet(current, null)) {
                        mutableRequests.tryEmit(null)
                    }
                    if (continuation.isActive) continuation.resume(allowed)
                }
            slot.set(request)
            continuation.invokeOnCancellation {
                if (pending.compareAndSet(request, null)) mutableRequests.tryEmit(null)
                request.deny()
            }
            pending.getAndSet(request)?.deny()
            mutableRequests.tryEmit(request)
        }
}
