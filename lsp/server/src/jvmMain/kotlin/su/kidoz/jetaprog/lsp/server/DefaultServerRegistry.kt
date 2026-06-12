package su.kidoz.jetaprog.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.lsp.protocol.ClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.InitializeParams

private val logger = KotlinLogging.logger {}

/**
 * Default thread-safe implementation of EmbeddedServerRegistry.
 *
 * Manages server factories and active server instances with lazy initialization.
 */
public class DefaultServerRegistry(
    private val config: EmbeddedServerConfig,
) : EmbeddedServerRegistry {
    private val mutex = Mutex()
    private val factories = mutableMapOf<String, EmbeddedServerFactory>()
    private val servers = mutableMapOf<String, EmbeddedLspServer>()

    override fun registerServerFactory(
        languageId: String,
        factory: EmbeddedServerFactory,
    ): Disposable {
        factories[languageId] = factory
        logger.info { "Registered server factory for language: $languageId" }
        return Disposable {
            factories.remove(languageId)
            logger.info { "Unregistered server factory for language: $languageId" }
        }
    }

    override suspend fun getServer(languageId: String): EmbeddedLspServer? =
        mutex.withLock {
            // Return existing server if available
            servers[languageId]?.let { return it }

            // Create and initialize new server if factory exists
            val factory = factories[languageId] ?: return null

            logger.info { "Creating embedded server for language: $languageId" }

            val server = factory.create(config)

            // Initialize the server
            val initParams =
                InitializeParams(
                    processId = ProcessHandle.current().pid().toInt(),
                    rootUri = config.rootUri,
                    capabilities = ClientCapabilities(),
                    workspaceFolders =
                        config.workspaceFolders.map { uri ->
                            su.kidoz.jetaprog.lsp.protocol.WorkspaceFolder(
                                uri = uri,
                                name = uri.substringAfterLast('/'),
                            )
                        },
                )

            try {
                val result = server.initialize(initParams)
                server.initialized()
                servers[languageId] = server
                logger.info { "Initialized embedded server for $languageId: ${result.serverInfo?.name}" }
                server
            } catch (e: Exception) {
                logger.error(e) { "Failed to initialize embedded server for $languageId" }
                null
            }
        }

    override fun getAllServers(): List<EmbeddedLspServer> = servers.values.toList()

    override fun getRegisteredLanguages(): Set<String> = factories.keys.toSet()

    override fun hasFactory(languageId: String): Boolean = languageId in factories

    override fun hasActiveServer(languageId: String): Boolean = languageId in servers

    override suspend fun shutdownServer(languageId: String): Unit =
        mutex.withLock {
            servers.remove(languageId)?.let { server ->
                try {
                    server.shutdown()
                    server.dispose()
                    logger.info { "Shut down embedded server for language: $languageId" }
                } catch (e: Exception) {
                    logger.error(e) { "Error shutting down server for $languageId" }
                }
            }
        }

    override suspend fun shutdownAll(): Unit =
        mutex.withLock {
            val languageIds = servers.keys.toList()
            for (languageId in languageIds) {
                servers.remove(languageId)?.let { server ->
                    try {
                        server.shutdown()
                        server.dispose()
                        logger.info { "Shut down embedded server for language: $languageId" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error shutting down server for $languageId" }
                    }
                }
            }
        }

    override fun dispose() {
        // Synchronous cleanup - servers should already be shut down
        servers.values.forEach { it.dispose() }
        servers.clear()
        factories.clear()
    }
}
