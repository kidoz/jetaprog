package su.kidoz.jetaprog.app.agent

/**
 * The state of a file immediately before an agent write.
 *
 * @property existed Whether the file existed before the write.
 * @property content The prior contents, or null when [existed] is false.
 */
internal data class FileSnapshot(
    val existed: Boolean,
    val content: String?,
)
