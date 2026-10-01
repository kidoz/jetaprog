package su.kidoz.jetaprog.configuration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CargoSettingsSerializationTest {
    @Test
    fun savedCargoPackageSelectionsKeepTheirSerializedKey() {
        for (type in listOf("cargo_build", "cargo_test", "cargo_clippy")) {
            val saved = """{"type":"$type","package_":"workspace-tool"}"""
            val settings = Json.decodeFromString<ConfigurationSettings>(saved)
            val packageName =
                when (type) {
                    "cargo_build" -> assertIs<ConfigurationSettings.CargoBuild>(settings).packageName
                    "cargo_test" -> assertIs<ConfigurationSettings.CargoTest>(settings).packageName
                    else -> assertIs<ConfigurationSettings.CargoClippy>(settings).packageName
                }
            assertEquals("workspace-tool", packageName)

            val encoded = Json.encodeToJsonElement(ConfigurationSettings.serializer(), settings).jsonObject
            assertEquals("workspace-tool", encoded["package_"]?.jsonPrimitive?.content)
            assertFalse("packageName" in encoded)
        }
    }
}
