package su.kidoz.jetaprog.editor.editing

import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * Per-language comment syntax used by editing operations.
 */
public object CommentSyntax {
    private val lineCommentPrefixes: Map<String, String> =
        mapOf(
            LanguageId.KOTLIN.value to "//",
            LanguageId.JAVA.value to "//",
            LanguageId.RUST.value to "//",
            LanguageId.CPP.value to "//",
            LanguageId.JAVASCRIPT.value to "//",
            LanguageId.TYPESCRIPT.value to "//",
            LanguageId.GO.value to "//",
            LanguageId.VALA.value to "//",
            LanguageId.VAPI.value to "//",
            LanguageId.PYTHON.value to "#",
            LanguageId.YAML.value to "#",
            LanguageId.TOML.value to "#",
            LanguageId.MESON.value to "#",
        )

    /**
     * The line-comment prefix for [languageId], or null when the language has no
     * line comments (e.g. XML, Markdown).
     */
    public fun lineCommentPrefix(languageId: LanguageId): String? = lineCommentPrefixes[languageId.value]
}
