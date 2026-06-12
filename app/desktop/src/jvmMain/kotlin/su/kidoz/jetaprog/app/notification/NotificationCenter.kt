package su.kidoz.jetaprog.app.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Severity of a [Notification]. Drives stripe color and icon in the overlay.
 */
public enum class NotificationSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

/**
 * A user-facing notification (toast / inline banner).
 *
 * Notifications are produced by ViewModels via [NotificationCenter] in
 * response to one-shot effects (e.g. `ShowError`). The overlay component is
 * responsible for rendering, auto-dismissing, and stacking.
 *
 * @property id Monotonically increasing identifier assigned by the center.
 * @property severity Visual severity — error/warning render with bolder
 *                    stripes; info is the default.
 * @property title Required short heading. Must read independently of body.
 * @property message Optional secondary body. Use the *subject + cause +
 *                   remedy* pattern from the UI/UX guide.
 * @property actionLabel Optional action button label (e.g. "Retry"). Pair
 *                       with [onAction]; both null = no action button.
 * @property onAction Callback invoked when the action button is clicked.
 * @property autoDismissMs Auto-dismiss timeout. Pass `null` to require
 *                         manual dismissal (typical for errors).
 */
public data class Notification(
    public val id: Long = 0L,
    public val severity: NotificationSeverity,
    public val title: String,
    public val message: String? = null,
    public val actionLabel: String? = null,
    public val onAction: (() -> Unit)? = null,
    public val autoDismissMs: Long? = DEFAULT_AUTO_DISMISS_MS,
) {
    public companion object {
        public const val DEFAULT_AUTO_DISMISS_MS: Long = 8_000L
    }
}

/**
 * Process-wide notification hub. Single instance per application
 * (held by `JetaProgApplication`); ViewModels obtain a reference via DI or
 * constructor injection.
 *
 * Notifications are stored in insertion order. The overlay decides how many
 * to render simultaneously and queues the rest.
 */
public class NotificationCenter {
    private val nextId = AtomicLong(0L)
    private val mutable = MutableStateFlow<List<Notification>>(emptyList())

    public val notifications: StateFlow<List<Notification>> = mutable.asStateFlow()

    /**
     * Push a notification. Returns the assigned id (for later [dismiss]).
     */
    public fun push(notification: Notification): Long {
        val id = nextId.incrementAndGet()
        val stamped = notification.copy(id = id)
        mutable.update { it + stamped }
        return id
    }

    /** Convenience: push an info-severity notification. */
    public fun info(
        title: String,
        message: String? = null,
    ): Long = push(Notification(severity = NotificationSeverity.INFO, title = title, message = message))

    /** Convenience: push a success-severity notification. */
    public fun success(
        title: String,
        message: String? = null,
    ): Long = push(Notification(severity = NotificationSeverity.SUCCESS, title = title, message = message))

    /** Convenience: push a warning-severity notification. */
    public fun warning(
        title: String,
        message: String? = null,
    ): Long = push(Notification(severity = NotificationSeverity.WARNING, title = title, message = message))

    /**
     * Convenience: push an error-severity notification. Auto-dismiss is
     * disabled by default — errors require user acknowledgement.
     */
    public fun error(
        title: String,
        message: String? = null,
    ): Long =
        push(
            Notification(
                severity = NotificationSeverity.ERROR,
                title = title,
                message = message,
                autoDismissMs = null,
            ),
        )

    /** Dismiss a specific notification. No-op if [id] is unknown. */
    public fun dismiss(id: Long) {
        mutable.update { current -> current.filterNot { it.id == id } }
    }

    /** Dismiss every active notification. */
    public fun dismissAll() {
        mutable.update { emptyList() }
    }
}
