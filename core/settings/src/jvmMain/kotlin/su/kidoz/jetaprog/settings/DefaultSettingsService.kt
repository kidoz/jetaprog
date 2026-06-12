package su.kidoz.jetaprog.settings

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.BuildSystemsConfig
import su.kidoz.jetaprog.settings.model.EditorSettings
import su.kidoz.jetaprog.settings.model.LanguageDefaults
import su.kidoz.jetaprog.settings.model.LanguagesSettings
import su.kidoz.jetaprog.settings.model.PluginsSettings
import su.kidoz.jetaprog.settings.model.ToolsSettings
import su.kidoz.jetaprog.settings.storage.SettingsStorage

private val logger = KotlinLogging.logger {}

/**
 * Default implementation of [SettingsService].
 *
 * Implements layered settings resolution: workspace -> user -> defaults.
 */
public class DefaultSettingsService(
    private val storage: SettingsStorage,
) : SettingsService {
    private var _projectPath: String? = null
    override val projectPath: String?
        get() = _projectPath

    private val userSettingsState = MutableStateFlow<AllSettings?>(null)
    private val workspaceSettingsState = MutableStateFlow<AllSettings?>(null)
    private val resolvedSettingsState = MutableStateFlow(AllSettings.DEFAULT)

    override val settings: Flow<AllSettings> = resolvedSettingsState.asStateFlow()

    override val appearance: Flow<AppearanceSettings> = resolvedSettingsState.map { it.appearance }

    override val editor: Flow<EditorSettings> = resolvedSettingsState.map { it.editor }

    override val languages: Flow<LanguagesSettings> = resolvedSettingsState.map { it.languages }

    override val tools: Flow<ToolsSettings> = resolvedSettingsState.map { it.tools }

    override val plugins: Flow<PluginsSettings> = resolvedSettingsState.map { it.plugins }

    override suspend fun initialize(projectPath: String?) {
        _projectPath = projectPath
        reload()
    }

    override fun getCurrentSettings(): AllSettings = resolvedSettingsState.value

    override suspend fun updateSettings(
        scope: SettingsScope,
        update: AllSettings.() -> AllSettings,
    ): Result<Unit> {
        val currentScopeSettings = getScopeSettings(scope) ?: AllSettings.DEFAULT
        val newSettings = currentScopeSettings.update()

        return storage.save(scope, newSettings, _projectPath).onSuccess {
            when (scope) {
                SettingsScope.IDE -> logger.debug { "Saved IDE-level settings" }
                SettingsScope.USER -> userSettingsState.value = newSettings
                SettingsScope.WORKSPACE -> workspaceSettingsState.value = newSettings
            }
            resolveSettings()
        }
    }

    override suspend fun updateAppearance(
        scope: SettingsScope,
        update: AppearanceSettings.() -> AppearanceSettings,
    ): Result<Unit> =
        updateSettings(scope) {
            copy(appearance = appearance.update())
        }

    override suspend fun updateEditor(
        scope: SettingsScope,
        update: EditorSettings.() -> EditorSettings,
    ): Result<Unit> =
        updateSettings(scope) {
            copy(editor = editor.update())
        }

    override suspend fun updateLanguages(
        scope: SettingsScope,
        update: LanguagesSettings.() -> LanguagesSettings,
    ): Result<Unit> =
        updateSettings(scope) {
            copy(languages = languages.update())
        }

    override suspend fun updateTools(
        scope: SettingsScope,
        update: ToolsSettings.() -> ToolsSettings,
    ): Result<Unit> =
        updateSettings(scope) {
            copy(tools = tools.update())
        }

    override suspend fun updatePlugins(
        scope: SettingsScope,
        update: PluginsSettings.() -> PluginsSettings,
    ): Result<Unit> =
        updateSettings(scope) {
            copy(plugins = plugins.update())
        }

    override suspend fun resetToDefaults(
        scope: SettingsScope,
        category: SettingsCategory?,
    ): Result<Unit> =
        if (category == null) {
            // Reset all categories
            storage.delete(scope, _projectPath).onSuccess {
                when (scope) {
                    SettingsScope.IDE -> logger.debug { "Reset IDE-level settings" }
                    SettingsScope.USER -> userSettingsState.value = null
                    SettingsScope.WORKSPACE -> workspaceSettingsState.value = null
                }
                resolveSettings()
            }
        } else {
            // Reset specific category
            updateSettings(scope) {
                when (category) {
                    SettingsCategory.APPEARANCE -> copy(appearance = AppearanceSettings.DEFAULT)
                    SettingsCategory.EDITOR -> copy(editor = EditorSettings.DEFAULT)
                    SettingsCategory.LANGUAGES -> copy(languages = LanguagesSettings.DEFAULT)
                    SettingsCategory.TOOLS -> copy(tools = ToolsSettings.DEFAULT)
                    SettingsCategory.PLUGINS -> copy(plugins = PluginsSettings.DEFAULT)
                }
            }
        }

    override suspend fun reload(): Result<Unit> =
        runCatching {
            // Load user settings
            storage
                .load(SettingsScope.USER, null)
                .onSuccess { settings ->
                    userSettingsState.value = settings
                    logger.debug { "Loaded user settings" }
                }.onFailure { error ->
                    userSettingsState.value = null
                    logger.warn(error) { "Failed to load user settings, using defaults" }
                }

            // Load workspace settings if project path is set
            _projectPath?.let { path ->
                storage
                    .load(SettingsScope.WORKSPACE, path)
                    .onSuccess { settings ->
                        workspaceSettingsState.value = settings
                        logger.debug { "Loaded workspace settings from $path" }
                    }.onFailure { error ->
                        workspaceSettingsState.value = null
                        logger.warn(error) { "Failed to load workspace settings from $path, using defaults" }
                    }
            }

            resolveSettings()
        }

    override suspend fun getRawSettings(scope: SettingsScope): AllSettings? = getScopeSettings(scope)

    private fun getScopeSettings(scope: SettingsScope): AllSettings? =
        when (scope) {
            SettingsScope.IDE -> null

            // IDE-level settings loaded separately
            SettingsScope.USER -> userSettingsState.value

            SettingsScope.WORKSPACE -> workspaceSettingsState.value
        }

    /**
     * Resolve settings with layered precedence: workspace -> user -> defaults.
     *
     * Each field is resolved independently, allowing workspace settings to
     * override specific user settings while keeping other user settings.
     */
    private fun resolveSettings() {
        val defaults = AllSettings.DEFAULT
        val user = userSettingsState.value ?: defaults
        val workspace = workspaceSettingsState.value

        val resolved =
            if (workspace != null) {
                // Merge workspace over user over defaults
                AllSettings(
                    appearance = mergeAppearance(defaults.appearance, user.appearance, workspace.appearance),
                    editor = mergeEditor(defaults.editor, user.editor, workspace.editor),
                    languages = mergeLanguages(defaults.languages, user.languages, workspace.languages),
                    tools = mergeTools(defaults.tools, user.tools, workspace.tools),
                    plugins = mergePlugins(defaults.plugins, user.plugins, workspace.plugins),
                )
            } else {
                user
            }

        resolvedSettingsState.value = resolved
    }

    /**
     * Merge appearance settings with field-level precedence.
     *
     * For each field, use workspace value if it differs from default,
     * otherwise use user value.
     */
    private fun mergeAppearance(
        defaults: AppearanceSettings,
        user: AppearanceSettings,
        workspace: AppearanceSettings,
    ): AppearanceSettings =
        AppearanceSettings(
            theme = if (workspace.theme != defaults.theme) workspace.theme else user.theme,
            fontFamily = if (workspace.fontFamily != defaults.fontFamily) workspace.fontFamily else user.fontFamily,
            fontSize = if (workspace.fontSize != defaults.fontSize) workspace.fontSize else user.fontSize,
            lineHeight = if (workspace.lineHeight != defaults.lineHeight) workspace.lineHeight else user.lineHeight,
            uiScale = if (workspace.uiScale != defaults.uiScale) workspace.uiScale else user.uiScale,
            showToolbarLabels =
                if (workspace.showToolbarLabels != defaults.showToolbarLabels) {
                    workspace.showToolbarLabels
                } else {
                    user.showToolbarLabels
                },
            compactMode =
                if (workspace.compactMode !=
                    defaults.compactMode
                ) {
                    workspace.compactMode
                } else {
                    user.compactMode
                },
        )

    /**
     * Merge editor settings with field-level precedence.
     */
    private fun mergeEditor(
        defaults: EditorSettings,
        user: EditorSettings,
        workspace: EditorSettings,
    ): EditorSettings =
        EditorSettings(
            tabSize = if (workspace.tabSize != defaults.tabSize) workspace.tabSize else user.tabSize,
            useTabs = if (workspace.useTabs != defaults.useTabs) workspace.useTabs else user.useTabs,
            showLineNumbers =
                if (workspace.showLineNumbers !=
                    defaults.showLineNumbers
                ) {
                    workspace.showLineNumbers
                } else {
                    user.showLineNumbers
                },
            showMinimap =
                if (workspace.showMinimap !=
                    defaults.showMinimap
                ) {
                    workspace.showMinimap
                } else {
                    user.showMinimap
                },
            wordWrap = if (workspace.wordWrap != defaults.wordWrap) workspace.wordWrap else user.wordWrap,
            showWhitespace =
                if (workspace.showWhitespace !=
                    defaults.showWhitespace
                ) {
                    workspace.showWhitespace
                } else {
                    user.showWhitespace
                },
            highlightCurrentLine =
                if (workspace.highlightCurrentLine != defaults.highlightCurrentLine) {
                    workspace.highlightCurrentLine
                } else {
                    user.highlightCurrentLine
                },
            bracketMatching =
                if (workspace.bracketMatching !=
                    defaults.bracketMatching
                ) {
                    workspace.bracketMatching
                } else {
                    user.bracketMatching
                },
            autoCloseBrackets =
                if (workspace.autoCloseBrackets != defaults.autoCloseBrackets) {
                    workspace.autoCloseBrackets
                } else {
                    user.autoCloseBrackets
                },
            autoSave = if (workspace.autoSave != defaults.autoSave) workspace.autoSave else user.autoSave,
            autoSaveDelayMs =
                if (workspace.autoSaveDelayMs !=
                    defaults.autoSaveDelayMs
                ) {
                    workspace.autoSaveDelayMs
                } else {
                    user.autoSaveDelayMs
                },
            trimTrailingWhitespace =
                if (workspace.trimTrailingWhitespace != defaults.trimTrailingWhitespace) {
                    workspace.trimTrailingWhitespace
                } else {
                    user.trimTrailingWhitespace
                },
            insertFinalNewline =
                if (workspace.insertFinalNewline != defaults.insertFinalNewline) {
                    workspace.insertFinalNewline
                } else {
                    user.insertFinalNewline
                },
            maxLineLength =
                if (workspace.maxLineLength != defaults.maxLineLength) workspace.maxLineLength else user.maxLineLength,
        )

    /**
     * Merge languages settings.
     *
     * For defaults, uses field-level precedence (workspace overrides if different from defaults).
     * For collections (languages, languageServers), combines both with workspace taking precedence for same keys.
     */
    private fun mergeLanguages(
        defaults: LanguagesSettings,
        user: LanguagesSettings,
        workspace: LanguagesSettings,
    ): LanguagesSettings =
        LanguagesSettings(
            defaults =
                LanguageDefaults(
                    encoding =
                        if (workspace.defaults.encoding != defaults.defaults.encoding) {
                            workspace.defaults.encoding
                        } else {
                            user.defaults.encoding
                        },
                    lineEndings =
                        if (workspace.defaults.lineEndings != defaults.defaults.lineEndings) {
                            workspace.defaults.lineEndings
                        } else {
                            user.defaults.lineEndings
                        },
                    tabSize =
                        if (workspace.defaults.tabSize != defaults.defaults.tabSize) {
                            workspace.defaults.tabSize
                        } else {
                            user.defaults.tabSize
                        },
                    insertSpaces =
                        if (workspace.defaults.insertSpaces != defaults.defaults.insertSpaces) {
                            workspace.defaults.insertSpaces
                        } else {
                            user.defaults.insertSpaces
                        },
                ),
            // Merge maps: user as base, workspace overrides same keys
            languages = user.languages + workspace.languages,
            languageServers = user.languageServers + workspace.languageServers,
        )

    /**
     * Merge tools settings.
     *
     * For buildSystems, uses field-level precedence.
     * For collections (mcpServers, externalTools), combines both with workspace taking precedence.
     */
    private fun mergeTools(
        defaults: ToolsSettings,
        user: ToolsSettings,
        workspace: ToolsSettings,
    ): ToolsSettings =
        ToolsSettings(
            buildSystems =
                BuildSystemsConfig(
                    autoDetect =
                        if (workspace.buildSystems.autoDetect != defaults.buildSystems.autoDetect) {
                            workspace.buildSystems.autoDetect
                        } else {
                            user.buildSystems.autoDetect
                        },
                    defaultBuildSystem =
                        workspace.buildSystems.defaultBuildSystem ?: user.buildSystems.defaultBuildSystem,
                    gradle = workspace.buildSystems.gradle,
                    meson = workspace.buildSystems.meson,
                    cmake = workspace.buildSystems.cmake,
                ),
            // Merge maps: user as base, workspace overrides same keys
            mcpServers = user.mcpServers + workspace.mcpServers,
            externalTools = user.externalTools + workspace.externalTools,
        )

    /**
     * Merge plugins settings.
     *
     * Collections are combined (union), scalar values use field-level precedence.
     */
    private fun mergePlugins(
        defaults: PluginsSettings,
        user: PluginsSettings,
        workspace: PluginsSettings,
    ): PluginsSettings =
        PluginsSettings(
            // Union of disabled plugins from both scopes
            disabledPlugins = user.disabledPlugins + workspace.disabledPlugins,
            updatePolicy =
                if (workspace.updatePolicy != defaults.updatePolicy) workspace.updatePolicy else user.updatePolicy,
            allowPrerelease =
                if (workspace.allowPrerelease !=
                    defaults.allowPrerelease
                ) {
                    workspace.allowPrerelease
                } else {
                    user.allowPrerelease
                },
            trustedSources = user.trustedSources + workspace.trustedSources,
            pluginSettings = user.pluginSettings + workspace.pluginSettings,
        )
}
