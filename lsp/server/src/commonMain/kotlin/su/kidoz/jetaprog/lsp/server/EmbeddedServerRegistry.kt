package su.kidoz.jetaprog.lsp.server

import su.kidoz.jetaprog.common.Disposable

/**
 * Registry for embedded LSP servers.
 *
 * Manages the lifecycle of embedded servers and provides discovery.
 * Servers are lazily created when first requested for a language.
 */
public interface EmbeddedServerRegistry : Disposable {
    /**
     * Register a factory for creating embedded servers for a language.
     *
     * @param languageId Language identifier (e.g., "kotlin", "java")
     * @param factory Factory function to create servers
     * @return Disposable to unregister the factory
     */
    public fun registerServerFactory(
        languageId: String,
        factory: EmbeddedServerFactory,
    ): Disposable

    /**
     * Get or create a server for the given language.
     *
     * If a server for the language exists, return it.
     * If a factory is registered, create and initialize a new server.
     * Otherwise, return null.
     *
     * @param languageId Language identifier
     * @return The server or null if not available
     */
    public suspend fun getServer(languageId: String): EmbeddedLspServer?

    /**
     * Get all active (initialized) servers.
     *
     * @return List of active servers
     */
    public fun getAllServers(): List<EmbeddedLspServer>

    /**
     * Get all registered language IDs.
     *
     * @return Set of language IDs with registered factories
     */
    public fun getRegisteredLanguages(): Set<String>

    /**
     * Check if a server factory is registered for a language.
     *
     * @param languageId Language identifier
     * @return True if a factory is registered
     */
    public fun hasFactory(languageId: String): Boolean

    /**
     * Check if a server is active for a language.
     *
     * @param languageId Language identifier
     * @return True if an active server exists
     */
    public fun hasActiveServer(languageId: String): Boolean

    /**
     * Shutdown a specific server.
     *
     * @param languageId Language identifier
     */
    public suspend fun shutdownServer(languageId: String)

    /**
     * Shutdown all active servers.
     */
    public suspend fun shutdownAll()
}

/**
 * Factory for creating embedded LSP servers.
 */
public fun interface EmbeddedServerFactory {
    /**
     * Create a new server instance.
     *
     * The server should be created but not initialized.
     * Initialization will be called by the registry.
     *
     * @param config Server configuration
     * @return New server instance
     */
    public fun create(config: EmbeddedServerConfig): EmbeddedLspServer
}

/**
 * Configuration for an embedded server.
 *
 * @property rootUri Root URI of the workspace (e.g., "file:///path/to/project")
 * @property workspaceFolders List of workspace folder URIs
 * @property initializationOptions Additional initialization options
 */
public data class EmbeddedServerConfig(
    val rootUri: String,
    val workspaceFolders: List<String> = listOf(rootUri),
    val initializationOptions: Map<String, Any?> = emptyMap(),
) {
    /**
     * Get the root path from the root URI.
     *
     * @return Root path or null if not a file URI
     */
    public fun rootPath(): String? = if (rootUri.startsWith("file://")) rootUri.removePrefix("file://") else null
}
