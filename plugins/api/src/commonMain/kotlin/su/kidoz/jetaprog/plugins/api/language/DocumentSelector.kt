package su.kidoz.jetaprog.plugins.api.language

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * A selector for matching documents.
 */
@Serializable
public data class DocumentSelector(
    /**
     * The language IDs to match.
     */
    val languages: List<LanguageId> = emptyList(),
    /**
     * Glob pattern to match file paths.
     */
    val pattern: String? = null,
    /**
     * Scheme to match (e.g., "file", "untitled").
     */
    val scheme: String? = null,
) {
    /**
     * Checks if this selector matches a document.
     *
     * Patterns follow the usual glob syntax: `*` matches within a path segment, `**` matches
     * across segments, `?` matches a single character, and `{a,b}` matches alternatives.
     * A pattern without a path separator is matched against the file name only.
     */
    public fun matches(
        languageId: LanguageId,
        uri: String,
    ): Boolean {
        if (languages.isNotEmpty() && languageId !in languages) return false
        if (scheme != null && !uri.startsWith("$scheme:")) return false
        val glob = pattern ?: return true
        val path =
            when {
                "://" in uri -> uri.substringAfter("://")
                ':' in uri -> uri.substringAfter(':')
                else -> uri
            }
        val target = if ('/' in glob) path.removePrefix("/") else path.substringAfterLast('/')
        return GlobPattern(glob).matches(target)
    }

    public companion object {
        /**
         * Creates a selector for a single language.
         */
        public fun forLanguage(languageId: LanguageId): DocumentSelector =
            DocumentSelector(languages = listOf(languageId))

        /**
         * Creates a selector for multiple languages.
         */
        public fun forLanguages(languageIds: List<LanguageId>): DocumentSelector =
            DocumentSelector(languages = languageIds)

        /**
         * Creates a selector for a glob pattern.
         */
        public fun forPattern(pattern: String): DocumentSelector = DocumentSelector(pattern = pattern)

        /**
         * Creates a selector that matches all documents.
         */
        public fun all(): DocumentSelector = DocumentSelector()
    }
}
