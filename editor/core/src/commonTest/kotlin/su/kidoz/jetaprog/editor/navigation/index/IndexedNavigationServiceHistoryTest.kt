package su.kidoz.jetaprog.editor.navigation.index

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationHistory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The outermost navigation service is the one the UI talks to, so persisted recent
 * files must land in its history. They used to be seeded into an inner layer's
 * separate history and were never shown.
 */
class IndexedNavigationServiceHistoryTest {
    @Test
    fun seededRecentFilesAreServedBack() =
        runTest {
            val service = IndexedNavigationService(symbolIndex = InMemorySymbolIndex(), history = NavigationHistory())

            service.seedRecentFiles(listOf("/w/Recent.kt", "/w/Older.kt"))

            assertEquals(listOf("/w/Recent.kt", "/w/Older.kt"), service.getRecentFiles(limit = 10).map { it.filePath })
        }

    @Test
    fun aVisitMovesTheFileToTheFrontOfRecents() =
        runTest {
            val service = IndexedNavigationService(symbolIndex = InMemorySymbolIndex(), history = NavigationHistory())
            service.seedRecentFiles(listOf("/w/Recent.kt", "/w/Older.kt"))

            service.recordNavigation("/w/Older.kt", TextPosition(4, 0), preview = null)

            assertEquals(listOf("/w/Older.kt", "/w/Recent.kt"), service.getRecentFiles(limit = 10).map { it.filePath })
            assertEquals("/w/Older.kt", service.goBack()?.filePath ?: service.getRecentLocations(1).single().filePath)
        }
}
