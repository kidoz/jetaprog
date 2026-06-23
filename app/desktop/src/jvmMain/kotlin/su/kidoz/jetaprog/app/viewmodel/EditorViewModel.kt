package su.kidoz.jetaprog.app.viewmodel

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
import su.kidoz.jetaprog.editor.completion.CompletionController
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.editor.search.FindMatcher
import su.kidoz.jetaprog.editor.state.CompletionState
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.state.EditorTab
import su.kidoz.jetaprog.editor.state.FindToggle
import su.kidoz.jetaprog.editor.state.HoverState
import su.kidoz.jetaprog.editor.state.NotificationType
import su.kidoz.jetaprog.editor.state.SignatureHelpState
import su.kidoz.jetaprog.editor.state.SignatureInfo
import su.kidoz.jetaprog.editor.state.SignatureParameter
import su.kidoz.jetaprog.editor.syntax.Diagnostic
import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerRegistry
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.cpp.CppLexer
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
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpContext
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpTriggerKind
import su.kidoz.jetaprog.plugins.kotlin.KotlinFormatter
import su.kidoz.jetaprog.plugins.runtime.activation.ActivationEventService
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

/**
 * ViewModel for the code editor.
 *
 * @param fileSystem The file system for reading/writing files.
 * @param settingsService Service for accessing IDE settings.
 * @param navigationService Service for code navigation features.
 * @param languageRegistry Registry for language features (completion, hover, etc.).
 * @param activationEvents Service for firing activation triggers when documents open.
 */
