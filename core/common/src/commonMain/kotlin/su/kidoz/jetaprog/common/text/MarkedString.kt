package su.kidoz.jetaprog.common.text

import kotlinx.serialization.Serializable

/**
 * A marked string (markdown or code) for displaying rich content.
 */
@Serializable
public sealed interface MarkedString {
    /**
     * Plain markdown text.
     */
    @Serializable
    public data class Markdown(
        val value: String,
    ) : MarkedString

    /**
     * A code block with language identifier.
     */
    @Serializable
    public data class Code(
        val language: String,
        val value: String,
    ) : MarkedString
}
