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
        get() = 1 // Version tracking not implemented in EditorState

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
        val lines = content.lines()
        var offset = 0

        // Add lengths of all lines before the target line
        for (i in 0 until position.line.coerceAtMost(lines.size - 1)) {
            offset += lines[i].length + 1 // +1 for newline
        }

        // Add the column offset within the target line
        val lineLength = if (position.line < lines.size) lines[position.line].length else 0
        return offset + position.column.coerceAtMost(lineLength)
    }

    override fun positionAt(offset: Int): TextPosition {
        val content = state.content
        var remaining = offset.coerceIn(0, content.length)
        val lines = content.lines()

        for ((lineNum, line) in lines.withIndex()) {
            if (remaining <= line.length) {
                return TextPosition(lineNum, remaining)
            }
            remaining -= line.length + 1 // +1 for newline
        }

        // If offset exceeds content, return end position
        return TextPosition(
            (lines.size - 1).coerceAtLeast(0),
            lines.lastOrNull()?.length ?: 0,
        )
    }

    override suspend fun save(): Boolean {
        // Save is handled by EditorViewModel, not the adapter
        return false
    }
}
