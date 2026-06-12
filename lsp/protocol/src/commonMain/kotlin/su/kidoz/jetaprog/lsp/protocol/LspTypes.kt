package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * LSP Position - zero-based line and character.
 */
@Serializable
public data class LspPosition(
    val line: Int,
    val character: Int,
)

/**
 * LSP Range - start and end positions.
 */
@Serializable
public data class LspRange(
    val start: LspPosition,
    val end: LspPosition,
)

/**
 * LSP Location - URI and range.
 */
@Serializable
public data class LspLocation(
    val uri: String,
    val range: LspRange,
)

/**
 * LSP Text Document Identifier.
 */
@Serializable
public data class TextDocumentIdentifier(
    val uri: String,
)

/**
 * LSP Versioned Text Document Identifier.
 */
@Serializable
public data class VersionedTextDocumentIdentifier(
    val uri: String,
    val version: Int,
)

/**
 * LSP Text Document Item - full document content.
 */
@Serializable
public data class TextDocumentItem(
    val uri: String,
    val languageId: String,
    val version: Int,
    val text: String,
)

/**
 * LSP Text Document Position Params.
 */
@Serializable
public data class TextDocumentPositionParams(
    val textDocument: TextDocumentIdentifier,
    val position: LspPosition,
)

/**
 * LSP Text Edit.
 */
@Serializable
public data class LspTextEdit(
    val range: LspRange,
    val newText: String,
)

/**
 * LSP Markup Content.
 */
@Serializable
public data class MarkupContent(
    val kind: String, // "plaintext" or "markdown"
    val value: String,
)

/**
 * LSP Diagnostic Severity.
 */
@Serializable
public enum class LspDiagnosticSeverity {
    @SerialName("1")
    Error,

    @SerialName("2")
    Warning,

    @SerialName("3")
    Information,

    @SerialName("4")
    Hint,
}

/**
 * LSP Diagnostic.
 */
@Serializable
public data class LspDiagnostic(
    val range: LspRange,
    val message: String,
    val severity: LspDiagnosticSeverity? = null,
    val code: String? = null,
    val source: String? = null,
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,
)

/**
 * LSP Diagnostic Related Information.
 */
@Serializable
public data class DiagnosticRelatedInformation(
    val location: LspLocation,
    val message: String,
)

/**
 * LSP Completion Item Kind.
 */
@Serializable
public enum class LspCompletionItemKind {
    @SerialName("1")
    Text,

    @SerialName("2")
    Method,

    @SerialName("3")
    Function,

    @SerialName("4")
    Constructor,

    @SerialName("5")
    Field,

    @SerialName("6")
    Variable,

    @SerialName("7")
    Class,

    @SerialName("8")
    Interface,

    @SerialName("9")
    Module,

    @SerialName("10")
    Property,

    @SerialName("11")
    Unit,

    @SerialName("12")
    Value,

    @SerialName("13")
    Enum,

    @SerialName("14")
    Keyword,

    @SerialName("15")
    Snippet,

    @SerialName("16")
    Color,

    @SerialName("17")
    File,

    @SerialName("18")
    Reference,

    @SerialName("19")
    Folder,

    @SerialName("20")
    EnumMember,

    @SerialName("21")
    Constant,

    @SerialName("22")
    Struct,

    @SerialName("23")
    Event,

    @SerialName("24")
    Operator,

    @SerialName("25")
    TypeParameter,
}

/**
 * LSP Completion Item.
 */
@Serializable
public data class LspCompletionItem(
    val label: String,
    val kind: LspCompletionItemKind? = null,
    val detail: String? = null,
    val documentation: MarkupContent? = null,
    val deprecated: Boolean? = null,
    val preselect: Boolean? = null,
    val sortText: String? = null,
    val filterText: String? = null,
    val insertText: String? = null,
    val insertTextFormat: Int? = null, // 1 = PlainText, 2 = Snippet
    val textEdit: LspTextEdit? = null,
    val additionalTextEdits: List<LspTextEdit>? = null,
    val command: LspCommand? = null,
    val data: JsonElement? = null,
)

/**
 * LSP Completion List.
 */
@Serializable
public data class LspCompletionList(
    val isIncomplete: Boolean,
    val items: List<LspCompletionItem>,
)

/**
 * LSP Hover.
 */
@Serializable
public data class LspHover(
    val contents: MarkupContent,
    val range: LspRange? = null,
)

// ============================================================================
// Signature Help
// ============================================================================

/**
 * LSP Signature Help.
 */
@Serializable
public data class LspSignatureHelp(
    /**
     * One or more signatures.
     */
    val signatures: List<LspSignatureInformation>,
    /**
     * The active signature.
     */
    val activeSignature: Int? = null,
    /**
     * The active parameter of the active signature.
     */
    val activeParameter: Int? = null,
)

/**
 * LSP Signature Information.
 */
@Serializable
public data class LspSignatureInformation(
    /**
     * The label of this signature (e.g., function name with parameters).
     */
    val label: String,
    /**
     * Documentation for this signature.
     */
    val documentation: MarkupContent? = null,
    /**
     * The parameters of this signature.
     */
    val parameters: List<LspParameterInformation>? = null,
    /**
     * The index of the active parameter (overrides SignatureHelp.activeParameter).
     */
    val activeParameter: Int? = null,
)

/**
 * LSP Parameter Information.
 */
@Serializable
public data class LspParameterInformation(
    /**
     * The label of this parameter (name or offset tuple as string).
     */
    val label: String,
    /**
     * Documentation for this parameter.
     */
    val documentation: MarkupContent? = null,
)

/**
 * LSP Command.
 */
