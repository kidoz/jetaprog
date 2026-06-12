package su.kidoz.jetaprog.common.completion

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextRange

/**
 * A completion item.
 *
 * Enhanced with type information for smart completion support,
 * following IntelliJ IDEA's LookupElement design.
 */
@Serializable
public data class CompletionItem(
    /**
     * The label shown in the completion list.
     */
    val label: String,
    /**
     * The kind of completion item.
     */
    val kind: CompletionItemKind = CompletionItemKind.Text,
    /**
     * Additional details about the item.
     */
    val detail: String? = null,
    /**
     * Documentation for the item.
     */
    val documentation: String? = null,
    /**
     * Text to insert when selected.
     */
    val insertText: String = label,
    /**
     * Whether the insert text is a snippet.
     */
    val insertTextIsSnippet: Boolean = false,
    /**
     * The range to replace when inserting.
     */
    val range: TextRange? = null,
    /**
     * Filter text for matching.
     */
    val filterText: String = label,
    /**
     * Sort text for ordering.
     */
    val sortText: String = label,
    /**
     * Whether this item should be preselected.
     */
    val preselect: Boolean = false,
    /**
     * Additional edits to apply.
     */
    val additionalTextEdits: List<TextEditData> = emptyList(),
    /**
     * Type text displayed on the right side of the completion item (smart completion).
     * For methods, this is typically the return type.
     * For variables, this is the variable type.
     */
    val typeText: String? = null,
    /**
     * Tail text displayed after the label, shown grayed (smart completion).
     * For methods, this typically shows parameter info like "(x: Int, y: Int)".
     * For properties, this might show "from ClassName".
     */
    val tailText: String? = null,
    /**
     * The fully qualified return/result type name for type-aware filtering.
     * Used by smart completion to filter items by expected type.
     */
    val returnTypeName: String? = null,
    /**
     * The fully qualified containing type name.
     * For members, this is the declaring class.
     */
    val containerTypeName: String? = null,
    /**
     * Priority for sorting (higher values appear first).
     * Used for relevance-based sorting in smart completion.
     */
    val priority: Int = 0,
    /**
     * Tags/annotations for additional filtering and display.
     * Examples: "deprecated", "experimental", "suspend"
     */
    val tags: List<String> = emptyList(),
    /**
     * Source of this completion item for attribution.
     */
    val source: CompletionSource = CompletionSource.Unknown,
)

/**
 * Kinds of completion items.
 */
public enum class CompletionItemKind {
    Text,
    Method,
    Function,
    Constructor,
    Field,
    Variable,
    Class,
    Interface,
    Module,
    Property,
    Unit,
    Value,
    Enum,
    Keyword,
    Snippet,
    Color,
    File,
    Reference,
    Folder,
    EnumMember,
    Constant,
    Struct,
    Event,
    Operator,
    TypeParameter,
}

/**
 * Source of completion items for attribution and filtering.
 */
@Serializable
public enum class CompletionSource {
    /**
     * Unknown or unspecified source.
     */
    Unknown,

    /**
     * From Language Server Protocol.
     */
    Lsp,

    /**
     * From local symbol index.
     */
    LocalIndex,

    /**
     * Language keywords.
     */
    Keywords,

    /**
     * Live templates/snippets.
     */
    Templates,

    /**
     * Postfix templates.
     */
    PostfixTemplates,

    /**
     * File path completion.
     */
    FilePaths,

    /**
     * User-defined or plugin-provided.
     */
    Plugin,
}

/**
 * A list of completion items.
 */
@Serializable
public data class CompletionList(
    /**
     * The completion items.
     */
    val items: List<CompletionItem>,
    /**
     * Whether this list is incomplete.
     */
    val isIncomplete: Boolean = false,
)

/**
 * Text edit data for serialization.
 */
@Serializable
public data class TextEditData(
    val range: TextRange,
    val newText: String,
)

/**
 * Trigger kind for completion requests.
 */
public enum class CompletionTriggerKind {
    /**
     * Completion was triggered by typing or Ctrl+Space.
     */
    Invoked,

    /**
     * Completion was triggered by a trigger character.
     */
    TriggerCharacter,

    /**
     * Completion was re-triggered to get more items.
     */
    TriggerForIncompleteCompletions,
}

/**
 * Context for a completion request.
 */
@Serializable
public data class CompletionContext(
    /**
     * How completion was triggered.
     */
    val triggerKind: CompletionTriggerKind,
    /**
     * The character that triggered completion, if any.
     */
    val triggerCharacter: Char? = null,
)
