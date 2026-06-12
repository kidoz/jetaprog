package su.kidoz.jetaprog.common.messaging.impl

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.messaging.BroadcastDirection
import su.kidoz.jetaprog.common.messaging.MessageBus
import su.kidoz.jetaprog.common.messaging.MessageBusConnection
import su.kidoz.jetaprog.common.messaging.Topic

/**
 * Default implementation of [MessageBus] with hierarchical broadcasting support.
 *
 * Supports parent-child bus relationships with configurable broadcast directions
 * per topic (TO_CHILDREN, TO_DIRECT_CHILDREN, TO_PARENT, NONE).
 */
public class DefaultMessageBus(
    override val parent: MessageBus? = null,
) : MessageBus {
    private val connections = mutableListOf<DefaultMessageBusConnection>()
    private val childBuses = mutableListOf<DefaultMessageBus>()
    private val topicSubscribers = mutableMapOf<Topic<*>, MutableList<Any>>()

    @Volatile
    private var _isDisposed = false

    override val isDisposed: Boolean get() = _isDisposed

    init {
        if (parent is DefaultMessageBus) {
            parent.addChild(this)
        }
    }

    override fun connect(parentDisposable: Disposable): MessageBusConnection {
        check(!_isDisposed) { "Cannot connect to a disposed MessageBus" }
        val connection = DefaultMessageBusConnection(this)
        synchronized(connections) {
            connections.add(connection)
        }
        return connection
    }

    override fun connect(): MessageBusConnection {
        check(!_isDisposed) { "Cannot connect to a disposed MessageBus" }
        val connection = DefaultMessageBusConnection(this)
        synchronized(connections) {
            connections.add(connection)
        }
        return connection
    }

    override fun <L : Any> publish(
        topic: Topic<L>,
        action: (L) -> Unit,
    ) {
        check(!_isDisposed) { "Cannot publish on a disposed MessageBus" }
        val handlers = collectHandlers(topic)
        for (handler in handlers) {
            action(handler)
        }
    }

    override fun hasSubscribers(topic: Topic<*>): Boolean {
        synchronized(topicSubscribers) {
            val local = topicSubscribers[topic]?.isNotEmpty() == true
            if (local) return true
        }
        return when (topic.broadcastDirection) {
            BroadcastDirection.TO_CHILDREN, BroadcastDirection.TO_DIRECT_CHILDREN -> {
                synchronized(childBuses) {
                    childBuses.any { it.hasLocalSubscribers(topic) }
                }
            }

            BroadcastDirection.TO_PARENT -> {
                (parent as? DefaultMessageBus)?.hasLocalSubscribers(topic) == true
            }

            BroadcastDirection.NONE -> {
                false
            }
        }
    }

    override fun dispose() {
        if (_isDisposed) return
        _isDisposed = true

        synchronized(childBuses) {
            childBuses.toList().forEach { it.dispose() }
            childBuses.clear()
        }
        synchronized(connections) {
            connections.toList().forEach { it.dispose() }
            connections.clear()
        }
        synchronized(topicSubscribers) {
            topicSubscribers.clear()
        }

        if (parent is DefaultMessageBus) {
            parent.removeChild(this)
        }
    }

    /**
     * Called when a connection subscribes to a topic.
     */
    internal fun notifySubscribed(
        topic: Topic<*>,
        handler: Any,
    ) {
        synchronized(topicSubscribers) {
            topicSubscribers.getOrPut(topic) { mutableListOf() }.add(handler)
        }
    }

    /**
     * Called when a connection is disposed.
     */
    internal fun notifyConnectionDisposed(connection: DefaultMessageBusConnection) {
        synchronized(connections) {
            connections.remove(connection)
        }
        synchronized(topicSubscribers) {
            for (topic in connection.getTopics()) {
                val handler = connection.getHandler(topic)
                topicSubscribers[topic]?.remove(handler)
            }
        }
    }

    private fun addChild(child: DefaultMessageBus) {
        synchronized(childBuses) {
            childBuses.add(child)
        }
    }

    private fun removeChild(child: DefaultMessageBus) {
        synchronized(childBuses) {
            childBuses.remove(child)
        }
    }

    private fun hasLocalSubscribers(topic: Topic<*>): Boolean {
        synchronized(topicSubscribers) {
            return topicSubscribers[topic]?.isNotEmpty() == true
        }
    }

    /**
     * Collects all handlers for a topic, including from parent/child buses
     * according to the topic's broadcast direction.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <L : Any> collectHandlers(topic: Topic<L>): List<L> {
        val handlers = mutableListOf<L>()

        // Local handlers
        synchronized(topicSubscribers) {
            topicSubscribers[topic]?.forEach { handlers.add(it as L) }
        }

        // Broadcast according to direction
        when (topic.broadcastDirection) {
            BroadcastDirection.TO_CHILDREN -> {
                synchronized(childBuses) {
                    for (child in childBuses) {
                        handlers.addAll(child.collectAllDescendantHandlers(topic))
                    }
                }
            }

            BroadcastDirection.TO_DIRECT_CHILDREN -> {
                synchronized(childBuses) {
                    for (child in childBuses) {
                        handlers.addAll(child.getLocalHandlers(topic))
                    }
                }
            }

            BroadcastDirection.TO_PARENT -> {
                val parentBus = parent as? DefaultMessageBus
                if (parentBus != null) {
                    handlers.addAll(parentBus.getLocalHandlers(topic))
                }
            }

            BroadcastDirection.NONE -> { /* no propagation */ }
        }

        return handlers
    }

    @Suppress("UNCHECKED_CAST")
    private fun <L : Any> getLocalHandlers(topic: Topic<L>): List<L> {
        synchronized(topicSubscribers) {
            return topicSubscribers[topic]?.map { it as L } ?: emptyList()
        }
    }

    private fun <L : Any> collectAllDescendantHandlers(topic: Topic<L>): List<L> {
        val handlers = mutableListOf<L>()
        handlers.addAll(getLocalHandlers(topic))
        synchronized(childBuses) {
            for (child in childBuses) {
                handlers.addAll(child.collectAllDescendantHandlers(topic))
            }
        }
        return handlers
    }
}
