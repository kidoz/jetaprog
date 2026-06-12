package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.app.ui.dialogs.settings.EditingMcpServer
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsEffect
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsState
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.settings.SettingsCategory
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.McpServerConfig

/**
 * ViewModel for the Settings dialog.
 */
public class SettingsViewModel(
    private val settingsService: SettingsService,
) : MviViewModel<SettingsIntent, SettingsState, SettingsEffect>(SettingsState()) {
    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            // Dialog visibility
            is SettingsIntent.Show -> {
                handleShow()
            }

            is SettingsIntent.Hide -> {
                handleHide()
            }

            // Navigation
            is SettingsIntent.SelectCategory -> {
                handleSelectCategory(intent.category)
            }

            is SettingsIntent.SelectSubItem -> {
                handleSelectSubItem(intent.subItem)
            }

            is SettingsIntent.SetScope -> {
                handleSetScope(intent.scope)
            }

            // Search
            is SettingsIntent.Search -> {
                handleSearch(intent.query)
            }

            is SettingsIntent.ClearSearch -> {
                handleClearSearch()
            }

            // Appearance settings
            is SettingsIntent.SetTheme -> {
                updateAppearance { copy(theme = intent.theme) }
            }

            is SettingsIntent.SetFontFamily -> {
                updateAppearance { copy(fontFamily = intent.fontFamily) }
            }

            is SettingsIntent.SetFontSize -> {
                updateAppearance { copy(fontSize = intent.size) }
            }

            is SettingsIntent.SetLineHeight -> {
                updateAppearance { copy(lineHeight = intent.height) }
            }

            is SettingsIntent.SetUiScale -> {
                updateAppearance { copy(uiScale = intent.scale) }
            }

            is SettingsIntent.SetShowToolbarLabels -> {
                updateAppearance { copy(showToolbarLabels = intent.show) }
            }

            is SettingsIntent.SetCompactMode -> {
                updateAppearance { copy(compactMode = intent.compact) }
            }

            // Editor settings
            is SettingsIntent.SetTabSize -> {
                updateEditor { copy(tabSize = intent.size) }
            }

            is SettingsIntent.SetUseTabs -> {
                updateEditor { copy(useTabs = intent.useTabs) }
            }

            is SettingsIntent.SetShowLineNumbers -> {
                updateEditor { copy(showLineNumbers = intent.show) }
            }

            is SettingsIntent.SetShowMinimap -> {
                updateEditor { copy(showMinimap = intent.show) }
            }

            is SettingsIntent.SetWordWrap -> {
                updateEditor { copy(wordWrap = intent.wrap) }
            }

            is SettingsIntent.SetShowWhitespace -> {
                updateEditor { copy(showWhitespace = intent.show) }
            }

            is SettingsIntent.SetHighlightCurrentLine -> {
                updateEditor { copy(highlightCurrentLine = intent.highlight) }
            }

            is SettingsIntent.SetBracketMatching -> {
                updateEditor { copy(bracketMatching = intent.match) }
            }

            is SettingsIntent.SetAutoCloseBrackets -> {
                updateEditor { copy(autoCloseBrackets = intent.close) }
            }

            is SettingsIntent.SetAutoSave -> {
                updateEditor { copy(autoSave = intent.enabled) }
            }

            is SettingsIntent.SetAutoSaveDelay -> {
                updateEditor { copy(autoSaveDelayMs = intent.delayMs) }
            }

            is SettingsIntent.SetTrimTrailingWhitespace -> {
                updateEditor { copy(trimTrailingWhitespace = intent.trim) }
            }

            is SettingsIntent.SetInsertFinalNewline -> {
                updateEditor { copy(insertFinalNewline = intent.insert) }
            }

            is SettingsIntent.SetMaxLineLength -> {
                updateEditor { copy(maxLineLength = intent.length) }
            }

            // Language settings
            is SettingsIntent.SetCompletionPreference -> {
                updateLanguages {
                    val langConfig =
                        languages[intent.languageId] ?: su.kidoz.jetaprog.settings.model
                            .LanguageConfig()
                    copy(
                        languages =
                            languages + (
                                intent.languageId to
                                    langConfig.copy(
                                        completionPreference = intent.preference,
                                    )
                            ),
                    )
                }
            }

            // Tools - Build Systems
            is SettingsIntent.SetBuildSystemAutoDetect -> {
                updateTools {
                    copy(buildSystems = buildSystems.copy(autoDetect = intent.autoDetect))
                }
            }

            // MCP Servers
            is SettingsIntent.AddMcpServer -> {
                handleAddMcpServer()
            }

            is SettingsIntent.EditMcpServer -> {
                handleEditMcpServer(intent.id)
            }

            is SettingsIntent.UpdateEditingMcpServer -> {
                handleUpdateEditingMcpServer(intent.config)
            }

            is SettingsIntent.SaveEditingMcpServer -> {
                handleSaveEditingMcpServer()
            }

            is SettingsIntent.CancelEditingMcpServer -> {
                handleCancelEditingMcpServer()
            }

            is SettingsIntent.RemoveMcpServer -> {
                handleRemoveMcpServer(intent.id)
            }

            is SettingsIntent.ToggleMcpServer -> {
                handleToggleMcpServer(intent.id, intent.enabled)
            }

            // Plugins
            is SettingsIntent.TogglePlugin -> {
                handleTogglePlugin(intent.pluginId, intent.enabled)
            }

            is SettingsIntent.SetPluginUpdatePolicy -> {
                updatePlugins { copy(updatePolicy = intent.policy) }
            }

            is SettingsIntent.SetAllowPrerelease -> {
                updatePlugins { copy(allowPrerelease = intent.allow) }
            }

            // Actions
            is SettingsIntent.Apply -> {
                handleApply()
            }

            is SettingsIntent.ApplyAndClose -> {
                handleApplyAndClose()
            }

            is SettingsIntent.Cancel -> {
                handleCancel()
            }

            is SettingsIntent.ResetToDefaults -> {
                handleResetToDefaults()
            }

            is SettingsIntent.ResetCategoryToDefaults -> {
                handleResetCategoryToDefaults(intent.category)
            }

            // Internal
            is SettingsIntent.LoadSettings -> {
                handleLoadSettings(intent.settings)
            }

            is SettingsIntent.LoadError -> {
                handleLoadError(intent.error)
            }
        }
    }

    private suspend fun handleShow() {
        updateState { copy(isVisible = true, isLoading = true) }
        loadCurrentSettings()
    }

    private fun handleHide() {
        updateState { copy(isVisible = false) }
    }

    private fun handleSelectCategory(category: SettingsCategory) {
        updateState { copy(selectedCategory = category, selectedSubItem = null) }
    }

    private fun handleSelectSubItem(subItem: String?) {
        updateState { copy(selectedSubItem = subItem) }
    }

    private fun handleSetScope(scope: su.kidoz.jetaprog.settings.SettingsScope) {
        updateState { copy(activeScope = scope) }
    }

    private fun handleSearch(query: String) {
        updateState { copy(searchQuery = query) }
    }

    private fun handleClearSearch() {
        updateState { copy(searchQuery = "") }
    }

    private inline fun updateAppearance(
        crossinline update: su.kidoz.jetaprog.settings.model.AppearanceSettings.() ->
        su.kidoz.jetaprog.settings.model.AppearanceSettings,
    ) {
        updateState {
            val currentSettings = pendingChanges ?: effectiveSettings
            val newAppearance = currentSettings.appearance.update()
            copy(
                pendingChanges = currentSettings.copy(appearance = newAppearance),
                hasUnsavedChanges = true,
            )
        }
    }

    private inline fun updateEditor(
        crossinline update: su.kidoz.jetaprog.settings.model.EditorSettings.() ->
        su.kidoz.jetaprog.settings.model.EditorSettings,
    ) {
        updateState {
            val currentSettings = pendingChanges ?: effectiveSettings
            val newEditor = currentSettings.editor.update()
            copy(
                pendingChanges = currentSettings.copy(editor = newEditor),
                hasUnsavedChanges = true,
            )
        }
    }

    private inline fun updateLanguages(
        crossinline update: su.kidoz.jetaprog.settings.model.LanguagesSettings.() ->
        su.kidoz.jetaprog.settings.model.LanguagesSettings,
    ) {
        updateState {
            val currentSettings = pendingChanges ?: effectiveSettings
            val newLanguages = currentSettings.languages.update()
            copy(
                pendingChanges = currentSettings.copy(languages = newLanguages),
                hasUnsavedChanges = true,
            )
        }
    }

    private inline fun updateTools(
        crossinline update: su.kidoz.jetaprog.settings.model.ToolsSettings.() ->
        su.kidoz.jetaprog.settings.model.ToolsSettings,
    ) {
        updateState {
            val currentSettings = pendingChanges ?: effectiveSettings
            val newTools = currentSettings.tools.update()
            copy(
                pendingChanges = currentSettings.copy(tools = newTools),
                hasUnsavedChanges = true,
            )
        }
    }

    private inline fun updatePlugins(
        crossinline update: su.kidoz.jetaprog.settings.model.PluginsSettings.() ->
        su.kidoz.jetaprog.settings.model.PluginsSettings,
    ) {
        updateState {
            val currentSettings = pendingChanges ?: effectiveSettings
            val newPlugins = currentSettings.plugins.update()
            copy(
                pendingChanges = currentSettings.copy(plugins = newPlugins),
                hasUnsavedChanges = true,
            )
        }
    }

    private fun handleAddMcpServer() {
        updateState {
            copy(
                editingMcpServer =
                    EditingMcpServer(
                        id = null,
                        isNew = true,
                        config =
                            McpServerConfig(
                                name = "New Server",
                                command = "",
                            ),
                    ),
            )
        }
    }

    private fun handleEditMcpServer(id: String) {
        val server = currentState.tools.mcpServers[id] ?: return
        updateState {
            copy(
                editingMcpServer =
                    EditingMcpServer(
                        id = id,
                        isNew = false,
                        config = server,
                    ),
            )
        }
    }

    private fun handleUpdateEditingMcpServer(config: McpServerConfig) {
        updateState {
            copy(editingMcpServer = editingMcpServer?.copy(config = config))
        }
    }

    private fun handleSaveEditingMcpServer() {
        val editing = currentState.editingMcpServer ?: return
        val id = editing.id ?: generateServerId(editing.config.name)

        updateTools {
            copy(mcpServers = mcpServers + (id to editing.config))
        }

        updateState { copy(editingMcpServer = null) }
    }

    private fun handleCancelEditingMcpServer() {
        updateState { copy(editingMcpServer = null) }
    }

    private fun handleRemoveMcpServer(id: String) {
        updateTools {
            copy(mcpServers = mcpServers - id)
        }
    }

    private fun handleToggleMcpServer(
        id: String,
        enabled: Boolean,
    ) {
        updateTools {
            val server = mcpServers[id] ?: return@updateTools this
            copy(mcpServers = mcpServers + (id to server.copy(enabled = enabled)))
        }
    }

    private fun handleTogglePlugin(
        pluginId: String,
        enabled: Boolean,
    ) {
        updatePlugins {
            val newDisabled =
                if (enabled) {
                    disabledPlugins - pluginId
                } else {
                    disabledPlugins + pluginId
                }
            copy(disabledPlugins = newDisabled)
        }
    }

    private suspend fun handleApply() {
        val pending = currentState.pendingChanges ?: return

        updateState { copy(isLoading = true) }

        settingsService
            .updateSettings(currentState.activeScope) { pending }
            .onSuccess {
                updateState {
                    copy(
                        appearance = pending.appearance,
                        editor = pending.editor,
                        languages = pending.languages,
                        tools = pending.tools,
                        plugins = pending.plugins,
                        pendingChanges = null,
                        hasUnsavedChanges = false,
                        isLoading = false,
                    )
                }
                emitEffect(SettingsEffect.SettingsSaved)
                emitEffect(SettingsEffect.ShowSuccess("Settings saved"))
            }.onFailure { error ->
                updateState { copy(isLoading = false) }
                emitEffect(SettingsEffect.ShowError("Failed to save settings: ${error.message}"))
            }
    }

    private suspend fun handleApplyAndClose() {
        handleApply()
        if (!currentState.hasUnsavedChanges) {
            handleHide()
            emitEffect(SettingsEffect.CloseDialog)
        }
    }

    private suspend fun handleCancel() {
        updateState {
            copy(
                pendingChanges = null,
                hasUnsavedChanges = false,
            )
        }
        handleHide()
        emitEffect(SettingsEffect.CloseDialog)
    }

    private suspend fun handleResetToDefaults() {
        settingsService
            .resetToDefaults(currentState.activeScope, null)
            .onSuccess {
                loadCurrentSettings()
                emitEffect(SettingsEffect.ShowSuccess("Settings reset to defaults"))
            }.onFailure { error ->
                emitEffect(SettingsEffect.ShowError("Failed to reset settings: ${error.message}"))
            }
    }

    private suspend fun handleResetCategoryToDefaults(category: SettingsCategory) {
        settingsService
            .resetToDefaults(currentState.activeScope, category)
            .onSuccess {
                loadCurrentSettings()
                emitEffect(SettingsEffect.ShowSuccess("${category.displayName} settings reset to defaults"))
            }.onFailure { error ->
                emitEffect(SettingsEffect.ShowError("Failed to reset settings: ${error.message}"))
            }
    }

    private fun handleLoadSettings(settings: AllSettings) {
        updateState {
            copy(
                appearance = settings.appearance,
                editor = settings.editor,
                languages = settings.languages,
                tools = settings.tools,
                plugins = settings.plugins,
                pendingChanges = null,
                hasUnsavedChanges = false,
                isLoading = false,
            )
        }
    }

    private suspend fun handleLoadError(error: String) {
        updateState { copy(isLoading = false) }
        emitEffect(SettingsEffect.ShowError(error))
    }

    private suspend fun loadCurrentSettings() {
        val settings = settingsService.getCurrentSettings()
        dispatch(SettingsIntent.LoadSettings(settings))
    }

    private fun generateServerId(name: String): String {
        val base =
            name
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        val existing = currentState.tools.mcpServers.keys
        var id = base
        var counter = 1
        while (id in existing) {
            id = "$base-$counter"
            counter++
        }
        return id
    }
}
