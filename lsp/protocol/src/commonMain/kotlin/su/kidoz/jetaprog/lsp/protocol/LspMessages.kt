package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC message base.
 */
@Serializable
public sealed interface JsonRpcMessage {
    public val jsonrpc: String
        get() = "2.0"
}

/**
 * JSON-RPC Request.
 */
@Serializable
@SerialName("request")
public data class JsonRpcRequest(
    val id: Int,
    val method: String,
    val params: JsonElement? = null,
) : JsonRpcMessage

/**
 * JSON-RPC Response.
 */
@Serializable
@SerialName("response")
public data class JsonRpcResponse(
    val id: Int,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
) : JsonRpcMessage

/**
 * JSON-RPC Notification.
 */
@Serializable
@SerialName("notification")
public data class JsonRpcNotification(
    val method: String,
    val params: JsonElement? = null,
) : JsonRpcMessage

/**
 * JSON-RPC Error.
 */
@Serializable
public data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
) {
    public companion object {
        public const val PARSE_ERROR: Int = -32700
        public const val INVALID_REQUEST: Int = -32600
        public const val METHOD_NOT_FOUND: Int = -32601
        public const val INVALID_PARAMS: Int = -32602
        public const val INTERNAL_ERROR: Int = -32603
        public const val SERVER_NOT_INITIALIZED: Int = -32002
        public const val UNKNOWN_ERROR_CODE: Int = -32001
        public const val REQUEST_CANCELLED: Int = -32800
        public const val CONTENT_MODIFIED: Int = -32801
    }
}

// ============================================================================
// Initialize Request/Response
// ============================================================================

/**
 * Initialize request params.
 */
@Serializable
public data class InitializeParams(
    val processId: Int?,
    val rootUri: String?,
    val capabilities: ClientCapabilities,
    val trace: String? = "off",
    val workspaceFolders: List<WorkspaceFolder>? = null,
    val initializationOptions: JsonElement? = null,
)

/**
 * Workspace folder.
 */
@Serializable
public data class WorkspaceFolder(
    val uri: String,
    val name: String,
)

/**
 * Client capabilities.
 */
@Serializable
public data class ClientCapabilities(
    val textDocument: TextDocumentClientCapabilities? = null,
    val workspace: WorkspaceClientCapabilities? = null,
)

/**
 * Text document client capabilities.
 */
@Serializable
public data class TextDocumentClientCapabilities(
    val synchronization: TextDocumentSyncClientCapabilities? = null,
    val completion: CompletionClientCapabilities? = null,
    val hover: HoverClientCapabilities? = null,
    val definition: DefinitionClientCapabilities? = null,
    val references: ReferenceClientCapabilities? = null,
    val documentSymbol: DocumentSymbolClientCapabilities? = null,
    val codeAction: CodeActionClientCapabilities? = null,
    val formatting: DocumentFormattingClientCapabilities? = null,
    val publishDiagnostics: PublishDiagnosticsClientCapabilities? = null,
    val semanticTokens: SemanticTokensClientCapabilities? = null,
)

@Serializable
public data class TextDocumentSyncClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val willSave: Boolean? = null,
    val willSaveWaitUntil: Boolean? = null,
    val didSave: Boolean? = null,
)

@Serializable
public data class CompletionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val completionItem: CompletionItemCapabilities? = null,
)

@Serializable
public data class CompletionItemCapabilities(
    val snippetSupport: Boolean? = null,
    val commitCharactersSupport: Boolean? = null,
    val documentationFormat: List<String>? = null,
    val deprecatedSupport: Boolean? = null,
    val preselectSupport: Boolean? = null,
)

@Serializable
public data class HoverClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val contentFormat: List<String>? = null,
)

@Serializable
public data class DefinitionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val linkSupport: Boolean? = null,
)

@Serializable
public data class ReferenceClientCapabilities(
    val dynamicRegistration: Boolean? = null,
)

