package su.kidoz.jetaprog.common.extensions

import su.kidoz.jetaprog.common.Disposable

/**
 * Default thread-safe implementation of [ExtensionRegistry].
 *
 * Supports dynamic extension registration/unregistration with listener notifications.
 */
public class DefaultExtensionRegistry : ExtensionRegistry {
    private val extensions = mutableMapOf<ExtensionPointName<*>, MutableList<Any>>()
    private val listeners = mutableMapOf<ExtensionPointName<*>, MutableList<ExtensionPointListener<*>>>()

    override fun <T : Any> registerExtension(
        extensionPoint: ExtensionPointName<T>,
        extension: T,
    ): Disposable {
        synchronized(extensions) {
            extensions.getOrPut(extensionPoint) { mutableListOf() }.add(extension)
        }
        notifyAdded(extensionPoint, extension)
        return Disposable {
            synchronized(extensions) {
                extensions[extensionPoint]?.remove(extension)
            }
            notifyRemoved(extensionPoint, extension)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getExtensions(extensionPoint: ExtensionPointName<T>): List<T> {
        synchronized(extensions) {
            return (extensions[extensionPoint]?.toList() as? List<T>) ?: emptyList()
        }
    }

    override fun hasExtensions(extensionPoint: ExtensionPointName<*>): Boolean {
        synchronized(extensions) {
            return extensions[extensionPoint]?.isNotEmpty() == true
        }
    }

    override fun <T : Any> addExtensionPointListener(
        extensionPoint: ExtensionPointName<T>,
        listener: ExtensionPointListener<T>,
    ): Disposable {
        synchronized(listeners) {
            listeners.getOrPut(extensionPoint) { mutableListOf() }.add(listener)
        }
        return Disposable {
            synchronized(listeners) {
                listeners[extensionPoint]?.remove(listener)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> notifyAdded(
        extensionPoint: ExtensionPointName<T>,
        extension: T,
    ) {
        val eps: List<ExtensionPointListener<T>>
        synchronized(listeners) {
            eps = (listeners[extensionPoint]?.toList() as? List<ExtensionPointListener<T>>) ?: return
        }
        for (listener in eps) {
            listener.extensionAdded(extension)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> notifyRemoved(
        extensionPoint: ExtensionPointName<T>,
        extension: T,
    ) {
        val eps: List<ExtensionPointListener<T>>
        synchronized(listeners) {
            eps = (listeners[extensionPoint]?.toList() as? List<ExtensionPointListener<T>>) ?: return
        }
        for (listener in eps) {
            listener.extensionRemoved(extension)
        }
    }
}
