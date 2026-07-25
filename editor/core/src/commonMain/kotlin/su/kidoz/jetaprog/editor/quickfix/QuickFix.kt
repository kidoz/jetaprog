package su.kidoz.jetaprog.editor.quickfix

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextPosition

/** A single text replacement within a document, by character offsets. */
@Serializable
public data class TextReplacement(
    /** Inclusive start offset. */
    val startOffset: Int,
    /** Exclusive end offset; equal to [startOffset] for a pure insertion. */
    val endOffset: Int,
    /** Text to put in place of the range. */
    val newText: String,
)

/** An action offered for the caret position, applied as document edits. */
@Serializable
public data class QuickFix(
    /** Label shown in the popup. */
    val title: String,
    /** Edits to apply; the caller applies them back-to-front. */
    val edits: List<TextReplacement>,
)

/**
 * Supplies quick fixes for a caret position.
 *
 * Implemented per language in the application layer; the editor only needs the
 * resulting edits, which it applies through its normal content update so fixes
 * participate in undo.
 */
public fun interface QuickFixProvider {
    /**
     * Returns fixes available at [position] in [content] of [filePath].
     *
     * [content] is the live buffer, which may differ from the file on disk.
     */
    public suspend fun quickFixes(
        filePath: String,
        content: String,
        position: TextPosition,
    ): List<QuickFix>
}

/** Applies [edits] to [content], back-to-front so earlier offsets stay valid. */
public fun applyReplacements(
    content: String,
    edits: List<TextReplacement>,
): String {
    val builder = StringBuilder(content)
    for (edit in edits.sortedByDescending { it.startOffset }) {
        if (edit.startOffset < 0 || edit.endOffset > builder.length || edit.startOffset > edit.endOffset) continue
        builder.replace(edit.startOffset, edit.endOffset, edit.newText)
    }
    return builder.toString()
}
