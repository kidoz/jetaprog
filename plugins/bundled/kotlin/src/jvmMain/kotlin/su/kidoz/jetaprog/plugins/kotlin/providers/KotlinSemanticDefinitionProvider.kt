package su.kidoz.jetaprog.plugins.kotlin.providers

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.plugins.api.language.Location
import su.kidoz.jetaprog.plugins.api.services.DefinitionProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer

/**
 * Resolution-based go-to-definition (Phase 3).
 *
 * Resolves the reference under the cursor against the classpath and navigates to
 * the target declaration when its source is in the current file. Returns nothing
 * for references into compiled libraries or other files, leaving those to the
 * symbol-index navigation provider.
 */
public class KotlinSemanticDefinitionProvider(
    private val analyzer: KotlinSemanticAnalyzer,
) : DefinitionProvider {
    override suspend fun provideDefinition(
        document: TextDocument,
        position: TextPosition,
    ): List<Location> {
        if (!analyzer.isReady()) return emptyList()
        val text = document.getText()
        val offset = position.toOffset(text)
        val location = analyzer.definition(text, offset) ?: return emptyList()
        return listOf(
            Location(
                uri = document.uri.value,
                range =
                    TextRange(
                        start = text.offsetToPosition(location.startOffset),
                        end = text.offsetToPosition(location.endOffset),
                    ),
            ),
        )
    }

    private fun TextPosition.toOffset(text: String): Int {
        val lines = text.lines()
        if (line >= lines.size) return text.length
        val before = lines.take(line).sumOf { it.length + 1 }
        return (before + column).coerceIn(0, text.length)
    }

    private fun String.offsetToPosition(offset: Int): TextPosition {
        val safe = offset.coerceIn(0, length)
        val prefix = substring(0, safe)
        val line = prefix.count { it == '\n' }
        val column = safe - (prefix.lastIndexOf('\n') + 1)
        return TextPosition(line, column)
    }
}
