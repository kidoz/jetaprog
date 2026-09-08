package su.kidoz.jetaprog.plugins.support

/**
 * Receives the document lifecycle the editor reports, in step with the external
 * language servers. In-process consumers such as embedded servers subscribe through
 * [LanguageRegistry.addDocumentSyncListener] so they see unsaved edits.
 */
public interface DocumentSyncListener {
    /** A document was opened with [content]. */
    public suspend fun documentOpened(
        uri: String,
        languageId: String,
        content: String,
    )

    /** The full text of an open document is now [content]. */
    public suspend fun documentChanged(
        uri: String,
        languageId: String,
        content: String,
    )

    /** An open document was written to disk; [content] is the saved text when known. */
    public suspend fun documentSaved(
        uri: String,
        languageId: String,
        content: String?,
    )

    /** An open document was closed. */
    public suspend fun documentClosed(
        uri: String,
        languageId: String,
    )
}
