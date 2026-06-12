package su.kidoz.jetaprog.common.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import su.kidoz.jetaprog.common.Disposable

/**
 * Represents an event source that can be subscribed to.
 * @param T The type of events emitted
 */
public interface Event<out T> {
    /**
     * Subscribes to this event with a handler.
     * @param handler The handler to call when an event is emitted
     * @return A Disposable that can be used to unsubscribe
     */
    public fun subscribe(handler: suspend (T) -> Unit): Disposable

    /**
     * Returns this event as a Flow for use with coroutines.
     */
    public fun asFlow(): Flow<T>
}

/**
 * An event emitter that can emit events to subscribers.
 * @param T The type of events to emit
 */
public class EventEmitter<T> : Event<T> {
    private val flow = MutableSharedFlow<T>(extraBufferCapacity = BUFFER_CAPACITY)
    private val subscriptions = mutableListOf<Disposable>()

    /**
     * Emits an event to all subscribers.
     * @param event The event to emit
     */
    public suspend fun emit(event: T) {
        flow.emit(event)
    }

    /**
     * Tries to emit an event without suspending.
     * @param event The event to emit
     * @return true if the event was emitted, false if the buffer was full
     */
    public fun tryEmit(event: T): Boolean = flow.tryEmit(event)

    override fun subscribe(handler: suspend (T) -> Unit): Disposable {
        val subscription = FlowSubscription(flow, handler)
        subscriptions.add(subscription)
        return Disposable {
            subscription.dispose()
            subscriptions.remove(subscription)
        }
    }

    override fun asFlow(): Flow<T> = flow.asSharedFlow()

    /**
     * Returns the number of active subscribers.
     */
    public val subscriberCount: Int get() = flow.subscriptionCount.value

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}

/**
 * Internal subscription implementation using Flow.
 */
private class FlowSubscription<T>(
    private val flow: MutableSharedFlow<T>,
    private val handler: suspend (T) -> Unit,
) : Disposable {
    @Volatile
    private var disposed = false

    override fun dispose() {
        disposed = true
    }
}
