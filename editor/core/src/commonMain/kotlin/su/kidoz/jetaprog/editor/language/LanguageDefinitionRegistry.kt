package su.kidoz.jetaprog.editor.language

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * Registry holding the [LanguageDefinition] for every known language.
 *
 * This is the single source of truth for language identity: file-type detection, lexer
 * selection, and alias lookup all resolve here. Built-in definitions are registered at
 * class-load time; plugins extend or add definitions through the language service.
 *
 * Registering a definition for an already-known language merges it with the existing one
 * (union of extensions, filenames, and aliases). Detection is first-registration-wins per
 * extension or filename, so built-in associations stay stable when plugins add overlapping
 * definitions.
 */
public object LanguageDefinitionRegistry {
    private val definitions = LinkedHashMap<String, LanguageDefinition>()
    private var filenameIndex: Map<String, LanguageId> = emptyMap()
    private var extensionIndex: Map<String, LanguageId> = emptyMap()

    init {
        BuiltinLanguageDefinitions.all.forEach { merge(it) }
        rebuildIndexes()
    }

    /**
     * Registers a definition, merging it with any existing definition for the same language.
     *
     * @return a disposable that restores the previous definition (or removes the language
     *   if it was unknown before).
     */
    public fun register(definition: LanguageDefinition): Disposable {
        val key = definition.id.value
        val previous = definitions[key]
        merge(definition)
        rebuildIndexes()
        return Disposable {
            if (previous != null) {
                definitions[key] = previous
            } else {
                definitions.remove(key)
            }
            rebuildIndexes()
        }
    }

    /**
     * Returns the definition for a language, or null if the language is unknown.
     */
    public fun find(id: LanguageId): LanguageDefinition? = definitions[id.value]

    /**
     * Returns all registered definitions in registration order.
     */
    public fun all(): List<LanguageDefinition> = definitions.values.toList()

    /**
     * Detects the language of a file from its name.
     *
     * Exact filename matches (e.g. `meson.build`) take precedence over extension matches.
     * Returns null when no definition claims the file.
     */
    public fun detect(fileName: String): LanguageId? {
        val lower = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        filenameIndex[lower]?.let { return it }
        val extension = lower.substringAfterLast('.', "")
        if (extension.isEmpty()) return null
        return extensionIndex[extension]
    }

    /**
     * Detects a language from a bare file extension (with or without a leading dot).
     */
    public fun detectByExtension(extension: String): LanguageId? = extensionIndex[normalizeExtension(extension)]

    /**
     * Returns the lexer identifier for a language, or null when it has no lexer.
     */
    public fun lexerIdFor(id: LanguageId): String? = definitions[id.value]?.lexerId

    private fun merge(definition: LanguageDefinition) {
        val key = definition.id.value
        val normalized = definition.normalized()
        val existing = definitions[key]
        definitions[key] =
            if (existing == null) {
                normalized
            } else {
                existing.copy(
                    extensions = (existing.extensions + normalized.extensions).distinct(),
                    filenames = (existing.filenames + normalized.filenames).distinct(),
                    aliases = (existing.aliases + normalized.aliases).distinct(),
                    firstLine = existing.firstLine ?: normalized.firstLine,
                    lexerId = existing.lexerId ?: normalized.lexerId,
                )
            }
    }

    private fun rebuildIndexes() {
        val filenames = mutableMapOf<String, LanguageId>()
        val extensions = mutableMapOf<String, LanguageId>()
        for (definition in definitions.values) {
            for (filename in definition.filenames) {
                filenames.getOrPut(filename) { definition.id }
            }
            for (extension in definition.extensions) {
                extensions.getOrPut(extension) { definition.id }
            }
        }
        filenameIndex = filenames
        extensionIndex = extensions
    }

    private fun LanguageDefinition.normalized(): LanguageDefinition =
        copy(
            extensions = extensions.map(::normalizeExtension).filter { it.isNotEmpty() },
            filenames = filenames.map { it.lowercase() }.filter { it.isNotEmpty() },
        )

    private fun normalizeExtension(extension: String): String = extension.removePrefix(".").lowercase()
}
