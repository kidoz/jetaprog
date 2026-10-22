package su.kidoz.jetaprog.app.ui.agent

/** File name read from the workspace root at the start of a turn. */
internal const val AGENTS_FILE: String = "AGENTS.md"

/** Longest instruction text copied into one prompt. */
internal const val AGENTS_MAX_CHARS: Int = 32_000

/**
 * The project instructions to send, or null when [raw] is blank.
 *
 * Text past [AGENTS_MAX_CHARS] is cut. `@import` and `@include` are left as written.
 */
internal fun instructionText(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.length <= AGENTS_MAX_CHARS) return trimmed
    return trimmed.take(AGENTS_MAX_CHARS)
}

/**
 * Puts [instructions] ahead of the user prompt.
 *
 * A missing file leaves [prompt] unchanged.
 */
internal fun promptWithInstructions(
    instructions: String?,
    prompt: String,
): String {
    if (instructions.isNullOrBlank()) return prompt
    return "Project instructions ($AGENTS_FILE):\n$instructions\n\n$prompt"
}

/**
 * Chip label for the root instructions, or null when they are absent or already attached.
 */
internal fun instructionChip(
    attachments: List<ComposerAttachment>,
    included: Boolean,
): String? {
    if (!included) return null
    val attached =
        attachments.any { attachment ->
            val path = attachment.path.replace('\\', '/')
            path == AGENTS_FILE || path.endsWith("/$AGENTS_FILE")
        }
    return if (attached) null else AGENTS_FILE
}
