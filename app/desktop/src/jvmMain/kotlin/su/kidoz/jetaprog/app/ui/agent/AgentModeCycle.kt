package su.kidoz.jetaprog.app.ui.agent

/**
 * The mode after [currentId] in [modes], wrapping past the last entry.
 *
 * Returns null when the agent advertised fewer than two modes. An id that is
 * not in [modes] starts at the first entry. The conversation is not involved.
 */
internal fun nextCycledModeId(
    currentId: String?,
    modes: List<ModeOption>,
): String? {
    if (modes.size < 2) return null
    val index = modes.indexOfFirst { it.id == currentId }
    val nextIndex = if (index < 0) 0 else (index + 1) % modes.size
    val nextId = modes[nextIndex].id
    return if (nextId == currentId) null else nextId
}
