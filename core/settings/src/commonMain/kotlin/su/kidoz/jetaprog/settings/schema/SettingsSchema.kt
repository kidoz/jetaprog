package su.kidoz.jetaprog.settings.schema

import kotlinx.serialization.Serializable

/**
 * JSON Schema type for a settings field.
 */
public enum class SchemaType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    ENUM,
}

/**
 * Describes a single settings field for schema generation.
 */
@Serializable
public data class SettingsFieldSchema(
    /**
     * The field path (e.g., "editor.tabSize").
     */
    val path: String,
    /**
     * The field type.
     */
    val type: SchemaType,
    /**
     * Human-readable description.
     */
    val description: String,
    /**
     * Default value as a string.
     */
    val defaultValue: String,
    /**
     * Allowed enum values (for ENUM type).
     */
    val enumValues: List<String> = emptyList(),
    /**
     * Minimum value (for INTEGER/NUMBER).
     */
    val minimum: Double? = null,
    /**
     * Maximum value (for INTEGER/NUMBER).
     */
    val maximum: Double? = null,
)

/**
 * Versioned settings container for migration support.
 */
@Serializable
public data class VersionedSettings(
    /**
     * The schema version of this settings file.
     */
    val version: Int = CURRENT_VERSION,
    /**
     * The raw settings JSON content.
     */
    val settings: kotlinx.serialization.json.JsonElement? = null,
) {
    public companion object {
        /**
         * The current settings schema version.
         */
        public const val CURRENT_VERSION: Int = 1
    }
}

/**
 * Generates JSON Schema for JetaProg settings.
 *
 * The schema enables:
 * - Autocomplete in JSON editors for settings files
 * - Validation of settings values
 * - Documentation of available settings
 */
public object SettingsSchemaGenerator {
    private val fields = mutableListOf<SettingsFieldSchema>()

    init {
        buildSchema()
    }

    /**
     * Returns all field schemas.
     */
    public fun getFields(): List<SettingsFieldSchema> = fields.toList()

    /**
     * Returns the field schema for a specific path.
     */
    public fun getField(path: String): SettingsFieldSchema? = fields.find { it.path == path }

