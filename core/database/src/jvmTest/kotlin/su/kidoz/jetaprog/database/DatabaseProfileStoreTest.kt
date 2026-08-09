package su.kidoz.jetaprog.database

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DatabaseProfileStoreTest {
    @Test
    fun `round trips profiles without credential fields`() =
        runTest {
            val directory = Files.createTempDirectory("jetaprog-database-profiles").toFile()
            val file = directory.resolve("database-connections.json")
            val store = JvmDatabaseProfileStore(file)
            val expected = listOf(profile(DatabaseDialect.POSTGRESQL))

            assertTrue(store.save(expected).isSuccess)
            assertEquals(expected, store.load().getOrThrow())
            assertFalse(file.readText().contains("password", ignoreCase = true))
        }

    @Test
    fun `missing profile file loads as an empty list`() =
        runTest {
            val file = Files.createTempDirectory("jetaprog-database-profiles").resolve("missing.json").toFile()

            assertEquals(emptyList(), JvmDatabaseProfileStore(file).load().getOrThrow())
        }
}
