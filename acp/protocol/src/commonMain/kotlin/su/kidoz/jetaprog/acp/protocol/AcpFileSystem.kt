package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable

/**
 * Parameters of the `fs/read_text_file` request.
 */
@Serializable
public data class ReadTextFileRequest(
    /** The session on whose behalf the read is performed. */
    val sessionId: String,
    /** The absolute path of the file to read. */
    val path: String,
    /** Optional 1-based line to start reading from. */
    val line: Int? = null,
    /** Optional maximum number of lines to return. */
    val limit: Int? = null,
)

/**
 * Result of the `fs/read_text_file` request.
 */
@Serializable
public data class ReadTextFileResponse(
    /** The requested file contents. */
    val content: String,
)

/**
 * Parameters of the `fs/write_text_file` request.
 */
@Serializable
public data class WriteTextFileRequest(
    /** The session on whose behalf the write is performed. */
    val sessionId: String,
    /** The absolute path of the file to write. */
    val path: String,
    /** The full contents to write. */
    val content: String,
)
