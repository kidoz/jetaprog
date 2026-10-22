package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.app.ui.agent.EditorSnippet
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.math.min

/** Largest selection copied into an agent prompt. Longer selections are cut and marked. */
internal const val SELECTION_EXCERPT_MAX_CHARS: Int = 2_000

/**
 * Reads the active editor file and its selection for the agent composer.
 *
 * Returns null for an empty editor or an untitled buffer. Paths inside [workspacePath] are
 * relative; other files keep their absolute path.
 */
internal fun editorSnippet(
    workspacePath: String,
    state: EditorState,
): EditorSnippet? {
    val uri = state.activeDocumentUri?.value ?: return null
    if (!uri.startsWith("file://")) return null
    val absolute = uri.removePrefix("file://")
    val root = workspacePath.trimEnd('/', '\\')
    val relative =
        when {
            absolute == root -> absolute.substringAfterLast('/').substringAfterLast('\\').ifBlank { absolute }
            absolute.startsWith("$root/") -> absolute.removePrefix("$root/")
            absolute.startsWith("$root\\") -> absolute.removePrefix("$root\\")
            else -> absolute
        }
    val label = relative.substringAfterLast('/').substringAfterLast('\\').ifBlank { relative }
    val selection =
        if (state.cursor.hasSelection) {
            selectionExcerpt(state.content, state.cursor.selectionStart, state.cursor.selectionEnd)
        } else {
            null
        }
    return EditorSnippet(label = label, path = relative, selection = selection)
}

/**
 * Returns the text between [start] and [end], or null when that range is blank.
 *
 * Offsets are 0-based line and column, matching the editor caret. The result is capped at
 * [SELECTION_EXCERPT_MAX_CHARS].
 */
internal fun selectionExcerpt(
    content: String,
    start: TextPosition,
    end: TextPosition,
): String? {
    val lines = content.split('\n')

    fun offset(position: TextPosition): Int {
        var cursor = 0
        val lineIndex = position.line.coerceAtLeast(0)
        for (line in 0 until lineIndex) {
            if (line >= lines.size) return content.length
            cursor += lines[line].length + 1
        }
        val lineLength = lines.getOrNull(lineIndex)?.length ?: 0
        return (cursor + position.column.coerceAtLeast(0).coerceAtMost(lineLength)).coerceIn(0, content.length)
    }

    val from = offset(start)
    val to = offset(end)
    if (from == to) return null
    val raw = content.substring(min(from, to), maxOf(from, to))
    if (raw.isBlank()) return null
    return if (raw.length <= SELECTION_EXCERPT_MAX_CHARS) {
        raw
    } else {
        raw.take(SELECTION_EXCERPT_MAX_CHARS) + "…"
    }
}
