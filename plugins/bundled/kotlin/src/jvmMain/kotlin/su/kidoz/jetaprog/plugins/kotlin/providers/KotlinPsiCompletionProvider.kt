package su.kidoz.jetaprog.plugins.kotlin.providers

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
import su.kidoz.jetaprog.plugins.api.language.CompletionItemKind
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinPsiAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSymbolKind

/**
 * Parser-backed completion provider (Phase 1).
 *
 * Offers in-file declarations (parsed from PSI) and Kotlin keywords matching the
 * identifier prefix at the cursor. Unlike the symbol-index provider it reflects
 * the live, unsaved buffer; classpath- and scope-aware completion is layered on
 * later via the Kotlin Analysis API.
 */
public class KotlinPsiCompletionProvider(
    private val analyzer: KotlinPsiAnalyzer,
) : CompletionProvider {
    override suspend fun provideCompletionItems(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList {
        val text = document.getText()
        val prefix = extractPrefix(text, position)
        if (prefix.isEmpty()) return CompletionList(items = emptyList(), isIncomplete = false)

        val declarationItems =
            analyzer
                .declarations(text)
                .filter { it.name.startsWith(prefix) }
                .map { declaration ->
                    CompletionItem(
                        label = declaration.name,
                        kind = declaration.kind.toCompletionKind(),
                        insertText = declaration.name,
                    )
                }

        val keywordItems =
            KOTLIN_KEYWORDS
                .filter { it.startsWith(prefix) }
                .map { keyword ->
                    CompletionItem(label = keyword, kind = CompletionItemKind.Keyword, insertText = keyword)
                }

        val items = (declarationItems + keywordItems).distinctBy { it.label to it.kind }
        return CompletionList(items = items, isIncomplete = false)
    }

    private fun extractPrefix(
        text: String,
        position: TextPosition,
    ): String {
        val lines = text.lines()
        val line = lines.getOrNull(position.line) ?: return ""
        var column = (position.column - 1).coerceAtMost(line.length - 1)
        val builder = StringBuilder()
        while (column >= 0 && (line[column].isLetterOrDigit() || line[column] == '_')) {
            builder.insert(0, line[column])
            column--
        }
        return builder.toString()
    }

    private fun KotlinSymbolKind.toCompletionKind(): CompletionItemKind =
        when (this) {
            KotlinSymbolKind.FUNCTION -> CompletionItemKind.Function
            KotlinSymbolKind.CLASS -> CompletionItemKind.Class
            KotlinSymbolKind.INTERFACE -> CompletionItemKind.Interface
            KotlinSymbolKind.OBJECT -> CompletionItemKind.Class
            KotlinSymbolKind.PROPERTY -> CompletionItemKind.Property
            KotlinSymbolKind.PARAMETER -> CompletionItemKind.Variable
            KotlinSymbolKind.OTHER -> CompletionItemKind.Text
        }

    private companion object {
        private val KOTLIN_KEYWORDS =
            listOf(
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while",
                "by",
                "catch",
                "constructor",
                "delegate",
                "dynamic",
                "field",
                "file",
                "finally",
                "get",
                "import",
                "init",
                "param",
                "property",
                "receiver",
                "set",
                "setparam",
                "value",
                "where",
                "abstract",
                "actual",
                "annotation",
                "companion",
                "const",
                "crossinline",
                "data",
                "enum",
                "expect",
                "external",
                "final",
                "infix",
                "inline",
                "inner",
                "internal",
                "lateinit",
                "noinline",
                "open",
                "operator",
                "out",
                "override",
                "private",
                "protected",
                "public",
                "reified",
                "sealed",
                "suspend",
                "tailrec",
                "vararg",
            )
    }
}
