package su.kidoz.jetaprog.common.messaging

/**
 * Broadcast direction for message delivery in hierarchical buses.
 */
public enum class BroadcastDirection {
    /**
     * Message propagates from parent bus to all child buses.
     */
    TO_CHILDREN,

    /**
     * Message propagates from parent bus to direct child buses only.
     */
    TO_DIRECT_CHILDREN,

    /**
     * Message propagates from child bus up to parent bus.
     */
    TO_PARENT,

    /**
     * No propagation -- message stays on the bus where it was published.
     */
    NONE,
}

/**
 * Defines a messaging endpoint with a typed listener interface.
 *
 * Topics are used with [MessageBus] to implement publish-subscribe messaging.
 * Each topic has a unique name and a listener class that defines the callback interface.
 *
 * Usage:
 * ```kotlin
 * interface MyListener {
 *     fun onEvent(data: String)
 * }
 *
 * val MY_TOPIC = Topic.create<MyListener>("MyTopic", BroadcastDirection.NONE)
 *
 * // Subscribe
 * bus.connect(disposable).subscribe(MY_TOPIC) { data -> println(data) }
 *
 * // Publish
 * bus.syncPublisher(MY_TOPIC).onEvent("hello")
 * ```
 *
 * @param L The listener interface type
 */
public class Topic<L : Any> private constructor(
    /**
     * The unique display name of this topic.
     */
    public val displayName: String,
    /**
     * The broadcast direction for hierarchical message delivery.
     */
    public val broadcastDirection: BroadcastDirection,
) {
    override fun toString(): String = "Topic($displayName)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Topic<*>) return false
        return displayName == other.displayName
    }

    override fun hashCode(): Int = displayName.hashCode()

    public companion object {
        /**
         * Creates a new topic with the given name and broadcast direction.
         *
         * @param displayName The unique name for the topic
         * @param broadcastDirection How messages propagate in hierarchical buses
         * @return A new topic instance
         */
        public fun <L : Any> create(
            displayName: String,
            broadcastDirection: BroadcastDirection = BroadcastDirection.NONE,
        ): Topic<L> = Topic(displayName, broadcastDirection)
    }
}
