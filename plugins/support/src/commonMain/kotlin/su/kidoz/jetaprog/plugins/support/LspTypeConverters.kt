package su.kidoz.jetaprog.plugins.support

import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.lsp.protocol.LspCodeAction
import su.kidoz.jetaprog.lsp.protocol.LspCompletionItem
import su.kidoz.jetaprog.lsp.protocol.LspCompletionItemKind
import su.kidoz.jetaprog.lsp.protocol.LspCompletionList
import su.kidoz.jetaprog.lsp.protocol.LspDiagnostic
import su.kidoz.jetaprog.lsp.protocol.LspDiagnosticSeverity
import su.kidoz.jetaprog.lsp.protocol.LspHover
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspParameterInformation
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspRange
import su.kidoz.jetaprog.lsp.protocol.LspSignatureHelp
import su.kidoz.jetaprog.lsp.protocol.LspSignatureInformation
import su.kidoz.jetaprog.lsp.protocol.LspSymbolKind
import su.kidoz.jetaprog.lsp.protocol.LspTextEdit
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
import su.kidoz.jetaprog.plugins.api.language.CompletionItemKind
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.language.Hover
import su.kidoz.jetaprog.plugins.api.language.Location
import su.kidoz.jetaprog.plugins.api.language.ParameterInformation
import su.kidoz.jetaprog.plugins.api.language.SignatureHelp
import su.kidoz.jetaprog.plugins.api.language.SignatureInformation
import su.kidoz.jetaprog.plugins.api.language.TextEditData
import su.kidoz.jetaprog.plugins.api.services.CodeAction
import su.kidoz.jetaprog.plugins.api.services.CodeActionKind
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.plugins.api.services.WorkspaceEdit

/**
 * Convert LSP Position to JetaProg TextPosition.
 */
public fun LspPosition.toTextPosition(): TextPosition = TextPosition(line + 1, character + 1)

/**
 * Convert JetaProg TextPosition to LSP Position.
 */
public fun TextPosition.toLspPosition(): LspPosition = LspPosition(line - 1, column - 1)

/**
 * Convert LSP Range to JetaProg TextRange.
 */
public fun LspRange.toTextRange(): TextRange =
    TextRange(
        start.toTextPosition(),
        end.toTextPosition(),
    )

/**
 * Convert JetaProg TextRange to LSP Range.
 */
public fun TextRange.toLspRange(): LspRange =
    LspRange(
        start.toLspPosition(),
        end.toLspPosition(),
    )

/**
 * Convert LSP Location to JetaProg Location.
 */
public fun LspLocation.toLocation(): Location =
    Location(
        uri = uri,
        range = range.toTextRange(),
    )

/**
 * Convert JetaProg Location to LSP Location.
 */
public fun Location.toLspLocation(): LspLocation =
    LspLocation(
        uri = uri,
        range = range.toLspRange(),
    )

/**
 * Convert LSP Completion Item Kind to JetaProg CompletionItemKind.
 */
public fun LspCompletionItemKind.toCompletionItemKind(): CompletionItemKind =
    when (this) {
        LspCompletionItemKind.Text -> CompletionItemKind.Text
        LspCompletionItemKind.Method -> CompletionItemKind.Method
        LspCompletionItemKind.Function -> CompletionItemKind.Function
        LspCompletionItemKind.Constructor -> CompletionItemKind.Constructor
        LspCompletionItemKind.Field -> CompletionItemKind.Field
        LspCompletionItemKind.Variable -> CompletionItemKind.Variable
        LspCompletionItemKind.Class -> CompletionItemKind.Class
        LspCompletionItemKind.Interface -> CompletionItemKind.Interface
        LspCompletionItemKind.Module -> CompletionItemKind.Module
        LspCompletionItemKind.Property -> CompletionItemKind.Property
        LspCompletionItemKind.Unit -> CompletionItemKind.Unit
        LspCompletionItemKind.Value -> CompletionItemKind.Value
        LspCompletionItemKind.Enum -> CompletionItemKind.Enum
        LspCompletionItemKind.Keyword -> CompletionItemKind.Keyword
        LspCompletionItemKind.Snippet -> CompletionItemKind.Snippet
        LspCompletionItemKind.Color -> CompletionItemKind.Color
        LspCompletionItemKind.File -> CompletionItemKind.File
        LspCompletionItemKind.Reference -> CompletionItemKind.Reference
        LspCompletionItemKind.Folder -> CompletionItemKind.Folder
        LspCompletionItemKind.EnumMember -> CompletionItemKind.EnumMember
        LspCompletionItemKind.Constant -> CompletionItemKind.Constant
        LspCompletionItemKind.Struct -> CompletionItemKind.Struct
        LspCompletionItemKind.Event -> CompletionItemKind.Event
        LspCompletionItemKind.Operator -> CompletionItemKind.Operator
        LspCompletionItemKind.TypeParameter -> CompletionItemKind.TypeParameter
    }

