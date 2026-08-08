package su.kidoz.jetaprog.app.adapter

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.editor.document.TextDocument as EditorTextDocument

/**
 * Immutable [TextDocument] snapshot over file content that is not open in the editor.
 *
 * Used to feed language providers (native or LSP-backed) from services that only know a
 * file path, such as navigation. Position math is delegated to the editor document model.
 */
public class SnapshotTextDocument(
    private val document: EditorTextDocument,
) : TextDocument {
    override val uri: DocumentUri get() = document.uri
    override val fileName: String get() = document.fileName
    override val languageId: LanguageId get() = document.languageId
    override val version: Int get() = document.version
    override val isDirty: Boolean get() = false
    override val isUntitled: Boolean get() = false
    override val lineCount: Int get() = document.lineCount

    override fun getText(): String = document.content

    override fun getText(range: TextRange): String = document.getText(range)

    override fun getLine(lineNumber: Int): String = document.getLine(lineNumber)

    override fun offsetAt(position: TextPosition): Int = document.positionToOffset(position)

    override fun positionAt(offset: Int): TextPosition = document.offsetToPosition(offset)

    override suspend fun save(): Boolean = false

    public companion object {
        /**
         * Creates a snapshot for a file path with already-loaded content.
         */
        public fun of(
            filePath: String,
            languageId: LanguageId,
            content: String,
        ): SnapshotTextDocument =
            SnapshotTextDocument(
                EditorTextDocument(
                    uri = DocumentUri.fromPath(filePath),
                    languageId = languageId,
                    version = 0,
                    content = content,
                    fileName = filePath.substringAfterLast('/'),
                ),
            )
    }
}
