package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.ui.text.AnnotatedString
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.folding.DependencyFolding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldProjectionTest {
    private val source = "package demo\n\nimport a.B\nimport c.D\n\nfun main() {}\n"
    private val folds = DependencyFolding.detect(source, LanguageId.KOTLIN)
    private val projection = FoldProjection(source, folds)

    @Test
    fun projectionCompactsLinesAndRetainsUnfoldedSourceOffsets() {
        assertEquals("package demo\n\nimports … (2)\n\nfun main() {}\n", projection.text)
        assertEquals(listOf(0, 1, 2, 4, 5, 6), projection.visibleSourceLines)
        for (offset in 0..source.length) {
            if (folds.none { offset in it.start until it.end }) {
                assertEquals(offset, projection.transformedToOriginal(projection.originalToTransformed(offset)))
            }
        }
        val original = (0..source.length).map(projection::originalToTransformed)
        val transformed = (0..projection.text.length).map(projection::transformedToOriginal)
        assertTrue(original.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(transformed.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun foldingNeverChangesSourceAndAnnotationsOutsideBlocksSurvive() {
        val annotated = AnnotatedString(source)
        assertEquals(source, projection.source)
        assertEquals(projection.text, projection.project(annotated).text)
        assertEquals(source, FoldProjection(source, emptyList()).text)
    }

    @Test
    fun hiddenDeletionIsDetectedButTypingBelowFoldIsNotBlocked() {
        val fold = folds.single()
        assertEquals(setOf(fold.key), projection.hiddenDeletion(source.removeRange(fold.end - 1, fold.end)))
        assertTrue(projection.hiddenDeletion(source + "x").isEmpty())
        assertTrue(projection.hiddenDeletion(source.replace("main", "start")).isEmpty())
    }
}