/**
 * Convert JetaProg CompletionItemKind to LSP Completion Item Kind.
 */
public fun CompletionItemKind.toLspCompletionItemKind(): LspCompletionItemKind =
    when (this) {
        CompletionItemKind.Text -> LspCompletionItemKind.Text
        CompletionItemKind.Method -> LspCompletionItemKind.Method
        CompletionItemKind.Function -> LspCompletionItemKind.Function
        CompletionItemKind.Constructor -> LspCompletionItemKind.Constructor
        CompletionItemKind.Field -> LspCompletionItemKind.Field
        CompletionItemKind.Variable -> LspCompletionItemKind.Variable
        CompletionItemKind.Class -> LspCompletionItemKind.Class
        CompletionItemKind.Interface -> LspCompletionItemKind.Interface
        CompletionItemKind.Module -> LspCompletionItemKind.Module
        CompletionItemKind.Property -> LspCompletionItemKind.Property
        CompletionItemKind.Unit -> LspCompletionItemKind.Unit
        CompletionItemKind.Value -> LspCompletionItemKind.Value
        CompletionItemKind.Enum -> LspCompletionItemKind.Enum
        CompletionItemKind.Keyword -> LspCompletionItemKind.Keyword
        CompletionItemKind.Snippet -> LspCompletionItemKind.Snippet
        CompletionItemKind.Color -> LspCompletionItemKind.Color
        CompletionItemKind.File -> LspCompletionItemKind.File
        CompletionItemKind.Reference -> LspCompletionItemKind.Reference
        CompletionItemKind.Folder -> LspCompletionItemKind.Folder
        CompletionItemKind.EnumMember -> LspCompletionItemKind.EnumMember
        CompletionItemKind.Constant -> LspCompletionItemKind.Constant
        CompletionItemKind.Struct -> LspCompletionItemKind.Struct
        CompletionItemKind.Event -> LspCompletionItemKind.Event
        CompletionItemKind.Operator -> LspCompletionItemKind.Operator
        CompletionItemKind.TypeParameter -> LspCompletionItemKind.TypeParameter
    }

/**
 * Convert LSP Completion Item to JetaProg CompletionItem.
 */
public fun LspCompletionItem.toCompletionItem(): CompletionItem =
    CompletionItem(
        label = label,
        kind = kind?.toCompletionItemKind() ?: CompletionItemKind.Text,
        detail = detail,
        documentation = documentation?.value,
        insertText = insertText ?: label,
        insertTextIsSnippet = insertTextFormat == 2,
        filterText = filterText ?: label,
        sortText = sortText ?: label,
        preselect = preselect ?: false,
        range = textEdit?.range?.toTextRange(),
        additionalTextEdits = additionalTextEdits?.map { it.toTextEditData() } ?: emptyList(),
    )

/**
 * Convert LSP Text Edit to JetaProg TextEditData.
 */
public fun LspTextEdit.toTextEditData(): TextEditData =
    TextEditData(
        range = range.toTextRange(),
        newText = newText,
    )

/**
 * Convert LSP Completion List to JetaProg CompletionList.
 */
public fun LspCompletionList.toCompletionList(): CompletionList =
    CompletionList(
        items = items.map { it.toCompletionItem() },
        isIncomplete = isIncomplete,
    )

/**
 * Convert LSP Hover to JetaProg Hover.
 */
public fun LspHover.toHover(): Hover =
    Hover(
        contents =
            listOf(
                when (contents.kind) {
                    "markdown" -> MarkedString.Markdown(contents.value)
                    else -> MarkedString.Code(contents.value, "text")
                },
            ),
        range = range?.toTextRange(),
    )

// ============================================================================
// Signature Help Converters
// ============================================================================

/**
 * Convert LSP Signature Help to JetaProg SignatureHelp.
 */
public fun LspSignatureHelp.toSignatureHelp(): SignatureHelp =
    SignatureHelp(
        signatures = signatures.map { it.toSignatureInformation() },
        activeSignature = activeSignature ?: 0,
        activeParameter = activeParameter ?: 0,
    )

/**
 * Convert LSP Signature Information to JetaProg SignatureInformation.
 */
