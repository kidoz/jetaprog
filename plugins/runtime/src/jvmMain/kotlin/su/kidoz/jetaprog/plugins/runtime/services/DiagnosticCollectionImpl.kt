package su.kidoz.jetaprog.plugins.runtime.services

import su.kidoz.jetaprog.plugins.api.services.DiagnosticCollection
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of DiagnosticCollection for storing diagnostics.
 */
internal class DiagnosticCollectionImpl(
    override val name: String,
) : DiagnosticCollection {
    private val diagnostics = ConcurrentHashMap<String, List<LanguageDiagnostic>>()
    private var disposed = false

    override fun set(
        uri: String,
        diagnostics: List<LanguageDiagnostic>,
    ) {
        check(!disposed) { "DiagnosticCollection has been disposed" }
        this.diagnostics[uri] = diagnostics
    }

    override fun delete(uri: String) {
        check(!disposed) { "DiagnosticCollection has been disposed" }
        diagnostics.remove(uri)
    }

    override fun clear() {
        check(!disposed) { "DiagnosticCollection has been disposed" }
        diagnostics.clear()
    }

    override fun get(uri: String): List<LanguageDiagnostic> = diagnostics[uri] ?: emptyList()

    override fun dispose() {
        disposed = true
        diagnostics.clear()
    }
}
