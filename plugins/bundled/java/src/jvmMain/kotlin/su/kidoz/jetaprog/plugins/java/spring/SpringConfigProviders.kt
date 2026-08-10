package su.kidoz.jetaprog.plugins.java.spring

import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionItemKind
import su.kidoz.jetaprog.common.completion.CompletionList
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.language.Hover
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.HoverProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument

/**
 * Completes Spring Boot configuration keys in `application.properties` and
 * `application.yml` files, backed by [SpringConfigMetadataIndex].
 */
public class SpringConfigCompletionProvider(
    private val index: SpringConfigMetadataIndex,
    private val classpathJars: suspend () -> List<String>,
) : CompletionProvider {
    override suspend fun provideCompletionItems(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList? {
        index.ensureLoaded(classpathJars())
        return if (SpringConfigDocuments.isPropertiesFile(document)) {
            completeProperties(document, position)
        } else {
            completeYaml(document, position)
        }
    }

    private fun completeProperties(
        document: TextDocument,
        position: TextPosition,
    ): CompletionList? {
        val line = document.getLine(position.line)
        val beforeCursor = line.substring(0, position.column.coerceIn(0, line.length))
        if ('=' in beforeCursor || ':' in beforeCursor) return null
        val prefix = beforeCursor.trimStart()
        if (prefix.startsWith("#") || prefix.startsWith("!")) return null

        val items =
            index.withPrefix(prefix).map { property ->
                CompletionItem(
                    label = property.name,
                    kind = CompletionItemKind.Property,
                    detail = property.type,
                    documentation = property.renderDocumentation(),
                )
            }
        return CompletionList(items)
    }

    private fun completeYaml(
        document: TextDocument,
        position: TextPosition,
    ): CompletionList? {
        val keyContext = SpringConfigDocuments.yamlKeyContext(document, position) ?: return null
        val parentPrefix = if (keyContext.parentPath.isEmpty()) "" else "${keyContext.parentPath}."

        // Collapse matching full keys to their next path segment below the parent,
        // keeping full property details for leaves.
        val segments = LinkedHashMap<String, SpringConfigProperty?>()
        for (property in index.withPrefix(parentPrefix + keyContext.token)) {
            val remainder = property.name.removePrefix(parentPrefix)
            val segment = remainder.substringBefore('.')
            val leaf = property.takeIf { '.' !in remainder }
            if (leaf != null || segment !in segments) {
                segments[segment] = leaf ?: segments[segment]
            }
        }

        val items =
            segments.map { (segment, property) ->
                CompletionItem(
                    label = segment,
                    kind = CompletionItemKind.Property,
                    detail = property?.type,
                    documentation = property?.renderDocumentation(),
                    insertText = "$segment: ",
                )
            }
        return CompletionList(items)
    }
}

/**
 * Shows the documented type, description, and default of a Spring Boot configuration
 * key on hover.
 */
public class SpringConfigHoverProvider(
    private val index: SpringConfigMetadataIndex,
    private val classpathJars: suspend () -> List<String>,
) : HoverProvider {
    override suspend fun provideHover(
        document: TextDocument,
        position: TextPosition,
    ): Hover? {
        index.ensureLoaded(classpathJars())
        val key =
            if (SpringConfigDocuments.isPropertiesFile(document)) {
                SpringConfigDocuments.propertiesKeyAt(document, position.line)
            } else {
                SpringConfigDocuments.yamlKeyAt(document, position.line)
            } ?: return null

        val property = index.find(key) ?: return null
        return Hover(contents = listOf(MarkedString.Markdown(property.renderDocumentation())))
    }
}

private fun SpringConfigProperty.renderDocumentation(): String =
    buildString {
        append("`").append(name).append("`")
        type?.let { append("\n\n").append(it) }
        description?.let { append("\n\n").append(it) }
        defaultValue?.let { append("\n\nDefault: `").append(it).append("`") }
    }