public fun LspSignatureInformation.toSignatureInformation(): SignatureInformation =
    SignatureInformation(
        label = label,
        documentation = documentation?.value,
        parameters = parameters?.map { it.toParameterInformation() } ?: emptyList(),
    )

/**
 * Convert LSP Parameter Information to JetaProg ParameterInformation.
 */
public fun LspParameterInformation.toParameterInformation(): ParameterInformation =
    ParameterInformation(
        label = label,
        documentation = documentation?.value,
    )

/**
 * Convert LSP Text Edit to JetaProg TextEdit.
 */
public fun LspTextEdit.toTextEdit(): TextEdit =
    TextEdit(
        range = range.toTextRange(),
        newText = newText,
    )

/**
 * Convert JetaProg TextEdit to LSP Text Edit.
 */
public fun TextEdit.toLspTextEdit(): LspTextEdit =
    LspTextEdit(
        range = range.toLspRange(),
        newText = newText,
    )

/**
 * Convert LSP Diagnostic Severity to JetaProg DiagnosticSeverity.
 */
public fun LspDiagnosticSeverity.toDiagnosticSeverity(): DiagnosticSeverity =
    when (this) {
        LspDiagnosticSeverity.Error -> DiagnosticSeverity.ERROR
        LspDiagnosticSeverity.Warning -> DiagnosticSeverity.WARNING
        LspDiagnosticSeverity.Information -> DiagnosticSeverity.INFORMATION
        LspDiagnosticSeverity.Hint -> DiagnosticSeverity.HINT
    }

/**
 * Convert LSP Diagnostic to JetaProg LanguageDiagnostic.
 */
public fun LspDiagnostic.toLanguageDiagnostic(): LanguageDiagnostic =
    LanguageDiagnostic(
        range = range.toTextRange(),
        message = message,
        severity = severity?.toDiagnosticSeverity() ?: DiagnosticSeverity.ERROR,
        code = code,
        source = source,
    )

/**
 * Convert LSP Code Action to JetaProg CodeAction.
 */
public fun LspCodeAction.toCodeAction(): CodeAction =
    CodeAction(
        title = title,
        kind =
            when (kind) {
                "quickfix" -> CodeActionKind.QuickFix
                "refactor" -> CodeActionKind.Refactor
                "refactor.extract" -> CodeActionKind.RefactorExtract
                "refactor.inline" -> CodeActionKind.RefactorInline
                "refactor.rewrite" -> CodeActionKind.RefactorRewrite
                "source" -> CodeActionKind.Source
                "source.organizeImports" -> CodeActionKind.SourceOrganizeImports
                else -> CodeActionKind.QuickFix
            },
        diagnostics = diagnostics?.map { it.toLanguageDiagnostic() } ?: emptyList(),
        edit =
            edit?.let { lspEdit ->
                WorkspaceEdit(
                    changes =
                        lspEdit.changes?.mapValues { (_, edits) ->
                            edits.map { it.toTextEdit() }
                        } ?: emptyMap(),
                )
            },
        isPreferred = isPreferred ?: false,
    )

/**
 * Convert LSP Symbol Kind to string for display.
 */
public fun LspSymbolKind.toDisplayName(): String =
    when (this) {
        LspSymbolKind.File -> "File"
        LspSymbolKind.Module -> "Module"
        LspSymbolKind.Namespace -> "Namespace"
        LspSymbolKind.Package -> "Package"
        LspSymbolKind.Class -> "Class"
        LspSymbolKind.Method -> "Method"
        LspSymbolKind.Property -> "Property"
        LspSymbolKind.Field -> "Field"
        LspSymbolKind.Constructor -> "Constructor"
        LspSymbolKind.Enum -> "Enum"
        LspSymbolKind.Interface -> "Interface"
        LspSymbolKind.Function -> "Function"
        LspSymbolKind.Variable -> "Variable"
        LspSymbolKind.Constant -> "Constant"
        LspSymbolKind.String -> "String"
        LspSymbolKind.Number -> "Number"
        LspSymbolKind.Boolean -> "Boolean"
        LspSymbolKind.Array -> "Array"
        LspSymbolKind.Object -> "Object"
        LspSymbolKind.Key -> "Key"
        LspSymbolKind.Null -> "Null"
        LspSymbolKind.EnumMember -> "Enum Member"
        LspSymbolKind.Struct -> "Struct"
        LspSymbolKind.Event -> "Event"
        LspSymbolKind.Operator -> "Operator"
        LspSymbolKind.TypeParameter -> "Type Parameter"
    }
