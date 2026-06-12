package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * Service for editor operations.
 */
public interface EditorService {
    /**
     * The currently active editor, or null if none.
     */
    public val activeEditor: TextEditor?

    /**
     * All visible editors.
     */
    public val visibleEditors: List<TextEditor>

    /**
     * Flow emitting when the active editor changes.
     */
    public val onDidChangeActiveEditor: Flow<TextEditor?>

    /**
     * Flow emitting when visible editors change.
     */
    public val onDidChangeVisibleEditors: Flow<List<TextEditor>>

    /**
     * Opens a document.
     * @param uri The document URI to open
     * @param options Open options
     * @return The text editor showing the document
     */
    public suspend fun openDocument(
        uri: DocumentUri,
        options: OpenOptions = OpenOptions(),
    ): TextEditor

    /**
     * Opens a document by file path.
     * @param path The file path to open
     * @param options Open options
     * @return The text editor showing the document
     */
    public suspend fun openFile(
        path: String,
        options: OpenOptions = OpenOptions(),
    ): TextEditor

    /**
     * Shows an existing document in an editor.
     * @param document The document to show
     * @param options Show options
     * @return The text editor showing the document
     */
    public suspend fun showDocument(
        document: TextDocument,
        options: ShowOptions = ShowOptions(),
    ): TextEditor

    /**
     * Registers a listener for document changes.
     * @param handler The handler to call when a document changes
     * @return Disposable to unregister the listener
     */
    public fun onDidChangeTextDocument(handler: suspend (TextDocumentChangeEvent) -> Unit): Disposable

    /**
     * Registers a listener for document opens.
     */
    public fun onDidOpenTextDocument(handler: suspend (TextDocument) -> Unit): Disposable

    /**
     * Registers a listener for document closes.
     */
    public fun onDidCloseTextDocument(handler: suspend (TextDocument) -> Unit): Disposable

    /**
     * Registers a listener for document saves.
     */
    public fun onDidSaveTextDocument(handler: suspend (TextDocument) -> Unit): Disposable
}

/**
 * Options for opening a document.
 */
public data class OpenOptions(
    /**
     * Whether to preview the document (close when another is opened).
     */
    val preview: Boolean = true,
    /**
     * The column to open the editor in.
     */
    val viewColumn: ViewColumn = ViewColumn.Active,
    /**
     * Whether to preserve focus on the current editor.
     */
    val preserveFocus: Boolean = false,
)

/**
 * Options for showing a document.
 */
public data class ShowOptions(
    /**
     * The column to show the editor in.
     */
    val viewColumn: ViewColumn = ViewColumn.Active,
    /**
     * Whether to preserve focus on the current editor.
     */
    val preserveFocus: Boolean = false,
    /**
     * Optional selection to show.
     */
    val selection: TextRange? = null,
)

/**
 * View column for editor placement.
 */
public enum class ViewColumn {
    Active,
    Beside,
    One,
    Two,
    Three,
}

/**
 * Represents a text editor.
 */
public interface TextEditor {
    /**
     * The document shown in this editor.
     */
    public val document: TextDocument

    /**
     * The current cursor position.
     */
    public val cursorPosition: TextPosition

    /**
     * The current selections (may be multiple for multi-cursor).
     */
    public val selections: List<TextRange>

    /**
     * The visible range of the editor.
     */
    public val visibleRange: TextRange

    /**
     * The view column this editor is in.
     */
    public val viewColumn: ViewColumn

    /**
     * Edits the document.
     * @param edit The edit builder
     * @return Whether the edit was applied successfully
     */
    public suspend fun edit(edit: TextEditorEdit.() -> Unit): Boolean

    /**
     * Sets the cursor position.
     */
    public suspend fun setCursorPosition(position: TextPosition)

    /**
     * Sets the selection.
     */
    public suspend fun setSelection(range: TextRange)

    /**
     * Sets multiple selections (multi-cursor).
     */
    public suspend fun setSelections(ranges: List<TextRange>)

    /**
     * Reveals a range in the editor.
     */
    public suspend fun revealRange(
        range: TextRange,
        revealType: RevealType = RevealType.Default,
    )
}

/**
 * Builder for text editor edits.
 */
public interface TextEditorEdit {
    /**
     * Inserts text at a position.
     */
    public fun insert(
        position: TextPosition,
        text: String,
    )

    /**
     * Deletes text in a range.
     */
    public fun delete(range: TextRange)

    /**
     * Replaces text in a range.
     */
    public fun replace(
        range: TextRange,
        text: String,
    )
}

/**
 * How to reveal a range.
 */
public enum class RevealType {
    Default,
    InCenter,
    InCenterIfOutsideViewport,
    AtTop,
}

/**
 * Represents a text document.
 */
public interface TextDocument {
    /**
     * The document URI.
     */
    public val uri: DocumentUri

    /**
     * The file name.
     */
    public val fileName: String

    /**
     * The language ID.
     */
    public val languageId: LanguageId

    /**
     * The document version.
     */
    public val version: Int

    /**
     * Whether the document has unsaved changes.
     */
    public val isDirty: Boolean

    /**
     * Whether the document is untitled.
     */
    public val isUntitled: Boolean

    /**
     * The line count.
     */
    public val lineCount: Int

    /**
     * Gets the full text content.
     */
    public fun getText(): String

    /**
     * Gets text in a range.
     */
    public fun getText(range: TextRange): String

    /**
     * Gets a line's text.
     */
    public fun getLine(lineNumber: Int): String

    /**
     * Converts a position to an offset.
     */
    public fun offsetAt(position: TextPosition): Int

    /**
     * Converts an offset to a position.
     */
    public fun positionAt(offset: Int): TextPosition

    /**
     * Saves the document.
     */
    public suspend fun save(): Boolean
}

/**
 * Event for text document changes.
 */
public data class TextDocumentChangeEvent(
    /**
     * The document that changed.
     */
    val document: TextDocument,
    /**
     * The content changes.
     */
    val contentChanges: List<TextDocumentContentChange>,
)

/**
 * A content change in a document.
 */
public data class TextDocumentContentChange(
    /**
     * The range that was changed.
     */
    val range: TextRange,
    /**
     * The new text for the range.
     */
    val text: String,
)
