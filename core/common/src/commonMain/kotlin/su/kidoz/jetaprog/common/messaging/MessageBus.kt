package su.kidoz.jetaprog.common.messaging

import su.kidoz.jetaprog.common.Disposable

/**
 * Publish-subscribe messaging infrastructure for decoupled component communication.
 *
 * Message buses can be organized hierarchically (Application -> Project -> Module).
 * Messages propagate according to the [BroadcastDirection] defined on each [Topic].
 *
 * Usage:
 * ```kotlin
 * // Subscribe
 * val connection = bus.connect(parentDisposable)
 * connection.subscribe(MY_TOPIC, myHandler)
 *
 * // Publish
 * bus.publish(MY_TOPIC) { listener -> listener.onEvent(data) }
 * ```
 */
public interface MessageBus : Disposable {
    /**
     * The parent bus in the hierarchy, or null if this is the root bus.
     */
    public val parent: MessageBus?

    /**
     * Creates a new connection to this bus.
     *
     * The connection is automatically disposed when the given [parentDisposable] is disposed.
     *
     * @param parentDisposable The parent disposable controlling the connection lifecycle
     * @return A new connection for subscribing to topics
     */
    public fun connect(parentDisposable: Disposable): MessageBusConnection

    /**
     * Creates a new standalone connection to this bus.
     *
     * The caller is responsible for disposing the returned connection.
     *
     * @return A new connection for subscribing to topics
     */
    public fun connect(): MessageBusConnection

    /**
     * Publishes a message to all subscribers of the given topic.
     *
     * The [action] is invoked for each subscriber handler, following the
     * topic's broadcast direction for hierarchical delivery.
     *
     * @param topic The topic to publish on
     * @param action The action to invoke on each subscriber
     * @param L The listener interface type
     */
    public fun <L : Any> publish(
        topic: Topic<L>,
        action: (L) -> Unit,
    )

    /**
     * Returns true if this bus has been disposed.
     */
    public val isDisposed: Boolean

    /**
     * Returns true if this bus has any subscribers for the given topic.
     */
    public fun hasSubscribers(topic: Topic<*>): Boolean
}
