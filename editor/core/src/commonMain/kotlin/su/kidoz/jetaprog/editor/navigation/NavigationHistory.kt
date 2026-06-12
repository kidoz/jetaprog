package su.kidoz.jetaprog.editor.navigation

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Manages navigation history for back/forward navigation.
 *
 * Provides a stack-based history with:
 * - Back stack for previous locations
 * - Forward stack for undone navigations
 * - Recent files tracking
 * - Last edit location tracking
 */
public class NavigationHistory(
    private val maxSize: Int = 100,
) {
    private val backStack = ArrayDeque<NavigationHistoryEntry>()
    private val forwardStack = ArrayDeque<NavigationHistoryEntry>()
    private var lastEditLocation: NavigationHistoryEntry? = null
    private val recentFiles = LinkedHashSet<String>()

    /**
     * Record a navigation to a location.
     * Clears the forward stack as a new navigation path is started.
     */
    public fun record(
        filePath: String,
        position: TextPosition,
        preview: String? = null,
    ) {
        val entry =
            NavigationHistoryEntry(
                filePath = filePath,
                position = position,
                timestamp = currentTimeMillis(),
                preview = preview,
            )

        // Don't record duplicates of the current location
        if (backStack.lastOrNull()?.isSameLocation(entry) == true) {
            return
        }

        backStack.addLast(entry)
        forwardStack.clear()

        // Keep size bounded
        while (backStack.size > maxSize) {
            backStack.removeFirst()
        }

        // Track recent files
        recentFiles.remove(filePath)
        recentFiles.add(filePath)
        while (recentFiles.size > maxSize) {
            recentFiles.remove(recentFiles.first())
        }
    }

    /**
     * Record an edit location.
     */
    public fun recordEdit(
        filePath: String,
        position: TextPosition,
        preview: String? = null,
    ) {
        lastEditLocation =
            NavigationHistoryEntry(
                filePath = filePath,
                position = position,
                timestamp = currentTimeMillis(),
                preview = preview,
            )
    }

    /**
     * Go back in history.
     *
     * @return The previous location, or null if at the start
     */
    public fun goBack(): NavigationHistoryEntry? {
        if (backStack.size <= 1) return null

        val current = backStack.removeLast()
        forwardStack.addFirst(current)

        return backStack.lastOrNull()
    }

    /**
     * Go forward in history.
     *
     * @return The next location, or null if at the end
     */
    public fun goForward(): NavigationHistoryEntry? {
        if (forwardStack.isEmpty()) return null

        val entry = forwardStack.removeFirst()
        backStack.addLast(entry)

        return entry
    }

    /**
     * Check if back navigation is available.
     */
    public fun canGoBack(): Boolean = backStack.size > 1

    /**
     * Check if forward navigation is available.
     */
    public fun canGoForward(): Boolean = forwardStack.isNotEmpty()

    /**
     * Get the current location.
     */
    public fun current(): NavigationHistoryEntry? = backStack.lastOrNull()

    /**
     * Get the last edit location.
     */
    public fun getLastEditLocation(): NavigationHistoryEntry? = lastEditLocation

    /**
     * Get recent locations (most recent first).
     */
    public fun getRecentLocations(limit: Int = 30): List<NavigationHistoryEntry> =
        backStack.toList().takeLast(limit).reversed()

    /**
     * Get recent files (most recent first).
     */
    public fun getRecentFiles(limit: Int = 30): List<String> = recentFiles.toList().takeLast(limit).reversed()

    /**
     * Clear all history.
     */
    public fun clear() {
        backStack.clear()
        forwardStack.clear()
        recentFiles.clear()
        lastEditLocation = null
    }

    private fun NavigationHistoryEntry.isSameLocation(other: NavigationHistoryEntry): Boolean =
        filePath == other.filePath &&
            position.line == other.position.line &&
            position.column == other.position.column

    private fun currentTimeMillis(): Long = System.currentTimeMillis()
}