public class EditorViewModel(
    private val fileSystem: FileSystem,
    private val settingsService: SettingsService,
    private val navigationService: NavigationService? = null,
    private val languageRegistry: LanguageRegistry? = null,
    private val activationEvents: ActivationEventService? = null,
) : MviViewModel<EditorIntent, EditorState, EditorEffect>(EditorState()) {
    private val completionController = CompletionController()
    private var completionJob: Job? = null
    private var hoverJob: Job? = null
    private var signatureHelpJob: Job? = null
    private val lspOpenDocuments = mutableSetOf<String>()
    private val undoManagers = mutableMapOf<String, UndoManager>()

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
        LexerRegistry.register(CppLexer())
        LexerRegistry.register(MesonLexer())
        LexerRegistry.register(XmlLexer())
        LexerRegistry.register(TomlLexer())
        LexerRegistry.register(MarkdownLexer())
        LexerRegistry.register(PythonLexer())

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
            if (uri != currentState.activeDocumentUri?.value) return@onDiagnostics
            updateState {
                copy(diagnostics = diagnostics.map { it.toEditorDiagnostic() })
            }
        }

        // Observe settings changes
        viewModelScope.launch {
            settingsService.settings.collect { newSettings ->
                _settings.value = newSettings
            }
        }
    }

    override suspend fun handleIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.OpenFile -> {
                openFile(intent.path)
            }

            is EditorIntent.Save -> {
                saveCurrentFile()
            }

            is EditorIntent.SaveAs -> {
                saveAs(intent.path)
            }

            is EditorIntent.CloseTab -> {
                closeTab(intent.index)
            }

            is EditorIntent.CloseAllTabs -> {
                closeAllTabs()
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

            is EditorIntent.InsertText -> {
                insertText(intent.text)
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

            // Other intents not yet implemented
            else -> { /* TODO: Implement other intents */ }
        }
    }

    private suspend fun openFile(path: String) {
        updateState { copy(isLoading = true, error = null) }

        try {
            val content =
                withContext(Dispatchers.IO) {
                    fileSystem.readText(path).getOrThrow()
                }

            val fileName = File(path).name
            val languageId = detectLanguage(fileName)
            val uri = DocumentUri.file(path)

            // Check if file is already open
            val existingIndex = currentState.tabs.indexOfFirst { it.uri == uri }
            if (existingIndex >= 0) {
                switchTab(existingIndex)
                return
            }

            // Tokenize the content
            val tokens = tokenize(content, languageId)

            val newTab =
                EditorTab(
                    uri = uri,
                    name = fileName,
                    isDirty = false,
                )

            updateState {
                val newTabs = tabs + newTab
                copy(
                    tabs = newTabs,
                    activeTabIndex = newTabs.size - 1,
                    activeDocumentUri = uri,
                    content = content,
                    languageId = languageId,
                    tokens = tokens,
                    diagnostics = emptyList(),
                    isLoading = false,
                )
            }

            syncDocumentOpened(uri, languageId, content)
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

    private suspend fun saveCurrentFile() {
        val activeTab = currentState.activeTab ?: return
        val path = activeTab.uri.toPath() ?: return
        val uri = activeTab.uri
        val languageId = currentState.languageId

        updateState { copy(isSaving = true) }

        try {
            withContext(Dispatchers.IO) {
                fileSystem.writeText(path, currentState.content).getOrThrow()
            }

            updateState {
                val updatedTabs =
                    tabs.mapIndexed { index, tab ->
                        if (index == activeTabIndex) tab.copy(isDirty = false) else tab
                    }
                copy(tabs = updatedTabs, isSaving = false)
            }

            emitEffect(EditorEffect.FileSaved(path))
            syncDocumentSaved(uri, languageId, currentState.content)
            emitEffect(EditorEffect.ShowNotification("File saved", NotificationType.SUCCESS))
        } catch (e: Exception) {
            updateState { copy(isSaving = false, error = "Failed to save: ${e.message}") }
            emitEffect(EditorEffect.ShowError("Failed to save file: ${e.message}"))
        }
    }

    private suspend fun saveAs(path: String) {
        updateState { copy(isSaving = true) }

        try {
            withContext(Dispatchers.IO) {
                fileSystem.writeText(path, currentState.content).getOrThrow()
            }

            val fileName = File(path).name
            val uri = DocumentUri.file(path)
            val languageId = detectLanguage(fileName)

            updateState {
                val updatedTabs =
                    tabs.mapIndexed { index, tab ->
                        if (index == activeTabIndex) {
                            tab.copy(uri = uri, name = fileName, isDirty = false)
                        } else {
                            tab
                        }
                    }
                copy(
                    tabs = updatedTabs,
                    activeDocumentUri = uri,
                    languageId = languageId,
                    isSaving = false,
                )
            }

            syncDocumentOpened(uri, languageId, currentState.content)
            syncDocumentSaved(uri, languageId, currentState.content)
            emitEffect(EditorEffect.FileSaved(path))
        } catch (e: Exception) {
            updateState { copy(isSaving = false) }
            emitEffect(EditorEffect.ShowError("Failed to save file: ${e.message}"))
        }
    }

    private fun closeTab(index: Int) {
        if (index !in currentState.tabs.indices) return

        val tab = currentState.tabs[index]
        val newTabs = currentState.tabs.filterIndexed { i, _ -> i != index }
        val newActiveIndex =
            when {
                newTabs.isEmpty() -> -1
                index >= newTabs.size -> newTabs.size - 1
                else -> index
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
                )
            }
        } else {
            updateState { copy(tabs = newTabs, activeTabIndex = newActiveIndex) }
            if (newActiveIndex >= 0) {
                viewModelScope.launch {
                    loadTabContent(newActiveIndex)
                }
            }
        }

        undoManagers.remove(tab.uri.value)
        syncDocumentClosed(tab.uri, detectLanguage(tab.name))
        viewModelScope.launch {
            emitEffect(EditorEffect.FileClosed(tab.uri.value))
        }
    }

    private fun closeAllTabs() {
        currentState.tabs.forEach { tab ->
            syncDocumentClosed(tab.uri, detectLanguage(tab.name))
        }
        undoManagers.clear()
        updateState {
            copy(
                tabs = emptyList(),
                activeTabIndex = -1,
                activeDocumentUri = null,
                content = "",
                tokens = TokenList(emptyList()),
                diagnostics = emptyList(),
            )
        }
    }

    private suspend fun switchTab(index: Int) {
        if (index !in currentState.tabs.indices) return
        if (index == currentState.activeTabIndex) return

        updateState { copy(activeTabIndex = index) }
        loadTabContent(index)
    }

    private suspend fun loadTabContent(index: Int) {
        val tab = currentState.tabs.getOrNull(index) ?: return
        val path = tab.uri.toPath() ?: return

        updateState { copy(isLoading = true) }

        try {
            val content =
                withContext(Dispatchers.IO) {
                    fileSystem.readText(path).getOrThrow()
                }

            val languageId = detectLanguage(tab.name)
            val tokens = tokenize(content, languageId)

            updateState {
                copy(
                    activeDocumentUri = tab.uri,
                    content = content,
                    languageId = languageId,
                    tokens = tokens,
                    diagnostics = emptyList(),
                    isLoading = false,
                )
            }

            syncDocumentOpened(tab.uri, languageId, content)
        } catch (e: Exception) {
            updateState { copy(isLoading = false, error = "Failed to load file: ${e.message}") }
        }
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
        val tokens = tokenize(content, currentState.languageId)

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
                tokens = tokens,
                tabs = updatedTabs,
                cursor = newCursor?.let { cursor.moveTo(it) } ?: cursor,
            )
        }

        currentState.activeDocumentUri?.let { uri ->
            syncDocumentChanged(uri, currentState.languageId, content)
        }

        refreshFindMatches()
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
        val lines = currentState.content.lines()
        val line = currentState.cursor.position.line
        if (line !in lines.indices) return

        val newLines = lines.toMutableList().apply { removeAt(line) }
        val newLine = line.coerceAtMost((newLines.size - 1).coerceAtLeast(0))
        val newColumn =
            currentState.cursor.position.column
                .coerceAtMost(newLines.getOrElse(newLine) { "" }.length)

        updateContent(newLines.joinToString("\n"), coalesceUndo = false)
        updateState { copy(cursor = cursor.moveTo(TextPosition(newLine, newColumn))) }
    }

    private fun duplicateLine() {
        val lines = currentState.content.lines()
        val position = currentState.cursor.position
        if (position.line !in lines.indices) return

        val newLines = lines.toMutableList().apply { add(position.line + 1, lines[position.line]) }
        updateContent(newLines.joinToString("\n"), coalesceUndo = false)
        updateState { copy(cursor = cursor.moveTo(TextPosition(position.line + 1, position.column))) }
    }

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
        if (position.line <= 0 && position.column <= 0) return 0
        var index = 0
        var line = 0
        while (index < content.length && line < position.line) {
            if (content[index] == '\n') line++
            index++
        }
        return (index + position.column).coerceIn(0, content.length)
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
        val offset = positionToOffset(content, currentState.cursor.position)
        val newContent =
            buildString {
                append(content, 0, offset)
                append(text)
                append(content, offset, content.length)
            }
        updateContent(newContent)
    }

    private fun goToLine(lineNumber: Int) {
        // Scroll to the specified line
        updateState { copy(scrollLine = lineNumber.coerceIn(1, lineCount)) }
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
        moveCursor(position)
    }

    private fun tokenize(
        content: String,
        languageId: LanguageId,
    ): TokenList {
        val lexer = getLexerForLanguage(languageId)

        // Use the layered highlighter for token management
        // This will:
        // 1. Use hand-written lexers as base (Layer 1)
        // 2. Apply Tree-sitter tokens when available (Layer 2)
        // 3. Apply LSP semantic tokens when available (Layer 3)
        layeredHighlighter.highlight(content, lexer, semanticTokenProvider = null)

        // Return immediate lexer tokens for synchronous use
        // The layered highlighter will asynchronously update with semantic tokens
        return lexer?.tokenize(content) ?: TokenList(emptyList())
    }

    private fun getLexerForLanguage(languageId: LanguageId): Lexer? {
        val id =
            when (languageId) {
                LanguageId.KOTLIN -> "kotlin"
                LanguageId.VALA -> "vala"
                LanguageId.JAVA -> "java"
                LanguageId.RUST -> "rust"
                LanguageId.CPP -> "cpp"
                LanguageId.MESON -> "meson"
                LanguageId.XML -> "xml"
                LanguageId.TOML -> "toml"
                LanguageId.PYTHON -> "python"
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
    ) {
        // Cancel any pending completion request
        completionJob?.cancel()

        // Prefer override from UI to avoid stale content during async updates
        val filterText =
            filterTextOverride
                ?: currentState.completionState.filterText.ifEmpty {
                    extractCurrentIdentifierPrefix()
                }

        // Update state to show loading
        updateState {
            copy(
                completionState =
                    CompletionState(
                        isVisible = true,
                        isLoading = true,
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
                }
                val completionItems = getCompletionItems()
                // Apply latest filter to the items (may have updated while request was in flight)
                val activeFilter = currentState.completionState.filterText
                val filteredItems =
                    if (activeFilter.isNotEmpty()) {
                        completionController.filterItems(completionItems, activeFilter)
                    } else {
                        completionItems
                    }
                updateState {
                    copy(
                        completionState =
                            completionState.copy(
                                items = filteredItems,
                                isLoading = false,
                                selectedIndex = 0,
                                isVisible = filteredItems.isNotEmpty(),
                            ),
                    )
                }
            }
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

        // Find the replacement range - from word start before trigger to current position
        val (replaceStart, replaceEnd) = completionController.getReplacementRange(content, cursorOffset)

        // Get current line's indentation
        val currentLineStart = content.lastIndexOf('\n', replaceStart - 1) + 1
        val lineContent = content.substring(currentLineStart, replaceStart)
        val indentation = lineContent.takeWhile { it == ' ' || it == '\t' }

        // Get the text to insert
        val insertText = item.insertText

        // Process snippet placeholders and apply indentation to multi-line text
        val textToInsert =
            if (item.insertTextIsSnippet) {
                // Simple snippet processing - remove placeholders
                val processed =
                    insertText
                        .replace(Regex("\\$\\d+"), "")
                        .replace(Regex("\\$\\{\\d+:([^}]*)\\}"), "$1")
                applyIndentation(processed, indentation)
            } else {
                applyIndentation(insertText, indentation)
            }

        // Build new content: before replacement + completion text + after replacement
        val newContent =
            buildString {
                append(content.substring(0, replaceStart))
                append(textToInsert)
                append(content.substring(replaceEnd))
            }

        updateContent(newContent)

        // Dismiss completion popup
        dismissCompletion()

        // Emit effect for additional edits (like auto-imports)
        if (item.additionalTextEdits.isNotEmpty()) {
            viewModelScope.launch {
                emitEffect(EditorEffect.CompletionApplied(item, item.additionalTextEdits))
            }
        }
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
    ): Int {
        var offset = 0
        var currentLine = 0

        for (char in content) {
            if (currentLine == line) {
                return (offset + column).coerceAtMost(content.length)
            }
            if (char == '\n') {
                currentLine++
            }
            offset++
        }

        // If we reach here, we're at the last line
        return (offset + column).coerceAtMost(content.length)
    }

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
        val filtered = completionController.filterItems(currentState.completionState.items, filterText)
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

    private fun moveCursor(position: TextPosition) {
        updateState { copy(cursor = cursor.moveTo(position)) }
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

        // Show loading state
        updateState {
            copy(
                hoverState =
                    HoverState(
                        isLoading = true,
                        position = position,
                    ),
            )
        }

        // Launch hover request with debounce
        hoverJob =
            viewModelScope.launch {
                delay(HOVER_DEBOUNCE_MS)

                val hover =
                    languageRegistry?.let { registry ->
                        registry.provideHover(TextDocumentAdapter(currentState), position)
                    }
                val contents = diagnosticsAt(position) + hover?.contents.orEmpty()
                if (contents.isEmpty()) {
                    dismissHover()
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

    private fun dismissHover() {
        hoverJob?.cancel()
        hoverJob = null
        updateState {
            copy(hoverState = HoverState())
        }
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
            lowerFileName == "cargo.toml" || lowerFileName == "cargo.lock" -> return LanguageId.TOML
            lowerFileName == "pom.xml" -> return LanguageId.XML
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
            "cpp", "cc", "cxx", "c", "h", "hpp" -> LanguageId.CPP
            "json" -> LanguageId.JSON
            "yaml", "yml" -> LanguageId.YAML
            "toml" -> LanguageId.TOML
            "xml", "pom", "xsd", "xsl", "xslt", "svg" -> LanguageId.XML
            "html", "htm" -> LanguageId.HTML
            "css" -> LanguageId.CSS
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
        val registry = languageRegistry ?: return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return
        if (!lspOpenDocuments.add(uriValue)) return

        viewModelScope.launch {
            // Fire language opened trigger (may activate pending plugins)
            activationEvents?.fireLanguageOpened(languageId.value)

            registry.notifyDocumentOpened(uriValue, languageId.value, content)
        }
    }

    private fun syncDocumentChanged(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String,
    ) {
        val registry = languageRegistry ?: return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        viewModelScope.launch {
            if (!lspOpenDocuments.contains(uriValue)) {
                registry.notifyDocumentOpened(uriValue, languageId.value, content)
                lspOpenDocuments.add(uriValue)
            } else {
                registry.notifyDocumentChanged(uriValue, languageId.value, content)
            }
        }
    }

    private fun syncDocumentSaved(
        uri: DocumentUri,
        languageId: LanguageId,
        content: String?,
    ) {
        val registry = languageRegistry ?: return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        viewModelScope.launch {
            registry.notifyDocumentSaved(uriValue, languageId.value, content)
        }
    }

    private fun syncDocumentClosed(
        uri: DocumentUri,
        languageId: LanguageId,
    ) {
        val registry = languageRegistry ?: return
        val uriValue = uri.value
        if (!uriValue.startsWith("file://")) return

        lspOpenDocuments.remove(uriValue)
        viewModelScope.launch {
            registry.notifyDocumentClosed(uriValue, languageId.value)
        }
    }

    private fun LanguageDiagnostic.toEditorDiagnostic(): Diagnostic =
        Diagnostic(
            range = range,
            message = message,
            severity = severity,
            source = source,
            code = code,
        )

    private companion object {
        /**
         * Debounce delay for hover requests in milliseconds.
         */
        const val HOVER_DEBOUNCE_MS = 400L

        /**
         * Debounce delay for auto-triggered completion requests in milliseconds.
         * Manual invocations (Ctrl+Space) are not debounced.
         */
        const val COMPLETION_DEBOUNCE_MS = 150L
    }
}
