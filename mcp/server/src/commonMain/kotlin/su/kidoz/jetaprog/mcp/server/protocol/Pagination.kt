package su.kidoz.jetaprog.mcp.server.protocol

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cursor-based pagination support for MCP list operations.
 */
public object Pagination {
    /**
     * Default page size for paginated lists.
     */
    public const val DEFAULT_PAGE_SIZE: Int = 100

    /**
     * Paginates a list of items based on a cursor.
     *
     * @param items The full list of items to paginate
     * @param cursor The cursor from the previous request (null for first page)
     * @param pageSize Maximum number of items per page
     * @return A PaginatedList containing the current page and next cursor
     */
    public fun <T> paginate(
        items: List<T>,
        cursor: String?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): PaginatedList<T> {
        val startIndex = cursor?.let { decodeCursor(it) } ?: 0

        if (startIndex >= items.size) {
            return PaginatedList(
                items = emptyList(),
                nextCursor = null,
            )
        }

        val endIndex = minOf(startIndex + pageSize, items.size)
        val pageItems = items.subList(startIndex, endIndex)

        val nextCursor =
            if (endIndex < items.size) {
                encodeCursor(endIndex)
            } else {
                null
            }

        return PaginatedList(
            items = pageItems,
            nextCursor = nextCursor,
        )
    }

    /**
     * Encodes an index into a cursor string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeCursor(index: Int): String = Base64.encode(index.toString().encodeToByteArray())

    /**
     * Decodes a cursor string into an index.
     *
     * @return The decoded index, or 0 if the cursor is invalid
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeCursor(cursor: String): Int =
        try {
            String(Base64.decode(cursor)).toInt()
        } catch (_: Exception) {
            0
        }
}

/**
 * A paginated list result.
 *
 * @param T The type of items in the list
 */
public data class PaginatedList<T>(
    /**
     * The items in the current page.
     */
    val items: List<T>,
    /**
     * Cursor for the next page, or null if this is the last page.
     */
    val nextCursor: String?,
)
