package su.kidoz.jetaprog.app.adapter

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.plugins.api.services.TextDocument

/**
 * Adapter that wraps [EditorState] to implement [TextDocument] interface.
 *
 * This allows the editor state to be used with language services like
 * [su.kidoz.jetaprog.languages.support.LanguageRegistry] for completions,
 * hover, and other language features.
 */
public class TextDocumentAdapter(
    private val state: EditorState,
) : TextDocument {
    override val uri: DocumentUri
        get() = state.activeDocumentUri ?: DocumentUri.untitled(0)

    override val fileName: String
        get() = state.activeTab?.name ?: "untitled"

    override val languageId: LanguageId
        get() = state.languageId

    override val version: Int
        get() = state.documentVersion

    override val isDirty: Boolean
        get() = state.activeTab?.isDirty ?: false

    override val isUntitled: Boolean
        get() = state.activeDocumentUri?.value?.startsWith("untitled:") ?: true

    override val lineCount: Int
        get() = state.lineCount

    override fun getText(): String = state.content

    override fun getText(range: TextRange): String {
        val content = state.content
        val startOffset = offsetAt(range.start)
        val endOffset = offsetAt(range.end)
        return content.substring(
            startOffset.coerceIn(0, content.length),
            endOffset.coerceIn(0, content.length),
        )
    }

    override fun getLine(lineNumber: Int): String {
        val lines = state.content.lines()
        return if (lineNumber in lines.indices) lines[lineNumber] else ""
    }

    override fun offsetAt(position: TextPosition): Int {
        val content = state.content
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

    override fun positionAt(offset: Int): TextPosition {
        val content = state.content
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

    override suspend fun save(): Boolean {
        // Save is handled by EditorViewModel, not the adapter
        return false
    }
}
