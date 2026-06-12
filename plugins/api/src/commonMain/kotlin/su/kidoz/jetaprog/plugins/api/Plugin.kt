package su.kidoz.jetaprog.plugins.api

/**
 * The core interface that all plugins must implement.
 *
 * Plugins are the primary extension mechanism for JetaProg IDE.
 * They can contribute language support, UI components, commands, and more.
 */
public interface Plugin {
    /**
     * The plugin manifest containing metadata.
     */
    public val manifest: PluginManifest

    /**
     * Called when the plugin is activated.
     *
     * This is where the plugin should register its contributions
     * (commands, providers, etc.) with the IDE.
     *
     * @param context The plugin context providing access to IDE services
     */
    public suspend fun activate(context: PluginContext)

    /**
     * Called when the plugin is deactivated.
     *
     * This is where the plugin should clean up any resources.
     * Note that subscriptions added to context.subscriptions are
     * automatically disposed.
     */
    public suspend fun deactivate()
}

/**
 * A base class for plugins that provides a default implementation.
 */
public abstract class BasePlugin(
    override val manifest: PluginManifest,
) : Plugin {
    protected lateinit var context: PluginContext
        private set

    override suspend fun activate(context: PluginContext) {
        this.context = context
        onActivate()
    }

    override suspend fun deactivate() {
        onDeactivate()
    }

    /**
     * Called when the plugin is activated. Override to add initialization logic.
     */
    protected open suspend fun onActivate() {}

    /**
     * Called when the plugin is deactivated. Override to add cleanup logic.
     */
    protected open suspend fun onDeactivate() {}
}