@Serializable
public data class DocumentSymbolClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val hierarchicalDocumentSymbolSupport: Boolean? = null,
)

@Serializable
public data class CodeActionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val codeActionLiteralSupport: CodeActionLiteralSupport? = null,
)

@Serializable
public data class CodeActionLiteralSupport(
    val codeActionKind: CodeActionKindSupport,
)

@Serializable
public data class CodeActionKindSupport(
    val valueSet: List<String>,
)

@Serializable
public data class DocumentFormattingClientCapabilities(
    val dynamicRegistration: Boolean? = null,
)

@Serializable
public data class PublishDiagnosticsClientCapabilities(
    val relatedInformation: Boolean? = null,
    val versionSupport: Boolean? = null,
)

/**
 * Semantic tokens client capabilities.
 */
@Serializable
public data class SemanticTokensClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val requests: SemanticTokensRequests? = null,
    val tokenTypes: List<String>? = null,
    val tokenModifiers: List<String>? = null,
    val formats: List<String>? = null,
    val overlappingTokenSupport: Boolean? = null,
    val multilineTokenSupport: Boolean? = null,
)

/**
 * Semantic tokens request capabilities.
 */
@Serializable
public data class SemanticTokensRequests(
    val range: Boolean? = null,
    val full: SemanticTokensFullRequests? = null,
)

/**
 * Full semantic tokens request capabilities.
 */
@Serializable
public data class SemanticTokensFullRequests(
    val delta: Boolean? = null,
)

@Serializable
public data class WorkspaceClientCapabilities(
    val applyEdit: Boolean? = null,
    val workspaceEdit: WorkspaceEditClientCapabilities? = null,
    val workspaceFolders: Boolean? = null,
)

@Serializable
public data class WorkspaceEditClientCapabilities(
    val documentChanges: Boolean? = null,
)

/**
 * Initialize result.
 */
@Serializable
public data class InitializeResult(
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo? = null,
)

/**
 * Server info.
 */
@Serializable
public data class ServerInfo(
    val name: String,
    val version: String? = null,
)

/**
 * Server capabilities.
 */
@Serializable
public data class ServerCapabilities(
    val textDocumentSync: TextDocumentSyncOptions? = null,
    val completionProvider: CompletionOptions? = null,
    val hoverProvider: Boolean? = null,
    val definitionProvider: Boolean? = null,
    val referencesProvider: Boolean? = null,
    val documentSymbolProvider: Boolean? = null,
    val codeActionProvider: Boolean? = null,
    val documentFormattingProvider: Boolean? = null,
    val documentRangeFormattingProvider: Boolean? = null,
    val renameProvider: Boolean? = null,
    val workspaceSymbolProvider: Boolean? = null,
    val semanticTokensProvider: SemanticTokensOptions? = null,
)

/**
 * Text document sync options.
 */
@Serializable
public data class TextDocumentSyncOptions(
    val openClose: Boolean? = null,
    val change: Int? = null, // 0 = None, 1 = Full, 2 = Incremental
    val save: SaveOptions? = null,
)

@Serializable
public data class SaveOptions(
    val includeText: Boolean? = null,
)

/**
 * Completion options.
 */
@Serializable
public data class CompletionOptions(
    val triggerCharacters: List<String>? = null,
    val resolveProvider: Boolean? = null,
)

// ============================================================================
// Text Document Sync Notifications
// ============================================================================

/**
 * Did open text document params.
 */
@Serializable
public data class DidOpenTextDocumentParams(
    val textDocument: TextDocumentItem,
)

/**
 * Did change text document params.
 */
@Serializable
public data class DidChangeTextDocumentParams(
    val textDocument: VersionedTextDocumentIdentifier,
    val contentChanges: List<TextDocumentContentChangeEvent>,
)

/**
 * Text document content change event.
 */
@Serializable
public data class TextDocumentContentChangeEvent(
    val range: LspRange? = null,
    val rangeLength: Int? = null,
    val text: String,
)

