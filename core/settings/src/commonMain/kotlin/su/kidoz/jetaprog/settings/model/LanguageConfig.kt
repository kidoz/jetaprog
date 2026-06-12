package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Defines the preferred source for code completions.
 */
@Serializable
public enum class CompletionProviderPreference {
    /** Use only native, in-process completion providers ("IntelliJ-style"). */
    Native,

    /** Use only Language Server Protocol (LSP) providers. */
    Lsp,

    /** Merge results from both Native and LSP providers. */
    Hybrid,
}

/**
 * Configuration for a specific programming language.
 */
@Serializable
public data class LanguageConfig(
    /**
     * Whether this language is enabled.
     */
    val enabled: Boolean = true,
    /**
     * The preferred source for code completions.
     */
    val completionPreference: CompletionProviderPreference = CompletionProviderPreference.Hybrid,
    /**
     * Tab size override for this language (null = use default).
     */
    val tabSize: Int? = null,
    /**
     * Use tabs instead of spaces for this language (null = use default).
     */
    val useTabs: Boolean? = null,
    /**
     * Auto-format on save.
     */
    val formatOnSave: Boolean = false,
    /**
     * Auto-format on paste.
     */
    val formatOnPaste: Boolean = false,
    /**
     * Language server ID to use for this language (null = none).
     */
    val languageServerId: String? = null,
    /**
     * Additional language-specific settings.
     */
    val customSettings: Map<String, String> = emptyMap(),
)

/**
 * Configuration for a Language Server Protocol server.
 */
@Serializable
public data class LanguageServerConfig(
    /**
     * Whether this language server is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Command to start the language server.
     */
    val command: String,
    /**
     * Command-line arguments.
     */
    val args: List<String> = emptyList(),
    /**
     * Environment variables.
     */
    val env: Map<String, String> = emptyMap(),
    /**
     * Working directory for the server.
     */
    val workingDirectory: String? = null,
    /**
     * File patterns to detect project root.
     */
    val rootPatterns: List<String> = emptyList(),
    /**
     * Language IDs this server handles.
     */
    val languages: List<String> = emptyList(),
    /**
     * LSP initialization options.
     */
    val initializationOptions: Map<String, String> = emptyMap(),
)

/**
 * Default language settings applied when no language-specific config exists.
 */
@Serializable
public data class LanguageDefaults(
    /**
     * Default file encoding.
     */
    val encoding: String = "UTF-8",
    /**
     * Default line ending style.
     */
    val lineEndings: LineEnding = LineEnding.LF,
    /**
     * Default tab size.
     */
    val tabSize: Int = 4,
    /**
     * Use spaces by default.
     */
    val insertSpaces: Boolean = true,
)

/**
 * Line ending styles.
 */
@Serializable
public enum class LineEnding(
    public val value: String,
) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/**
 * Container for all language-related settings.
 */
@Serializable
public data class LanguagesSettings(
    /**
     * Default settings for all languages.
     */
    val defaults: LanguageDefaults = LanguageDefaults(),
    /**
     * Per-language configurations keyed by language ID.
     */
    val languages: Map<String, LanguageConfig> = emptyMap(),
    /**
     * Language server configurations keyed by server ID.
     */
    val languageServers: Map<String, LanguageServerConfig> = emptyMap(),
) {
    public companion object {
        public val DEFAULT: LanguagesSettings = LanguagesSettings()
    }
}
