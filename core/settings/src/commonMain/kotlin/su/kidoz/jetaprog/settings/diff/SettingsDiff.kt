package su.kidoz.jetaprog.settings.diff

import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.EditorSettings
import su.kidoz.jetaprog.settings.model.LanguagesSettings
import su.kidoz.jetaprog.settings.model.PluginsSettings
import su.kidoz.jetaprog.settings.model.ToolsSettings

/**
 * Represents a diff between two settings instances.
 *
 * Tracks which categories and fields have changed, enabling:
 * - Efficient diff-based persistence (only save non-default values)
 * - Targeted change notifications (notify only for changed categories)
 * - Smart merging of workspace/user/default layers
 */
public data class SettingsDiff(
    /**
     * The categories that have changed.
     */
    val changedCategories: Set<String>,
    /**
     * The specific fields that changed, in "category.field" format.
     */
    val changedFields: Set<String>,
    /**
     * The total number of changed fields.
     */
    val changeCount: Int = changedFields.size,
) {
    /**
     * Whether any settings have changed.
     */
    public val hasChanges: Boolean get() = changedFields.isNotEmpty()

    /**
     * Returns true if the given category has any changes.
     */
    public fun hasChangesIn(category: String): Boolean = category in changedCategories

    public companion object {
        /**
         * An empty diff (no changes).
         */
        public val EMPTY: SettingsDiff = SettingsDiff(emptySet(), emptySet(), 0)

        /**
         * Computes the diff between two [AllSettings] instances.
         *
         * @param old The old settings
         * @param new The new settings
         * @return A diff describing what changed
         */
        public fun compute(
            old: AllSettings,
            new: AllSettings,
        ): SettingsDiff {
            val categories = mutableSetOf<String>()
            val fields = mutableSetOf<String>()

            diffAppearance(old.appearance, new.appearance, categories, fields)
            diffEditor(old.editor, new.editor, categories, fields)
            diffLanguages(old.languages, new.languages, categories, fields)
            diffTools(old.tools, new.tools, categories, fields)
            diffPlugins(old.plugins, new.plugins, categories, fields)

            return SettingsDiff(categories, fields)
        }

        /**
         * Computes which fields differ from the defaults.
         * Used for diff-based persistence (only save non-default values).
         *
         * @param settings The settings to compare against defaults
         * @return A diff of non-default fields
         */
        public fun computeFromDefaults(settings: AllSettings): SettingsDiff = compute(AllSettings.DEFAULT, settings)

        private fun diffAppearance(
            old: AppearanceSettings,
            new: AppearanceSettings,
            categories: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            var changed = false
            if (old.theme != new.theme) {
                fields.add("appearance.theme")
                changed = true
            }
            if (old.fontFamily != new.fontFamily) {
                fields.add("appearance.fontFamily")
                changed = true
            }
            if (old.fontSize != new.fontSize) {
                fields.add("appearance.fontSize")
                changed = true
            }
            if (old.uiScale != new.uiScale) {
                fields.add("appearance.uiScale")
                changed = true
            }
            if (old.compactMode != new.compactMode) {
                fields.add("appearance.compactMode")
                changed = true
            }
            if (changed) categories.add("appearance")
        }

        private fun diffEditor(
            old: EditorSettings,
            new: EditorSettings,
            categories: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            var changed = false
            if (old.tabSize != new.tabSize) {
                fields.add("editor.tabSize")
                changed = true
            }
            if (old.useTabs != new.useTabs) {
                fields.add("editor.useTabs")
                changed = true
            }
            if (old.showLineNumbers != new.showLineNumbers) {
                fields.add("editor.showLineNumbers")
                changed = true
            }
            if (old.showMinimap != new.showMinimap) {
                fields.add("editor.showMinimap")
                changed = true
            }
            if (old.wordWrap != new.wordWrap) {
                fields.add("editor.wordWrap")
                changed = true
            }
            if (old.showWhitespace != new.showWhitespace) {
                fields.add("editor.showWhitespace")
                changed = true
            }
            if (old.highlightCurrentLine != new.highlightCurrentLine) {
                fields.add("editor.highlightCurrentLine")
                changed = true
            }
            if (old.bracketMatching != new.bracketMatching) {
                fields.add("editor.bracketMatching")
                changed = true
            }
            if (old.autoCloseBrackets != new.autoCloseBrackets) {
                fields.add("editor.autoCloseBrackets")
                changed = true
            }
            if (old.autoSave != new.autoSave) {
                fields.add("editor.autoSave")
                changed = true
            }
            if (old.autoSaveDelayMs != new.autoSaveDelayMs) {
                fields.add("editor.autoSaveDelayMs")
                changed = true
            }
            if (old.trimTrailingWhitespace != new.trimTrailingWhitespace) {
                fields.add("editor.trimTrailingWhitespace")
                changed = true
            }
            if (old.insertFinalNewline != new.insertFinalNewline) {
                fields.add("editor.insertFinalNewline")
                changed = true
            }
            if (old.maxLineLength != new.maxLineLength) {
                fields.add("editor.maxLineLength")
                changed = true
            }
            if (changed) categories.add("editor")
        }

        private fun diffLanguages(
            old: LanguagesSettings,
            new: LanguagesSettings,
            categories: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            if (old != new) {
                categories.add("languages")
                fields.add("languages")
            }
        }

        private fun diffTools(
            old: ToolsSettings,
            new: ToolsSettings,
            categories: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            if (old != new) {
                categories.add("tools")
                fields.add("tools")
            }
        }

        private fun diffPlugins(
            old: PluginsSettings,
            new: PluginsSettings,
            categories: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            if (old != new) {
                categories.add("plugins")
                fields.add("plugins")
            }
        }
    }
}