/**
 * Did save text document params.
 */
@Serializable
public data class DidSaveTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
    val text: String? = null,
)

/**
 * Did close text document params.
 */
@Serializable
public data class DidCloseTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
)

// ============================================================================
// Diagnostics
// ============================================================================

/**
 * Publish diagnostics params.
 */
@Serializable
public data class PublishDiagnosticsParams(
    val uri: String,
    val version: Int? = null,
    val diagnostics: List<LspDiagnostic>,
)

// ============================================================================
// Completion
// ============================================================================

/**
 * Completion params.
 */
@Serializable
public data class CompletionParams(
    val textDocument: TextDocumentIdentifier,
    val position: LspPosition,
    val context: CompletionContext? = null,
)

/**
 * Completion context.
 */
@Serializable
public data class CompletionContext(
    val triggerKind: Int, // 1 = Invoked, 2 = TriggerCharacter, 3 = TriggerForIncompleteCompletions
    val triggerCharacter: String? = null,
)

// ============================================================================
// Signature Help
// ============================================================================

/**
 * Signature help params.
 */
@Serializable
public data class SignatureHelpParams(
    val textDocument: TextDocumentIdentifier,
    val position: LspPosition,
    val context: SignatureHelpContext? = null,
)

/**
 * Signature help context.
 */
@Serializable
public data class SignatureHelpContext(
    /**
     * Trigger kind: 1 = Invoked, 2 = TriggerCharacter, 3 = ContentChange.
     */
    val triggerKind: Int,
    /**
     * The character that triggered signature help.
     */
    val triggerCharacter: String? = null,
    /**
     * Whether this is a retrigger.
     */
    val isRetrigger: Boolean = false,
    /**
     * The currently active signature help (for retriggers).
     */
    val activeSignatureHelp: LspSignatureHelp? = null,
)

/**
 * Signature help trigger kind constants.
 */
public object SignatureHelpTriggerKind {
    public const val INVOKED: Int = 1
    public const val TRIGGER_CHARACTER: Int = 2
    public const val CONTENT_CHANGE: Int = 3
}

// ============================================================================
// References
// ============================================================================

/**
 * Reference params.
 */
@Serializable
public data class ReferenceParams(
    val textDocument: TextDocumentIdentifier,
    val position: LspPosition,
    val context: ReferenceContext,
)

/**
 * Reference context.
 */
@Serializable
public data class ReferenceContext(
    val includeDeclaration: Boolean,
)

// ============================================================================
// Code Actions
// ============================================================================

/**
 * Code action params.
 */
@Serializable
public data class CodeActionParams(
    val textDocument: TextDocumentIdentifier,
    val range: LspRange,
    val context: CodeActionContext,
)

/**
 * Code action context.
 */
@Serializable
public data class CodeActionContext(
    val diagnostics: List<LspDiagnostic>,
    val only: List<String>? = null,
)

// ============================================================================
// Formatting
// ============================================================================

/**
 * Document formatting params.
 */
@Serializable
public data class DocumentFormattingParams(
    val textDocument: TextDocumentIdentifier,
    val options: LspFormattingOptions,
)

/**
 * Formatting options.
 */
@Serializable
public data class LspFormattingOptions(
    val tabSize: Int,
    val insertSpaces: Boolean,
    val trimTrailingWhitespace: Boolean? = null,
    val insertFinalNewline: Boolean? = null,
    val trimFinalNewlines: Boolean? = null,
)

// ============================================================================
// Document Symbols
// ============================================================================

/**
 * Document symbol params.
 */
@Serializable
public data class DocumentSymbolParams(
    val textDocument: TextDocumentIdentifier,
)

// ============================================================================
// Document Highlight
// ============================================================================

/**
 * Document highlight params (same as TextDocumentPositionParams).
 */
public typealias DocumentHighlightParams = TextDocumentPositionParams

// ============================================================================
// Call Hierarchy
// ============================================================================

/**
 * Call hierarchy prepare params.
 */
