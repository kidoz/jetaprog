package su.kidoz.jetaprog.settings

import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.settings.model.EditorSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyFoldingSettingsTest {
    @Test
    fun existingSettingsEnableFoldingWithoutMigration() {
        assertTrue(Json.decodeFromString<EditorSettings>("{}").collapseImportsByDefault)
    }

    @Test
    fun disablingFoldingSurvivesPersistence() {
        val saved = Json.encodeToString(EditorSettings(collapseImportsByDefault = false))
        assertFalse(Json.decodeFromString<EditorSettings>(saved).collapseImportsByDefault)
    }
}
