package su.kidoz.jetaprog.editor.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the persisted part of [NavigationHistory]: the recent-files
 * list snapshot and restore round-trip.
 */
class NavigationHistoryTest {
    @Test
    fun restoreReplacesRecentFilesInMostRecentFirstOrder() {
        val history = NavigationHistory()
        history.restoreRecentFiles(listOf("c.kt", "b.kt", "a.kt"))

        assertEquals(listOf("c.kt", "b.kt", "a.kt"), history.getRecentFiles())
    }

    @Test
    fun restoreThenRecordDeduplicatesAndPrepends() {
        val history = NavigationHistory()
        history.restoreRecentFiles(listOf("b.kt", "a.kt"))
        history.record(
            "b.kt",
            su.kidoz.jetaprog.common.text
                .TextPosition(0, 0),
        )
        history.record(
            "new.kt",
            su.kidoz.jetaprog.common.text
                .TextPosition(0, 0),
        )

        assertEquals(listOf("new.kt", "b.kt", "a.kt"), history.getRecentFiles())
    }

    @Test
    fun restoreEmptyClearsTheList() {
        val history = NavigationHistory()
        history.record(
            "a.kt",
            su.kidoz.jetaprog.common.text
                .TextPosition(0, 0),
        )
        history.restoreRecentFiles(emptyList())

        assertTrue(history.getRecentFiles().isEmpty())
    }
}
