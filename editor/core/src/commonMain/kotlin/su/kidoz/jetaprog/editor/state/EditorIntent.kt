package su.kidoz.jetaprog.editor.state

import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Intents (actions) that can be dispatched to the editor.
 */
public sealed interface EditorIntent : Intent {
    // File operations

    /**
     * Open a file at the given path.
     */
    public data class OpenFile(
        val path: String,
    ) : EditorIntent

    /**
     * Restore the tabs of a previous editing session.
     *
     * Missing files are skipped silently. [activeTabIndex] indexes into
     * [filePaths] and is remapped to the surviving tabs; [cursor] applies
     * to the restored active tab.
     */
    public data class RestoreSession(
        val filePaths: List<String>,
        val activeTabIndex: Int,
        val cursor: TextPosition? = null,
    ) : EditorIntent

    /**
     * Save the active document.
     */
    public data object Save : EditorIntent

    /**
     * Save the active document to a new path.
     */
    public data class SaveAs(
        val path: String,
    ) : EditorIntent

    /**
     * Close a tab.
     */
    public data class CloseTab(
        val index: Int,
    ) : EditorIntent

    /**
     * Close all tabs.
     */
    public data object CloseAllTabs : EditorIntent

    /**
     * Switch to a tab by index.
     */
    public data class SwitchTab(
        val index: Int,
    ) : EditorIntent

    // Text editing

    /**
     * Insert text at the current cursor position.
     */
    public data class InsertText(
        val text: String,
    ) : EditorIntent

    /**
     * Delete text in a range.
     */
    public data class DeleteRange(
        val range: TextRange,
    ) : EditorIntent

    /**
     * Delete the character before the cursor.
     */
    public data object Backspace : EditorIntent

    /**
     * Delete the character after the cursor.
     */
    public data object Delete : EditorIntent

    /**
     * Delete the current line.
     */
    public data object DeleteLine : EditorIntent

    /**
     * Duplicate the current line.
     */
    public data object DuplicateLine : EditorIntent

    // Cursor movement

    /**
     * Move the cursor to a position.
     */
    public data class MoveCursor(
        val position: TextPosition,
    ) : EditorIntent

    /**
     * Move the cursor up.
     */
    public data object MoveUp : EditorIntent

    /**
     * Move the cursor down.
     */
    public data object MoveDown : EditorIntent

    /**
     * Move the cursor left.
     */
    public data object MoveLeft : EditorIntent

    /**
     * Move the cursor right.
     */
    public data object MoveRight : EditorIntent

    /**
     * Move the cursor to the start of the line.
     */
    public data object MoveToLineStart : EditorIntent

    /**
     * Move the cursor to the end of the line.
     */
    public data object MoveToLineEnd : EditorIntent

    /**
     * Move the cursor to the start of the document.
     */
    public data object MoveToDocumentStart : EditorIntent

    /**
     * Move the cursor to the end of the document.
     */
    public data object MoveToDocumentEnd : EditorIntent

    /**
     * Move the cursor to a specific line.
     */
    public data class GoToLine(
        val lineNumber: Int,
    ) : EditorIntent

    // Selection

    /**
     * Select text in a range.
     */
    public data class Select(
        val range: TextRange,
    ) : EditorIntent

    /**
     * Select all text.
     */
    public data object SelectAll : EditorIntent

    /**
     * Clear the selection.
     */
    public data object ClearSelection : EditorIntent

    /**
     * Extend selection up.
     */
    public data object SelectUp : EditorIntent

    /**
     * Extend selection down.
     */
    public data object SelectDown : EditorIntent

    /**
     * Extend selection left.
     */
    public data object SelectLeft : EditorIntent

    /**
     * Extend selection right.
     */
    public data object SelectRight : EditorIntent

    // Undo/Redo

    /**
     * Undo the last edit.
     */
    public data object Undo : EditorIntent

    /**
     * Redo the last undone edit.
     */
    public data object Redo : EditorIntent

    // Clipboard

    /**
     * Cut the selected text.
     */
    public data object Cut : EditorIntent

    /**
     * Copy the selected text.
     */
    public data object Copy : EditorIntent

    /**
     * Paste from clipboard.
     */
    public data class Paste(
        val text: String,
    ) : EditorIntent

    // View options

    /**
     * Toggle line numbers.
     */
    public data object ToggleLineNumbers : EditorIntent

    /**
     * Toggle minimap.
     */
    public data object ToggleMinimap : EditorIntent

    /**
     * Toggle word wrap.
     */
    public data object ToggleWordWrap : EditorIntent

    /**
     * Scroll to a line.
     */
    public data class ScrollToLine(
        val lineNumber: Int,
    ) : EditorIntent

    // Content update

    /**
     * Update the content of the active document.
     */
    public data class UpdateContent(
        val content: String,
    ) : EditorIntent

    // Syntax tokens

    /**
     * Set syntax tokens for the active document.
     */
    public data class SetTokens(
        val tokens: List<su.kidoz.jetaprog.editor.syntax.Token>,
    ) : EditorIntent

    /**
     * Apply semantic tokens overlay.
     */
    public data class ApplySemanticTokens(
        val data: List<Int>,
        val tokenTypes: List<String>,
    ) : EditorIntent

    /**
     * Set the VCS change markers of the active document, keyed by 0-based line index.
     */
    public data class SetLineChangeMarkers(
        val markers: Map<Int, LineChangeMarker>,
    ) : EditorIntent

    // Completion

