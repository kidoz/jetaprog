package su.kidoz.jetaprog.app.project

import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.gradle.wrapper.JvmGradleWrapperGenerator
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectCreatorIntegrationTest {
    private lateinit var projectsDir: File

    @BeforeTest
    fun setUp() {
        projectsDir = createTempDirectory("project-creator-integration").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectsDir.deleteRecursively()
    }

    @Test
    fun `generated Kotlin Gradle project passes tests through its wrapper`() =
        runBlocking {
            val projectPath =
                ProjectCreator(JvmFileSystem(), JvmGradleWrapperGenerator())
                    .createProject(
                        ProjectConfig(
                            name = PROJECT_NAME,
                            location = projectsDir.absolutePath,
                            template = KotlinGradleTemplate,
                            packageName = "com.example.generated",
                            initGit = false,
                            createReadme = false,
                            license = License.NONE,
                        ),
                    ).getOrThrow()
            val project = File(projectPath)
            val output = File(project, "gradle-test-output.txt")
            val process =
                ProcessBuilder(wrapperCommand(project))
                    .directory(project)
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start()

            val completed = process.waitFor(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!completed) process.destroyForcibly().waitFor()

            assertTrue(completed, "Generated project build timed out.\n${output.readText()}")
            assertEquals(0, process.exitValue(), output.readText())
        }

    private fun wrapperCommand(project: File): List<String> =
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("cmd", "/c", File(project, "gradlew.bat").absolutePath, "test", "--quiet", "--no-daemon")
        } else {
            listOf(File(project, "gradlew").absolutePath, "test", "--quiet", "--no-daemon")
        }

    private companion object {
        const val PROJECT_NAME = "generated-kotlin-project"
        const val TEST_TIMEOUT_MINUTES = 3L
    }
}
