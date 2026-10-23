package su.kidoz.jetaprog.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** File decisions retain provenance across repeated edits and never consume another write's snapshot. */
class AgentFileReviewTest {
    @Test
    fun repeatedWritesRevertToTheFirstPendingBaseline() {
        val review = AgentFileReview()
        review.record("file", file("original"), file("first"))
        review.record("file", file("first"), file("second"))
        assertEquals(file("original"), review.revertTarget("file", file("second")))
        assertNull(review.revertTarget("file", file("human edit")))
        assertEquals("original" to "second", review.netContents()["file"])
    }

    @Test
    fun keepingThenEditingRetainsKeptChangesOnRevert() {
        val review = AgentFileReview()
        review.record("file", file("original"), file("kept"))
        review.keep("file")
        review.record("file", file("kept"), file("pending"))
        assertEquals(file("kept"), review.revertTarget("file", file("pending")))
        review.reverted("file", file("kept"))
        assertEquals("original" to "kept", review.netContents()["file"])
    }

    @Test
    fun interveningHumanEditsBlockAnAggregateRevert() {
        val review = AgentFileReview()
        review.record("file", file("original"), file("agent"))
        review.record("file", file("human"), file("new agent"))
        assertNull(review.revertTarget("file", file("new agent")))
        assertNull(review.netContents()["file"])
        review.keep("file")
        review.record("file", file("another human edit"), file("pending"))
        assertEquals(file("another human edit"), review.revertTarget("file", file("pending")))
    }

    @Test
    fun creationAndDeletionRetainExistence() {
        val missing = FileSnapshot(false, null)
        val review = AgentFileReview()
        review.record("new", missing, file("created"))
        assertEquals(missing, review.revertTarget("new", file("created")))
        review.record("deleted", file("old"), missing)
        assertEquals(file("old"), review.revertTarget("deleted", missing))
        assertNull(review.revertTarget("deleted", file("human recreated")))
    }

    private fun file(content: String): FileSnapshot = FileSnapshot(true, content)
}
