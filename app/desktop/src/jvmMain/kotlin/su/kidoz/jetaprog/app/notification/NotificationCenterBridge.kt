package su.kidoz.jetaprog.app.notification

import kotlinx.coroutines.suspendCancellableCoroutine
import su.kidoz.jetaprog.app.plugin.PluginDialogRequests
import su.kidoz.jetaprog.plugins.api.services.InputBoxOptions
import su.kidoz.jetaprog.plugins.api.services.QuickPickItem
import su.kidoz.jetaprog.plugins.runtime.services.UiMessageSeverity
import su.kidoz.jetaprog.plugins.runtime.services.UiNotificationBridge
import su.kidoz.jetaprog.plugins.runtime.services.UiProgressHandle
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Bridges plugin notification requests onto the application's
 * [NotificationCenter] toasts and [PluginDialogRequests] modals.
 *
 * Messages with action items suspend until the user picks one (returned as
 * the action label) or dismisses the toast (null). Progress notifications
 * stay visible with a live detail line until the task finishes.
 */
public class NotificationCenterBridge(
    private val center: NotificationCenter,
    private val dialogRequests: PluginDialogRequests,
) : UiNotificationBridge {
    /** The currently visible "permanent" status toast, replaced on the next status. */
    private val lastStatusId = AtomicReference<Long?>(null)

    override suspend fun showMessage(
        severity: UiMessageSeverity,
        message: String,
        actions: List<String>,
    ): String? {
        if (actions.isEmpty()) {
            center.push(
                Notification(
                    severity = severity.toSeverity(),
                    title = PLUGIN_NOTIFICATION_TITLE,
                    message = message,
                ),
            )
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            fun resumeOnce(value: String?) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(value)
                }
            }
            center.push(
                Notification(
                    severity = severity.toSeverity(),
                    title = PLUGIN_NOTIFICATION_TITLE,
                    message = message,
                    actions =
                        actions.map { label ->
                            NotificationAction(label = label) { resumeOnce(label) }
                        },
                    onDismiss = { resumeOnce(null) },
                    autoDismissMs = null,
                ),
            )
        }
    }

    override fun showStatus(
        message: String,
        hideAfterMs: Long,
    ) {
        lastStatusId.getAndSet(null)?.let(center::dismiss)
        val id =
            center.push(
                Notification(
                    severity = NotificationSeverity.INFO,
                    title = message,
                    // hideAfterMs == 0 means "until replaced"; keep it on screen
                    // and swap it out when the next status arrives.
                    autoDismissMs = if (hideAfterMs > 0) hideAfterMs else null,
                ),
            )
        if (hideAfterMs <= 0) lastStatusId.set(id)
    }

    override fun beginProgress(title: String): UiProgressHandle {
        val id =
            center.push(
                Notification(
                    severity = NotificationSeverity.INFO,
                    title = title,
                    message = null,
                    autoDismissMs = null,
                ),
            )
        return object : UiProgressHandle {
            override fun report(
                message: String?,
                increment: Int?,
            ) {
                center.updateMessage(id, message)
            }

            override fun close() {
                center.dismiss(id)
            }
        }
    }

    override suspend fun requestInput(
        title: String?,
        prompt: String?,
        value: String?,
        placeholder: String?,
        password: Boolean,
    ): String? =
        dialogRequests.requestInput(
            InputBoxOptions(
                title = title,
                prompt = prompt,
                value = value,
                placeHolder = placeholder,
                password = password,
            ),
        )

    override suspend fun <T : QuickPickItem> requestQuickPick(
        items: List<T>,
        title: String?,
        placeholder: String?,
        multiSelect: Boolean,
    ): List<T>? {
        val picked =
            dialogRequests.requestQuickPick(
                items = items,
                title = title,
                placeholder = placeholder,
                multiSelect = multiSelect,
            ) ?: return null
        @Suppress("UNCHECKED_CAST")
        return picked as List<T>
    }

    private fun UiMessageSeverity.toSeverity(): NotificationSeverity =
        when (this) {
            UiMessageSeverity.INFO -> NotificationSeverity.INFO
            UiMessageSeverity.WARNING -> NotificationSeverity.WARNING
            UiMessageSeverity.ERROR -> NotificationSeverity.ERROR
        }

    private companion object {
        /** Heading for plugin-originated toasts so users can tell them apart. */
        const val PLUGIN_NOTIFICATION_TITLE = "Plugin"
    }
}
