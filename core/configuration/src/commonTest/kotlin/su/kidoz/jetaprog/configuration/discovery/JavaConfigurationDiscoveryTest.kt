package su.kidoz.jetaprog.configuration.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.JavaBuildTool
import su.kidoz.jetaprog.configuration.JavaCommand
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JavaConfigurationDiscoveryTest {
    @Test
    fun `Gradle Java application discovers run debug and test configurations`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    mapOf(
                        "/workspace/settings.gradle.kts" to "rootProject.name = \"sample-app\"",
                        "/workspace/build.gradle.kts" to
                            """
                            plugins {
                                java
                                application
                            }
                            application {
                                mainClass.set("com.example.Main")
                            }
                            """.trimIndent(),
                    ),
                    directories = setOf("/workspace/src/main/java"),
                )

            val configurations =
                ConfigurationDiscovery(
                    ProjectDetector(fileSystem),
                ).discoverConfigurations("/workspace")

            assertEquals(
                listOf(ConfigurationType.JAVA_RUN, ConfigurationType.JAVA_DEBUG, ConfigurationType.JAVA_TEST),
                configurations.map { it.type },
            )
            assertEquals(
                listOf("sample-app Run", "sample-app Debug", "sample-app Test"),
                configurations.map { it.name },
            )
            val settings = configurations.map { assertIs<ConfigurationSettings.Java>(it.settings) }
            assertEquals(listOf(JavaCommand.RUN, JavaCommand.DEBUG, JavaCommand.TEST), settings.map { it.command })
            assertEquals(listOf("run", "run", "test"), settings.map { it.task })
            assertEquals(List(3) { JavaBuildTool.GRADLE }, settings.map { it.buildTool })
            assertEquals("com.example.Main", settings.first().mainClass)
        }

    @Test
    fun `Maven Java application uses project artifact and wrapper`() =
        runTest {
            val fileSystem =
                MapFileSystem(
                    files =
                        mapOf(
                            "/workspace/pom.xml" to
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <parent><artifactId>parent-project</artifactId></parent>
                                  <artifactId>maven-app</artifactId>
                                  <build><plugins><plugin>
                                    <artifactId>exec-maven-plugin</artifactId>
                                    <configuration><mainClass>com.example.App</mainClass></configuration>
                                  </plugin></plugins></build>
                                </project>
                                """.trimIndent(),
                            "/workspace/mvnw" to "",
                        ),
                    directories = setOf("/workspace/src/main/java"),
                )

            val configurations =
                ConfigurationDiscovery(
                    ProjectDetector(fileSystem),
                ).discoverConfigurations("/workspace")

            assertEquals(listOf("maven-app Run", "maven-app Debug", "maven-app Test"), configurations.map { it.name })
            val run = assertIs<ConfigurationSettings.Java>(configurations.first().settings)
            assertEquals(JavaBuildTool.MAVEN, run.buildTool)
            assertEquals("compile exec:java", run.task)
            assertEquals("/workspace/mvnw", run.executable)
            assertEquals("com.example.App", run.mainClass)
        }

    private class MapFileSystem(
        private val files: Map<String, String>,
        private val directories: Set<String> = emptySet(),
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

        override suspend fun listDirectory(path: String): Result<List<FileEntry>> = Result.success(emptyList())

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
