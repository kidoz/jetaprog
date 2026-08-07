package su.kidoz.jetaprog.app.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.adapter.TextDocumentAdapter
import su.kidoz.jetaprog.common.completion.CompletionContext
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionItemKind
import su.kidoz.jetaprog.common.completion.CompletionTriggerKind
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.completion.CompletionController
import su.kidoz.jetaprog.editor.completion.SnippetExpander
import su.kidoz.jetaprog.editor.completion.smart.ExpectedTypeContext
import su.kidoz.jetaprog.editor.completion.smart.ExpectedTypeInference
import su.kidoz.jetaprog.editor.completion.smart.SmartCompletionFilter
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.editor.quickfix.AutoImportProvider
import su.kidoz.jetaprog.editor.quickfix.QuickFix
import su.kidoz.jetaprog.editor.quickfix.QuickFixProvider
import su.kidoz.jetaprog.editor.quickfix.TextReplacement
import su.kidoz.jetaprog.editor.quickfix.applyReplacements
import su.kidoz.jetaprog.editor.search.FindMatcher
import su.kidoz.jetaprog.editor.state.CompletionState
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.state.EditorTab
import su.kidoz.jetaprog.editor.state.FindToggle
import su.kidoz.jetaprog.editor.state.HoverState
import su.kidoz.jetaprog.editor.state.LineChangeMarker
import su.kidoz.jetaprog.editor.state.NotificationType
import su.kidoz.jetaprog.editor.state.QuickFixState
import su.kidoz.jetaprog.editor.state.SignatureHelpState
import su.kidoz.jetaprog.editor.state.SignatureInfo
import su.kidoz.jetaprog.editor.state.SignatureParameter
import su.kidoz.jetaprog.editor.state.WorkspaceDiagnostic
import su.kidoz.jetaprog.editor.syntax.Diagnostic
import su.kidoz.jetaprog.editor.syntax.IncrementalTokenizer
import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerRegistry
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.c.CLexer
import su.kidoz.jetaprog.editor.syntax.cmake.CMakeLexer
import su.kidoz.jetaprog.editor.syntax.cpp.CppLexer
import su.kidoz.jetaprog.editor.syntax.gitignore.GitignoreLexer
import su.kidoz.jetaprog.editor.syntax.highlighting.LayeredHighlighter
import su.kidoz.jetaprog.editor.syntax.java.JavaLexer
import su.kidoz.jetaprog.editor.syntax.kotlin.KotlinLexer
import su.kidoz.jetaprog.editor.syntax.markdown.MarkdownLexer
import su.kidoz.jetaprog.editor.syntax.meson.MesonLexer
import su.kidoz.jetaprog.editor.syntax.python.PythonLexer
import su.kidoz.jetaprog.editor.syntax.rust.RustLexer
import su.kidoz.jetaprog.editor.syntax.toml.TomlLexer
import su.kidoz.jetaprog.editor.syntax.vala.ValaLexer
import su.kidoz.jetaprog.editor.syntax.xml.XmlLexer
import su.kidoz.jetaprog.editor.undo.EditSnapshot
import su.kidoz.jetaprog.editor.undo.UndoManager
import su.kidoz.jetaprog.lint.integration.DiagnosticConverter
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspRange
import su.kidoz.jetaprog.lsp.protocol.LspTextEdit
import su.kidoz.jetaprog.lsp.protocol.LspWorkspaceEdit
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.CodeActionContext
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.api.services.LintService
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpContext
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpTriggerKind
import su.kidoz.jetaprog.plugins.api.services.TextDocumentChangeEvent
import su.kidoz.jetaprog.plugins.api.services.TextDocumentContentChange
import su.kidoz.jetaprog.plugins.kotlin.KotlinFormatter
import su.kidoz.jetaprog.plugins.runtime.activation.ActivationEventService
import su.kidoz.jetaprog.plugins.runtime.services.EditorServiceImpl
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.plugins.support.formatters.DefaultFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult
import su.kidoz.jetaprog.plugins.support.formatters.JsonFormatter
import su.kidoz.jetaprog.plugins.support.formatters.XmlFormatter
import su.kidoz.jetaprog.plugins.support.formatters.YamlFormatter
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import java.io.File
import java.net.URI
import su.kidoz.jetaprog.plugins.api.services.WorkspaceEdit as LanguageWorkspaceEdit

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the code editor.
 *
 * @param fileSystem The file system for reading/writing files.
 * @param settingsService Service for accessing IDE settings.
 * @param navigationService Service for code navigation features.
 * @param languageRegistry Registry for language features (completion, hover, etc.).
 * @param activationEvents Service for firing activation triggers when documents open.
 * @param lintService Service running registered lint rules to produce editor diagnostics.
 * @param quickFixProvider Supplies quick fixes for the caret position (Alt+Enter).
 * @param autoImportProvider Adds imports when an accepted completion needs one.
 * @param pluginEditorService Publishes editor lifecycle events to installed plugins.
 * @param workspacePath Root used to constrain file changes requested by language servers.
 */
