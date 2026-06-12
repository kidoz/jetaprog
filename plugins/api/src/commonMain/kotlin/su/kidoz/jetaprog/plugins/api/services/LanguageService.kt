package su.kidoz.jetaprog.plugins.api.services

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.completion.CompletionContext
import su.kidoz.jetaprog.common.completion.CompletionList
import su.kidoz.jetaprog.common.completion.CompletionTriggerKind
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.language.Hover
import su.kidoz.jetaprog.plugins.api.language.Location
import su.kidoz.jetaprog.plugins.api.language.SignatureHelp

/**
 * Re-export completion types for backward compatibility.
 */
public typealias CompletionContext = su.kidoz.jetaprog.common.completion.CompletionContext
public typealias CompletionTriggerKind = su.kidoz.jetaprog.common.completion.CompletionTriggerKind

/**
 * Service for language features.
 */
public interface LanguageService {
    /**
     * Registers a language configuration.
     */
    public fun registerLanguage(config: LanguageConfiguration): Disposable

    /**
     * Registers a completion provider.
     * @param selector Document selector to match
     * @param provider The completion provider
     * @param triggerCharacters Characters that trigger completion
     * @return Disposable to unregister
     */
    public fun registerCompletionProvider(
        selector: DocumentSelector,
        provider: CompletionProvider,
        triggerCharacters: List<Char> = emptyList(),
    ): Disposable

    /**
     * Registers a hover provider.
     */
    public fun registerHoverProvider(
        selector: DocumentSelector,
        provider: HoverProvider,
    ): Disposable

    /**
     * Registers a signature help provider.
     */
    public fun registerSignatureHelpProvider(
        selector: DocumentSelector,
        provider: SignatureHelpProvider,
        triggerCharacters: List<Char> = listOf('(', ','),
        retriggerCharacters: List<Char> = listOf(','),
    ): Disposable

    /**
     * Registers a definition provider.
     */
    public fun registerDefinitionProvider(
        selector: DocumentSelector,
        provider: DefinitionProvider,
    ): Disposable

    /**
     * Registers a references provider.
     */
    public fun registerReferencesProvider(
        selector: DocumentSelector,
        provider: ReferencesProvider,
    ): Disposable

    /**
     * Registers a document formatting provider.
     */
    public fun registerDocumentFormattingProvider(
        selector: DocumentSelector,
        provider: FormattingProvider,
    ): Disposable

    /**
     * Registers a code action provider.
     */
    public fun registerCodeActionProvider(
        selector: DocumentSelector,
        provider: CodeActionProvider,
    ): Disposable

    /**
     * Creates a diagnostic collection.
     * @param name The name of the collection
     * @return The diagnostic collection
     */
    public fun createDiagnosticCollection(name: String): DiagnosticCollection

    /**
     * Starts a language server.
     * @param config The language server configuration
     * @return The language client
     */
    public suspend fun startLanguageServer(config: LanguageServerConfig): LanguageClient
}

/**
 * Language configuration.
 */
public data class LanguageConfiguration(
    val id: LanguageId,
    val extensions: List<String> = emptyList(),
    val filenames: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val firstLine: String? = null,
)

/**
 * Provides code completions.
 */
