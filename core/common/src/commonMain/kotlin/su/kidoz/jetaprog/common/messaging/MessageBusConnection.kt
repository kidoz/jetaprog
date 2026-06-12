package su.kidoz.jetaprog.common.messaging

import su.kidoz.jetaprog.common.Disposable

/**
 * A connection to a [MessageBus] that manages topic subscriptions.
 *
 * All subscriptions made through a connection share the same lifecycle.
 * When the connection is disposed, all subscriptions are removed.
 */
public interface MessageBusConnection : Disposable {
    /**
     * Subscribes to a topic with a handler function.
     *
     * The handler will be called whenever a message is published on the topic.
     * The subscription remains active until this connection is disposed.
     *
     * @param topic The topic to subscribe to
     * @param handler The handler invoked for each message
     * @param L The listener interface type
     */
    public fun <L : Any> subscribe(
        topic: Topic<L>,
        handler: L,
    )
}