    /**
     * Generates a JSON Schema string.
     */
    public fun generateJsonSchema(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "${"$"}schema": "http://json-schema.org/draft-07/schema#",""")
        sb.appendLine("""  "title": "JetaProg Settings",""")
        sb.appendLine("""  "description": "Configuration schema for JetaProg IDE settings",""")
        sb.appendLine("""  "type": "object",""")
        sb.appendLine("""  "properties": {""")

        // Group by category
        val categories = fields.groupBy { it.path.substringBefore('.') }
        val catEntries = categories.entries.toList()

        for ((catIdx, entry) in catEntries.withIndex()) {
            val (category, catFields) = entry
            sb.appendLine("""    "$category": {""")
            sb.appendLine("""      "type": "object",""")
            sb.appendLine("""      "properties": {""")

            for ((fieldIdx, field) in catFields.withIndex()) {
                val fieldName = field.path.substringAfter('.')
                sb.appendLine("""        "$fieldName": {""")
                sb.appendLine("""          "type": "${field.type.name.lowercase()}",""")
                sb.appendLine("""          "description": "${field.description}",""")
                sb.appendLine("""          "default": ${formatDefault(field)}""")
                if (field.enumValues.isNotEmpty()) {
                    sb.appendLine("""          ,"enum": [${field.enumValues.joinToString(", ") { "\"$it\"" }}]""")
                }
                if (field.minimum != null) {
                    sb.appendLine("""          ,"minimum": ${field.minimum}""")
                }
                if (field.maximum != null) {
                    sb.appendLine("""          ,"maximum": ${field.maximum}""")
                }
                sb.append("        }")
                if (fieldIdx < catFields.size - 1) sb.append(",")
                sb.appendLine()
            }

            sb.appendLine("      }")
            sb.append("    }")
            if (catIdx < catEntries.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("  }")
        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * Adds a custom field schema (for plugin-contributed settings).
     */
    public fun addField(field: SettingsFieldSchema) {
        fields.add(field)
    }

    private fun formatDefault(field: SettingsFieldSchema): String =
        when (field.type) {
            SchemaType.STRING, SchemaType.ENUM -> "\"${field.defaultValue}\""
            SchemaType.BOOLEAN -> field.defaultValue
            SchemaType.INTEGER, SchemaType.NUMBER -> field.defaultValue
            SchemaType.ARRAY -> "[]"
            SchemaType.OBJECT -> "{}"
        }

    @Suppress("LongMethod")
    private fun buildSchema() {
        // Appearance
        fields.add(
            SettingsFieldSchema(
                "appearance.theme",
                SchemaType.ENUM,
                "Color theme",
                "DARK",
                enumValues = listOf("DARK", "LIGHT"),
            ),
        )
        fields.add(
            SettingsFieldSchema("appearance.fontFamily", SchemaType.STRING, "Editor font family", "JetBrains Mono"),
        )
        fields.add(
            SettingsFieldSchema(
                "appearance.fontSize",
                SchemaType.INTEGER,
                "Editor font size",
                "13",
                minimum = 6.0,
                maximum = 72.0,
            ),
        )
        fields.add(
            SettingsFieldSchema(
                "appearance.uiScale",
                SchemaType.NUMBER,
                "UI scale factor",
                "1.0",
                minimum = 0.5,
                maximum = 3.0,
            ),
        )

        // Editor
        fields.add(
            SettingsFieldSchema(
                "editor.tabSize",
                SchemaType.INTEGER,
                "Spaces per tab",
                "4",
                minimum = 1.0,
                maximum = 16.0,
            ),
        )
        fields.add(SettingsFieldSchema("editor.useTabs", SchemaType.BOOLEAN, "Use tabs for indentation", "false"))
        fields.add(SettingsFieldSchema("editor.showLineNumbers", SchemaType.BOOLEAN, "Show line numbers", "true"))
        fields.add(SettingsFieldSchema("editor.showMinimap", SchemaType.BOOLEAN, "Show minimap", "true"))
        fields.add(SettingsFieldSchema("editor.wordWrap", SchemaType.BOOLEAN, "Enable word wrap", "false"))
        fields.add(
            SettingsFieldSchema("editor.showWhitespace", SchemaType.BOOLEAN, "Show whitespace characters", "false"),
        )
        fields.add(
            SettingsFieldSchema("editor.highlightCurrentLine", SchemaType.BOOLEAN, "Highlight current line", "true"),
        )
        fields.add(
            SettingsFieldSchema("editor.bracketMatching", SchemaType.BOOLEAN, "Highlight matching brackets", "true"),
        )
        fields.add(SettingsFieldSchema("editor.autoCloseBrackets", SchemaType.BOOLEAN, "Auto-close brackets", "true"))
        fields.add(SettingsFieldSchema("editor.autoSave", SchemaType.BOOLEAN, "Auto-save files", "false"))
        fields.add(
            SettingsFieldSchema(
                "editor.autoSaveDelayMs",
                SchemaType.INTEGER,
                "Auto-save delay (ms)",
                "1000",
                minimum = 100.0,
                maximum = 60000.0,
            ),
        )
        fields.add(
            SettingsFieldSchema(
                "editor.trimTrailingWhitespace",
                SchemaType.BOOLEAN,
                "Trim trailing whitespace on save",
                "true",
            ),
        )
        fields.add(
            SettingsFieldSchema(
                "editor.insertFinalNewline",
                SchemaType.BOOLEAN,
                "Insert final newline on save",
                "true",
            ),
        )
        fields.add(
            SettingsFieldSchema(
                "editor.maxLineLength",
                SchemaType.INTEGER,
                "Max line length ruler",
                "120",
                minimum = 0.0,
                maximum = 500.0,
            ),
        )
    }
}

/**
 * Settings migration support.
 *
 * When the settings schema version changes, migrations transform
 * old settings to the new format.
 */
public interface SettingsMigration {
    /**
     * The source version this migration upgrades from.
     */
    public val fromVersion: Int

    /**
     * The target version this migration upgrades to.
     */
    public val toVersion: Int

    /**
     * Migrates settings from one version to another.
     *
     * @param settings The raw settings as a mutable map
     * @return The migrated settings
     */
    public fun migrate(settings: MutableMap<String, Any?>): MutableMap<String, Any?>
}

/**
 * Registry for settings migrations.
 */
public class SettingsMigrationRegistry {
    private val migrations = mutableListOf<SettingsMigration>()

    /**
     * Registers a migration.
     */
    public fun register(migration: SettingsMigration) {
        migrations.add(migration)
        migrations.sortBy { it.fromVersion }
    }

    /**
     * Applies all necessary migrations to bring settings from [fromVersion] to [toVersion].
     */
    public fun migrate(
        settings: MutableMap<String, Any?>,
        fromVersion: Int,
        toVersion: Int = VersionedSettings.CURRENT_VERSION,
    ): MutableMap<String, Any?> {
        var current = settings
        var version = fromVersion

        while (version < toVersion) {
            val migration =
                migrations.find { it.fromVersion == version }
                    ?: error("No migration found from version $version")
            current = migration.migrate(current)
            version = migration.toVersion
        }

        return current
    }
}
