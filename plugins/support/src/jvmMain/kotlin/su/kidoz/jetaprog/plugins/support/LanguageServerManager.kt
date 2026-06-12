package su.kidoz.jetaprog.plugins.support

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.lsp.client.LspClient
import su.kidoz.jetaprog.lsp.client.LspClientConfig
import su.kidoz.jetaprog.lsp.client.transport.StdioTransport
import su.kidoz.jetaprog.lsp.client.transport.TransportConfig

private val logger = KotlinLogging.logger {}

/**
 * JVM implementation of LanguageServerManager.
 */
public actual class LanguageServerManager {
    private val mutex = Mutex()
    private val servers = mutableMapOf<String, LspLanguageServerEntry>()

    private data class LspLanguageServerEntry(
        val server: LspLanguageServer,
        val client: LspClient,
        val transport: StdioTransport,
    )

    /**
     * Start an LSP server with the given configuration.
     */
    public actual suspend fun startServer(
        config: LspServerConfig,
        clientConfig: LspClientConfig,
    ): LspLanguageServer =
        mutex.withLock {
            // Check if already running
            servers[config.name]?.let { entry ->
                logger.info { "Server ${config.name} already running" }
                return@withLock entry.server
            }

            logger.info { "Starting LSP server: ${config.name}" }
            logger.debug { "Command: ${config.command.joinToString(" ")}" }

            // Create transport
            val transportConfig =
                TransportConfig.Stdio(
                    command = config.command,
                    workingDirectory = config.workingDirectory,
                )
            val transport = StdioTransport(transportConfig)

            // Start the transport
            transport.start()

            // Create client
            val client = LspClient(transport, clientConfig)

            // Start the client (initializes the server)
            client.start()

            logger.info { "LSP server ${config.name} initialized" }
            logger.debug { "Server capabilities: ${client.serverCapabilities}" }

            // Create wrapper
            val server = LspLanguageServer(config, client)

            // Store entry
            servers[config.name] = LspLanguageServerEntry(server, client, transport)

            server
        }

    /**
     * Stop and remove a server.
     */
    public actual suspend fun stopServer(name: String): Unit =
        mutex.withLock {
            servers.remove(name)?.let { entry ->
                logger.info { "Stopping LSP server: $name" }
                entry.client.stop()
            }
        }

    /**
     * Get a running server by name.
     */
    public actual fun getServer(name: String): LspLanguageServer? = servers[name]?.server

    /**
     * Get all running servers.
     */
    public actual fun getAllServers(): List<LspLanguageServer> = servers.values.map { it.server }

    /**
     * Stop all servers.
     */
    public actual suspend fun stopAll(): Unit =
        mutex.withLock {
            logger.info { "Stopping all LSP servers (${servers.size})" }
            servers.values.forEach { entry ->
                entry.client.stop()
            }
            servers.clear()
        }
}