@Serializable
public data class LspCommand(
    val title: String,
    val command: String,
    val arguments: List<JsonElement>? = null,
)

/**
 * LSP Symbol Kind.
 */
@Serializable
public enum class LspSymbolKind {
    @SerialName("1")
    File,

    @SerialName("2")
    Module,

    @SerialName("3")
    Namespace,

    @SerialName("4")
    Package,

    @SerialName("5")
    Class,

    @SerialName("6")
    Method,

    @SerialName("7")
    Property,

    @SerialName("8")
    Field,

    @SerialName("9")
    Constructor,

    @SerialName("10")
    Enum,

    @SerialName("11")
    Interface,

    @SerialName("12")
    Function,

    @SerialName("13")
    Variable,

    @SerialName("14")
    Constant,

    @SerialName("15")
    String,

    @SerialName("16")
    Number,

    @SerialName("17")
    Boolean,

    @SerialName("18")
    Array,

    @SerialName("19")
    Object,

    @SerialName("20")
    Key,

    @SerialName("21")
    Null,

    @SerialName("22")
    EnumMember,

    @SerialName("23")
    Struct,

    @SerialName("24")
    Event,

    @SerialName("25")
    Operator,

    @SerialName("26")
    TypeParameter,
}

/**
 * LSP Document Symbol.
 */
@Serializable
public data class LspDocumentSymbol(
    val name: String,
    val detail: String? = null,
    val kind: LspSymbolKind,
    val deprecated: Boolean? = null,
    val range: LspRange,
    val selectionRange: LspRange,
    val children: List<LspDocumentSymbol>? = null,
)

/**
 * LSP Symbol Information (flat representation).
 */
@Serializable
public data class LspSymbolInformation(
    val name: String,
    val kind: LspSymbolKind,
    val deprecated: Boolean? = null,
    val location: LspLocation,
    val containerName: String? = null,
)

/**
 * LSP Code Action Kind.
 */
public object LspCodeActionKind {
    public const val QUICK_FIX: String = "quickfix"
    public const val REFACTOR: String = "refactor"
    public const val REFACTOR_EXTRACT: String = "refactor.extract"
    public const val REFACTOR_INLINE: String = "refactor.inline"
    public const val REFACTOR_REWRITE: String = "refactor.rewrite"
    public const val SOURCE: String = "source"
    public const val SOURCE_ORGANIZE_IMPORTS: String = "source.organizeImports"
}

/**
 * LSP Code Action.
 */
@Serializable
public data class LspCodeAction(
    val title: String,
    val kind: String? = null,
    val diagnostics: List<LspDiagnostic>? = null,
    val isPreferred: Boolean? = null,
    val edit: LspWorkspaceEdit? = null,
    val command: LspCommand? = null,
    val data: JsonElement? = null,
)

/**
 * LSP Workspace Edit.
 */
@Serializable
public data class LspWorkspaceEdit(
    val changes: Map<String, List<LspTextEdit>>? = null,
    val documentChanges: List<TextDocumentEdit>? = null,
)

/**
 * LSP Text Document Edit.
 */
@Serializable
public data class TextDocumentEdit(
    val textDocument: VersionedTextDocumentIdentifier,
    val edits: List<LspTextEdit>,
)

// ============================================================================
// Document Highlight
// ============================================================================

/**
 * LSP Document Highlight Kind.
 */
@Serializable
public enum class LspDocumentHighlightKind {
    @SerialName("1")
    Text,

    @SerialName("2")
    Read,

    @SerialName("3")
    Write,
}

/**
 * LSP Document Highlight.
 */
@Serializable
public data class LspDocumentHighlight(
    val range: LspRange,
    val kind: LspDocumentHighlightKind? = null,
)

// ============================================================================
// Call Hierarchy
// ============================================================================

/**
 * LSP Call Hierarchy Item.
 */
@Serializable
public data class LspCallHierarchyItem(
    val name: String,
    val kind: LspSymbolKind,
    val detail: String? = null,
    val uri: String,
    val range: LspRange,
    val selectionRange: LspRange,
)

/**
 * LSP Call Hierarchy Incoming Call.
 */
@Serializable
public data class LspCallHierarchyIncomingCall(
    val from: LspCallHierarchyItem,
    val fromRanges: List<LspRange>,
)

/**
 * LSP Call Hierarchy Outgoing Call.
 */
@Serializable
public data class LspCallHierarchyOutgoingCall(
    val to: LspCallHierarchyItem,
    val fromRanges: List<LspRange>,
)

// ============================================================================
// Type Hierarchy
// ============================================================================

/**
 * LSP Type Hierarchy Item.
 */
@Serializable
public data class LspTypeHierarchyItem(
    val name: String,
    val kind: LspSymbolKind,
    val detail: String? = null,
    val uri: String,
    val range: LspRange,
    val selectionRange: LspRange,
)

// ============================================================================
// Workspace Edit Application
// ============================================================================

/**
 * Parameters for workspace/applyEdit request.
 */
@Serializable
public data class ApplyWorkspaceEditParams(
    /**
     * An optional label for the edit (shown in the UI).
     */
    val label: String? = null,
    /**
     * The edits to apply.
     */
    val edit: LspWorkspaceEdit,
)

/**
 * Result of workspace/applyEdit request.
 */
@Serializable
public data class ApplyWorkspaceEditResult(
    /**
     * Whether the edit was applied.
     */
    val applied: Boolean,
    /**
     * Failure reason if applied is false.
     */
    val failureReason: String? = null,
    /**
     * Index of the failed change if documentChanges failed.
     */
    val failedChange: Int? = null,
)
