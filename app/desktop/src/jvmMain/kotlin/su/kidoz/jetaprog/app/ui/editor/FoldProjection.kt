package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import su.kidoz.jetaprog.editor.folding.DependencyFold

/** A reversible display projection: the editable buffer always retains the complete source. */
internal class FoldProjection(
    val source: String,
    val folds: List<DependencyFold>,
) : OffsetMapping {
    private data class Segment(
        val fold: DependencyFold,
        val visualStart: Int,
        val label: String,
    )

    private val segments =
        buildList {
            var removed = 0
            for (fold in folds) {
                val label = "${fold.label} … (${fold.declarationCount})"
                add(Segment(fold, fold.start - removed, label))
                removed += fold.end - fold.start - label.length
            }
        }
    val text: String = project(AnnotatedString(source)).text
    val visibleSourceLines: List<Int> =
        source.lines().indices.filter { line ->
            folds.none { line > it.startLine && line <= it.endLine }
        }

    fun project(annotated: AnnotatedString): AnnotatedString =
        buildAnnotatedString {
            var offset = 0
            for (segment in segments) {
                append(annotated.subSequence(offset, segment.fold.start))
                append(segment.label)
                offset = segment.fold.end
            }
            append(annotated.subSequence(offset, annotated.length))
        }

    override fun originalToTransformed(offset: Int): Int {
        var removed = 0
        for ((fold, visualStart, label) in segments) {
            if (offset < fold.start) break
            if (offset <= fold.end) return visualStart + if (offset == fold.end) label.length else 0
            removed += fold.end - fold.start - label.length
        }
        return offset - removed
    }

    override fun transformedToOriginal(offset: Int): Int {
        var removed = 0
        for ((fold, visualStart, label) in segments) {
            if (offset < visualStart) break
            if (offset < visualStart + label.length) return fold.start
            removed += fold.end - fold.start - label.length
        }
        return (offset + removed).coerceIn(0, source.length)
    }

    /** Reveals a hidden deletion before accepting a destructive keystroke at a fold boundary. */
    fun hiddenDeletion(newSource: String): Set<String> {
        if (folds.isEmpty() || source == newSource) return emptySet()
        var prefix = 0
        while (prefix < minOf(source.length, newSource.length) && source[prefix] == newSource[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(source.length, newSource.length) - prefix &&
            source[source.lastIndex - suffix] == newSource[newSource.lastIndex - suffix]
        ) {
            suffix++
        }
        val removedEnd = source.length - suffix
        if (removedEnd <= prefix) return emptySet()
        return folds.filter { prefix < it.end && removedEnd > it.start }.map { it.key }.toSet()
    }

    fun visualLine(sourceLine: Int): Int {
        val index = visibleSourceLines.binarySearch(sourceLine)
        return if (index >= 0) index else (-index - 2).coerceAtLeast(0)
    }

    fun sourcePosition(visual: su.kidoz.jetaprog.common.text.TextPosition): su.kidoz.jetaprog.common.text.TextPosition =
        su.kidoz.jetaprog.common.text.TextPosition(
            visibleSourceLines.getOrElse(visual.line) {
                visibleSourceLines.lastOrNull()
                    ?: 0
            },
            visual.column,
        )
}
