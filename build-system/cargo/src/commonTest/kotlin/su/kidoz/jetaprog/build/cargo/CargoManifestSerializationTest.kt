package su.kidoz.jetaprog.build.cargo

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CargoManifestSerializationTest {
    @Test
    fun serializedManifestRetainsExistingFieldNames() {
        val saved =
            """
            {
                "package_": {"name": "tool", "version": "1.0.0", "rust_version": "1.85"},
                "dependencies": {"serde": {"default_features": false}},
                "lib": {"crate_type": ["cdylib"]}
            }
            """.trimIndent()

        val manifest = Json.decodeFromString<CargoManifest>(saved)

        assertEquals("tool", manifest.packageName?.name)
        assertEquals("1.85", manifest.packageName?.rustVersion)
        assertFalse(manifest.dependencies.getValue("serde").defaultFeatures)
        assertEquals(listOf("cdylib"), manifest.lib?.crateType)
        assertEquals(
            Json.parseToJsonElement(saved),
            Json.encodeToJsonElement(CargoManifest.serializer(), manifest),
        )
    }
}
