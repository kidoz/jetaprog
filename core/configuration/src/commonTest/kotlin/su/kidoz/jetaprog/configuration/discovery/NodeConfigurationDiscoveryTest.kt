package su.kidoz.jetaprog.configuration.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.NodePackageManager
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NodeConfigurationDiscoveryTest {
    @Test
    fun `package scripts discover pnpm run build and test configurations`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    mapOf(
                        "/workspace/package.json" to
                            """
                            {
                              "name": "web-app",
                              "packageManager": "pnpm@10.0.0",
                              "scripts": {
                                "start": "vite",
                                "build": "tsc && vite build",
                                "test": "vitest run",
                                "lint": "eslint ."
                              }
                            }
                            """.trimIndent(),
                    ),
                )
            val discovery = ConfigurationDiscovery(ProjectDetector(fileSystem))

            val configurations = discovery.discoverConfigurations("/workspace")

            assertEquals(listOf("web-app Run", "web-app Build", "web-app Test"), configurations.map { it.name })
            assertEquals(
                listOf(ConfigurationType.NODE_RUN, ConfigurationType.NODE_BUILD, ConfigurationType.NODE_TEST),
                configurations.map { it.type },
            )
            assertEquals(listOf("start", "build", "test"), configurations.map { nodeSettings(it).script })
            assertEquals(
                listOf(NodePackageManager.PNPM, NodePackageManager.PNPM, NodePackageManager.PNPM),
                configurations.map { nodeSettings(it).packageManager },
            )
            assertEquals(listOf(false, false, true), configurations.map { it.isTemporary })
        }

    @Test
    fun `lockfile selects yarn and dev script is used when start is absent`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    mapOf(
                        "/workspace/package.json" to
                            """
                            {
                              "name": "dashboard",
                              "scripts": {
                                "dev": "next dev",
                                "build": "next build",
                                "test": "jest"
                              }
                            }
                            """.trimIndent(),
                        "/workspace/yarn.lock" to "",
                    ),
                )
            val discovery = ConfigurationDiscovery(ProjectDetector(fileSystem))

            val configurations =
                discovery.discoverConfigurations(
                    projectPath = "/workspace",
                    existingNames = setOf("dashboard Build"),
                )

            assertEquals(listOf("dashboard Run", "dashboard Test"), configurations.map { it.name })
            assertEquals("dev", nodeSettings(configurations[0]).script)
            assertEquals(NodePackageManager.YARN, nodeSettings(configurations[0]).packageManager)
        }

    private fun nodeSettings(
        configuration: su.kidoz.jetaprog.configuration.RunConfiguration,
    ): ConfigurationSettings.Node = assertIs(configuration.settings)

    private class MapFileSystem(
        private val files: Map<String, String>,
    ) : FileSystem {
        override suspend fun readBytes(path: String): Result<ByteArray> =
            files[path]
                ?.encodeToByteArray()
                ?.let(Result.Companion::success)
                ?: Result.failure(NoSuchElementException(path))

        override suspend fun readText(
            path: String,
            charset: String,
        ): Result<String> = files[path]?.let(Result.Companion::success) ?: Result.failure(NoSuchElementException(path))

        override suspend fun writeBytes(
            path: String,
            content: ByteArray,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun writeText(
            path: String,
            content: String,
            charset: String,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun delete(path: String): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteRecursively(path: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun createDirectory(path: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun listDirectory(path: String): Result<List<FileEntry>> =
            Result.success(
                files.keys
                    .filter { it.substringBeforeLast('/') == path }
                    .map { filePath ->
                        FileEntry(
                            name = filePath.substringAfterLast('/'),
                            path = filePath,
                            isDirectory = false,
                            isFile = true,
                            isSymbolicLink = false,
                            size = files.getValue(filePath).length.toLong(),
                            lastModified = 0,
                            isHidden = false,
                        )
                    },
            )

        override suspend fun exists(path: String): Boolean = path in files

        override suspend fun isDirectory(path: String): Boolean = false

        override suspend fun isFile(path: String): Boolean = path in files

        override suspend fun getInfo(path: String): Result<FileEntry> = Result.failure(UnsupportedOperationException())

        override suspend fun copy(
            source: String,
            destination: String,
            overwrite: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun move(
            source: String,
            destination: String,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override fun watch(
            path: String,
            recursive: Boolean,
        ): Flow<FileSystemEvent> = emptyFlow()

        override fun resolve(
            base: String,
            relative: String,
        ): String = "$base/$relative"

        override fun parent(path: String): String? = path.substringBeforeLast('/', missingDelimiterValue = "")

        override fun fileName(path: String): String = path.substringAfterLast('/')

        override fun extension(path: String): String = path.substringAfterLast('.', missingDelimiterValue = "")
    }
}