    /**
     * Request code completion.
     */
    public data class RequestCompletion(
        val triggerKind: su.kidoz.jetaprog.common.completion.CompletionTriggerKind =
            su.kidoz.jetaprog.common.completion.CompletionTriggerKind.Invoked,
        val triggerCharacter: Char? = null,
        val filterText: String = "",
    ) : EditorIntent

    /**
     * Set completion items from a provider.
     */
    public data class SetCompletionItems(
        val items: List<su.kidoz.jetaprog.common.completion.CompletionItem>,
        val isIncomplete: Boolean = false,
    ) : EditorIntent

    /**
     * Apply the selected completion item.
     */
    public data class ApplyCompletion(
        val item: su.kidoz.jetaprog.common.completion.CompletionItem,
    ) : EditorIntent

    /**
     * Select a completion item by index.
     */
    public data class SelectCompletionItem(
        val index: Int,
    ) : EditorIntent

    /**
     * Move completion selection up.
     */
    public data object CompletionMoveUp : EditorIntent

    /**
     * Move completion selection down.
     */
    public data object CompletionMoveDown : EditorIntent

    /**
     * Dismiss the completion popup.
     */
    public data object DismissCompletion : EditorIntent

    /**
     * Update the completion filter text.
     */
    public data class UpdateCompletionFilter(
        val filterText: String,
    ) : EditorIntent

    // Hover

    /**
     * Request hover information at position.
     */
    public data class RequestHover(
        val position: TextPosition,
    ) : EditorIntent

    /**
     * Set hover content.
     */
    public data class SetHoverContent(
        val contents: List<su.kidoz.jetaprog.common.text.MarkedString>,
        val range: su.kidoz.jetaprog.common.text.TextRange? = null,
    ) : EditorIntent

    /**
     * Dismiss the hover popup.
     */
    public data object DismissHover : EditorIntent

    // Signature help

    /**
     * Request signature help at position.
     */
    public data class RequestSignatureHelp(
        val triggerCharacter: Char? = null,
        val isRetrigger: Boolean = false,
    ) : EditorIntent

    /**
     * Set signature help content.
     */
    public data class SetSignatureHelp(
        val signatures: List<SignatureInfo>,
        val activeSignature: Int = 0,
        val activeParameter: Int = 0,
        val position: TextPosition,
    ) : EditorIntent

    /**
     * Update the active parameter index.
     */
    public data class UpdateActiveParameter(
        val index: Int,
    ) : EditorIntent

    /**
     * Navigate to next signature.
     */
    public data object NextSignature : EditorIntent

    /**
     * Navigate to previous signature.
     */
    public data object PreviousSignature : EditorIntent

    /**
     * Dismiss signature help.
     */
    public data object DismissSignatureHelp : EditorIntent

    // Navigation

    /**
     * Go to definition of symbol at cursor.
     */
    public data object GoToDefinition : EditorIntent

    /**
     * Find references of symbol at cursor.
     */
    public data object FindReferences : EditorIntent

    /**
     * Open the symbol search dialog.
     */
    public data object OpenSymbolSearch : EditorIntent

    /**
     * Navigate to a file at a position.
     */
    public data class NavigateTo(
        val path: String,
        val position: TextPosition,
    ) : EditorIntent

    // Formatting

    /**
     * Format the entire document.
     */
    public data object FormatDocument : EditorIntent

    /**
     * Format the selected region.
     */
    public data class FormatSelection(
        val range: TextRange,
    ) : EditorIntent

    // Search

    /**
     * Find text.
     */
    public data class Find(
        val query: String,
        val caseSensitive: Boolean = false,
    ) : EditorIntent

    /**
     * Find and replace text.
     */
    public data class Replace(
        val find: String,
        val replaceWith: String,
        val all: Boolean = false,
    ) : EditorIntent

    /**
     * Open the find bar, optionally with the replace row shown.
     */
    public data class OpenFindBar(
        val withReplace: Boolean = false,
    ) : EditorIntent

    /**
     * Close the find bar.
     */
    public data object CloseFindBar : EditorIntent

    /**
     * Update the find query and recompute matches.
     */
    public data class UpdateFindQuery(
        val query: String,
    ) : EditorIntent

    /**
     * Update the replacement text.
     */
    public data class UpdateReplaceText(
        val text: String,
    ) : EditorIntent

    /**
     * Toggle a find option and recompute matches.
     */
    public data class ToggleFindOption(
        val option: FindToggle,
    ) : EditorIntent

    /**
     * Move to the next match.
     */
    public data object FindNext : EditorIntent

    /**
     * Move to the previous match.
     */
    public data object FindPrevious : EditorIntent

    /**
     * Replace the current match with the replacement text.
     */
    public data object ReplaceCurrent : EditorIntent

    /**
     * Replace all matches with the replacement text.
     */
    public data object ReplaceAll : EditorIntent
}

/**
 * Find options that can be toggled from the find bar.
 */
public enum class FindToggle {
    /**
     * Case-sensitive matching.
     */
    CASE_SENSITIVE,

    /**
     * Whole-word matching.
     */
    WHOLE_WORD,

    /**
     * Regular-expression matching.
     */
    REGEX,
}

/**
 * Information about a function/method signature.
 */
public data class SignatureInfo(
    /**
     * The signature label.
     */
    val label: String,
    /**
     * Documentation for the signature.
     */
    val documentation: String? = null,
    /**
     * The parameters.
     */
    val parameters: List<SignatureParameter> = emptyList(),
)

/**
 * Information about a parameter in a signature.
 */
public data class SignatureParameter(
    /**
     * The parameter label.
     */
    val label: String,
    /**
     * Documentation for the parameter.
     */
    val documentation: String? = null,
)
