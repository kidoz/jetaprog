package su.kidoz.jetaprog.configuration.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DotNetConfigurationDiscoveryTest {
    @Test
    fun `solution discovers nested C sharp run debug build and test targets`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    files =
                        mapOf(
                            "/workspace/Demo.sln" to "",
                            "/workspace/src/Demo.App/Demo.App.csproj" to
                                """
                                <Project Sdk="Microsoft.NET.Sdk">
                                  <PropertyGroup>
                                    <OutputType>Exe</OutputType>
                                    <TargetFramework>net10.0</TargetFramework>
                                    <AssemblyName>Demo.Cli</AssemblyName>
                                  </PropertyGroup>
                                </Project>
                                """.trimIndent(),
                            "/workspace/tests/Demo.Tests/Demo.Tests.csproj" to
                                """
                                <Project Sdk="Microsoft.NET.Sdk">
                                  <PropertyGroup>
                                    <TargetFramework>net10.0</TargetFramework>
                                    <IsTestProject>true</IsTestProject>
                                  </PropertyGroup>
                                </Project>
                                """.trimIndent(),
                        ),
                    directories =
                        setOf(
                            "/workspace",
                            "/workspace/src",
                            "/workspace/src/Demo.App",
                            "/workspace/tests",
                            "/workspace/tests/Demo.Tests",
                        ),
                )

            val configurations =
                ConfigurationDiscovery(ProjectDetector(fileSystem))
                    .discoverConfigurations("/workspace")

            assertEquals(
                listOf(
                    ConfigurationType.DOTNET_BUILD,
                    ConfigurationType.DOTNET_RUN,
                    ConfigurationType.DOTNET_DEBUG,
                    ConfigurationType.DOTNET_TEST,
                ),
                configurations.map { it.type },
            )
            val build = assertIs<ConfigurationSettings.DotNetBuild>(configurations[0].settings)
            val run = assertIs<ConfigurationSettings.DotNetRun>(configurations[1].settings)
            val debug = assertIs<ConfigurationSettings.DotNetDebug>(configurations[2].settings)
            val test = assertIs<ConfigurationSettings.DotNetTest>(configurations[3].settings)
            assertEquals("/workspace/Demo.sln", build.targetPath)
            assertEquals("/workspace/src/Demo.App/Demo.App.csproj", run.projectPath)
            assertEquals("net10.0", debug.targetFramework)
            assertEquals("Demo.Cli", debug.assemblyName)
            assertEquals("/workspace/Demo.sln", test.targetPath)
        }

    @Test
    fun `class library does not offer run or debug configurations`() =
        runTest {
            val projectPath = "/workspace/Library.csproj"
            val fileSystem =
                MapFileSystem(
                    files =
                        mapOf(
                            projectPath to
                                """
                                <Project Sdk="Microsoft.NET.Sdk">
                                  <PropertyGroup><TargetFramework>net10.0</TargetFramework></PropertyGroup>
                                </Project>
                                """.trimIndent(),
                        ),
                    directories = setOf("/workspace"),
                )

            val configurations =
                ConfigurationDiscovery(ProjectDetector(fileSystem))
                    .discoverConfigurations("/workspace")

            assertEquals(
                listOf(ConfigurationType.DOTNET_BUILD, ConfigurationType.DOTNET_TEST),
                configurations.map { it.type },
            )
        }

    @Test
    fun `DotProlog console project discovers run debug build and test targets`() =
        runTest {
            val projectPath = "/workspace/HelloProlog.dplproj"
            val fileSystem =
                MapFileSystem(
                    files =
                        mapOf(
                            projectPath to
                                """
                                <Project Sdk="Microsoft.NET.Sdk">
                                  <Sdk Name="DotProlog.Sdk" Version="0.5.0" />
                                  <PropertyGroup>
                                    <OutputType>Exe</OutputType>
                                    <TargetFramework>net10.0</TargetFramework>
                                  </PropertyGroup>
                                </Project>
                                """.trimIndent(),
                        ),
                    directories = setOf("/workspace"),
                )

            val configurations =
                ConfigurationDiscovery(ProjectDetector(fileSystem))
                    .discoverConfigurations("/workspace")

            assertEquals(
                listOf(
                    ConfigurationType.DOTNET_BUILD,
                    ConfigurationType.DOTNET_RUN,
                    ConfigurationType.DOTNET_DEBUG,
                    ConfigurationType.DOTNET_TEST,
                ),
                configurations.map { it.type },
            )
            val run = assertIs<ConfigurationSettings.DotNetRun>(configurations[1].settings)
            assertEquals(projectPath, run.projectPath)
        }

    private class MapFileSystem(
        private val files: Map<String, String>,
        private val directories: Set<String>,
    ) : FileSystem {
        override suspend fun readBytes(path: String): Result<ByteArray> =
            files[path]?.encodeToByteArray()?.let(Result.Companion::success)
                ?: Result.failure(NoSuchElementException(path))

        override suspend fun readText(
            path: String,
            charset: String,
        ): Result<String> = files[path]?.let(Result.Companion::success) ?: Result.failure(NoSuchElementException(path))

        override suspend fun writeBytes(
            path: String,
            content: ByteArray,
        ): Result<Unit> = unsupported()

        override suspend fun writeText(
            path: String,
            content: String,
            charset: String,
        ): Result<Unit> = unsupported()

        override suspend fun delete(path: String): Result<Unit> = unsupported()

        override suspend fun deleteRecursively(path: String): Result<Unit> = unsupported()

        override suspend fun createDirectory(path: String): Result<Unit> = unsupported()

        override suspend fun listDirectory(path: String): Result<List<FileEntry>> {
            val prefix = "${path.trimEnd('/')}/"
            val entries =
                (files.keys + directories)
                    .asSequence()
                    .filter { it.startsWith(prefix) }
                    .map { it.removePrefix(prefix) }
                    .filter { it.isNotEmpty() && '/' !in it }
                    .distinct()
                    .map { name ->
                        val entryPath = "$prefix$name"
                        FileEntry(
                            name = name,
                            path = entryPath,
                            isDirectory = entryPath in directories,
                            isFile = entryPath in files,
                            isSymbolicLink = false,
                            size = files[entryPath]?.length?.toLong() ?: 0,
                            lastModified = 0,
                            isHidden = name.startsWith('.'),
                        )
                    }.toList()
            return Result.success(entries)
        }

        override suspend fun exists(path: String): Boolean = path in files || path in directories

        override suspend fun isDirectory(path: String): Boolean = path in directories

        override suspend fun isFile(path: String): Boolean = path in files

        override suspend fun getInfo(path: String): Result<FileEntry> = unsupported()

        override suspend fun copy(
            source: String,
            destination: String,
            overwrite: Boolean,
        ): Result<Unit> = unsupported()

        override suspend fun move(
            source: String,
            destination: String,
        ): Result<Unit> = unsupported()

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

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }
}
