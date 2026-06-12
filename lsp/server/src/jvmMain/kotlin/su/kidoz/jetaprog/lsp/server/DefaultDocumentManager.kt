package su.kidoz.jetaprog.lsp.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Default thread-safe implementation of DocumentManager.
 *
 * Uses StateFlow for reactive document state updates.
 */
public class DefaultDocumentManager : DocumentManager {
    private val _documents = MutableStateFlow<Map<String, OpenDocument>>(emptyMap())

    override val documents: StateFlow<Map<String, OpenDocument>> = _documents.asStateFlow()

    override fun open(
        uri: String,
        languageId: String,
        version: Int,
        text: String,
    ) {
        val document =
            OpenDocument(
                uri = uri,
                languageId = languageId,
                version = version,
                text = text,
            )
        _documents.update { current ->
            current + (uri to document)
        }
    }

    override fun update(
        uri: String,
        version: Int,
        text: String,
    ) {
        _documents.update { current ->
            val existing = current[uri] ?: return@update current
            current + (uri to existing.copy(version = version, text = text))
        }
    }

    override fun close(uri: String) {
        _documents.update { current ->
            current - uri
        }
    }

    override fun get(uri: String): OpenDocument? = _documents.value[uri]

    override fun getByLanguage(languageId: String): List<OpenDocument> =
        _documents.value.values.filter { it.languageId == languageId }
}