public class EditorViewModel(
    private val fileSystem: FileSystem,
    private val settingsService: SettingsService,
    private val navigationService: NavigationService? = null,
    private val languageRegistry: LanguageRegistry? = null,
    private val activationEvents: ActivationEventService? = null,
    private val lintService: LintService? = null,
    private val quickFixProvider: QuickFixProvider? = null,
    private val autoImportProvider: AutoImportProvider? = null,
    private val pluginEditorService: EditorServiceImpl? = null,
    private val workspacePath: String? = null,
) : MviViewModel<EditorIntent, EditorState, EditorEffect>(EditorState()) {
    private val completionController = CompletionController()

    /** Live state retained for every open document, including dirty buffers. */
    private data class DocumentSession(
        val content: String,
        val languageId: LanguageId,
        val cursor: Cursor = Cursor.Zero,
        val scrollLine: Int = 0,
        val lineChangeMarkers: Map<Int, LineChangeMarker> = emptyMap(),
        val version: Int = 1,
    )

    /**
     * The result set exactly as the provider returned it.
     *
     * Filtering narrows a copy: filtering the already-filtered list in place meant
     * backspacing could never widen the popup again.
     */
    private var unfilteredCompletionItems: List<CompletionItem> = emptyList()
    private var completionJob: Job? = null
    private var hoverJob: Job? = null
    private var signatureHelpJob: Job? = null
    private var lintJob: Job? = null
    private var autoSaveJob: Job? = null
    private val lspOpenDocuments = mutableSetOf<String>()
    private val undoManagers = mutableMapOf<String, UndoManager>()
    private val incrementalTokenizers = mutableMapOf<String, IncrementalTokenizer>()
    private val lspDiagnostics = mutableMapOf<String, List<Diagnostic>>()
    private val lintDiagnostics = mutableMapOf<String, List<Diagnostic>>()
    private val documentSessions = mutableMapOf<String, DocumentSession>()
    private var pendingWorkspaceQuickFixes = emptyList<Pair<QuickFix, LanguageWorkspaceEdit>>()

    /**
     * Layered highlighter that combines multiple token sources.
     * Priority: LSP semantic tokens > Tree-sitter > Hand-written lexers
     */
    private val layeredHighlighter = LayeredHighlighter(viewModelScope)

    private val _settings = MutableStateFlow(settingsService.getCurrentSettings())
    public val settings: StateFlow<AllSettings> = _settings

    init {
        // Register lexers
        LexerRegistry.register(KotlinLexer())
        LexerRegistry.register(ValaLexer())
        LexerRegistry.register(JavaLexer())
        LexerRegistry.register(RustLexer())
        LexerRegistry.register(CLexer())
        LexerRegistry.register(CppLexer())
        LexerRegistry.register(MesonLexer())
        LexerRegistry.register(CMakeLexer())
        LexerRegistry.register(XmlLexer())
        LexerRegistry.register(TomlLexer())
        LexerRegistry.register(MarkdownLexer())
        LexerRegistry.register(PythonLexer())
        LexerRegistry.register(GitignoreLexer())

        // Register formatters
        FormatterRegistry.register(KotlinFormatter())
        FormatterRegistry.register(JsonFormatter())
        FormatterRegistry.register(XmlFormatter())
        FormatterRegistry.register(YamlFormatter())
        FormatterRegistry.setDefaultFormatter(DefaultFormatter())

        // Observe token changes from the layered highlighter
        viewModelScope.launch {
            layeredHighlighter.tokens.collect { tokens ->
                updateState { copy(tokens = tokens) }
            }
        }

        languageRegistry?.onDiagnostics { uri, diagnostics ->
            lspDiagnostics[uri] = diagnostics.map { it.toEditorDiagnostic() }
            refreshDiagnosticsState()
        }
        languageRegistry?.onWorkspaceEdit { label, edit -> applyWorkspaceEdit(label, edit) }

        // Plugins activate lazily when their language is first opened, so the
        // initial lint pass can run before their rules exist. Re-lint whenever
        // the rule set changes so the first file opened still gets diagnostics.
        lintService?.let { service ->
            viewModelScope.launch {
                service.observeProviderChanges().collect { relintActiveDocument() }
            }
        }

        // Observe settings changes
        viewModelScope.launch {
            settingsService.settings.collect { newSettings ->
                _settings.value = newSettings
                updateState {
                    copy(
                        showLineNumbers = newSettings.editor.showLineNumbers,
                        showMinimap = newSettings.editor.showMinimap,
                        wordWrap = newSettings.editor.wordWrap,
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.OpenFile -> {
                openFile(intent.path)
            }

            is EditorIntent.RestoreSession -> {
                restoreSession(intent)
            }

            is EditorIntent.Save -> {
                saveCurrentFile()
            }

            is EditorIntent.SaveAll -> {
                saveAllFiles()
            }

            is EditorIntent.SaveAs -> {
                saveAs(intent.path)
            }

            is EditorIntent.CloseTab -> {
                closeTab(intent.index, intent.discardChanges)
            }

            is EditorIntent.SaveAndCloseTab -> {
                saveAndCloseTab(intent.index)
            }

            is EditorIntent.CloseAllTabs -> {
                closeAllTabs(intent.discardChanges)
            }

            is EditorIntent.SwitchTab -> {
                switchTab(intent.index)
            }

            is EditorIntent.UpdateContent -> {
                updateContent(intent.content)
            }

            is EditorIntent.SetTokens -> {
                setTokens(intent.tokens)
            }

            is EditorIntent.ApplySemanticTokens -> {
                applySemanticTokens(intent.data, intent.tokenTypes)
            }

            is EditorIntent.SetLineChangeMarkers -> {
                updateState { copy(lineChangeMarkers = intent.markers) }
                updateActiveDocumentSession { copy(lineChangeMarkers = intent.markers) }
            }

            is EditorIntent.InsertText -> {
                insertText(intent.text)
            }

            is EditorIntent.DeleteRange -> {
                replaceRange(intent.range, "")
            }

            is EditorIntent.Backspace -> {
                backspace()
            }

            is EditorIntent.Delete -> {
                deleteForward()
            }

            is EditorIntent.GoToLine -> {
                goToLine(intent.lineNumber)
            }

            is EditorIntent.ToggleLineNumbers -> {
                toggleLineNumbers()
            }

            is EditorIntent.ToggleMinimap -> {
                toggleMinimap()
            }

            is EditorIntent.ToggleWordWrap -> {
                toggleWordWrap()
            }

            // Navigation intents (will be implemented later)
            is EditorIntent.RequestQuickFixes -> {
                requestQuickFixes()
            }

            is EditorIntent.ApplyQuickFix -> {
                applyQuickFix(intent.index)
            }

            is EditorIntent.MoveQuickFixSelection -> {
                moveQuickFixSelection(intent.delta)
            }

            is EditorIntent.DismissQuickFixes -> {
                updateState { copy(quickFixState = QuickFixState()) }
            }

            is EditorIntent.GoToDefinition -> {
                handleGoToDefinition()
            }

            is EditorIntent.FindReferences -> {
                handleFindReferences()
            }

            is EditorIntent.OpenSymbolSearch -> {
                handleOpenSymbolSearch()
            }

            is EditorIntent.NavigateTo -> {
                navigateTo(intent.path, intent.position)
            }

            // Completion intents
            is EditorIntent.RequestCompletion -> {
                requestCompletion(
                    intent.triggerKind,
                    intent.triggerCharacter,
                    intent.filterText,
                    smart = intent.smart,
                )
            }

            is EditorIntent.ApplyCompletion -> {
                applyCompletion(intent.item)
            }

            is EditorIntent.DismissCompletion -> {
                dismissCompletion()
            }

            is EditorIntent.CompletionMoveUp -> {
                completionMoveUp()
            }

            is EditorIntent.CompletionMoveDown -> {
                completionMoveDown()
            }

            is EditorIntent.SelectCompletionItem -> {
                selectCompletionItem(intent.index)
            }

            is EditorIntent.UpdateCompletionFilter -> {
                updateCompletionFilter(intent.filterText)
            }

            is EditorIntent.SetCompletionItems -> {
                setCompletionItems(intent.items, intent.isIncomplete)
            }

            is EditorIntent.MoveCursor -> {
                moveCursor(intent.position)
            }

            is EditorIntent.MoveUp -> {
                moveCursorVertically(-1)
            }

            is EditorIntent.MoveDown -> {
                moveCursorVertically(1)
            }

            is EditorIntent.MoveLeft -> {
                moveCursorByOffset(-1)
            }

            is EditorIntent.MoveRight -> {
                moveCursorByOffset(1)
            }

            is EditorIntent.MoveToLineStart -> {
                moveCursorToLineBoundary(end = false)
            }

            is EditorIntent.MoveToLineEnd -> {
                moveCursorToLineBoundary(end = true)
            }

            is EditorIntent.MoveToDocumentStart -> {
                moveCursor(TextPosition.Zero, synchronizeUi = true)
            }

            is EditorIntent.MoveToDocumentEnd -> {
                moveCursorToOffset(currentState.content, currentState.content.length)
            }

            is EditorIntent.Select -> {
                select(intent.range)
            }

            is EditorIntent.SelectAll -> {
                selectAll()
            }

            is EditorIntent.ClearSelection -> {
                clearSelection()
            }

            is EditorIntent.SelectUp -> {
                extendSelectionVertically(-1)
            }

            is EditorIntent.SelectDown -> {
                extendSelectionVertically(1)
            }

            is EditorIntent.SelectLeft -> {
                extendSelectionByOffset(-1)
            }

            is EditorIntent.SelectRight -> {
                extendSelectionByOffset(1)
            }

            is EditorIntent.Copy -> {
                copySelection()
            }

            is EditorIntent.Cut -> {
                cutSelection()
            }

            is EditorIntent.Paste -> {
                insertText(intent.text)
            }

            is EditorIntent.ScrollToLine -> {
                goToLine(intent.lineNumber)
            }

            // Formatting intents
            is EditorIntent.FormatDocument -> {
                formatDocument()
            }

            is EditorIntent.FormatSelection -> {
                formatSelection(intent.range)
            }

            // Hover intents
            is EditorIntent.RequestHover -> {
                requestHover(intent.position)
            }

            is EditorIntent.SetHoverContent -> {
                setHoverContent(intent.contents, intent.range)
            }

            is EditorIntent.DismissHover -> {
                dismissHover()
            }

            // Signature Help intents
            is EditorIntent.RequestSignatureHelp -> {
                requestSignatureHelp(intent.triggerCharacter, intent.isRetrigger)
            }

            is EditorIntent.SetSignatureHelp -> {
                setSignatureHelp(
                    intent.signatures,
                    intent.activeSignature,
                    intent.activeParameter,
                )
            }

            is EditorIntent.UpdateActiveParameter -> {
                updateActiveParameter(intent.index)
            }

            is EditorIntent.NextSignature -> {
                nextSignature()
            }

            is EditorIntent.PreviousSignature -> {
                previousSignature()
            }

            is EditorIntent.DismissSignatureHelp -> {
                dismissSignatureHelp()
            }

            // Undo/redo intents
            is EditorIntent.Undo -> {
                undo()
            }

            is EditorIntent.Redo -> {
                redo()
            }

            // Line operation intents
            is EditorIntent.DeleteLine -> {
                deleteLine()
            }

            is EditorIntent.DuplicateLine -> {
                duplicateLine()
            }

            // Find/replace intents
            is EditorIntent.OpenFindBar -> {
                openFindBar(intent.withReplace)
            }

            is EditorIntent.CloseFindBar -> {
                closeFindBar()
            }

            is EditorIntent.UpdateFindQuery -> {
                updateFindQuery(intent.query)
            }

            is EditorIntent.UpdateReplaceText -> {
                updateReplaceText(intent.text)
            }

            is EditorIntent.ToggleFindOption -> {
                toggleFindOption(intent.option)
            }

            is EditorIntent.FindNext -> {
                findNext()
            }

            is EditorIntent.FindPrevious -> {
                findPrevious()
            }

            is EditorIntent.ReplaceCurrent -> {
                replaceCurrent()
            }

            is EditorIntent.ReplaceAll -> {
                replaceAll()
            }

            is EditorIntent.Find -> {
                handleLegacyFind(intent)
            }

            is EditorIntent.Replace -> {
                handleLegacyReplace(intent)
            }
        }
    }

    private suspend fun openFile(path: String) {
        val fileName = File(path).name
        val uri = DocumentUri.file(path)
        val existingIndex = currentState.tabs.indexOfFirst { it.uri == uri }
        if (existingIndex >= 0) {
            switchTab(existingIndex)
            return
        }

        updateState { copy(isLoading = true, error = null) }

        try {
            val content =
                withContext(Dispatchers.IO) {
                    fileSystem.readText(path).getOrThrow()
                }

            val languageId = detectLanguage(fileName)

            // Tokenize the content
            layeredHighlighter.clearSemanticTokens()
            val tokens = tokenize(content, languageId, documentKey = uri.value)

            val newTab =
                EditorTab(
                    uri = uri,
                    name = fileName,
                    isDirty = false,
                )

            documentSessions[uri.value] =
                DocumentSession(
                    content = content,
                    languageId = languageId,
                )

            updateState {
                val newTabs = tabs + newTab
                copy(
                    tabs = newTabs,
                    activeTabIndex = newTabs.size - 1,
                    activeDocumentUri = uri,
                    content = content,
                    documentVersion = 1,
                    languageId = languageId,
                    tokens = tokens,
                    diagnostics = emptyList(),
                    cursor = Cursor.Zero,
                    scrollLine = 0,
                    lineChangeMarkers = emptyMap(),
                    isLoading = false,
                )
            }

            syncDocumentOpened(uri, languageId, content)
            scheduleLint(uri, languageId, content, LintTrigger.OPEN)
            emitEffect(EditorEffect.FileOpened(path))
        } catch (e: Exception) {
            updateState {
                copy(
                    isLoading = false,
                    error = "Failed to open file: ${e.message}",
                )
            }
            emitEffect(EditorEffect.ShowError("Failed to open file: ${e.message}"))
        }
    }

    private suspend fun restoreSession(intent: EditorIntent.RestoreSession) {
        val existingFiles =
            intent.filePaths.withIndex().filter { (_, path) ->
                withContext(Dispatchers.IO) { fileSystem.exists(path) }
            }
        existingFiles.forEach { (_, path) -> openFile(path) }

        val activeIndex = existingFiles.indexOfFirst { it.index == intent.activeTabIndex }
        if (activeIndex >= 0) {
            switchTab(activeIndex)
        }
        intent.cursor?.let { moveCursor(it, synchronizeUi = true) }
    }

    private suspend fun saveCurrentFile() {
        val activeTab = currentState.activeTab ?: return
        val path = activeTab.uri.toPath() ?: return
        val uri = activeTab.uri
        val languageId = currentState.languageId
        val contentToSave = prepareContentForSave(currentState.content)
        if (contentToSave != currentState.content) {
            updateContent(contentToSave, coalesceUndo = false)
        }

        updateState { copy(isSaving = true, error = null) }

        try {
            withContext(Dispatchers.IO) {
                fileSystem.writeText(path, contentToSave).getOrThrow()
            }

            updateState {
                val updatedTabs =
                    tabs.map { tab ->
                        val sessionContent = documentSessions[tab.uri.value]?.content
                        if (tab.uri == uri && sessionContent == contentToSave) {
                            tab.copy(isDirty = false)
                        } else {
                            tab
                        }
                    }
                copy(tabs = updatedTabs, isSaving = false)
            }

            emitEffect(EditorEffect.FileSaved(path))
            syncDocumentSaved(uri, languageId, contentToSave)
            scheduleLint(uri, languageId, contentToSave, LintTrigger.SAVE)
            emitEffect(EditorEffect.ShowNotification("File saved", NotificationType.SUCCESS))
        } catch (e: Exception) {
            updateState { copy(isSaving = false, error = "Failed to save: ${e.message}") }
            emitEffect(EditorEffect.ShowError("Failed to save file: ${e.message}"))
        }
    }

    private suspend fun saveAllFiles() {
        val originalUri = currentState.activeDocumentUri
        val dirtyUris = currentState.tabs.filter { it.isDirty }.map { it.uri }
        dirtyUris.forEach { uri ->
            val index = currentState.tabs.indexOfFirst { it.uri == uri }
            if (index >= 0) {
                switchTab(index)
                saveCurrentFile()
            }
        }
        val originalIndex = currentState.tabs.indexOfFirst { it.uri == originalUri }
        if (originalIndex >= 0) switchTab(originalIndex)
    }

    private suspend fun saveAndCloseTab(index: Int) {
        val uri = currentState.tabs.getOrNull(index)?.uri ?: return
        switchTab(index)
        saveCurrentFile()
        val savedIndex = currentState.tabs.indexOfFirst { it.uri == uri && !it.isDirty }
        if (savedIndex >= 0) closeTab(savedIndex, discardChanges = true)
    }

    private suspend fun saveAs(path: String) {
        val sourceTab = currentState.activeTab ?: return
        val sourceUri = sourceTab.uri
        val sourceLanguageId = currentState.languageId
        val contentToSave = prepareContentForSave(currentState.content)
        if (contentToSave != currentState.content) {
            updateContent(contentToSave, coalesceUndo = false)
        }
        val fileName = File(path).name
        val uri = DocumentUri.file(path)
        if (currentState.tabs.any { it.uri == uri && it.uri != sourceUri }) {
            emitEffect(EditorEffect.ShowError("A tab for $fileName is already open"))
            return
        }

        updateState { copy(isSaving = true) }

        try {
            withContext(Dispatchers.IO) {
                fileSystem.writeText(path, contentToSave).getOrThrow()
            }

            val languageId = detectLanguage(fileName)
            val oldSession =
                documentSessions.remove(sourceUri.value)
                    ?: DocumentSession(contentToSave, sourceLanguageId, currentState.cursor)
            documentSessions[uri.value] =
                oldSession.copy(
                    content = contentToSave,
                    languageId = languageId,
                    version = oldSession.version + 1,
                )
            undoManagers.remove(sourceUri.value)?.let { undoManagers[uri.value] = it }
            incrementalTokenizers.keys.removeAll { it.startsWith("${sourceUri.value}:") }
            clearDiagnostics(sourceUri)
            syncDocumentClosed(sourceUri, sourceLanguageId)
            layeredHighlighter.clearSemanticTokens()
            val tokens = tokenize(contentToSave, languageId, documentKey = uri.value)

            updateState {
                val updatedTabs =
                    tabs.map { tab ->
                        if (tab.uri == sourceUri) {
                            tab.copy(uri = uri, name = fileName, isDirty = false)
                        } else {
                            tab
                        }
                    }
                copy(
                    tabs = updatedTabs,
                    activeDocumentUri = uri,
                    languageId = languageId,
                    tokens = tokens,
                    diagnostics = emptyList(),
                    isSaving = false,
                )
            }

            syncDocumentOpened(uri, languageId, contentToSave)
            syncDocumentSaved(uri, languageId, contentToSave)
            scheduleLint(uri, languageId, contentToSave, LintTrigger.SAVE)
            emitEffect(EditorEffect.FileSaved(path))
        } catch (e: Exception) {
            updateState { copy(isSaving = false, error = "Failed to save as: ${e.message}") }
            emitEffect(EditorEffect.ShowError("Failed to save file: ${e.message}"))
        }
    }

    private fun closeTab(
        index: Int,
        discardChanges: Boolean = false,
    ) {
        if (index !in currentState.tabs.indices) return

        val tab = currentState.tabs[index]
        if (tab.isDirty && !discardChanges) {
            viewModelScope.launch {
                emitEffect(
                    EditorEffect.ShowConfirmation(
                        message = "Discard unsaved changes to ${tab.name}?",
                        onConfirm = {
                            val confirmedIndex = currentState.tabs.indexOfFirst { it.uri == tab.uri }
                            if (confirmedIndex >= 0) {
                                dispatch(EditorIntent.CloseTab(confirmedIndex, discardChanges = true))
                            }
                        },
                        onCancel = {},
                        onSave = {
                            val saveIndex = currentState.tabs.indexOfFirst { it.uri == tab.uri }
                            if (saveIndex >= 0) dispatch(EditorIntent.SaveAndCloseTab(saveIndex))
                        },
                    ),
                )
            }
            return
        }

        val oldActiveIndex = currentState.activeTabIndex
        val closingActiveTab = index == oldActiveIndex
        val newTabs = currentState.tabs.filterIndexed { i, _ -> i != index }
        val newActiveIndex =
            when {
                newTabs.isEmpty() -> -1
                index < oldActiveIndex -> oldActiveIndex - 1
                index == oldActiveIndex -> index.coerceAtMost(newTabs.lastIndex)
                else -> oldActiveIndex
            }

        if (newTabs.isEmpty()) {
            updateState {
                copy(
                    tabs = emptyList(),
                    activeTabIndex = -1,
                    activeDocumentUri = null,
                    content = "",
                    tokens = TokenList(emptyList()),
                    diagnostics = emptyList(),
                    cursor = Cursor.Zero,
                    scrollLine = 0,
                    lineChangeMarkers = emptyMap(),
                )
            }
        } else if (closingActiveTab) {
            activateTab(newTabs, newActiveIndex)
        } else {
            updateState { copy(tabs = newTabs, activeTabIndex = newActiveIndex) }
        }

        syncDocumentClosed(tab.uri, detectLanguage(tab.name))
        documentSessions.remove(tab.uri.value)
        undoManagers.remove(tab.uri.value)
        incrementalTokenizers.keys.removeAll { it.startsWith("${tab.uri.value}:") }
        clearDiagnostics(tab.uri)
        viewModelScope.launch {
            emitEffect(EditorEffect.FileClosed(tab.uri.value))
        }
    }

    private fun closeAllTabs(discardChanges: Boolean = false) {
        if (currentState.hasUnsavedChanges && !discardChanges) {
            viewModelScope.launch {
                emitEffect(
                    EditorEffect.ShowConfirmation(
                        message = "Discard unsaved changes in all open files?",
                        onConfirm = { dispatch(EditorIntent.CloseAllTabs(discardChanges = true)) },
                        onCancel = {},
                        onSave = { dispatch(EditorIntent.SaveAll) },
                    ),
                )
            }
            return
        }
        currentState.tabs.forEach { tab ->
            syncDocumentClosed(tab.uri, detectLanguage(tab.name))
        }
        undoManagers.clear()
        incrementalTokenizers.clear()
        documentSessions.clear()
        lintJob?.cancel()
        autoSaveJob?.cancel()
        lspDiagnostics.clear()
        lintDiagnostics.clear()
        updateState {
            copy(
                tabs = emptyList(),
                activeTabIndex = -1,
                activeDocumentUri = null,
                content = "",
                tokens = TokenList(emptyList()),
                diagnostics = emptyList(),
                cursor = Cursor.Zero,
                scrollLine = 0,
                lineChangeMarkers = emptyMap(),
            )
        }
    }

    private suspend fun switchTab(index: Int) {
        if (index !in currentState.tabs.indices) return
        if (index == currentState.activeTabIndex) return

        snapshotActiveDocument()
        activateTab(currentState.tabs, index)
    }

    private fun activateTab(
        updatedTabs: List<EditorTab>,
        index: Int,
    ) {
        val tab = updatedTabs.getOrNull(index) ?: return
        val session = documentSessions[tab.uri.value] ?: return
        layeredHighlighter.clearSemanticTokens()
        val tokens = tokenize(session.content, session.languageId, documentKey = tab.uri.value)

        updateState {
            copy(
                tabs = updatedTabs,
                activeTabIndex = index,
                activeDocumentUri = tab.uri,
                content = session.content,
                documentVersion = session.version,
                languageId = session.languageId,
                cursor = session.cursor,
                scrollLine = session.scrollLine,
                lineChangeMarkers = session.lineChangeMarkers,
                tokens = tokens,
                diagnostics = diagnosticsFor(tab.uri),
                completionState = CompletionState(),
                hoverState = HoverState(),
                quickFixState = QuickFixState(),
                signatureHelpState = SignatureHelpState(),
                isLoading = false,
                error = null,
            )
        }
        refreshFindMatches()
    }

    private fun snapshotActiveDocument() {
        val uri = currentState.activeDocumentUri?.value ?: return
        val previous = documentSessions[uri] ?: return
        documentSessions[uri] =
            previous.copy(
                content = currentState.content,
                languageId = currentState.languageId,
                cursor = currentState.cursor,
                scrollLine = currentState.scrollLine,
                lineChangeMarkers = currentState.lineChangeMarkers,
            )
    }

    private fun updateActiveDocumentSession(update: DocumentSession.() -> DocumentSession) {
        val uri = currentState.activeDocumentUri?.value ?: return
        val session = documentSessions[uri] ?: return
        documentSessions[uri] = session.update()
    }

    private fun updateContent(
        content: String,
        coalesceUndo: Boolean = true,
    ) {
        if (content == currentState.content) return

        currentState.activeDocumentUri?.let { uri ->
            undoManagerFor(uri.value).recordBeforeEdit(
                before = currentSnapshot(),
                nowMs = System.currentTimeMillis(),
                coalesce = coalesceUndo,
            )
        }

        applyContent(content)
    }

    /**
     * Apply new content to the active document without touching undo history.
     */
    private fun applyContent(
        content: String,
        newCursor: TextPosition? = null,
    ) {
        // Tokenize the new content
        val tokens =
            tokenize(
                content,
                currentState.languageId,
                documentKey = currentState.activeDocumentUri?.value,
            )

        // Mark the current tab as dirty
        val updatedTabs =
            currentState.tabs.mapIndexed { index, tab ->
                if (index == currentState.activeTabIndex && !tab.isDirty) {
                    tab.copy(isDirty = true)
                } else {
                    tab
                }
            }

        updateState {
            copy(
                content = content,
                documentVersion = documentVersion + 1,
                tokens = tokens,
                tabs = updatedTabs,
                cursor = newCursor?.let { cursor.moveTo(it) } ?: cursor,
            )
        }

        updateActiveDocumentSession {
            copy(
                content = content,
                cursor = newCursor?.let { cursor.moveTo(it) } ?: cursor,
                version = version + 1,
            )
        }

        currentState.activeDocumentUri?.let { uri ->
            syncDocumentChanged(uri, currentState.languageId, content)
            scheduleLint(uri, currentState.languageId, content, LintTrigger.TYPE)
            scheduleAutoSave(uri)
        }

        refreshFindMatches()
    }

    private fun scheduleAutoSave(uri: DocumentUri) {
        autoSaveJob?.cancel()
        val editorSettings = _settings.value.editor
        if (!editorSettings.autoSave) return
        val expectedVersion = documentSessions[uri.value]?.version ?: return
        autoSaveJob =
            viewModelScope.launch {
                delay(editorSettings.autoSaveDelayMs)
                val session = documentSessions[uri.value]
                val tab = currentState.tabs.firstOrNull { it.uri == uri }
                if (
                    currentState.activeDocumentUri == uri &&
                    session?.version == expectedVersion &&
                    tab?.isDirty == true
                ) {
                    dispatch(EditorIntent.Save)
                }
            }
    }

    private fun prepareContentForSave(content: String): String {
        val editorSettings = _settings.value.editor
        var prepared = content
        if (editorSettings.trimTrailingWhitespace) {
            prepared = TRAILING_WHITESPACE.replace(prepared, "")
        }
        if (editorSettings.insertFinalNewline && prepared.isNotEmpty() && !prepared.endsWith('\n')) {
            val lineSeparator = if (prepared.contains("\r\n")) "\r\n" else "\n"
            prepared += lineSeparator
        }
        return prepared
    }

    private fun undoManagerFor(uri: String): UndoManager = undoManagers.getOrPut(uri) { UndoManager() }

    private fun currentSnapshot(): EditSnapshot = EditSnapshot(currentState.content, currentState.cursor.position)

    private fun undo() {
        val uri = currentState.activeDocumentUri?.value ?: return
        val restored = undoManagers[uri]?.undo(currentSnapshot()) ?: return
        applyContent(restored.content, newCursor = restored.cursor)
    }

    private fun redo() {
        val uri = currentState.activeDocumentUri?.value ?: return
        val restored = undoManagers[uri]?.redo(currentSnapshot()) ?: return
        applyContent(restored.content, newCursor = restored.cursor)
    }

    private fun deleteLine() {
        val content = currentState.content
        val line = currentState.cursor.position.line
        val bounds = lineBounds(content, line) ?: return
        val removalStart =
            if (bounds.separatorEnd == bounds.contentEnd && bounds.start > 0) {
                if (bounds.start >= 2 && content[bounds.start - 2] == '\r') bounds.start - 2 else bounds.start - 1
            } else {
                bounds.start
            }
        val removalEnd = if (bounds.separatorEnd > bounds.contentEnd) bounds.separatorEnd else bounds.contentEnd
        val updated = content.removeRange(removalStart, removalEnd)
        updateContent(updated, coalesceUndo = false)
        val newLine = line.coerceAtMost((updated.lines().size - 1).coerceAtLeast(0))
        moveCursor(TextPosition(newLine, 0), synchronizeUi = true)
    }

    private fun duplicateLine() {
        val content = currentState.content
        val position = currentState.cursor.position
        val bounds = lineBounds(content, position.line) ?: return
        val lineText = content.substring(bounds.start, bounds.contentEnd)
        val separator = if (content.contains("\r\n")) "\r\n" else "\n"
        val updated =
            if (bounds.separatorEnd > bounds.contentEnd) {
                content.replaceRange(bounds.separatorEnd, bounds.separatorEnd, lineText + separator)
            } else {
                content + separator + lineText
            }
        updateContent(updated, coalesceUndo = false)
        moveCursor(TextPosition(position.line + 1, position.column), synchronizeUi = true)
    }

    private fun lineBounds(
        content: String,
        targetLine: Int,
    ): LineBounds? {
        if (targetLine < 0) return null
        var start = 0
        var line = 0
        while (line < targetLine) {
            val newline = content.indexOf('\n', start)
            if (newline < 0) return null
            start = newline + 1
            line++
        }
        val newline = content.indexOf('\n', start)
        if (newline < 0) return LineBounds(start, content.length, content.length)
        val contentEnd = if (newline > start && content[newline - 1] == '\r') newline - 1 else newline
        return LineBounds(start, contentEnd, newline + 1)
    }

    private data class LineBounds(
        val start: Int,
        val contentEnd: Int,
        val separatorEnd: Int,
    )

    // ========================================================================
    // Find/Replace Methods
    // ========================================================================

    private fun refreshFindMatches(anchorOffset: Int? = null) {
        val findState = currentState.findReplaceState
        if (!findState.isVisible) return

        val matches = FindMatcher.findMatches(currentState.content, findState.query, findState.options)
        val anchor =
            anchorOffset
                ?: findState.currentMatch?.start
                ?: positionToOffset(currentState.content, currentState.cursor.position)
        val index = FindMatcher.matchIndexAtOrAfter(matches, anchor)

        updateState {
            copy(findReplaceState = findReplaceState.copy(matches = matches, currentMatchIndex = index))
        }
    }

    private fun openFindBar(withReplace: Boolean) {
        updateState {
            copy(findReplaceState = findReplaceState.copy(isVisible = true, showReplace = withReplace))
        }
        refreshFindMatches(
            anchorOffset = positionToOffset(currentState.content, currentState.cursor.position),
        )
    }

    private fun closeFindBar() {
        updateState {
            copy(
                findReplaceState =
                    findReplaceState.copy(
                        isVisible = false,
                        matches = emptyList(),
                        currentMatchIndex = -1,
                    ),
            )
        }
    }

    private fun updateFindQuery(query: String) {
        updateState { copy(findReplaceState = findReplaceState.copy(query = query)) }
        refreshFindMatches(
            anchorOffset = positionToOffset(currentState.content, currentState.cursor.position),
        )
    }

    private fun updateReplaceText(text: String) {
        updateState { copy(findReplaceState = findReplaceState.copy(replaceText = text)) }
    }

    private fun toggleFindOption(option: FindToggle) {
        updateState {
            val options = findReplaceState.options
            val newOptions =
                when (option) {
                    FindToggle.CASE_SENSITIVE -> options.copy(caseSensitive = !options.caseSensitive)
                    FindToggle.WHOLE_WORD -> options.copy(wholeWord = !options.wholeWord)
                    FindToggle.REGEX -> options.copy(regex = !options.regex)
                }
            copy(findReplaceState = findReplaceState.copy(options = newOptions))
        }
        refreshFindMatches()
    }

    private fun findNext() {
        val findState = currentState.findReplaceState
        if (findState.matches.isEmpty()) return
        val next = (findState.currentMatchIndex + 1).mod(findState.matches.size)
        updateState { copy(findReplaceState = findReplaceState.copy(currentMatchIndex = next)) }
    }

    private fun findPrevious() {
        val findState = currentState.findReplaceState
        if (findState.matches.isEmpty()) return
        val previous =
            if (findState.currentMatchIndex < 0) {
                findState.matches.lastIndex
            } else {
                (findState.currentMatchIndex - 1).mod(findState.matches.size)
            }
        updateState { copy(findReplaceState = findReplaceState.copy(currentMatchIndex = previous)) }
    }

    private fun replaceCurrent() {
        val findState = currentState.findReplaceState
        val match = findState.currentMatch ?: return
        val newContent =
            currentState.content.replaceRange(match.start, match.end, findState.replaceText)
        updateContent(newContent, coalesceUndo = false)
        // Anchor past the replacement so a self-matching replacement does not get stuck.
        refreshFindMatches(anchorOffset = match.start + findState.replaceText.length)
    }

    private suspend fun replaceAll() {
        val findState = currentState.findReplaceState
        if (findState.matches.isEmpty()) return

        var content = currentState.content
        findState.matches.asReversed().forEach { match ->
            content = content.replaceRange(match.start, match.end, findState.replaceText)
        }
        val count = findState.matches.size

        updateContent(content, coalesceUndo = false)
        emitEffect(
            EditorEffect.ShowNotification(
                "Replaced $count occurrence${if (count == 1) "" else "s"}",
                NotificationType.INFO,
            ),
        )
    }

    private fun handleLegacyFind(intent: EditorIntent.Find) {
        updateState {
            copy(
                findReplaceState =
                    findReplaceState.copy(
                        isVisible = true,
                        query = intent.query,
                        options = findReplaceState.options.copy(caseSensitive = intent.caseSensitive),
                    ),
            )
        }
        refreshFindMatches(
            anchorOffset = positionToOffset(currentState.content, currentState.cursor.position),
        )
    }

    private suspend fun handleLegacyReplace(intent: EditorIntent.Replace) {
        updateState {
            copy(
                findReplaceState =
                    findReplaceState.copy(
                        isVisible = true,
                        showReplace = true,
                        query = intent.find,
                        replaceText = intent.replaceWith,
                    ),
            )
        }
        refreshFindMatches(
            anchorOffset = positionToOffset(currentState.content, currentState.cursor.position),
        )
        if (intent.all) {
            replaceAll()
        } else {
            replaceCurrent()
        }
    }

    /**
     * Convert a line/column position to a character offset in [content].
     */
    private fun positionToOffset(
        content: String,
        position: TextPosition,
    ): Int {
        var index = 0
        var line = 0
        while (index < content.length && line < position.line) {
            if (content[index] == '\n') {
                line++
            }
            index++
        }
        var lineEnd = index
        while (lineEnd < content.length && content[lineEnd] != '\r' && content[lineEnd] != '\n') {
            lineEnd++
        }
        return (index + position.column).coerceIn(index, lineEnd)
    }

    private fun offsetToPosition(
        content: String,
        offset: Int,
    ): TextPosition {
        val safeOffset = offset.coerceIn(0, content.length)
        var line = 0
        var column = 0
        for (index in 0 until safeOffset) {
            when (content[index]) {
                '\n' -> {
                    line++
                    column = 0
                }

                '\r' -> {}

                else -> {
                    column++
                }
            }
        }
        return TextPosition(line, column)
    }

    private fun setTokens(tokens: List<su.kidoz.jetaprog.editor.syntax.Token>) {
        updateState { copy(tokens = TokenList(tokens)) }
    }

    /**
     * Apply semantic tokens from LSP to enhance syntax highlighting.
     * These tokens are merged with base tokens using the layered highlighter.
     */
    private fun applySemanticTokens(
        data: List<Int>,
        tokenTypes: List<String>,
    ) {
        layeredHighlighter.applySemanticTokens(data, tokenTypes)
    }

    private fun insertText(text: String) {
        val content = currentState.content
        val start = positionToOffset(content, currentState.cursor.selectionStart)
        val end = positionToOffset(content, currentState.cursor.selectionEnd)
        replaceOffsets(start, end, text)
    }

    private fun replaceRange(
        range: TextRange,
        replacement: String,
    ) {
        replaceOffsets(
            positionToOffset(currentState.content, range.start),
            positionToOffset(currentState.content, range.end),
            replacement,
        )
    }

    private fun replaceOffsets(
        start: Int,
        end: Int,
        replacement: String,
    ) {
        val safeStart = minOf(start, end).coerceIn(0, currentState.content.length)
        val safeEnd = maxOf(start, end).coerceIn(safeStart, currentState.content.length)
        val updated = currentState.content.replaceRange(safeStart, safeEnd, replacement)
        updateContent(updated, coalesceUndo = false)
        moveCursorToOffset(updated, safeStart + replacement.length)
    }

    private fun backspace() {
        val cursor = currentState.cursor
        if (cursor.hasSelection) {
            replaceOffsets(
                positionToOffset(currentState.content, cursor.selectionStart),
                positionToOffset(currentState.content, cursor.selectionEnd),
                "",
            )
            return
        }
        val offset = positionToOffset(currentState.content, cursor.position)
        if (offset > 0) replaceOffsets(offset - 1, offset, "")
    }

    private fun deleteForward() {
        val cursor = currentState.cursor
        if (cursor.hasSelection) {
            replaceOffsets(
                positionToOffset(currentState.content, cursor.selectionStart),
                positionToOffset(currentState.content, cursor.selectionEnd),
                "",
            )
            return
        }
        val offset = positionToOffset(currentState.content, cursor.position)
        if (offset < currentState.content.length) replaceOffsets(offset, offset + 1, "")
    }

    private fun moveCursorByOffset(delta: Int) {
        val currentOffset = positionToOffset(currentState.content, currentState.cursor.position)
        moveCursorToOffset(currentState.content, currentOffset + delta)
    }

    private fun moveCursorVertically(delta: Int) {
        val lines = currentState.content.lines()
        val position = currentState.cursor.position
        val line = (position.line + delta).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        moveCursor(TextPosition(line, position.column.coerceAtMost(lines.getOrElse(line) { "" }.length)), true)
    }

    private fun moveCursorToLineBoundary(end: Boolean) {
        val lines = currentState.content.lines()
        val line =
            currentState.cursor.position.line
                .coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        moveCursor(TextPosition(line, if (end) lines.getOrElse(line) { "" }.length else 0), true)
    }

    private fun select(range: TextRange) {
        updateState {
            copy(
                cursor = Cursor(position = range.end, anchor = range.start),
                caretSyncVersion = caretSyncVersion + 1,
            )
        }
        updateActiveDocumentSession { copy(cursor = currentState.cursor) }
    }

    private fun selectAll() {
        select(TextRange(TextPosition.Zero, offsetToPosition(currentState.content, currentState.content.length)))
    }

    private fun clearSelection() {
        updateState { copy(cursor = cursor.clearSelection(), caretSyncVersion = caretSyncVersion + 1) }
        updateActiveDocumentSession { copy(cursor = currentState.cursor) }
    }

    private fun extendSelectionByOffset(delta: Int) {
        val offset = positionToOffset(currentState.content, currentState.cursor.position)
        extendSelectionTo(offsetToPosition(currentState.content, offset + delta))
    }

    private fun extendSelectionVertically(delta: Int) {
        val lines = currentState.content.lines()
        val position = currentState.cursor.position
        val line = (position.line + delta).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        extendSelectionTo(TextPosition(line, position.column.coerceAtMost(lines.getOrElse(line) { "" }.length)))
    }

    private fun extendSelectionTo(position: TextPosition) {
        updateState {
            copy(
                cursor = cursor.selectTo(position),
                caretSyncVersion = caretSyncVersion + 1,
            )
        }
        updateActiveDocumentSession { copy(cursor = currentState.cursor) }
    }

    private suspend fun copySelection() {
        val cursor = currentState.cursor
        if (!cursor.hasSelection) return
        val start = positionToOffset(currentState.content, cursor.selectionStart)
        val end = positionToOffset(currentState.content, cursor.selectionEnd)
        emitEffect(EditorEffect.CopyToClipboard(currentState.content.substring(start, end)))
    }

    private suspend fun cutSelection() {
        val cursor = currentState.cursor
        if (!cursor.hasSelection) return
        copySelection()
        replaceOffsets(
            positionToOffset(currentState.content, cursor.selectionStart),
            positionToOffset(currentState.content, cursor.selectionEnd),
            "",
        )
    }

    private fun goToLine(lineNumber: Int) {
        val targetLine = lineNumber.coerceIn(1, currentState.lineCount)
        updateState {
            copy(
                scrollLine = targetLine,
                caretSyncVersion = caretSyncVersion + 1,
            )
        }
        updateActiveDocumentSession { copy(scrollLine = currentState.scrollLine) }
    }

    private fun toggleLineNumbers() {
        updateState { copy(showLineNumbers = !showLineNumbers) }
    }

    private fun toggleMinimap() {
        updateState { copy(showMinimap = !showMinimap) }
    }

    private fun toggleWordWrap() {
        updateState { copy(wordWrap = !wordWrap) }
    }

    /**
     * Computes fixes for the caret position and opens the popup.
     *
     * A notification is shown when nothing applies, so Alt+Enter always gives
     * feedback rather than appearing to do nothing.
     */
    private suspend fun requestQuickFixes() {
        val path = currentState.activeTab?.uri?.toPath() ?: return
        val position = currentState.cursor.position
        val fixes =
            quickFixProvider?.quickFixes(path, currentState.content, position).orEmpty() +
                languageCodeActions(position)

        if (fixes.isEmpty()) {
            emitEffect(EditorEffect.ShowNotification("No quick fixes here", NotificationType.INFO))
            return
        }
        updateState {
            copy(
                quickFixState =
                    QuickFixState(
                        isVisible = true,
                        fixes = fixes,
                        selectedIndex = 0,
                        position = position,
                    ),
            )
        }
    }

    /**
     * Code actions offered by the language provider (an LSP server, when one is
     * configured) for the caret position.
     *
     * Multi-file actions are retained as workspace edits and applied atomically
     * when the user selects the corresponding quick fix.
     */
    private suspend fun languageCodeActions(position: TextPosition): List<QuickFix> {
        val registry = languageRegistry ?: return emptyList()
        val document = TextDocumentAdapter(currentState)
        if (currentState.activeDocumentUri == null) return emptyList()
        val caretRange = TextRange(position, position)

        val actions =
            runCatching {
                registry.provideCodeActions(
                    document = document,
                    range = caretRange,
                    context = CodeActionContext(diagnostics = emptyList()),
                )
            }.getOrNull() ?: return emptyList()

        pendingWorkspaceQuickFixes = emptyList()
        return actions.mapNotNull { action ->
            val workspaceEdit = action.edit ?: return@mapNotNull null
            if (workspaceEdit.changes.values.all { edits -> edits.isEmpty() }) return@mapNotNull null
            val fix = QuickFix(title = action.title, edits = emptyList())
            pendingWorkspaceQuickFixes = pendingWorkspaceQuickFixes + (fix to workspaceEdit)
            fix
        }
    }

    /** Applies an LSP-requested multi-document edit to live buffers and files. */
    private suspend fun applyWorkspaceEdit(
        label: String?,
        edit: LspWorkspaceEdit,
    ): Boolean =
        runCatching {
            val editsByUri = mutableMapOf<String, MutableList<LspTextEdit>>()
            edit.changes.orEmpty().forEach { (uri, edits) ->
                editsByUri.getOrPut(uri, ::mutableListOf).addAll(edits)
            }
            edit.documentChanges.orEmpty().forEach { documentEdit ->
                editsByUri
                    .getOrPut(documentEdit.textDocument.uri, ::mutableListOf)
                    .addAll(documentEdit.edits)
            }
            if (editsByUri.isEmpty()) return@runCatching true

            val prepared =
                editsByUri.map { (uri, edits) ->
                    val path = workspaceEditPath(uri)
                    val session = documentSessions[uri]
                    val content =
                        when {
                            currentState.activeDocumentUri?.value == uri -> currentState.content
                            session != null -> session.content
                            else -> withContext(Dispatchers.IO) { fileSystem.readText(path).getOrThrow() }
                        }
                    PreparedWorkspaceEdit(
                        uri = DocumentUri(uri),
                        path = path,
                        originalContent = content,
                        updatedContent = applyLspTextEdits(content, edits),
                        openSession = session,
                    )
                }

            val closedChanges = prepared.filter { it.openSession == null }
            if (closedChanges.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    closedChanges.forEach { change ->
                        fileSystem.writeText(change.path, change.updatedContent).getOrThrow()
                    }
                }
            }
            val openChanges = prepared.filter { it.openSession != null }
            if (openChanges.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    openChanges.forEach(::applyOpenWorkspaceEdit)
                }
            }
            prepared.forEach { clearDiagnostics(it.uri) }
            emitEffect(
                EditorEffect.ShowNotification(
                    label ?: "Applied workspace edit to ${prepared.size} file(s)",
                    NotificationType.SUCCESS,
                ),
            )
            true
        }.getOrElse { error ->
            logger.warn(error) { "Failed to apply LSP workspace edit" }
            emitEffect(EditorEffect.ShowError("Could not apply workspace edit: ${error.message}"))
            false
        }

    private fun workspaceEditPath(uri: String): String {
        require(uri.startsWith("file:")) { "Only file workspace edits are supported" }
        val target = File(URI(uri)).canonicalFile
        workspacePath?.let { rootPath ->
            val root = File(rootPath).canonicalFile
            require(target == root || target.path.startsWith(root.path + File.separator)) {
                "Workspace edit targets a file outside the project"
            }
        }
        return target.path
    }

    private fun applyOpenWorkspaceEdit(change: PreparedWorkspaceEdit) {
        val session = change.openSession ?: return
        undoManagerFor(change.uri.value).recordBeforeEdit(
            before = EditSnapshot(change.originalContent, session.cursor.position),
            nowMs = System.currentTimeMillis(),
            coalesce = false,
        )
        if (currentState.activeDocumentUri == change.uri) {
            applyContent(change.updatedContent)
            return
        }
        documentSessions[change.uri.value] =
            session.copy(content = change.updatedContent, version = session.version + 1)
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.uri == change.uri) tab.copy(isDirty = true) else tab
                    },
            )
        }
        syncDocumentChanged(change.uri, session.languageId, change.updatedContent)
    }

    private fun applyLspTextEdits(
        content: String,
        edits: List<LspTextEdit>,
    ): String {
        val replacements =
            edits.map { edit ->
                val start = lspOffset(content, edit.range.start.line, edit.range.start.character)
                val end = lspOffset(content, edit.range.end.line, edit.range.end.character)
                require(start <= end) { "Workspace edit has an inverted range" }
                TextReplacement(start, end, edit.newText)
            }
        val ordered = replacements.sortedBy(TextReplacement::startOffset)
        ordered.zipWithNext().forEach { (first, second) ->
            require(first.endOffset <= second.startOffset) { "Workspace edit contains overlapping ranges" }
        }
        return applyReplacements(content, replacements)
    }

    private fun lspOffset(
        content: String,
        line: Int,
        character: Int,
    ): Int {
        require(line >= 0 && character >= 0) { "Workspace edit has a negative position" }
        var currentLine = 0
        var lineStart = 0
        while (currentLine < line) {
            val newline = content.indexOf('\n', lineStart)
            require(newline >= 0) { "Workspace edit line is outside the document" }
            lineStart = newline + 1
            currentLine++
        }
        val newline = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
        val lineEnd = if (newline > lineStart && content[newline - 1] == '\r') newline - 1 else newline
        require(character <= lineEnd - lineStart) { "Workspace edit character is outside the line" }
        return lineStart + character
    }

    private data class PreparedWorkspaceEdit(
        val uri: DocumentUri,
        val path: String,
        val originalContent: String,
        val updatedContent: String,
        val openSession: DocumentSession?,
    )

    private fun moveQuickFixSelection(delta: Int) {
        val state = currentState.quickFixState
        if (!state.isVisible || state.fixes.isEmpty()) return
        val next = (state.selectedIndex + delta).coerceIn(0, state.fixes.lastIndex)
        updateState { copy(quickFixState = state.copy(selectedIndex = next)) }
    }

    /**
     * Applies a fix by rewriting the document through the normal content update,
     * so the change lands on the undo stack like a manual edit.
     */
    private suspend fun applyQuickFix(index: Int) {
        val fix = currentState.quickFixState.fixes.getOrNull(index) ?: return
        val workspaceEdit = pendingWorkspaceQuickFixes.firstOrNull { (candidate) -> candidate === fix }?.second
        if (workspaceEdit != null) {
            applyWorkspaceEdit(fix.title, workspaceEdit.toLspWorkspaceEdit())
            updateState { copy(quickFixState = QuickFixState()) }
            return
        }
        val updated = applyReplacements(currentState.content, fix.edits)
        updateState { copy(quickFixState = QuickFixState()) }
        updateContent(updated)
    }

    private fun LanguageWorkspaceEdit.toLspWorkspaceEdit(): LspWorkspaceEdit =
        LspWorkspaceEdit(
            changes =
                changes.mapValues { (_, edits) ->
                    edits.map { edit ->
                        LspTextEdit(
                            range =
                                LspRange(
                                    start = LspPosition(edit.range.start.line, edit.range.start.column),
                                    end = LspPosition(edit.range.end.line, edit.range.end.column),
                                ),
                            newText = edit.newText,
                        )
                    }
                },
        )

    private suspend fun handleGoToDefinition() {
        val service = navigationService
        if (service == null) {
            emitEffect(EditorEffect.ShowNotification("Navigation not available", NotificationType.INFO))
            return
        }

        val path = currentState.activeTab?.uri?.toPath()
        if (path == null) {
            emitEffect(EditorEffect.ShowNotification("No active file", NotificationType.INFO))
            return
        }

        val position = currentState.cursor.position
        val target = service.getDefinition(path, position)

        if (target != null) {
            // Record navigation before jumping
            service.recordNavigation(path, position)
            emitEffect(EditorEffect.NavigateTo(target.filePath, target.position))
        } else {
            emitEffect(EditorEffect.ShowNotification("No definition found", NotificationType.INFO))
        }
    }

    private suspend fun handleFindReferences() {
        val service = navigationService
        if (service == null) {
            emitEffect(EditorEffect.ShowNotification("Navigation not available", NotificationType.INFO))
            return
        }

        val path = currentState.activeTab?.uri?.toPath()
        if (path == null) {
            emitEffect(EditorEffect.ShowNotification("No active file", NotificationType.INFO))
            return
        }

        val position = currentState.cursor.position
        val result = service.findUsages(path, position, SearchScope.PROJECT)

        if (result != null && result.totalCount > 0) {
            emitEffect(EditorEffect.ShowUsages(result))
        } else {
            emitEffect(EditorEffect.ShowNotification("No references found", NotificationType.INFO))
        }
    }

    private suspend fun handleOpenSymbolSearch() {
        if (navigationService == null) {
            emitEffect(EditorEffect.ShowNotification("Navigation not available", NotificationType.INFO))
            return
        }
        emitEffect(EditorEffect.ShowSymbolSearch)
    }

    private suspend fun navigateTo(
        path: String,
        position: su.kidoz.jetaprog.common.text.TextPosition,
    ) {
        // Record navigation for back/forward support
        navigationService?.recordNavigation(path, position)
        openFile(path)
        goToLine(position.line + 1)
        moveCursor(position, synchronizeUi = true)
    }

    private fun tokenize(
        content: String,
        languageId: LanguageId,
        documentKey: String? = null,
    ): TokenList {
        // Highlighting a very large document blocks the UI on every edit
        if (content.length > MAX_HIGHLIGHT_CONTENT_LENGTH) {
            layeredHighlighter.setBaseTokens(TokenList(emptyList()))
            return TokenList(emptyList())
        }

        val lexer = getLexerForLanguage(languageId)
        if (lexer == null) {
            layeredHighlighter.setBaseTokens(TokenList(emptyList()))
            return TokenList(emptyList())
        }

        // Lex once — incrementally when the document is known — and feed the
        // result to the layered highlighter, which overlays LSP semantic
        // tokens when they arrive.
        val tokens =
            if (documentKey != null) {
                incrementalTokenizers
                    .getOrPut("$documentKey:${lexer.languageId}") { IncrementalTokenizer(lexer) }
                    .tokenize(content)
            } else {
                lexer.tokenize(content)
            }
        layeredHighlighter.setBaseTokens(tokens)
        return tokens
    }

    private fun getLexerForLanguage(languageId: LanguageId): Lexer? {
        val id =
            when (languageId) {
                LanguageId.KOTLIN -> "kotlin"
                LanguageId.VALA -> "vala"
                LanguageId.JAVA -> "java"
                LanguageId.RUST -> "rust"
                LanguageId.C -> "c"
                LanguageId.CPP -> "cpp"
                LanguageId.MESON -> "meson"
                LanguageId.CMAKE -> "cmake"
                LanguageId.XML -> "xml"
                LanguageId.TOML -> "toml"
                LanguageId.MARKDOWN -> "markdown"
                LanguageId.PYTHON -> "python"
                LanguageId.GITIGNORE -> "gitignore"
                else -> null
            }
        return id?.let { LexerRegistry.get(it) }
    }

    // ========================================================================
    // Completion Methods
    // ========================================================================

    private fun requestCompletion(
        triggerKind: CompletionTriggerKind,
        triggerCharacter: Char?,
        filterTextOverride: String?,
        smart: Boolean = false,
    ) {
        // Cancel any pending completion request
        completionJob?.cancel()

        // Prefer override from UI to avoid stale content during async updates
        val filterText =
            filterTextOverride
                ?: currentState.completionState.filterText.ifEmpty {
                    extractCurrentIdentifierPrefix()
                }

        updateState {
            copy(
                completionState =
                    CompletionState(
                        isVisible = true,
                        // Loading is raised only once the request is actually in flight,
                        // so a debounced keystroke does not flash an empty popup.
                        isLoading = triggerKind == CompletionTriggerKind.Invoked,
                        triggerPosition = cursor.position,
                        triggerKind = triggerKind,
                        triggerCharacter = triggerCharacter,
                        filterText = filterText,
                    ),
            )
        }

        // Launch completion request with debounce for auto-triggered completions
        completionJob =
            viewModelScope.launch {
                // Debounce auto-triggered completions to avoid thrashing LSP servers
                if (triggerKind != CompletionTriggerKind.Invoked) {
                    delay(COMPLETION_DEBOUNCE_MS)
                    updateState { copy(completionState = completionState.copy(isLoading = true)) }
                }
                val completionItems = getCompletionItems()
                unfilteredCompletionItems = completionItems
                // Apply latest filter to the items (may have updated while request was in flight)
                val activeFilter = currentState.completionState.filterText
                val filteredItems =
                    if (activeFilter.isNotEmpty()) {
                        completionController.filterItems(completionItems, activeFilter)
                    } else {
                        completionItems
                    }

                // Smart mode narrows what the provider already ranked, rather than
                // sourcing separately, so the server's semantic ordering is preserved.
                val smartResult =
                    if (smart) {
                        SmartCompletionFilter.apply(filteredItems, inferExpectedType())
                    } else {
                        null
                    }
                val itemsToShow = smartResult?.items ?: filteredItems

                updateState {
                    copy(
                        completionState =
                            completionState.copy(
                                items = itemsToShow,
                                isLoading = false,
                                selectedIndex = 0,
                                isVisible = itemsToShow.isNotEmpty(),
                                smartTypeName = smartResult?.takeIf { it.narrowed }?.expectedTypeName,
                            ),
                    )
                }
            }
    }

    /**
     * Infers the type expected at the caret, for smart completion.
     */
    private fun inferExpectedType(): ExpectedTypeContext {
        val content = currentState.content
        val position = currentState.cursor.position
        return ExpectedTypeInference.inferAt(content, positionToOffset(content, position.line, position.column))
    }

    /**
     * Extract the identifier prefix at the current cursor position.
     */
    private fun extractCurrentIdentifierPrefix(): String {
        val content = currentState.content
        val position = currentState.cursor.position
        val lines = content.lines()

        if (position.line >= lines.size) return ""

        val line = lines[position.line]
        if (position.column > line.length) return ""

        // Find the start of the identifier
        var start = position.column - 1
        while (start >= 0 && (line[start].isLetterOrDigit() || line[start] == '_')) {
            start--
        }

        return line.substring(start + 1, position.column)
    }

    private suspend fun getCompletionItems(): List<CompletionItem> {
        val registry = languageRegistry
        if (registry != null) {
            return withContext(Dispatchers.Default) {
                val document = TextDocumentAdapter(currentState)
                val context =
                    CompletionContext(
                        triggerKind = currentState.completionState.triggerKind,
                        triggerCharacter = currentState.completionState.triggerCharacter,
                    )

                val result =
                    registry.provideCompletions(
                        document,
                        currentState.cursor.position,
                        context,
                    )
                result.items.ifEmpty { getSampleCompletions(currentState.languageId) }
            }
        }
        // Fallback to sample completions if no registry
        return withContext(Dispatchers.Default) {
            getSampleCompletions(currentState.languageId)
        }
    }

    /**
     * Sample completions for demonstration.
     * TODO: Replace with actual language service integration.
     */
    private fun getSampleCompletions(languageId: LanguageId): List<CompletionItem> =
        when (languageId) {
            LanguageId.KOTLIN -> {
                listOf(
                    // Kotlin keywords
                    CompletionItem(label = "fun", kind = CompletionItemKind.Keyword, detail = "function"),
                    CompletionItem(label = "val", kind = CompletionItemKind.Keyword, detail = "read-only variable"),
                    CompletionItem(label = "var", kind = CompletionItemKind.Keyword, detail = "mutable variable"),
                    CompletionItem(label = "class", kind = CompletionItemKind.Keyword, detail = "class declaration"),
                    CompletionItem(label = "data class", kind = CompletionItemKind.Keyword, detail = "data class"),
                    CompletionItem(label = "object", kind = CompletionItemKind.Keyword, detail = "singleton object"),
                    CompletionItem(label = "interface", kind = CompletionItemKind.Keyword, detail = "interface"),
                    CompletionItem(
                        label = "sealed",
                        kind = CompletionItemKind.Keyword,
                        detail = "sealed class/interface",
                    ),
                    CompletionItem(label = "suspend", kind = CompletionItemKind.Keyword, detail = "suspend function"),
                    CompletionItem(label = "inline", kind = CompletionItemKind.Keyword, detail = "inline function"),
                    // Common functions
                    CompletionItem(
                        label = "println",
                        kind = CompletionItemKind.Function,
                        detail = "Print with newline",
                        insertText = "println(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "print",
                        kind = CompletionItemKind.Function,
                        detail = "Print without newline",
                        insertText = "print(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "listOf",
                        kind = CompletionItemKind.Function,
                        detail = "Create immutable list",
                        insertText = "listOf(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "mutableListOf",
                        kind = CompletionItemKind.Function,
                        detail = "Create mutable list",
                        insertText = "mutableListOf(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "mapOf",
                        kind = CompletionItemKind.Function,
                        detail = "Create immutable map",
                        insertText = "mapOf(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "setOf",
                        kind = CompletionItemKind.Function,
                        detail = "Create immutable set",
                        insertText = "setOf(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    // Compose completions
                    CompletionItem(
                        label = "Column",
                        kind = CompletionItemKind.Function,
                        detail = "Compose vertical layout",
                        insertText = "Column {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "Row",
                        kind = CompletionItemKind.Function,
                        detail = "Compose horizontal layout",
                        insertText = "Row {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "Box",
                        kind = CompletionItemKind.Function,
                        detail = "Compose stack layout",
                        insertText = "Box {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "Text",
                        kind = CompletionItemKind.Function,
                        detail = "Compose text",
                        insertText = "Text(\"\$0\")",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "Button",
                        kind = CompletionItemKind.Function,
                        detail = "Compose button",
                        insertText = "Button(onClick = { \$1 }) {\n    Text(\"\$0\")\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "LazyColumn",
                        kind = CompletionItemKind.Function,
                        detail = "Compose lazy vertical list",
                        insertText = "LazyColumn {\n    items(\$1) { item ->\n        \$0\n    }\n}",
                        insertTextIsSnippet = true,
                    ),
                    // Modifier chain
                    CompletionItem(
                        label = "fillMaxWidth",
                        kind = CompletionItemKind.Function,
                        detail = "Modifier.fillMaxWidth()",
                    ),
                    CompletionItem(
                        label = "fillMaxHeight",
                        kind = CompletionItemKind.Function,
                        detail = "Modifier.fillMaxHeight()",
                    ),
                    CompletionItem(
                        label = "fillMaxSize",
                        kind = CompletionItemKind.Function,
                        detail = "Modifier.fillMaxSize()",
                    ),
                    CompletionItem(
                        label = "padding",
                        kind = CompletionItemKind.Function,
                        detail = "Modifier.padding()",
                        insertText = "padding(\$0.dp)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "background",
                        kind = CompletionItemKind.Function,
                        detail = "Modifier.background()",
                        insertText = "background(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    // Ktor completions
                    CompletionItem(
                        label = "get",
                        kind = CompletionItemKind.Function,
                        detail = "Ktor GET route",
                        insertText = "get(\"\$1\") {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "post",
                        kind = CompletionItemKind.Function,
                        detail = "Ktor POST route",
                        insertText = "post(\"\$1\") {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "put",
                        kind = CompletionItemKind.Function,
                        detail = "Ktor PUT route",
                        insertText = "put(\"\$1\") {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "delete",
                        kind = CompletionItemKind.Function,
                        detail = "Ktor DELETE route",
                        insertText = "delete(\"\$1\") {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "routing",
                        kind = CompletionItemKind.Function,
                        detail = "Ktor routing block",
                        insertText = "routing {\n    \$0\n}",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "call.respond",
                        kind = CompletionItemKind.Function,
                        detail = "Respond to request",
                        insertText = "call.respond(\$0)",
                        insertTextIsSnippet = true,
                    ),
                    CompletionItem(
                        label = "call.respondText",
                        kind = CompletionItemKind.Function,
                        detail = "Respond with text",
                        insertText = "call.respondText(\"\$0\")",
                        insertTextIsSnippet = true,
                    ),
                )
            }

            else -> {
                listOf(
                    CompletionItem(label = "if", kind = CompletionItemKind.Keyword, detail = "if statement"),
                    CompletionItem(label = "else", kind = CompletionItemKind.Keyword, detail = "else clause"),
                    CompletionItem(label = "for", kind = CompletionItemKind.Keyword, detail = "for loop"),
                    CompletionItem(label = "while", kind = CompletionItemKind.Keyword, detail = "while loop"),
                    CompletionItem(label = "return", kind = CompletionItemKind.Keyword, detail = "return statement"),
                )
            }
        }

    private fun applyCompletion(item: CompletionItem) {
        val content = currentState.content
        val cursorPosition = currentState.cursor.position

        // Calculate offset from current cursor position
        val cursorOffset = positionToOffset(content, cursorPosition.line, cursorPosition.column)

        // Prefer the range the language server supplied. Its edit is authoritative and
        // covers spans an identifier scan cannot, such as an include path or a qualified
        // name; the local scan is only a fallback for providers that send no range.
        val (replaceStart, replaceEnd) =
            item.range?.let { range ->
                positionToOffset(content, range.start.line, range.start.column) to
                    positionToOffset(content, range.end.line, range.end.column)
            } ?: completionController.getReplacementRange(content, cursorOffset)

        // Get current line's indentation
        val currentLineStart = content.lastIndexOf('\n', replaceStart - 1) + 1
        val lineContent = content.substring(currentLineStart, replaceStart)
        val indentation = lineContent.takeWhile { it == ' ' || it == '\t' }

        // Get the text to insert
        val insertText = item.insertText

        // Expand snippet placeholders and apply indentation to multi-line text.
        val expanded = if (item.insertTextIsSnippet) SnippetExpander.expand(insertText) else null
        val textToInsert = applyIndentation(expanded?.text ?: insertText, indentation)

        // Build new content: before replacement + completion text + after replacement
        val newContent =
            buildString {
                append(content.substring(0, replaceStart))
                append(textToInsert)
                append(content.substring(replaceEnd))
            }

        // Move the caret first: the editor syncs its text field when the content
        // changes and reads the caret from state, so updating content first would
        // leave the caret at its pre-completion offset.
        // Snippets ask for a caret inside the inserted text (between a call's
        // parentheses); everything else lands after it.
        val caretOffset = replaceStart + (expanded?.caretOffset ?: textToInsert.length)
        moveCursorToOffset(newContent, caretOffset)
        updateContent(newContent)

        // Dismiss completion popup
        dismissCompletion()

        applyCompletionImports(item, newContent)
    }

    /**
     * Adds any imports the accepted completion needs.
     *
     * Edits supplied by the language server are applied as given; otherwise the
     * auto-import provider resolves the symbol against the project. Running
     * after the insertion keeps both changes on the undo stack.
     */
    private fun applyCompletionImports(
        item: su.kidoz.jetaprog.common.completion.CompletionItem,
        contentAfterInsert: String,
    ) {
        val serverEdits = item.additionalTextEdits
        if (serverEdits.isNotEmpty()) {
            val replacements =
                serverEdits.map { edit ->
                    TextReplacement(
                        startOffset = edit.range.start.toOffset(contentAfterInsert),
                        endOffset = edit.range.end.toOffset(contentAfterInsert),
                        newText = edit.newText,
                    )
                }
            applyEditsKeepingCaret(contentAfterInsert, replacements)
            return
        }

        val provider = autoImportProvider ?: return
        val path = currentState.activeTab?.uri?.toPath() ?: return
        viewModelScope.launch {
            val edit = provider.importEditFor(path, contentAfterInsert, item.label) ?: return@launch
            // The document may have moved on while resolving; only apply to what we measured.
            if (currentState.content != contentAfterInsert) return@launch
            applyEditsKeepingCaret(contentAfterInsert, listOf(edit))
        }
    }

    /**
     * Applies [edits] and shifts the caret so it stays on the same text.
     *
     * An inserted import sits above the caret and pushes everything below it
     * down; without this the caret would keep its old offset and end up a line
     * off from where the user was typing.
     */
    private fun applyEditsKeepingCaret(
        content: String,
        edits: List<TextReplacement>,
    ) {
        val updated = applyReplacements(content, edits)
        val position = currentState.cursor.position
        val caret = positionToOffset(content, position.line, position.column)
        val shift =
            edits
                .filter { it.endOffset <= caret }
                .sumOf { it.newText.length - (it.endOffset - it.startOffset) }
        moveCursorToOffset(updated, caret + shift)
        updateContent(updated)
    }

    /** Converts a position to a character offset in [content]. */
    private fun su.kidoz.jetaprog.common.text.TextPosition.toOffset(content: String): Int {
        val lines = content.lines()
        if (line >= lines.size) return content.length
        val before = lines.take(line).sumOf { it.length + 1 }
        return (before + column).coerceIn(0, content.length)
    }

    /**
     * Apply indentation to multi-line text.
     * The first line keeps its original form, subsequent lines get the indentation prepended.
     */
    private fun applyIndentation(
        text: String,
        indentation: String,
    ): String {
        if (!text.contains('\n') || indentation.isEmpty()) {
            return text
        }

        val lines = text.split('\n')
        return lines
            .mapIndexed { index, line ->
                if (index == 0) line else indentation + line
            }.joinToString("\n")
    }

    /**
     * Convert line/column position to character offset.
     */
    private fun positionToOffset(
        content: String,
        line: Int,
        column: Int,
    ): Int = positionToOffset(content, TextPosition(line, column))

    private fun dismissCompletion() {
        completionJob?.cancel()
        completionJob = null
        updateState {
            copy(completionState = CompletionState())
        }
    }

    private fun completionMoveUp() {
        updateState {
            val newIndex = (completionState.selectedIndex - 1).coerceAtLeast(0)
            copy(completionState = completionState.copy(selectedIndex = newIndex))
        }
    }

    private fun completionMoveDown() {
        updateState {
            val newIndex =
                (completionState.selectedIndex + 1)
                    .coerceAtMost((completionState.items.size - 1).coerceAtLeast(0))
            copy(completionState = completionState.copy(selectedIndex = newIndex))
        }
    }

    private fun selectCompletionItem(index: Int) {
        updateState {
            val validIndex = index.coerceIn(0, (completionState.items.size - 1).coerceAtLeast(0))
            copy(completionState = completionState.copy(selectedIndex = validIndex))
        }
    }

    private fun updateCompletionFilter(filterText: String) {
        // The server truncated its answer, so the items it withheld can only be obtained by
        // asking again with the longer prefix - filtering locally would hide them forever.
        if (currentState.completionState.isIncomplete) {
            requestCompletion(CompletionTriggerKind.TriggerForIncompleteCompletions, null, filterText)
            return
        }

        val filtered = completionController.filterItems(unfilteredCompletionItems, filterText)
        updateState {
            copy(
                completionState =
                    completionState.copy(
                        filterText = filterText,
                        items = filtered,
                        selectedIndex = 0,
                    ),
            )
        }
    }

    /**
     * Moves the caret to a character offset in [content].
     */
    private fun moveCursorToOffset(
        content: String,
        offset: Int,
    ) {
        moveCursor(offsetToPosition(content, offset), synchronizeUi = true)
    }

    private fun moveCursor(
        position: TextPosition,
        synchronizeUi: Boolean = false,
    ) {
        updateState {
            copy(
                cursor = cursor.moveTo(position),
                caretSyncVersion = if (synchronizeUi) caretSyncVersion + 1 else caretSyncVersion,
            )
        }
        updateActiveDocumentSession { copy(cursor = currentState.cursor) }
    }

    private fun setCompletionItems(
        items: List<CompletionItem>,
        isIncomplete: Boolean,
    ) {
        updateState {
            copy(
                completionState =
                    completionState.copy(
                        items = items,
                        isIncomplete = isIncomplete,
                        isLoading = false,
                        selectedIndex = 0,
                        isVisible = items.isNotEmpty(),
                    ),
            )
        }
    }

    // ========================================================================
    // Hover Methods
    // ========================================================================

    private fun requestHover(position: su.kidoz.jetaprog.common.text.TextPosition) {
        // Cancel any pending hover request
        hoverJob?.cancel()

        // A popup anchored to a different position is stale the moment the pointer
        // moves, so drop it rather than letting it trail the cursor.
        if (currentState.hoverState.position != position) {
            clearHoverState()
        }

        hoverJob =
            viewModelScope.launch {
                // Wait for the pointer to settle before doing anything visible. Showing a
                // "Loading..." popup up front meant every pointer move over code produced
                // one, and a moving pointer restarted it before it could ever resolve.
                delay(HOVER_DEBOUNCE_MS)

                // Only advertise loading once the request is genuinely slow; a fast reply
                // would otherwise flash a spinner for a few milliseconds.
                val loadingIndicator =
                    launch {
                        delay(HOVER_LOADING_INDICATOR_MS)
                        updateState {
                            copy(hoverState = HoverState(isLoading = true, position = position))
                        }
                    }

                val hover =
                    try {
                        languageRegistry?.provideHover(TextDocumentAdapter(currentState), position)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A failed provider must not strand the popup on "Loading...".
                        logger.debug { "Hover request failed: ${e.message}" }
                        null
                    } finally {
                        loadingIndicator.cancel()
                    }

                val contents = diagnosticsAt(position) + hover?.contents.orEmpty()
                if (contents.isEmpty()) {
                    clearHoverState()
                } else {
                    updateState {
                        copy(
                            hoverState =
                                HoverState(
                                    isVisible = true,
                                    contents = contents,
                                    position = position,
                                    range = hover?.range,
                                    isLoading = false,
                                ),
                        )
                    }
                    emitEffect(EditorEffect.HoverLoaded(position))
                }
            }
    }

    /**
     * Diagnostic messages at the given position, rendered as hover content.
     */
    private fun diagnosticsAt(position: TextPosition): List<MarkedString> =
        currentState.diagnostics
            .filter { position in it.range }
            .map { diagnostic ->
                val label =
                    when (diagnostic.severity) {
                        DiagnosticSeverity.ERROR -> "Error"
                        DiagnosticSeverity.WARNING -> "Warning"
                        DiagnosticSeverity.INFORMATION -> "Info"
                        DiagnosticSeverity.HINT -> "Hint"
                    }
                val source = diagnostic.source?.let { " ($it)" } ?: ""
                MarkedString.Markdown("**$label**$source: ${diagnostic.message}")
            }

    private fun setHoverContent(
        contents: List<su.kidoz.jetaprog.plugins.api.language.MarkedString>,
        range: su.kidoz.jetaprog.common.text.TextRange?,
    ) {
        updateState {
            copy(
                hoverState =
                    hoverState.copy(
                        isVisible = true,
                        contents = contents,
                        range = range,
                        isLoading = false,
                    ),
            )
        }
    }

    /**
     * Resets the hover popup without touching [hoverJob], so it is safe to call from
     * inside the hover coroutine itself.
     */
    private fun clearHoverState() {
        updateState {
            copy(hoverState = HoverState())
        }
    }

    private fun dismissHover() {
        hoverJob?.cancel()
        hoverJob = null
        clearHoverState()
    }

    // ========================================================================
    // Signature Help Methods
    // ========================================================================

    private fun requestSignatureHelp(
        triggerCharacter: Char?,
        isRetrigger: Boolean,
    ) {
        // Cancel any pending signature help request
        signatureHelpJob?.cancel()

        // Show loading state
        updateState {
            copy(
                signatureHelpState =
                    SignatureHelpState(
                        isLoading = true,
                        position = cursor.position,
                    ),
            )
        }

        // Launch signature help request
        signatureHelpJob =
            viewModelScope.launch {
                val registry = languageRegistry
                if (registry != null) {
                    val document = TextDocumentAdapter(currentState)
                    val context =
                        SignatureHelpContext(
                            triggerKind =
                                when {
                                    triggerCharacter != null -> SignatureHelpTriggerKind.TriggerCharacter
                                    isRetrigger -> SignatureHelpTriggerKind.ContentChange
                                    else -> SignatureHelpTriggerKind.Invoked
                                },
                            triggerCharacter = triggerCharacter,
                            isRetrigger = isRetrigger,
                        )
                    val signatureHelp = registry.provideSignatureHelp(document, currentState.cursor.position, context)
                    if (signatureHelp != null && signatureHelp.signatures.isNotEmpty()) {
                        val signatures =
                            signatureHelp.signatures.map { sig ->
                                SignatureInfo(
                                    label = sig.label,
                                    documentation = sig.documentation,
                                    parameters =
                                        sig.parameters.map { param ->
                                            SignatureParameter(
                                                label = param.label,
                                                documentation = param.documentation,
                                            )
                                        },
                                )
                            }
                        updateState {
                            copy(
                                signatureHelpState =
                                    SignatureHelpState(
                                        isVisible = true,
                                        signatures = signatures,
                                        activeSignature = signatureHelp.activeSignature,
                                        activeParameter = signatureHelp.activeParameter,
                                        position = cursor.position,
                                        isLoading = false,
                                    ),
                            )
                        }
                        emitEffect(EditorEffect.SignatureHelpLoaded(currentState.cursor.position))
                    } else {
                        dismissSignatureHelp()
                    }
                } else {
                    dismissSignatureHelp()
                }
            }
    }

    private fun setSignatureHelp(
        signatures: List<SignatureInfo>,
        activeSignature: Int,
        activeParameter: Int,
    ) {
        updateState {
            copy(
                signatureHelpState =
                    signatureHelpState.copy(
                        isVisible = signatures.isNotEmpty(),
                        signatures = signatures,
                        activeSignature = activeSignature,
                        activeParameter = activeParameter,
                        isLoading = false,
                    ),
            )
        }
    }

    private fun updateActiveParameter(index: Int) {
        updateState {
            copy(
                signatureHelpState =
                    signatureHelpState.copy(
                        activeParameter = index.coerceAtLeast(0),
                    ),
            )
        }
    }

    private fun nextSignature() {
        updateState {
            val newIndex =
                (signatureHelpState.activeSignature + 1)
                    .coerceAtMost((signatureHelpState.signatures.size - 1).coerceAtLeast(0))
            copy(signatureHelpState = signatureHelpState.copy(activeSignature = newIndex))
        }
    }

    private fun previousSignature() {
        updateState {
            val newIndex = (signatureHelpState.activeSignature - 1).coerceAtLeast(0)
            copy(signatureHelpState = signatureHelpState.copy(activeSignature = newIndex))
        }
    }

    private fun dismissSignatureHelp() {
        signatureHelpJob?.cancel()
        signatureHelpJob = null
        updateState {
            copy(signatureHelpState = SignatureHelpState())
        }
    }

    // ========================================================================
    // Formatting Methods
    // ========================================================================

    private suspend fun formatDocument() {
        val content = currentState.content
        val languageId = currentState.languageId

        val formatter = FormatterRegistry.getFormatter(languageId)
        if (formatter == null) {
            emitEffect(
                EditorEffect.FormattingFailed("No formatter available for ${languageId.displayName}"),
            )
            return
        }

        val options =
            FormattingOptions(
                tabSize = 4,
                insertSpaces = true,
                trimTrailingWhitespace = true,
                insertFinalNewline = true,
            )

        val result =
            withContext(Dispatchers.Default) {
                formatter.format(content, options)
            }

        when (result) {
            is FormattingResult.Success -> {
                if (result.formattedText != content) {
                    updateContent(result.formattedText)
                    emitEffect(
                        EditorEffect.FormattingApplied(
                            originalLength = content.length,
                            newLength = result.formattedText.length,
                        ),
                    )
                    emitEffect(
                        EditorEffect.ShowNotification(
                            "Document formatted",
                            NotificationType.SUCCESS,
                        ),
                    )
                } else {
                    emitEffect(
                        EditorEffect.ShowNotification(
                            "Document already formatted",
                            NotificationType.INFO,
                        ),
                    )
                }
            }

            is FormattingResult.Failure -> {
                emitEffect(EditorEffect.FormattingFailed(result.error))
                emitEffect(EditorEffect.ShowError("Formatting failed: ${result.error}"))
            }
        }
    }

    private suspend fun formatSelection(range: su.kidoz.jetaprog.common.text.TextRange) {
        val content = currentState.content
        val languageId = currentState.languageId

        val formatter = FormatterRegistry.getFormatter(languageId)
        if (formatter == null) {
            emitEffect(
                EditorEffect.FormattingFailed("No formatter available for ${languageId.displayName}"),
            )
            return
        }

        val options =
            FormattingOptions(
                tabSize = 4,
                insertSpaces = true,
                trimTrailingWhitespace = true,
                insertFinalNewline = false,
            )

        val result =
            withContext(Dispatchers.Default) {
                formatter.formatRange(content, range, options)
            }

        when (result) {
            is FormattingResult.Success -> {
                if (result.formattedText != content) {
                    updateContent(result.formattedText)
                    emitEffect(
                        EditorEffect.FormattingApplied(
                            originalLength = content.length,
                            newLength = result.formattedText.length,
                        ),
                    )
                    emitEffect(
                        EditorEffect.ShowNotification(
                            "Selection formatted",
                            NotificationType.SUCCESS,
                        ),
                    )
                } else {
                    emitEffect(
                        EditorEffect.ShowNotification(
                            "Selection already formatted",
                            NotificationType.INFO,
                        ),
                    )
                }
            }

            is FormattingResult.Failure -> {
                emitEffect(EditorEffect.FormattingFailed(result.error))
                emitEffect(EditorEffect.ShowError("Formatting failed: ${result.error}"))
            }
        }
    }

    // ========================================================================
    // Language Detection
    // ========================================================================

    private fun detectLanguage(fileName: String): LanguageId {
        // Check for special filenames first
        val lowerFileName = fileName.lowercase()
        when {
            lowerFileName == "meson.build" || lowerFileName == "meson_options.txt" -> return LanguageId.MESON

            lowerFileName == "cmakelists.txt" || lowerFileName == "cmakecache.txt" -> return LanguageId.CMAKE

            lowerFileName == "cargo.toml" || lowerFileName == "cargo.lock" -> return LanguageId.TOML

            lowerFileName == "pom.xml" -> return LanguageId.XML

            // ".gitignore" itself, plus the "<name>.gitignore" templates some tools keep around.
            lowerFileName.endsWith(".gitignore") -> return LanguageId.GITIGNORE

            lowerFileName.endsWith(".sln") || lowerFileName.endsWith(".slnx") -> return LanguageId.MSBUILD
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> LanguageId.KOTLIN

            "java" -> LanguageId.JAVA

            "js" -> LanguageId.JAVASCRIPT

            "ts" -> LanguageId.TYPESCRIPT

            "py" -> LanguageId.PYTHON

            "cs", "csx" -> LanguageId.CSHARP

            "csproj", "fsproj", "vbproj", "props", "targets" -> LanguageId.MSBUILD

            "rs" -> LanguageId.RUST

            "go" -> LanguageId.GO

            // ".h" is shared between C and C++; clangd resolves the real dialect from
            // the compilation database, so map it to C like most editors do.
            "c", "h" -> LanguageId.C

            "cpp", "cc", "cxx", "c++", "cppm", "ixx", "ccm", "cxxm", "c++m" -> LanguageId.CPP

            "hpp", "hh", "hxx", "h++", "inl", "ipp", "tpp" -> LanguageId.CPP

            "json" -> LanguageId.JSON

            "yaml", "yml" -> LanguageId.YAML

            "toml" -> LanguageId.TOML

            "xml", "pom", "xsd", "xsl", "xslt", "svg" -> LanguageId.XML

            "html", "htm" -> LanguageId.HTML

            "css" -> LanguageId.CSS

            "cmake" -> LanguageId.CMAKE

            "md", "markdown" -> LanguageId.MARKDOWN

            "vala", "vapi" -> LanguageId.VALA

            else -> LanguageId.PLAIN_TEXT
        }
    }

    private fun DocumentUri.toPath(): String? =
        if (value.startsWith("file://")) {
            value.removePrefix("file://")
        } else {
            null
        }

    private fun syncDocumentOpened(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String,
    ) {
        val registry = languageRegistry
        if (registry == null && pluginEditorService == null) return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return
        if (!lspOpenDocuments.add(uriValue)) return

        viewModelScope.launch {
            // Fire language opened trigger (may activate pending plugins)
            activationEvents?.fireLanguageOpened(languageId.value)

            registry?.notifyDocumentOpened(uriValue, languageId.value, content)
            pluginDocument(uri, languageId, content).let { document ->
                pluginEditorService?.notifyDocumentOpened(document)
            }
        }
    }

    private fun syncDocumentChanged(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String,
    ) {
        val registry = languageRegistry
        if (registry == null && pluginEditorService == null) return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        viewModelScope.launch {
            if (!lspOpenDocuments.contains(uriValue)) {
                registry?.notifyDocumentOpened(uriValue, languageId.value, content)
                lspOpenDocuments.add(uriValue)
            } else {
                registry?.notifyDocumentChanged(uriValue, languageId.value, content)
            }
            pluginDocument(uri, languageId, content).let { document ->
                pluginEditorService?.notifyDocumentChanged(
                    TextDocumentChangeEvent(
                        document = document,
                        contentChanges =
                            listOf(
                                TextDocumentContentChange(
                                    range = TextRange(TextPosition.Zero, offsetToPosition(content, content.length)),
                                    text = content,
                                ),
                            ),
                    ),
                )
            }
        }
    }

    private fun syncDocumentSaved(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String?,
    ) {
        val registry = languageRegistry
        if (registry == null && pluginEditorService == null) return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        viewModelScope.launch {
            registry?.notifyDocumentSaved(uriValue, languageId.value, content)
            val savedContent = content ?: documentSessions[uriValue]?.content.orEmpty()
            pluginDocument(uri, languageId, savedContent).let { document ->
                pluginEditorService?.notifyDocumentSaved(document)
            }
        }
    }

    private fun syncDocumentClosed(
        uri: DocumentUri,
        languageId: LanguageId,
    ) {
        val registry = languageRegistry
        if (registry == null && pluginEditorService == null) return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        val document = pluginDocument(uri, languageId, documentSessions[uriValue]?.content.orEmpty())
        lspOpenDocuments.remove(uriValue)
        viewModelScope.launch {
            registry?.notifyDocumentClosed(uriValue, languageId.value)
            pluginEditorService?.notifyDocumentClosed(document)
        }
    }

    private fun pluginDocument(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String,
    ): TextDocumentAdapter {
        val tab =
            currentState.tabs.firstOrNull { it.uri == uri }
                ?: EditorTab(uri = uri, name = uri.value.substringAfterLast('/'))
        val version = documentSessions[uri.value]?.version ?: 1
        return TextDocumentAdapter(
            EditorState(
                tabs = listOf(tab),
                activeTabIndex = 0,
                activeDocumentUri = uri,
                content = content,
                documentVersion = version,
                languageId = languageId,
            ),
        )
    }

    // ========================================================================
    // Lint Diagnostics
    // ========================================================================

    /**
     * What caused a lint pass to be requested, gating it on the matching
     * configuration flag.
     */
    private enum class LintTrigger {
        OPEN,
        TYPE,
        SAVE,
    }

    /**
     * Publish the merged LSP and lint diagnostics for the active document.
     */
    private fun refreshDiagnosticsState() {
        val activeUri = currentState.activeDocumentUri?.value
        val workspaceDiagnostics =
            (lspDiagnostics.keys + lintDiagnostics.keys)
                .distinct()
                .sorted()
                .flatMap { uri ->
                    (lspDiagnostics[uri].orEmpty() + lintDiagnostics[uri].orEmpty()).map { diagnostic ->
                        WorkspaceDiagnostic(DocumentUri(uri), diagnostic)
                    }
                }
        updateState {
            copy(
                diagnostics =
                    activeUri
                        ?.let { uri ->
                            lspDiagnostics[uri].orEmpty() + lintDiagnostics[uri].orEmpty()
                        }.orEmpty(),
                workspaceDiagnostics = workspaceDiagnostics,
            )
        }
    }

    private fun diagnosticsFor(uri: DocumentUri): List<Diagnostic> =
        lspDiagnostics[uri.value].orEmpty() + lintDiagnostics[uri.value].orEmpty()

    /**
     * Run the lint engine against the document and surface results as diagnostics.
     *
     * Skipped when an LSP server already provides diagnostics for the language,
     * to avoid reporting the same issues twice.
     */
    private fun scheduleLint(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String,
        trigger: LintTrigger,
    ) {
        val service = lintService ?: return
        val path = uri.toPath() ?: return
        val configuration = service.getConfiguration()

        if (!configuration.enabled || configuration.isExcluded(path)) return
        if (languageRegistry?.hasLspServer(languageId.value) == true) return
        val triggerEnabled =
            when (trigger) {
                LintTrigger.OPEN -> true
                LintTrigger.TYPE -> configuration.lintOnType
                LintTrigger.SAVE -> configuration.lintOnSave
            }
        if (!triggerEnabled) return

        lintJob?.cancel()
        lintJob =
            viewModelScope.launch {
                // Debounce while typing so analysis runs on settled content
                if (trigger == LintTrigger.TYPE) {
                    delay(configuration.lintOnTypeDelayMs)
                }
                val results =
                    withContext(Dispatchers.Default) {
                        service.lintFile(uri.value, languageId.value, content)
                    }
                lintDiagnostics[uri.value] = DiagnosticConverter.toDiagnostics(results)
                refreshDiagnosticsState()
            }
    }

    /**
     * Re-runs lint for the document currently in the editor, if any.
     *
     * Used when the available rules change under an already-open document.
     */
    private fun relintActiveDocument() {
        val uri = currentState.activeDocumentUri ?: return
        scheduleLint(uri, currentState.languageId, currentState.content, LintTrigger.OPEN)
    }

    private fun clearDiagnostics(uri: DocumentUri) {
        lspDiagnostics.remove(uri.value)
        lintDiagnostics.remove(uri.value)
        refreshDiagnosticsState()
    }

    private fun LanguageDiagnostic.toEditorDiagnostic(): Diagnostic =
        Diagnostic(
            range = range,
            message = message,
            severity = severity,
            source = source,
            code = code,
        )

    internal companion object {
        /**
         * Debounce delay for hover requests in milliseconds.
         */
        const val HOVER_DEBOUNCE_MS = 400L

        /**
         * How long a settled hover request may run before a loading indicator appears.
         */
        const val HOVER_LOADING_INDICATOR_MS = 150L

        /**
         * Debounce delay for auto-triggered completion requests in milliseconds.
         * Manual invocations (Ctrl+Space) are not debounced.
         */
        const val COMPLETION_DEBOUNCE_MS = 150L

        /**
         * Documents larger than this (in characters) are not syntax
         * highlighted to keep editing responsive.
         */
        const val MAX_HIGHLIGHT_CONTENT_LENGTH = 1_000_000

        private val TRAILING_WHITESPACE = Regex("[\\t ]+(?=\\r?$)", RegexOption.MULTILINE)
    }
}
