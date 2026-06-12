package su.kidoz.jetaprog.common.messaging.impl

import su.kidoz.jetaprog.common.messaging.MessageBusConnection
import su.kidoz.jetaprog.common.messaging.Topic

/**
 * Default implementation of [MessageBusConnection].
 *
 * Stores topic-to-handler mappings and notifies the bus on disposal.
 */
internal class DefaultMessageBusConnection(
    private val bus: DefaultMessageBus,
) : MessageBusConnection {
    private val handlers = mutableMapOf<Topic<*>, Any>()

    @Volatile
    private var disposed = false

    override fun <L : Any> subscribe(
        topic: Topic<L>,
        handler: L,
    ) {
        check(!disposed) { "Cannot subscribe on a disposed connection" }
        check(!handlers.containsKey(topic)) { "Topic '$topic' already subscribed on this connection" }
        handlers[topic] = handler
        bus.notifySubscribed(topic, handler)
    }

    /**
     * Returns the handler registered for the given topic, or null.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <L : Any> getHandler(topic: Topic<L>): L? = handlers[topic] as? L

    /**
     * Returns all topics this connection is subscribed to.
     */
    internal fun getTopics(): Set<Topic<*>> = handlers.keys.toSet()

    override fun dispose() {
        if (disposed) return
        disposed = true
        bus.notifyConnectionDisposed(this)
        handlers.clear()
    }
}
