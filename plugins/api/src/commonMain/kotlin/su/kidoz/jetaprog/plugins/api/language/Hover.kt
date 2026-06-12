package su.kidoz.jetaprog.plugins.api.language

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Re-export MarkedString for backward compatibility.
 */
public typealias MarkedString = su.kidoz.jetaprog.common.text.MarkedString

/**
 * Hover information.
 */
@Serializable
public data class Hover(
    /**
     * The contents to display.
     */
    val contents: List<MarkedString>,
    /**
     * The range this hover applies to.
     */
    val range: TextRange? = null,
)

/**
 * A location in a file.
 */
@Serializable
public data class Location(
    /**
     * The file URI.
     */
    val uri: String,
    /**
     * The range in the file.
     */
    val range: TextRange,
)
