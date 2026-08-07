package su.kidoz.jetaprog.app.adapter

import kotlinx.coroutines.flow.first
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.plugins.api.services.OpenOptions
import su.kidoz.jetaprog.plugins.api.services.RevealType
import su.kidoz.jetaprog.plugins.api.services.ShowOptions
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.api.services.TextEditor
import su.kidoz.jetaprog.plugins.api.services.TextEditorEdit
import su.kidoz.jetaprog.plugins.api.services.ViewColumn

/** Connects the plugin editor API to the project-scoped editor view model. */
internal class EditorServiceBridge(
    private val viewModel: EditorViewModel,
) {
    internal suspend fun openDocument(
        uri: DocumentUri,
        options: OpenOptions,
    ): TextEditor {
        val path = uri.filePathOrNull() ?: error("Only file documents can be opened: $uri")
        viewModel.dispatch(EditorIntent.OpenFile(path))
        val state =
            viewModel.state.first { candidate ->
                candidate.error != null || candidate.tabs.any { it.uri == uri }
            }
        check(state.tabs.any { it.uri == uri }) { state.error ?: "Failed to open $path" }
        return ViewModelTextEditor(viewModel, uri, options.viewColumn)
    }

    internal suspend fun showDocument(
        document: TextDocument,
        options: ShowOptions,
    ): TextEditor {
        val editor = openDocument(document.uri, OpenOptions(viewColumn = options.viewColumn))
        options.selection?.let { editor.setSelection(it) }
        return editor
    }

    internal fun editorFor(uri: DocumentUri): TextEditor = ViewModelTextEditor(viewModel, uri, ViewColumn.Active)
}

private class ViewModelTextEditor(
    private val viewModel: EditorViewModel,
    private val uri: DocumentUri,
    override val viewColumn: ViewColumn,
) : TextEditor {
    override val document: TextDocument
        get() = ViewModelTextDocument(viewModel, uri)

    override val cursorPosition: TextPosition
        get() = activeState().cursor.position

    override val selections: List<TextRange>
        get() {
            val cursor = activeState().cursor
            return if (cursor.hasSelection) {
                listOf(
                    TextRange(cursor.selectionStart, cursor.selectionEnd),
                )
            } else {
                emptyList()
            }
        }

    override val visibleRange: TextRange
        get() {
            val state = activeState()
            return TextRange(TextPosition.Zero, offsetToPosition(state.content, state.content.length))
        }

    override suspend fun edit(edit: TextEditorEdit.() -> Unit): Boolean {
        activate()
        val state = activeState()
        val builder = EditorEditBuilder(state.content)
        builder.edit()
        val updated = builder.apply()
        if (updated == state.content) return true
        viewModel.dispatch(EditorIntent.UpdateContent(updated))
        viewModel.state.first { it.activeDocumentUri == uri && it.content == updated }
        return true
    }

    override suspend fun setCursorPosition(position: TextPosition) {
        activate()
        viewModel.dispatch(EditorIntent.MoveCursor(position))
        viewModel.state.first { it.activeDocumentUri == uri && it.cursor.position == position }
    }

    override suspend fun setSelection(range: TextRange) {
        activate()
        viewModel.dispatch(EditorIntent.Select(range))
        viewModel.state.first {
            it.activeDocumentUri == uri &&
                it.cursor.selectionStart == range.start &&
                it.cursor.selectionEnd == range.end
        }
    }

    override suspend fun setSelections(ranges: List<TextRange>) {
        ranges.firstOrNull()?.let { setSelection(it) } ?: run {
            activate()
            viewModel.dispatch(EditorIntent.ClearSelection)
        }
    }

    override suspend fun revealRange(
        range: TextRange,
        revealType: RevealType,
    ) {
        activate()
        viewModel.dispatch(EditorIntent.Select(range))
        viewModel.dispatch(EditorIntent.ScrollToLine(range.start.line + 1))
    }

    private suspend fun activate() {
        val index =
            viewModel.state.value.tabs
                .indexOfFirst { it.uri == uri }
        check(index >= 0) { "Document is no longer open: $uri" }
        if (viewModel.state.value.activeTabIndex != index) {
            viewModel.dispatch(EditorIntent.SwitchTab(index))
            viewModel.state.first { it.activeDocumentUri == uri }
        }
    }

    private fun activeState(): EditorState =
        viewModel.state.value.also { state ->
            check(state.activeDocumentUri == uri) { "Document is not active: $uri" }
        }
}

private class ViewModelTextDocument(
    private val viewModel: EditorViewModel,
    override val uri: DocumentUri,
) : TextDocument {
    private val state: EditorState
        get() =
            viewModel.state.value.also { current ->
                check(current.activeDocumentUri == uri) { "Document is not active: $uri" }
            }

    override val fileName: String get() = state.activeTab?.name ?: uri.value.substringAfterLast('/')
    override val languageId get() = state.languageId
    override val version: Int get() = state.documentVersion
    override val isDirty: Boolean get() = state.activeTab?.isDirty == true
    override val isUntitled: Boolean get() = uri.value.startsWith("untitled:")
    override val lineCount: Int get() = state.lineCount

    override fun getText(): String = state.content

    override fun getText(range: TextRange): String {
        val content = state.content
        return content.substring(positionToOffset(content, range.start), positionToOffset(content, range.end))
    }

    override fun getLine(lineNumber: Int): String = state.content.lines().getOrElse(lineNumber) { "" }

    override fun offsetAt(position: TextPosition): Int = positionToOffset(state.content, position)

    override fun positionAt(offset: Int): TextPosition = offsetToPosition(state.content, offset)

    override suspend fun save(): Boolean {
        if (!isDirty) return true
        viewModel.dispatch(EditorIntent.Save)
        return viewModel.state.first { it.activeDocumentUri == uri && !it.isSaving && !it.activeTab!!.isDirty }.error ==
            null
    }
}

private class EditorEditBuilder(
    private val original: String,
) : TextEditorEdit {
    private val replacements = mutableListOf<Replacement>()

    override fun insert(
        position: TextPosition,
        text: String,
    ) {
        val offset = positionToOffset(original, position)
        replacements += Replacement(offset, offset, text)
    }

    override fun delete(range: TextRange) {
        replace(range, "")
    }

    override fun replace(
        range: TextRange,
        text: String,
    ) {
        replacements +=
            Replacement(
                start = positionToOffset(original, range.start),
                end = positionToOffset(original, range.end),
                text = text,
            )
    }

    fun apply(): String {
        var result = original
        replacements
            .sortedByDescending { it.start }
            .forEach { replacement ->
                result = result.replaceRange(replacement.start, replacement.end, replacement.text)
            }
        return result
    }
}

private data class Replacement(
    val start: Int,
    val end: Int,
    val text: String,
)

private fun DocumentUri.filePathOrNull(): String? = value.takeIf { it.startsWith("file://") }?.removePrefix("file://")

private fun positionToOffset(
    content: String,
    position: TextPosition,
): Int {
    var start = 0
    repeat(position.line) {
        val newline = content.indexOf('\n', start)
        if (newline < 0) return content.length
        start = newline + 1
    }
    var end = start
    while (end < content.length && content[end] != '\r' && content[end] != '\n') end++
    return (start + position.column).coerceIn(start, end)
}

private fun offsetToPosition(
    content: String,
    offset: Int,
): TextPosition {
    var line = 0
    var column = 0
    for (index in 0 until offset.coerceIn(0, content.length)) {
        when (content[index]) {
            '\n' -> {
                line++
                column = 0
            }

            '\r' -> {}

            else -> {
                column++
            }
        }
    }
    return TextPosition(line, column)
}
