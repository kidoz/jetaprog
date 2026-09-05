package su.kidoz.jetaprog.app.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import su.kidoz.jetaprog.plugins.api.services.InputBoxOptions
import su.kidoz.jetaprog.plugins.api.services.QuickPickItem
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * A modal request a plugin made through the notification service, waiting for
 * the host UI to render it and hand back the user's answer.
 */
public sealed interface PluginDialogRequest {
    /** The plugin asked for text input. */
    public data class Input(
        val title: String?,
        val prompt: String?,
        val value: String?,
        val placeholder: String?,
        val password: Boolean,
        val completion: SingleShotCompletion<String?>,
    ) : PluginDialogRequest

    /** The plugin asked the user to pick from a list. */
    public data class QuickPick(
        val title: String?,
        val placeholder: String?,
        val multiSelect: Boolean,
        val items: List<QuickPickItem>,
        val completion: SingleShotCompletion<List<QuickPickItem>>,
    ) : PluginDialogRequest
}

/**
 * A one-shot completion wrapper: the underlying continuation may only be
 * resumed once, whichever side (user action or disposal) gets there first.
 */
public class SingleShotCompletion<T> internal constructor(
    private val continuation: AtomicReference<kotlin.coroutines.Continuation<T>?>,
) {
    /** Resumes with [value]; later calls are ignored. */
    public fun complete(value: T) {
        continuation.getAndSet(null)?.let { it.resume(value) }
    }

    /** The dialog was discarded without an answer. */
    public fun cancel() {
        val continuation = continuation.getAndSet(null) ?: return
        continuation.resumeWith(Result.failure(CancellationException("Plugin dialog dismissed")))
    }
}

/** Suspends the caller, handing it a [SingleShotCompletion] to resolve later. */
private suspend fun <T> suspendWithCompletion(block: (SingleShotCompletion<T>) -> Unit): T =
    suspendCancellableCoroutine { cont ->
        val completion = SingleShotCompletion(AtomicReference(cont))
        cont.invokeOnCancellation { completion.cancel() }
        block(completion)
    }

/**
 * Application-scoped queue of pending plugin modal requests. The notification
 * bridge enqueues; [su.kidoz.jetaprog.app.ui.plugin.PluginDialogHost] renders
 * the current request and completes it with the user's answer.
 *
 * Only one modal request is shown at a time (the latest wins; superseded
 * requests are cancelled with null, matching a dismissed dialog).
 */
public class PluginDialogRequests {
    private val mutableRequests =
        MutableSharedFlow<PluginDialogRequest?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val pending = AtomicReference<PluginDialogRequest?>(null)

    /** The request currently awaiting an answer, or null. */
    public val requests: SharedFlow<PluginDialogRequest?> = mutableRequests

    private fun show(request: PluginDialogRequest) {
        pending.getAndSet(request)?.let { previous -> previous.cancelCurrent() }
        mutableRequests.tryEmit(request)
    }

    private fun PluginDialogRequest.cancelCurrent() {
        when (this) {
            is PluginDialogRequest.Input -> completion.cancel()
            is PluginDialogRequest.QuickPick -> completion.cancel()
        }
    }

    /** Shows the input box and suspends until answered or cancelled. */
    public suspend fun requestInput(options: InputBoxOptions): String? =
        suspendWithCompletion { completion ->
            show(
                PluginDialogRequest.Input(
                    title = options.title,
                    prompt = options.prompt,
                    value = options.value,
                    placeholder = options.placeHolder,
                    password = options.password,
                    completion = completion,
                ),
            )
        }

    /** Shows the quick pick and suspends until answered or cancelled. */
    public suspend fun requestQuickPick(
        items: List<QuickPickItem>,
        title: String?,
        placeholder: String?,
        multiSelect: Boolean,
    ): List<QuickPickItem>? =
        suspendWithCompletion<List<QuickPickItem>> { completion ->
            show(
                PluginDialogRequest.QuickPick(
                    title = title,
                    placeholder = placeholder,
                    multiSelect = multiSelect,
                    items = items,
                    completion = completion,
                ),
            )
        }
}
