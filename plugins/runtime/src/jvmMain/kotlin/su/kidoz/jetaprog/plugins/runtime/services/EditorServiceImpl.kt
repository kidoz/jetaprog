package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.plugins.api.services.EditorService
import su.kidoz.jetaprog.plugins.api.services.OpenOptions
import su.kidoz.jetaprog.plugins.api.services.ShowOptions
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.api.services.TextDocumentChangeEvent
import su.kidoz.jetaprog.plugins.api.services.TextEditor

/**
 * Implementation of EditorService.
 *
 * Delegates document presentation to the host IDE while owning the observable
 * editor and document-event surface exposed to plugins.
 */
public class EditorServiceImpl(
    private val openDocumentHandler: suspend (DocumentUri, OpenOptions) -> TextEditor,
    private val showDocumentHandler: suspend (TextDocument, ShowOptions) -> TextEditor,
) : EditorService {
    private val _activeEditor = MutableStateFlow<TextEditor?>(null)
    private val _visibleEditors = MutableStateFlow<List<TextEditor>>(emptyList())

    private val documentChangeHandlers = mutableListOf<suspend (TextDocumentChangeEvent) -> Unit>()
    private val documentOpenHandlers = mutableListOf<suspend (TextDocument) -> Unit>()
    private val documentCloseHandlers = mutableListOf<suspend (TextDocument) -> Unit>()
    private val documentSaveHandlers = mutableListOf<suspend (TextDocument) -> Unit>()

    override val activeEditor: TextEditor? get() = _activeEditor.value

    override val visibleEditors: List<TextEditor> get() = _visibleEditors.value

    override val onDidChangeActiveEditor: Flow<TextEditor?> = _activeEditor.asStateFlow()

    override val onDidChangeVisibleEditors: Flow<List<TextEditor>> = _visibleEditors.asStateFlow()

    override suspend fun openDocument(
        uri: DocumentUri,
        options: OpenOptions,
    ): TextEditor {
        val editor = openDocumentHandler(uri, options)
        publishEditor(editor)
        return editor
    }

    override suspend fun openFile(
        path: String,
        options: OpenOptions,
    ): TextEditor {
        val uri = DocumentUri("file://$path")
        return openDocument(uri, options)
    }

    override suspend fun showDocument(
        document: TextDocument,
        options: ShowOptions,
    ): TextEditor {
        val editor = showDocumentHandler(document, options)
        publishEditor(editor)
        return editor
    }

    override fun onDidChangeTextDocument(handler: suspend (TextDocumentChangeEvent) -> Unit): Disposable {
        documentChangeHandlers.add(handler)
        return Disposable { documentChangeHandlers.remove(handler) }
    }

    override fun onDidOpenTextDocument(handler: suspend (TextDocument) -> Unit): Disposable {
        documentOpenHandlers.add(handler)
        return Disposable { documentOpenHandlers.remove(handler) }
    }

    override fun onDidCloseTextDocument(handler: suspend (TextDocument) -> Unit): Disposable {
        documentCloseHandlers.add(handler)
        return Disposable { documentCloseHandlers.remove(handler) }
    }

    override fun onDidSaveTextDocument(handler: suspend (TextDocument) -> Unit): Disposable {
        documentSaveHandlers.add(handler)
        return Disposable { documentSaveHandlers.remove(handler) }
    }

    // ========================================================================
    // Internal methods for editor infrastructure to call
    // ========================================================================

    /**
     * Notifies handlers about document changes.
     * Called by the editor infrastructure.
     */
    public suspend fun notifyDocumentChanged(event: TextDocumentChangeEvent) {
        documentChangeHandlers.forEach { it(event) }
    }

    /**
     * Notifies handlers about document open.
     * Called by the editor infrastructure.
     */
    public suspend fun notifyDocumentOpened(document: TextDocument) {
        documentOpenHandlers.forEach { it(document) }
    }

    /**
     * Notifies handlers about document close.
     * Called by the editor infrastructure.
     */
    public suspend fun notifyDocumentClosed(document: TextDocument) {
        documentCloseHandlers.forEach { it(document) }
    }

    /**
     * Notifies handlers about document save.
     * Called by the editor infrastructure.
     */
    public suspend fun notifyDocumentSaved(document: TextDocument) {
        documentSaveHandlers.forEach { it(document) }
    }

    /**
     * Updates the active editor.
     * Called by the editor infrastructure.
     */
    public fun setActiveEditor(editor: TextEditor?) {
        _activeEditor.value = editor
    }

    /**
     * Updates visible editors.
     * Called by the editor infrastructure.
     */
    public fun setVisibleEditors(editors: List<TextEditor>) {
        _visibleEditors.value = editors
    }

    private fun publishEditor(editor: TextEditor) {
        _activeEditor.value = editor
        _visibleEditors.value = listOf(editor)
    }
}
