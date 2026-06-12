package su.kidoz.jetaprog.lsp.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import su.kidoz.jetaprog.lsp.client.transport.LspTransport
import su.kidoz.jetaprog.lsp.protocol.ApplyWorkspaceEditParams
import su.kidoz.jetaprog.lsp.protocol.ApplyWorkspaceEditResult
import su.kidoz.jetaprog.lsp.protocol.ClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.CodeActionClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.CodeActionKindSupport
import su.kidoz.jetaprog.lsp.protocol.CodeActionLiteralSupport
import su.kidoz.jetaprog.lsp.protocol.CodeActionParams
import su.kidoz.jetaprog.lsp.protocol.CompletionClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.CompletionItemCapabilities
import su.kidoz.jetaprog.lsp.protocol.CompletionParams
import su.kidoz.jetaprog.lsp.protocol.DefinitionClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.DidChangeTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidCloseTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidSaveTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DocumentFormattingClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.DocumentFormattingParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.HoverClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.InitializeParams
import su.kidoz.jetaprog.lsp.protocol.InitializeResult
import su.kidoz.jetaprog.lsp.protocol.JsonRpcMessage
import su.kidoz.jetaprog.lsp.protocol.JsonRpcNotification
import su.kidoz.jetaprog.lsp.protocol.JsonRpcRequest
import su.kidoz.jetaprog.lsp.protocol.JsonRpcResponse
import su.kidoz.jetaprog.lsp.protocol.LspCodeAction
import su.kidoz.jetaprog.lsp.protocol.LspCodeActionKind
import su.kidoz.jetaprog.lsp.protocol.LspCompletionList
import su.kidoz.jetaprog.lsp.protocol.LspDocumentHighlight
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspHover
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspMethod
import su.kidoz.jetaprog.lsp.protocol.LspSignatureHelp
import su.kidoz.jetaprog.lsp.protocol.LspTextEdit
import su.kidoz.jetaprog.lsp.protocol.LspWorkspaceEdit
import su.kidoz.jetaprog.lsp.protocol.PublishDiagnosticsClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.PublishDiagnosticsParams
import su.kidoz.jetaprog.lsp.protocol.ReferenceClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.ReferenceParams
import su.kidoz.jetaprog.lsp.protocol.SemanticTokenModifiers
import su.kidoz.jetaprog.lsp.protocol.SemanticTokenTypes
import su.kidoz.jetaprog.lsp.protocol.SemanticTokens
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensFullRequests
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensParams
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensRangeParams
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensRequests
import su.kidoz.jetaprog.lsp.protocol.ServerCapabilities
import su.kidoz.jetaprog.lsp.protocol.SignatureHelpParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentSyncClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.WorkspaceClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.WorkspaceEditClientCapabilities
import su.kidoz.jetaprog.lsp.protocol.WorkspaceFolder
import kotlin.coroutines.CoroutineContext

/**
 * LSP client configuration.
 */
public data class LspClientConfig(
    val serverName: String,
    val rootUri: String,
    val workspaceFolders: List<WorkspaceFolder> = emptyList(),
    val initializationOptions: JsonElement? = null,
)

/**
 * Callback for diagnostics published by the language server.
 */
public typealias DiagnosticsCallback = (PublishDiagnosticsParams) -> Unit

/**
 * Callback for workspace edit requests from the language server.
 * Returns true if the edit was applied successfully, false otherwise.
 */
public typealias WorkspaceEditCallback = suspend (label: String?, edit: LspWorkspaceEdit) -> Boolean

/**
 * LSP client implementation.
 */
