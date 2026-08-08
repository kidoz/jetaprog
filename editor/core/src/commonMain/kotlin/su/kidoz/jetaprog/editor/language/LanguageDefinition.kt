package su.kidoz.jetaprog.editor.language

import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * Declarative identity of a language: how its files are recognized and which lexer renders them.
 *
 * A definition is the single source of truth for file-type association. It is registered in
 * [LanguageDefinitionRegistry] either as a built-in or by a plugin through the language service.
 */
public data class LanguageDefinition(
    /** The language this definition describes. */
    val id: LanguageId,
    /** File extensions for this language, with or without a leading dot (case-insensitive). */
    val extensions: List<String> = emptyList(),
    /** Exact file names for this language, e.g. `meson.build` (case-insensitive). */
    val filenames: List<String> = emptyList(),
    /** Human-readable aliases, e.g. `Golang`. */
    val aliases: List<String> = emptyList(),
    /** Optional regular expression matched against the first line of a file. */
    val firstLine: String? = null,
    /** Identifier of the lexer in `LexerRegistry` used for syntax highlighting, if any. */
    val lexerId: String? = null,
)
