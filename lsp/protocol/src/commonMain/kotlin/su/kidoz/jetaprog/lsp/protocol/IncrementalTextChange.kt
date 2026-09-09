package su.kidoz.jetaprog.lsp.protocol

/**
 * Expresses the difference between two versions of a document as one replaced range,
 * for servers that accept incremental `textDocument/didChange` events.
 *
 * The change is the span between the longest common prefix and the longest common
 * suffix. A keystroke therefore travels as a few bytes instead of the whole file, and
 * positions use UTF-16 units as the protocol's default encoding requires.
 */
public object IncrementalTextChange {
    /** The single change that turns [old] into [new]; a full-text event when nothing is shared. */
    public fun between(
        old: String,
        new: String,
    ): TextDocumentContentChangeEvent {
        val prefix = old.commonPrefixWith(new).length
        val maxSuffix = minOf(old.length, new.length) - prefix
        var suffix = 0
        while (suffix < maxSuffix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) {
            suffix++
        }
        val start = positionAt(old, prefix)
        val end = positionAt(old, old.length - suffix)
        return TextDocumentContentChangeEvent(
            range = LspRange(start, end),
            rangeLength = old.length - suffix - prefix,
            text = new.substring(prefix, new.length - suffix),
        )
    }

    private fun positionAt(
        text: String,
        offset: Int,
    ): LspPosition {
        var line = 0
        var lineStart = 0
        for (index in 0 until offset) {
            if (text[index] == '\n') {
                line++
                lineStart = index + 1
            }
        }
        return LspPosition(line, offset - lineStart)
    }
}