public class LspClient(
    private val transport: LspTransport,
    private val config: LspClientConfig,
    context: CoroutineContext = Dispatchers.Default,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    private val scope = CoroutineScope(context + SupervisorJob())
    private val mutex = Mutex()

    private var requestId = 0
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()

    private var _serverCapabilities: ServerCapabilities? = null
    public val serverCapabilities: ServerCapabilities?
        get() = _serverCapabilities

    private var _isInitialized: Boolean = false
    public val isInitialized: Boolean
        get() = _isInitialized

    private var diagnosticsCallback: DiagnosticsCallback? = null
    private var workspaceEditCallback: WorkspaceEditCallback? = null

    /**
     * Set callback for diagnostics notifications.
     */
    public fun onDiagnostics(callback: DiagnosticsCallback) {
        diagnosticsCallback = callback
    }

    /**
     * Set callback for workspace edit requests.
     *
     * The language server may request the client to apply edits to documents
     * (e.g., for code actions that require modifying multiple files).
     */
    public fun onWorkspaceEdit(callback: WorkspaceEditCallback) {
        workspaceEditCallback = callback
    }

    /**
     * Start the client and initialize the server.
     */
    public suspend fun start() {
        // Start listening to messages
        transport.incoming
            .onEach { handleMessage(it) }
            .launchIn(scope)

        // Send initialize request
        val result = initialize()
        _serverCapabilities = result.capabilities
        _isInitialized = true

        // Send initialized notification
        sendNotification<Unit>(LspMethod.INITIALIZED, null, null)
    }

    private suspend fun initialize(): InitializeResult {
        val params =
            InitializeParams(
                processId = null, // Let the server determine
                rootUri = config.rootUri,
                capabilities = createClientCapabilities(),
                workspaceFolders = config.workspaceFolders.ifEmpty { null },
                initializationOptions = config.initializationOptions,
            )

        val result =
            sendRequest(
                LspMethod.INITIALIZE,
                params,
                InitializeParams.serializer(),
                InitializeResult.serializer(),
            )
        return result ?: throw IllegalStateException("Initialize request returned null")
    }

    private fun createClientCapabilities(): ClientCapabilities =
        ClientCapabilities(
            textDocument =
                TextDocumentClientCapabilities(
                    synchronization =
                        TextDocumentSyncClientCapabilities(
                            didSave = true,
                        ),
                    completion =
                        CompletionClientCapabilities(
                            completionItem =
                                CompletionItemCapabilities(
                                    snippetSupport = true,
                                    documentationFormat = listOf("markdown", "plaintext"),
                                    deprecatedSupport = true,
                                    preselectSupport = true,
                                ),
                        ),
                    hover =
                        HoverClientCapabilities(
                            contentFormat = listOf("markdown", "plaintext"),
                        ),
                    definition =
                        DefinitionClientCapabilities(
                            linkSupport = true,
                        ),
                    references = ReferenceClientCapabilities(),
                    documentSymbol =
                        DocumentSymbolClientCapabilities(
                            hierarchicalDocumentSymbolSupport = true,
                        ),
                    codeAction =
                        CodeActionClientCapabilities(
                            codeActionLiteralSupport =
                                CodeActionLiteralSupport(
                                    codeActionKind =
                                        CodeActionKindSupport(
                                            valueSet =
                                                listOf(
                                                    LspCodeActionKind.QUICK_FIX,
                                                    LspCodeActionKind.REFACTOR,
                                                    LspCodeActionKind.SOURCE,
                                                ),
                                        ),
                                ),
                        ),
                    formatting = DocumentFormattingClientCapabilities(),
                    publishDiagnostics =
                        PublishDiagnosticsClientCapabilities(
                            relatedInformation = true,
                            versionSupport = true,
                        ),
                    semanticTokens =
                        SemanticTokensClientCapabilities(
                            requests =
                                SemanticTokensRequests(
                                    range = true,
                                    full = SemanticTokensFullRequests(delta = false),
                                ),
                            tokenTypes = SemanticTokenTypes.ALL,
                            tokenModifiers = SemanticTokenModifiers.ALL,
                            formats = listOf("relative"),
                            overlappingTokenSupport = false,
                            multilineTokenSupport = true,
                        ),
                ),
            workspace =
                WorkspaceClientCapabilities(
                    applyEdit = true,
                    workspaceEdit =
                        WorkspaceEditClientCapabilities(
                            documentChanges = true,
                        ),
                    workspaceFolders = true,
                ),
        )

    private fun handleMessage(message: JsonRpcMessage) {
        when (message) {
            is JsonRpcResponse -> handleResponse(message)
            is JsonRpcRequest -> handleRequest(message)
            is JsonRpcNotification -> handleNotification(message)
        }
    }

    private fun handleResponse(response: JsonRpcResponse) {
        val deferred = pendingRequests.remove(response.id)
        if (deferred != null) {
            val error = response.error
            if (error != null) {
                deferred.completeExceptionally(
                    LspException(error.code, error.message),
                )
            } else {
                deferred.complete(response.result)
            }
        }
    }

    private fun handleRequest(request: JsonRpcRequest) {
        // Handle server-to-client requests
        scope.launch {
            when (request.method) {
                "workspace/applyEdit" -> {
                    handleWorkspaceApplyEdit(request)
                }

                else -> {
                    // Unknown request, send error
                    val response =
                        JsonRpcResponse(
                            request.id,
                            null,
                            su.kidoz.jetaprog.lsp.protocol.JsonRpcError(
                                su.kidoz.jetaprog.lsp.protocol.JsonRpcError.METHOD_NOT_FOUND,
                                "Method not found: ${request.method}",
                            ),
                        )
                    transport.send(response)
                }
            }
        }
    }

    private suspend fun handleWorkspaceApplyEdit(request: JsonRpcRequest) {
        val callback = workspaceEditCallback
        val params = request.params

        if (params == null) {
            val response =
                JsonRpcResponse(
                    request.id,
                    json.encodeToJsonElement(
                        ApplyWorkspaceEditResult.serializer(),
                        ApplyWorkspaceEditResult(applied = false, failureReason = "Missing params"),
                    ),
                    null,
                )
            transport.send(response)
            return
        }

        try {
            val applyParams = json.decodeFromJsonElement(ApplyWorkspaceEditParams.serializer(), params)

            val applied =
                if (callback != null) {
                    callback(applyParams.label, applyParams.edit)
                } else {
                    // No callback registered, reject the edit
                    false
                }

            val result = ApplyWorkspaceEditResult(applied = applied)
            val response =
                JsonRpcResponse(
                    request.id,
                    json.encodeToJsonElement(ApplyWorkspaceEditResult.serializer(), result),
                    null,
                )
            transport.send(response)
        } catch (e: Exception) {
            val result = ApplyWorkspaceEditResult(applied = false, failureReason = e.message)
            val response =
                JsonRpcResponse(
                    request.id,
                    json.encodeToJsonElement(ApplyWorkspaceEditResult.serializer(), result),
                    null,
                )
            transport.send(response)
        }
    }

    private fun handleNotification(notification: JsonRpcNotification) {
        when (notification.method) {
            LspMethod.PUBLISH_DIAGNOSTICS -> {
                notification.params?.let { params ->
                    val diagnostics =
                        json.decodeFromJsonElement(
                            PublishDiagnosticsParams.serializer(),
                            params,
                        )
                    diagnosticsCallback?.invoke(diagnostics)
                }
            }
        }
    }

    private suspend fun <P, R> sendRequest(
        method: String,
        params: P?,
        paramsSerializer: kotlinx.serialization.KSerializer<P>?,
        resultSerializer: kotlinx.serialization.KSerializer<R>,
    ): R? {
        val id = mutex.withLock { ++requestId }
        val deferred = CompletableDeferred<JsonElement?>()

        mutex.withLock {
            pendingRequests[id] = deferred
        }

        val paramsJson =
            if (params != null && paramsSerializer != null) {
                json.encodeToJsonElement(paramsSerializer, params)
            } else {
                null
            }
        val request = JsonRpcRequest(id, method, paramsJson)

        transport.send(request)

        val result = deferred.await()
        return result?.let { json.decodeFromJsonElement(resultSerializer, it) }
    }

    private suspend fun <P> sendNotification(
        method: String,
        params: P?,
        paramsSerializer: kotlinx.serialization.KSerializer<P>?,
    ) {
        val paramsJson =
            if (params != null && paramsSerializer != null) {
                json.encodeToJsonElement(paramsSerializer, params)
            } else {
                null
            }
        val notification = JsonRpcNotification(method, paramsJson)
        transport.send(notification)
    }

    // ========================================================================
    // Text Document Sync
    // ========================================================================

    /**
     * Notify the server that a document was opened.
     */
    public suspend fun didOpen(params: DidOpenTextDocumentParams) {
        sendNotification(LspMethod.DID_OPEN, params, DidOpenTextDocumentParams.serializer())
    }

    /**
     * Notify the server that a document was changed.
     */
    public suspend fun didChange(params: DidChangeTextDocumentParams) {
        sendNotification(LspMethod.DID_CHANGE, params, DidChangeTextDocumentParams.serializer())
    }

    /**
     * Notify the server that a document was saved.
     */
    public suspend fun didSave(params: DidSaveTextDocumentParams) {
        sendNotification(LspMethod.DID_SAVE, params, DidSaveTextDocumentParams.serializer())
    }

    /**
     * Notify the server that a document was closed.
     */
    public suspend fun didClose(params: DidCloseTextDocumentParams) {
        sendNotification(LspMethod.DID_CLOSE, params, DidCloseTextDocumentParams.serializer())
    }

    // ========================================================================
    // Language Features
    // ========================================================================

    /**
     * Request completions at a position.
     */
    public suspend fun completion(params: CompletionParams): LspCompletionList? =
        sendRequest(
            LspMethod.COMPLETION,
            params,
            CompletionParams.serializer(),
            LspCompletionList.serializer(),
        )

    /**
     * Request hover information at a position.
     */
    public suspend fun hover(params: TextDocumentPositionParams): LspHover? =
        sendRequest(
            LspMethod.HOVER,
            params,
            TextDocumentPositionParams.serializer(),
            LspHover.serializer(),
        )

    /**
     * Request signature help at a position.
     */
    public suspend fun signatureHelp(params: SignatureHelpParams): LspSignatureHelp? =
        sendRequest(
            LspMethod.SIGNATURE_HELP,
            params,
            SignatureHelpParams.serializer(),
            LspSignatureHelp.serializer(),
        )

    /**
     * Request definition locations for a symbol.
     */
    public suspend fun definition(params: TextDocumentPositionParams): List<LspLocation>? =
        sendRequest(
            LspMethod.DEFINITION,
            params,
            TextDocumentPositionParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspLocation.serializer()),
        )

    /**
     * Request type definition locations for a symbol.
     */
    public suspend fun typeDefinition(params: TextDocumentPositionParams): List<LspLocation>? =
        sendRequest(
            LspMethod.TYPE_DEFINITION,
            params,
            TextDocumentPositionParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspLocation.serializer()),
        )

    /**
     * Request implementation locations for an interface/abstract method.
     */
    public suspend fun implementation(params: TextDocumentPositionParams): List<LspLocation>? =
        sendRequest(
            LspMethod.IMPLEMENTATION,
            params,
            TextDocumentPositionParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspLocation.serializer()),
        )

    /**
     * Request reference locations for a symbol.
     */
    public suspend fun references(params: ReferenceParams): List<LspLocation>? =
        sendRequest(
            LspMethod.REFERENCES,
            params,
            ReferenceParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspLocation.serializer()),
        )

    /**
     * Request document symbols.
     */
    public suspend fun documentSymbols(params: DocumentSymbolParams): List<LspDocumentSymbol>? =
        sendRequest(
            LspMethod.DOCUMENT_SYMBOL,
            params,
            DocumentSymbolParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspDocumentSymbol.serializer()),
        )

    /**
     * Request document highlights for a symbol (same symbol occurrences in file).
     */
    public suspend fun documentHighlight(params: TextDocumentPositionParams): List<LspDocumentHighlight>? =
        sendRequest(
            LspMethod.DOCUMENT_HIGHLIGHT,
            params,
            TextDocumentPositionParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspDocumentHighlight.serializer()),
        )

    /**
     * Request code actions.
     */
    public suspend fun codeActions(params: CodeActionParams): List<LspCodeAction>? =
        sendRequest(
            LspMethod.CODE_ACTION,
            params,
            CodeActionParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspCodeAction.serializer()),
        )

    /**
     * Request document formatting.
     */
    public suspend fun formatting(params: DocumentFormattingParams): List<LspTextEdit>? =
        sendRequest(
            LspMethod.FORMATTING,
            params,
            DocumentFormattingParams.serializer(),
            kotlinx.serialization.builtins.ListSerializer(LspTextEdit.serializer()),
        )

    // ========================================================================
    // Semantic Tokens
    // ========================================================================

    /**
     * Request full semantic tokens for a document.
     *
     * Returns semantic tokens for the entire document, which can be used
     * to provide more accurate syntax highlighting based on semantic analysis.
     */
    public suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens? =
        sendRequest(
            LspMethod.SEMANTIC_TOKENS_FULL,
            params,
            SemanticTokensParams.serializer(),
            SemanticTokens.serializer(),
        )

    /**
     * Request semantic tokens for a range within a document.
     *
     * More efficient than full tokens when only a portion of the document
     * is visible or needs updating.
     */
    public suspend fun semanticTokensRange(params: SemanticTokensRangeParams): SemanticTokens? =
        sendRequest(
            LspMethod.SEMANTIC_TOKENS_RANGE,
            params,
            SemanticTokensRangeParams.serializer(),
            SemanticTokens.serializer(),
        )

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Shutdown the server.
     */
    public suspend fun shutdown() {
        sendRequest<Unit, JsonElement>(
            LspMethod.SHUTDOWN,
            null,
            null,
            JsonElement.serializer(),
        )
    }

    /**
     * Exit the server.
     */
    public suspend fun exit() {
        sendNotification<Unit>(LspMethod.EXIT, null, null)
    }

    /**
     * Stop the client.
     */
    public suspend fun stop() {
        if (_isInitialized) {
            shutdown()
            exit()
        }
        transport.close()
        scope.cancel()
    }
}

/**
 * LSP exception.
 */
public class LspException(
    public val code: Int,
    override val message: String,
) : Exception(message)
