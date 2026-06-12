package su.kidoz.jetaprog.lsp.server

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyIncomingCallsParams
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyOutgoingCallsParams
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyPrepareParams
import su.kidoz.jetaprog.lsp.protocol.CodeActionParams
import su.kidoz.jetaprog.lsp.protocol.CompletionParams
import su.kidoz.jetaprog.lsp.protocol.DidChangeTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidCloseTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidSaveTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DocumentFormattingParams
import su.kidoz.jetaprog.lsp.protocol.DocumentHighlightParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.InitializeParams
import su.kidoz.jetaprog.lsp.protocol.InitializeResult
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyIncomingCall
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyItem
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyOutgoingCall
import su.kidoz.jetaprog.lsp.protocol.LspCodeAction
import su.kidoz.jetaprog.lsp.protocol.LspCompletionList
import su.kidoz.jetaprog.lsp.protocol.LspDocumentHighlight
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspHover
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspTextEdit
import su.kidoz.jetaprog.lsp.protocol.LspTypeHierarchyItem
import su.kidoz.jetaprog.lsp.protocol.PublishDiagnosticsParams
import su.kidoz.jetaprog.lsp.protocol.ReferenceParams
import su.kidoz.jetaprog.lsp.protocol.SemanticTokens
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensParams
import su.kidoz.jetaprog.lsp.protocol.ServerCapabilities
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchyPrepareParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySubtypesParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySupertypesParams

/**
 * Interface for embedded (in-process) LSP servers.
 *
 * Unlike external LSP servers that communicate via JSON-RPC over stdio/socket,
 * embedded servers use direct method calls for zero-latency communication
 * while maintaining LSP protocol semantics.
 */
public interface EmbeddedLspServer : Disposable {
    /**
     * Server identifier (e.g., "kotlin-embedded", "java-embedded").
     */
    public val serverId: String

    /**
     * Language ID this server handles (e.g., "kotlin", "java").
     */
    public val languageId: String

    /**
     * Server capabilities after initialization.
     */
    public val capabilities: ServerCapabilities

    /**
     * Whether the server is initialized and ready to handle requests.
     */
    public val isInitialized: Boolean

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Initialize the server with the given parameters.
     *
     * @param params Initialization parameters including root URI and capabilities
     * @return Initialization result with server capabilities
     */
    public suspend fun initialize(params: InitializeParams): InitializeResult

    /**
     * Handle initialized notification.
     * Called after the client has received the initialize response.
     */
    public suspend fun initialized()

    /**
     * Shutdown the server.
     * The server should clean up resources and prepare to exit.
     */
    public suspend fun shutdown()

    // ========================================================================
    // Text Document Sync
    // ========================================================================

    /**
     * Handle document opened notification.
     *
     * @param params Parameters including document URI, language ID, and content
     */
    public suspend fun didOpen(params: DidOpenTextDocumentParams)

    /**
     * Handle document changed notification.
     *
     * @param params Parameters including document URI and content changes
     */
    public suspend fun didChange(params: DidChangeTextDocumentParams)

    /**
     * Handle document saved notification.
     *
     * @param params Parameters including document URI and optional content
     */
    public suspend fun didSave(params: DidSaveTextDocumentParams)

    /**
     * Handle document closed notification.
     *
     * @param params Parameters including document URI
     */
    public suspend fun didClose(params: DidCloseTextDocumentParams)

    // ========================================================================
    // Language Features
    // ========================================================================

    /**
     * Get completions at a position.
     *
     * @param params Completion request parameters
     * @return Completion list or null if not available
     */
    public suspend fun completion(params: CompletionParams): LspCompletionList?

    /**
     * Get hover information at a position.
     *
     * @param params Position parameters
     * @return Hover information or null if not available
     */
    public suspend fun hover(params: TextDocumentPositionParams): LspHover?

    /**
     * Get definition locations for a symbol.
     *
     * @param params Position parameters
     * @return List of definition locations
     */
    public suspend fun definition(params: TextDocumentPositionParams): List<LspLocation>

    /**
     * Get type definition locations for a symbol.
     *
     * @param params Position parameters
     * @return List of type definition locations
     */
    public suspend fun typeDefinition(params: TextDocumentPositionParams): List<LspLocation>

    /**
     * Get implementation locations for an interface/abstract method.
     *
     * @param params Position parameters
     * @return List of implementation locations
     */
    public suspend fun implementation(params: TextDocumentPositionParams): List<LspLocation>

    /**
     * Get reference locations for a symbol.
     *
     * @param params Reference parameters including whether to include declaration
     * @return List of reference locations
     */
    public suspend fun references(params: ReferenceParams): List<LspLocation>

    /**
     * Get document symbols (outline).
     *
     * @param params Document symbol parameters
     * @return List of document symbols (hierarchical)
     */
    public suspend fun documentSymbol(params: DocumentSymbolParams): List<LspDocumentSymbol>

    /**
     * Get semantic tokens for the full document.
     *
     * @param params Semantic tokens parameters
     * @return Semantic tokens or null if not available
     */
    public suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens?

    /**
     * Get code actions for a range.
     *
     * @param params Code action parameters
     * @return List of available code actions
     */
    public suspend fun codeAction(params: CodeActionParams): List<LspCodeAction>

    /**
     * Format a document.
     *
     * @param params Formatting parameters
     * @return List of text edits to apply
     */
    public suspend fun formatting(params: DocumentFormattingParams): List<LspTextEdit>

    /**
     * Get document highlights for a symbol.
     *
     * @param params Document highlight parameters
     * @return List of document highlights
     */
    public suspend fun documentHighlight(params: DocumentHighlightParams): List<LspDocumentHighlight>

    // ========================================================================
    // Call Hierarchy
    // ========================================================================

    /**
     * Prepare call hierarchy at a position.
     *
     * @param params Position parameters
     * @return List of call hierarchy items at the position
     */
    public suspend fun prepareCallHierarchy(params: CallHierarchyPrepareParams): List<LspCallHierarchyItem>

    /**
     * Get incoming calls for a call hierarchy item.
     *
     * @param params Call hierarchy item
     * @return List of incoming calls
     */
    public suspend fun callHierarchyIncomingCalls(
        params: CallHierarchyIncomingCallsParams,
    ): List<LspCallHierarchyIncomingCall>

    /**
     * Get outgoing calls for a call hierarchy item.
     *
     * @param params Call hierarchy item
     * @return List of outgoing calls
     */
    public suspend fun callHierarchyOutgoingCalls(
        params: CallHierarchyOutgoingCallsParams,
    ): List<LspCallHierarchyOutgoingCall>

    // ========================================================================
    // Type Hierarchy
    // ========================================================================

    /**
     * Prepare type hierarchy at a position.
     *
     * @param params Position parameters
     * @return List of type hierarchy items at the position
     */
    public suspend fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): List<LspTypeHierarchyItem>

    /**
     * Get supertypes for a type hierarchy item.
     *
     * @param params Type hierarchy item
     * @return List of supertype items
     */
    public suspend fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): List<LspTypeHierarchyItem>

    /**
     * Get subtypes for a type hierarchy item.
     *
     * @param params Type hierarchy item
     * @return List of subtype items
     */
    public suspend fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): List<LspTypeHierarchyItem>

    // ========================================================================
    // Diagnostics (Server-to-Client)
    // ========================================================================

    /**
     * Set callback for diagnostics published by the server.
     *
     * @param callback Function to call when diagnostics are published
     */
    public fun onDiagnostics(callback: (PublishDiagnosticsParams) -> Unit)
}
