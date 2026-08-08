package su.kidoz.jetaprog.configuration.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.GoCommand
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GoConfigurationDiscoveryTest {
    @Test
    fun `go module discovers build run and json test configurations`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    mapOf(
                        "/workspace/go.mod" to "module example.com/acme/server\n\ngo 1.25\n",
                        "/workspace/main.go" to "package main\n\nfunc main() {}\n",
                    ),
                )
            val discovery = ConfigurationDiscovery(ProjectDetector(fileSystem))

            val configurations = discovery.discoverConfigurations("/workspace")

            assertEquals(listOf("server Build", "server Run", "server Test"), configurations.map { it.name })
            assertEquals(
                listOf(ConfigurationType.GO_BUILD, ConfigurationType.GO_RUN, ConfigurationType.GO_TEST),
                configurations.map { it.type },
            )

            val build = assertIs<ConfigurationSettings.Go>(configurations[0].settings)
            assertEquals(GoCommand.BUILD, build.command)
            assertEquals("./...", build.packagePattern)

            val run = assertIs<ConfigurationSettings.Go>(configurations[1].settings)
            assertEquals(GoCommand.RUN, run.command)
            assertEquals(".", run.packagePattern)

            val test = assertIs<ConfigurationSettings.Go>(configurations[2].settings)
            assertEquals(GoCommand.TEST, test.command)
            assertEquals(listOf("-json"), test.arguments)
            assertEquals("./...", test.packagePattern)
        }

    @Test
    fun `go library skips run configuration and existing names`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    mapOf(
                        "/workspace/go.mod" to "module example.com/acme/library\n",
                        "/workspace/library.go" to "package library\n",
                    ),
                )
            val discovery = ConfigurationDiscovery(ProjectDetector(fileSystem))

            val configurations =
                discovery.discoverConfigurations(
                    projectPath = "/workspace",
                    existingNames = setOf("library Build"),
                )

            assertEquals(listOf("library Test"), configurations.map { it.name })
            assertEquals(ConfigurationType.GO_TEST, configurations.single().type)
        }

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
