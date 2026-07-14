package su.kidoz.jetaprog.editor.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionTriggerKind
import su.kidoz.jetaprog.common.mvi.State
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.search.FindMatch
import su.kidoz.jetaprog.editor.search.FindOptions
import su.kidoz.jetaprog.editor.syntax.Diagnostic
import su.kidoz.jetaprog.editor.syntax.TokenList

/**
 * Represents an open editor tab.
 */
@Serializable
public data class EditorTab(
    /**
     * The document URI.
     */
    val uri: DocumentUri,
    /**
     * The display name for the tab.
     */
    val name: String,
    /**
     * Whether the document has unsaved changes.
     */
    val isDirty: Boolean = false,
    /**
     * Whether this tab is pinned.
     */
    val isPinned: Boolean = false,
)

/**
 * Immutable state for the editor.
 */
@Serializable
public data class EditorState(
    /**
     * The list of open tabs.
     */
    val tabs: List<EditorTab> = emptyList(),
    /**
     * The index of the active tab (-1 if none).
     */
    val activeTabIndex: Int = -1,
    /**
     * The document URI of the active document.
     */
    val activeDocumentUri: DocumentUri? = null,
    /**
     * The content of the active document.
     */
    val content: String = "",
    /**
     * The language ID of the active document.
     */
    val languageId: LanguageId = LanguageId.PLAIN_TEXT,
    /**
     * The current cursor position.
     */
    val cursor: Cursor = Cursor.Zero,
    /**
     * Whether the editor is loading a file.
     */
    val isLoading: Boolean = false,
    /**
     * Whether the editor is saving.
     */
    val isSaving: Boolean = false,
    /**
     * The list of diagnostics for the active document.
     */
    val diagnostics: List<Diagnostic> = emptyList(),
    /**
     * VCS change markers for the active document, keyed by 0-based line index.
     */
    val lineChangeMarkers: Map<Int, LineChangeMarker> = emptyMap(),
    /**
     * Whether line numbers are shown.
     */
    val showLineNumbers: Boolean = true,
    /**
     * Whether the minimap is shown.
     */
    val showMinimap: Boolean = true,
    /**
     * Whether word wrap is enabled.
     */
    val wordWrap: Boolean = false,
    /**
     * The current scroll position (line number).
     */
    val scrollLine: Int = 0,
    /**
     * Error message if an operation failed.
     */
    val error: String? = null,
    /**
     * Syntax highlighting tokens for the active document.
     */
    @Transient
    val tokens: TokenList = TokenList(emptyList()),
    /**
     * Completion popup state.
     */
    val completionState: CompletionState = CompletionState(),
    /**
     * Hover popup state.
     */
    val hoverState: HoverState = HoverState(),
    /**
     * Signature help popup state.
     */
    val signatureHelpState: SignatureHelpState = SignatureHelpState(),
    /**
     * Find/replace bar state.
     */
    val findReplaceState: FindReplaceState = FindReplaceState(),
) : State {
    /**
     * The active tab, if any.
     */
    public val activeTab: EditorTab?
        get() = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null

    /**
     * Whether there are any unsaved changes.
     */
    public val hasUnsavedChanges: Boolean
        get() = tabs.any { it.isDirty }

    /**
     * The current line number (1-based for display).
     */
    public val currentLine: Int get() = cursor.position.line + 1

    /**
     * The current column number (1-based for display).
     */
    public val currentColumn: Int get() = cursor.position.column + 1

    /**
     * The total number of lines in the document.
     */
    public val lineCount: Int get() = content.lines().size

    /**
     * Whether there are any errors in the diagnostics.
     */
    public val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    /**
     * Whether there are any warnings in the diagnostics.
     */
    public val hasWarnings: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * Severity levels for diagnostics.
 */
public enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFORMATION,
    HINT,
}

/**
 * The kind of VCS change shown as a gutter marker on a line.
 */
public enum class LineChangeMarker {
    /** The line was added. */
    ADDED,

    /** The line was modified. */
    MODIFIED,

    /** Content was deleted next to this line. */
    DELETED,
}

/**
 * State for the code completion popup.
 */
@Serializable
public data class CompletionState(
    /**
     * Whether the completion popup is visible.
     */
    val isVisible: Boolean = false,
    /**
     * Whether completion items are being fetched.
     */
    val isLoading: Boolean = false,
    /**
     * The list of completion items.
     */
    val items: List<CompletionItem> = emptyList(),
    /**
     * The index of the selected item.
     */
    val selectedIndex: Int = 0,
    /**
     * The current filter text.
     */
    val filterText: String = "",
    /**
     * Whether the list is incomplete.
     */
    val isIncomplete: Boolean = false,
    /**
     * The position where completion was triggered.
     */
    val triggerPosition: TextPosition = TextPosition(0, 0),
    /**
     * The kind of trigger that started completion.
     */
    val triggerKind: CompletionTriggerKind = CompletionTriggerKind.Invoked,
    /**
     * The character that triggered completion, if any.
     */
    val triggerCharacter: Char? = null,
) {
    /**
     * The currently selected item, if any.
     */
    public val selectedItem: CompletionItem?
        get() = if (selectedIndex in items.indices) items[selectedIndex] else null
}

/**
 * State for the hover information popup.
 */
@Serializable
public data class HoverState(
    /**
     * Whether the hover popup is visible.
     */
    val isVisible: Boolean = false,
    /**
     * Whether hover information is being fetched.
     */
    val isLoading: Boolean = false,
    /**
     * The hover contents (markdown/code blocks).
     */
    val contents: List<MarkedString> = emptyList(),
    /**
     * The range of text the hover applies to.
     */
    val range: TextRange? = null,
    /**
     * The position where hover was requested.
     */
    val position: TextPosition = TextPosition(0, 0),
)

/**
 * State for the find/replace bar.
 */
@Serializable
public data class FindReplaceState(
    /**
     * Whether the find bar is visible.
     */
    val isVisible: Boolean = false,
    /**
     * Whether the replace row is shown.
     */
    val showReplace: Boolean = false,
    /**
     * The current search query.
     */
    val query: String = "",
    /**
     * The current replacement text.
     */
    val replaceText: String = "",
    /**
     * The active search options.
     */
    val options: FindOptions = FindOptions(),
    /**
     * All matches of the query in the active document.
     */
    val matches: List<FindMatch> = emptyList(),
    /**
     * The index of the currently highlighted match (-1 if none).
     */
    val currentMatchIndex: Int = -1,
) {
    /**
     * The currently highlighted match, if any.
     */
    public val currentMatch: FindMatch?
        get() = matches.getOrNull(currentMatchIndex)
}

/**
 * State for the signature help popup.
 */
@Serializable
public data class SignatureHelpState(
    /**
     * Whether the signature help popup is visible.
     */
    val isVisible: Boolean = false,
    /**
     * Whether signature help is being fetched.
     */
    val isLoading: Boolean = false,
    /**
     * The available signatures.
     */
    @Transient
    val signatures: List<SignatureInfo> = emptyList(),
    /**
     * The index of the active signature.
     */
    val activeSignature: Int = 0,
    /**
     * The index of the active parameter.
     */
    val activeParameter: Int = 0,
    /**
     * The position where signature help was requested.
     */
    val position: TextPosition = TextPosition(0, 0),
) {
    /**
     * The currently active signature, if any.
     */
    public val activeSignatureInfo: SignatureInfo?
        get() = if (activeSignature in signatures.indices) signatures[activeSignature] else null
}
