package su.kidoz.jetaprog.common

/**
 * Represents a resource that can be disposed/cleaned up.
 * Follows the dispose pattern for resource management.
 */
public fun interface Disposable {
    /**
     * Disposes this resource, releasing any held resources.
     * After disposal, the resource should not be used.
     */
    public fun dispose()
}

/**
 * A collection of disposables that can be disposed together.
 */
public class DisposableCollection : Disposable {
    private val disposables = mutableListOf<Disposable>()
    private var disposed = false

    /**
     * Adds a disposable to this collection.
     * @param disposable The disposable to add
     * @return The added disposable for chaining
     */
    public fun <T : Disposable> add(disposable: T): T {
        check(!disposed) { "Cannot add to a disposed collection" }
        disposables.add(disposable)
        return disposable
    }

    /**
     * Adds a dispose action to this collection.
     * @param action The action to execute on dispose
     * @return A disposable representing the action
     */
    public fun add(action: () -> Unit): Disposable {
        val disposable = Disposable { action() }
        add(disposable)
        return disposable
    }

    /**
     * Disposes all contained disposables in reverse order.
     */
    override fun dispose() {
        if (disposed) return
        disposed = true
        disposables.asReversed().forEach { it.dispose() }
        disposables.clear()
    }

    /**
     * Returns true if this collection has been disposed.
     */
    public val isDisposed: Boolean get() = disposed

    /**
     * Returns the number of disposables in this collection.
     */
    public val size: Int get() = disposables.size
}

/**
 * Creates a disposable from a lambda.
 */
public fun disposable(action: () -> Unit): Disposable = Disposable { action() }
