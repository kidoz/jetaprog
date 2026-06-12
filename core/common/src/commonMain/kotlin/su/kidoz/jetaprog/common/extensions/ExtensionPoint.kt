package su.kidoz.jetaprog.common.extensions

import su.kidoz.jetaprog.common.Disposable

/**
 * A named extension point that accepts registrations of a specific type.
 *
 * Extension points allow plugins and modules to contribute implementations
 * that are discovered and used by the host system, similar to IntelliJ's
 * `ExtensionPointName` mechanism.
 *
 * Usage:
 * ```kotlin
 * // Define
 * val MY_EP = ExtensionPointName<MyContributor>("com.example.myContributor")
 *
 * // Register
 * extensionRegistry.registerExtension(MY_EP, MyContributorImpl())
 *
 * // Use
 * extensionRegistry.getExtensions(MY_EP).forEach { it.doWork() }
 * ```
 *
 * @param T The extension type
 */
public class ExtensionPointName<T : Any>(
    /**
     * The unique fully-qualified name of this extension point.
     */
    public val name: String,
) {
    override fun toString(): String = "ExtensionPoint($name)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtensionPointName<*>) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
}

/**
 * Registry for managing extension point registrations.
 *
 * Provides thread-safe registration and lookup of extensions, with support
 * for dynamic registration/unregistration through [Disposable] lifecycle.
 */
public interface ExtensionRegistry {
    /**
     * Registers an extension for the given extension point.
     *
     * @param extensionPoint The extension point to register with
     * @param extension The extension instance to register
     * @return A [Disposable] that unregisters the extension when disposed
     */
    public fun <T : Any> registerExtension(
        extensionPoint: ExtensionPointName<T>,
        extension: T,
    ): Disposable

    /**
     * Returns all registered extensions for the given extension point.
     *
     * @param extensionPoint The extension point to query
     * @return An immutable list of registered extensions (may be empty)
     */
    public fun <T : Any> getExtensions(extensionPoint: ExtensionPointName<T>): List<T>

    /**
     * Returns true if there are any extensions registered for the point.
     */
    public fun hasExtensions(extensionPoint: ExtensionPointName<*>): Boolean

    /**
     * Adds a listener that is notified when extensions are added or removed.
     *
     * @param extensionPoint The extension point to watch
     * @param listener The listener to notify
     * @return A [Disposable] that removes the listener when disposed
     */
    public fun <T : Any> addExtensionPointListener(
        extensionPoint: ExtensionPointName<T>,
        listener: ExtensionPointListener<T>,
    ): Disposable
}

/**
 * Listener for extension point changes.
 */
public interface ExtensionPointListener<in T : Any> {
    /**
     * Called when a new extension is registered.
     */
    public fun extensionAdded(extension: T) {}

    /**
     * Called when an extension is unregistered.
     */
    public fun extensionRemoved(extension: T) {}
}