public typealias CallHierarchyPrepareParams = TextDocumentPositionParams

/**
 * Call hierarchy incoming calls params.
 */
@Serializable
public data class CallHierarchyIncomingCallsParams(
    val item: LspCallHierarchyItem,
)

/**
 * Call hierarchy outgoing calls params.
 */
@Serializable
public data class CallHierarchyOutgoingCallsParams(
    val item: LspCallHierarchyItem,
)

// ============================================================================
// Type Hierarchy
// ============================================================================

/**
 * Type hierarchy prepare params.
 */
public typealias TypeHierarchyPrepareParams = TextDocumentPositionParams

/**
 * Type hierarchy supertypes params.
 */
@Serializable
public data class TypeHierarchySupertypesParams(
    val item: LspTypeHierarchyItem,
)

/**
 * Type hierarchy subtypes params.
 */
@Serializable
public data class TypeHierarchySubtypesParams(
    val item: LspTypeHierarchyItem,
)

// ============================================================================
// LSP Method Names
// ============================================================================

/**
 * LSP method names.
 */
public object LspMethod {
    // Lifecycle
    public const val INITIALIZE: String = "initialize"
    public const val INITIALIZED: String = "initialized"
    public const val SHUTDOWN: String = "shutdown"
    public const val EXIT: String = "exit"

    // Text Document Sync
    public const val DID_OPEN: String = "textDocument/didOpen"
    public const val DID_CHANGE: String = "textDocument/didChange"
    public const val DID_SAVE: String = "textDocument/didSave"
    public const val DID_CLOSE: String = "textDocument/didClose"

    // Diagnostics
    public const val PUBLISH_DIAGNOSTICS: String = "textDocument/publishDiagnostics"

    // Language Features
    public const val COMPLETION: String = "textDocument/completion"
    public const val COMPLETION_RESOLVE: String = "completionItem/resolve"
    public const val HOVER: String = "textDocument/hover"
    public const val SIGNATURE_HELP: String = "textDocument/signatureHelp"
    public const val DEFINITION: String = "textDocument/definition"
    public const val TYPE_DEFINITION: String = "textDocument/typeDefinition"
    public const val IMPLEMENTATION: String = "textDocument/implementation"
    public const val REFERENCES: String = "textDocument/references"
    public const val DOCUMENT_SYMBOL: String = "textDocument/documentSymbol"
    public const val DOCUMENT_HIGHLIGHT: String = "textDocument/documentHighlight"
    public const val CODE_ACTION: String = "textDocument/codeAction"
    public const val FORMATTING: String = "textDocument/formatting"
    public const val RANGE_FORMATTING: String = "textDocument/rangeFormatting"
    public const val RENAME: String = "textDocument/rename"

    // Semantic Tokens
    public const val SEMANTIC_TOKENS_FULL: String = "textDocument/semanticTokens/full"
    public const val SEMANTIC_TOKENS_RANGE: String = "textDocument/semanticTokens/range"
    public const val SEMANTIC_TOKENS_DELTA: String = "textDocument/semanticTokens/full/delta"

    // Call Hierarchy
    public const val CALL_HIERARCHY_PREPARE: String = "textDocument/prepareCallHierarchy"
    public const val CALL_HIERARCHY_INCOMING: String = "callHierarchy/incomingCalls"
    public const val CALL_HIERARCHY_OUTGOING: String = "callHierarchy/outgoingCalls"

    // Type Hierarchy
    public const val TYPE_HIERARCHY_PREPARE: String = "textDocument/prepareTypeHierarchy"
    public const val TYPE_HIERARCHY_SUPERTYPES: String = "typeHierarchy/supertypes"
    public const val TYPE_HIERARCHY_SUBTYPES: String = "typeHierarchy/subtypes"

    // Workspace
    public const val WORKSPACE_SYMBOL: String = "workspace/symbol"
    public const val APPLY_EDIT: String = "workspace/applyEdit"
}
