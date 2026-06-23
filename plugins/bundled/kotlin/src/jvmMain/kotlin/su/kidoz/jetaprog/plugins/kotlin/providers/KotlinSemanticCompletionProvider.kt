package su.kidoz.jetaprog.plugins.kotlin.providers

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
import su.kidoz.jetaprog.plugins.api.language.CompletionItemKind
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSymbolKind

/**
 * Classpath-aware member completion (Phase 2).
 *
 * When the cursor follows a member access (`receiver.prefix`), resolves the
 * receiver's type against the project classpath and offers its members. Returns
 * nothing in other positions, leaving keyword/in-file completion to the
 * parser-backed provider.
 */
public class KotlinSemanticCompletionProvider(
    private val analyzer: KotlinSemanticAnalyzer,
) : CompletionProvider {
    override suspend fun provideCompletionItems(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList {
        if (!analyzer.isReady()) return EMPTY

        val text = document.getText()
        val offset = position.toOffset(text)
        if (offset <= 0) return EMPTY

        val (memberAccess, prefix) = memberAccessAndPrefix(text, offset) ?: return EMPTY
        if (!memberAccess) return EMPTY

        val items =
            analyzer
                .memberCompletions(text, offset)
                .filter { prefix.isEmpty() || it.name.startsWith(prefix) }
                .map { declaration ->
                    CompletionItem(
                        label = declaration.name,
                        kind = declaration.kind.toCompletionKind(),
                        insertText = declaration.name,
                    )
                }
        return CompletionList(items = items, isIncomplete = false)
    }

    private fun TextPosition.toOffset(text: String): Int {
        val lines = text.lines()
        if (line >= lines.size) return text.length
        val before = lines.take(line).sumOf { it.length + 1 }
        return (before + column).coerceIn(0, text.length)
    }

    /**
     * Returns whether the position is a member access and the identifier prefix
     * already typed after the dot, or null if the position cannot be inspected.
     */
    private fun memberAccessAndPrefix(
        text: String,
        offset: Int,
    ): Pair<Boolean, String>? {
        var cursor = offset
        val prefix = StringBuilder()
        while (cursor > 0 && (text[cursor - 1].isLetterOrDigit() || text[cursor - 1] == '_')) {
            prefix.insert(0, text[cursor - 1])
            cursor--
        }
        if (cursor <= 0) return false to prefix.toString()
        return (text[cursor - 1] == '.') to prefix.toString()
    }

    private fun KotlinSymbolKind.toCompletionKind(): CompletionItemKind =
        when (this) {
            KotlinSymbolKind.FUNCTION -> CompletionItemKind.Method
            KotlinSymbolKind.PROPERTY -> CompletionItemKind.Property
            KotlinSymbolKind.CLASS -> CompletionItemKind.Class
            KotlinSymbolKind.INTERFACE -> CompletionItemKind.Interface
            KotlinSymbolKind.OBJECT -> CompletionItemKind.Class
            KotlinSymbolKind.PARAMETER -> CompletionItemKind.Variable
            KotlinSymbolKind.OTHER -> CompletionItemKind.Text
        }

    private companion object {
        private val EMPTY = CompletionList(items = emptyList(), isIncomplete = false)
    }
}