public fun interface CompletionProvider {
    public suspend fun provideCompletionItems(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList?
}

/**
 * Provides hover information.
 */
public fun interface HoverProvider {
    public suspend fun provideHover(
        document: TextDocument,
        position: TextPosition,
    ): Hover?
}

/**
 * Provides signature help for function/method calls.
 */
public fun interface SignatureHelpProvider {
    public suspend fun provideSignatureHelp(
        document: TextDocument,
        position: TextPosition,
        context: SignatureHelpContext,
    ): SignatureHelp?
}

/**
 * Context for signature help requests.
 */
public data class SignatureHelpContext(
    /**
     * How signature help was triggered.
     */
    val triggerKind: SignatureHelpTriggerKind,
    /**
     * The character that triggered signature help.
     */
    val triggerCharacter: Char? = null,
    /**
     * Whether this is a retrigger.
     */
    val isRetrigger: Boolean = false,
)

/**
 * How signature help was triggered.
 */
public enum class SignatureHelpTriggerKind {
    /**
     * Signature help was invoked manually (e.g., via shortcut).
     */
    Invoked,

    /**
     * Signature help was triggered by a trigger character.
     */
    TriggerCharacter,

    /**
     * Signature help was triggered by content changes.
     */
    ContentChange,
}

/**
 * Provides go-to-definition.
 */
public fun interface DefinitionProvider {
    public suspend fun provideDefinition(
        document: TextDocument,
        position: TextPosition,
    ): List<Location>
}

/**
 * Provides find-references.
 */
public fun interface ReferencesProvider {
    public suspend fun provideReferences(
        document: TextDocument,
        position: TextPosition,
        includeDeclaration: Boolean,
    ): List<Location>
}

/**
 * Provides document formatting.
 */
public fun interface FormattingProvider {
    public suspend fun provideDocumentFormattingEdits(
        document: TextDocument,
        options: FormattingOptions,
    ): List<TextEdit>
}

/**
 * Formatting options.
 */
public data class FormattingOptions(
    val tabSize: Int = 4,
    val insertSpaces: Boolean = true,
    val trimTrailingWhitespace: Boolean = true,
    val insertFinalNewline: Boolean = true,
)

/**
 * A text edit.
 */
public data class TextEdit(
    val range: TextRange,
    val newText: String,
)

/**
 * Provides code actions.
 */
public fun interface CodeActionProvider {
    public suspend fun provideCodeActions(
        document: TextDocument,
        range: TextRange,
        context: CodeActionContext,
    ): List<CodeAction>
}

/**
 * Context for code actions.
 */
public data class CodeActionContext(
    val diagnostics: List<LanguageDiagnostic>,
    val only: List<CodeActionKind>? = null,
)

/**
 * A code action.
 */
public data class CodeAction(
    val title: String,
    val kind: CodeActionKind? = null,
    val diagnostics: List<LanguageDiagnostic> = emptyList(),
    val edit: WorkspaceEdit? = null,
    val command: Command? = null,
    val isPreferred: Boolean = false,
)

/**
 * Kinds of code actions.
 */
public enum class CodeActionKind {
    QuickFix,
    Refactor,
    RefactorExtract,
    RefactorInline,
    RefactorRewrite,
    Source,
    SourceOrganizeImports,
    SourceFixAll,
}

/**
 * A workspace edit.
 */
public data class WorkspaceEdit(
    val changes: Map<String, List<TextEdit>> = emptyMap(),
)

/**
 * A command.
 */
public data class Command(
    val title: String,
    val command: String,
    val arguments: List<Any?> = emptyList(),
)

/**
 * A diagnostic collection for a source.
 */
public interface DiagnosticCollection : Disposable {
    /**
     * The name of this collection.
     */
    public val name: String

    /**
     * Sets diagnostics for a document.
     */
    public fun set(
        uri: String,
        diagnostics: List<LanguageDiagnostic>,
    )

    /**
     * Clears diagnostics for a document.
     */
    public fun delete(uri: String)

    /**
     * Clears all diagnostics.
     */
    public fun clear()

    /**
     * Gets diagnostics for a document.
     */
    public fun get(uri: String): List<LanguageDiagnostic>
}

/**
 * A diagnostic message.
 */
public data class LanguageDiagnostic(
    val range: TextRange,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val code: String? = null,
    val source: String? = null,
)

/**
 * Configuration for a language server.
 */
public data class LanguageServerConfig(
    val name: String,
    val command: List<String>,
    val args: List<String> = emptyList(),
    val documentSelector: DocumentSelector,
    val initializationOptions: Map<String, Any?> = emptyMap(),
    val workingDirectory: String? = null,
)

/**
 * Client for communicating with a language server.
 */
public interface LanguageClient {
    /**
     * Whether the server is running.
     */
    public val isRunning: Boolean

    /**
     * Sends a request to the language server.
     */
    public suspend fun <T> sendRequest(
        method: String,
        params: Any?,
    ): T

    /**
     * Sends a notification to the language server.
     */
    public suspend fun sendNotification(
        method: String,
        params: Any?,
    )

    /**
     * Stops the language server.
     */
    public suspend fun stop()
}
