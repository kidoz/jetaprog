package su.kidoz.jetaprog.app.navigation

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Go to File used a hardcoded extension list that left out whole languages; it now
 * offers every extension a language definition claims.
 */
class DefaultNavigationServiceFileSearchTest {
    private val root = Files.createTempDirectory("jetaprog-file-search").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun filesOfEveryKnownLanguageAreFound() =
        runTest {
            listOf(
                "Orders.cs",
                "orders.sql",
                "orders.html",
                "orders.css",
                "CMakeLists.txt",
                "orders.cc",
                "orders.mts",
                "orders.zzz",
            ).forEach { File(root, it).writeText("") }
            val service =
                DefaultNavigationService(
                    lspClient = null,
                    fileSystem = JvmFileSystem(),
                    workspacePath = root.absolutePath,
                )

            val found = service.searchFiles("orders", SearchScope.PROJECT, limit = 20).map { it.target.name }.toSet()

            assertEquals(
                setOf("Orders.cs", "orders.sql", "orders.html", "orders.css", "orders.cc", "orders.mts"),
                found,
            )
        }
}
