package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import su.kidoz.jetaprog.plugins.api.services.SettingsAccessService
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings

/**
 * Implementation of [SettingsAccessService] that bridges to the core [SettingsService].
 *
 * Provides read-only access to IDE settings for plugins.
 */
public class SettingsAccessServiceImpl(
    private val settingsService: SettingsService,
) : SettingsAccessService {
    override fun getString(
        category: String,
        key: String,
        defaultValue: String,
    ): String {
        val settings = settingsService.getCurrentSettings()
        return getSettingValue(settings, category, key) ?: defaultValue
    }

    override fun getInt(
        category: String,
        key: String,
        defaultValue: Int,
    ): Int {
        val settings = settingsService.getCurrentSettings()
        val value = getSettingValue(settings, category, key) ?: return defaultValue
        return value.toIntOrNull() ?: defaultValue
    }

    override fun getBoolean(
        category: String,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val settings = settingsService.getCurrentSettings()
        val value = getSettingValue(settings, category, key) ?: return defaultValue
        return value.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun observeChanges(category: String?): Flow<String> = settingsService.settings.map { category ?: "all" }

    override fun getAllSettings(): Map<String, String> {
        val settings = settingsService.getCurrentSettings()
        return buildMap {
            // Appearance
            put("appearance.theme", settings.appearance.theme.name)
            put("appearance.fontFamily", settings.appearance.fontFamily)
            put("appearance.fontSize", settings.appearance.fontSize.toString())
            put("appearance.uiScale", settings.appearance.uiScale.toString())

            // Editor
            put("editor.tabSize", settings.editor.tabSize.toString())
            put("editor.useTabs", settings.editor.useTabs.toString())
            put("editor.showLineNumbers", settings.editor.showLineNumbers.toString())
            put("editor.showMinimap", settings.editor.showMinimap.toString())
            put("editor.wordWrap", settings.editor.wordWrap.toString())
            put("editor.autoSave", settings.editor.autoSave.toString())
        }
    }

    private fun getSettingValue(
        settings: AllSettings,
        category: String,
        key: String,
    ): String? =
        when (category.lowercase()) {
            "appearance" -> getAppearanceValue(settings, key)
            "editor" -> getEditorValue(settings, key)
            else -> null
        }

    private fun getAppearanceValue(
        settings: AllSettings,
        key: String,
    ): String? =
        when (key) {
            "theme" -> settings.appearance.theme.name
            "fontFamily" -> settings.appearance.fontFamily
            "fontSize" -> settings.appearance.fontSize.toString()
            "uiScale" -> settings.appearance.uiScale.toString()
            else -> null
        }

    private fun getEditorValue(
        settings: AllSettings,
        key: String,
    ): String? =
        when (key) {
            "tabSize" -> settings.editor.tabSize.toString()
            "useTabs" -> settings.editor.useTabs.toString()
            "showLineNumbers" -> settings.editor.showLineNumbers.toString()
            "showMinimap" -> settings.editor.showMinimap.toString()
            "wordWrap" -> settings.editor.wordWrap.toString()
            "autoSave" -> settings.editor.autoSave.toString()
            else -> null
        }
}
