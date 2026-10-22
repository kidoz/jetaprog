package su.kidoz.jetaprog.app.project

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.build.gradle.wrapper.GradleWrapperGenerator
import su.kidoz.jetaprog.build.gradle.wrapper.GradleWrapperSpec
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProjectCreatorTest {
    private val processExecutor: ProcessExecutor = mockk()
    private lateinit var projectsDir: File

    @BeforeTest
    fun setUp() {
        projectsDir = createTempDirectory("project creator ").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectsDir.deleteRecursively()
    }

    @Test
    fun `creates Kotlin project with complete wrapper and current versions`() =
        runTest {
            val wrapperGenerator = RecordingWrapperGenerator()
            val creator = ProjectCreator(JvmFileSystem(), processExecutor, wrapperGenerator)

            val result = creator.createProject(kotlinProjectConfig()).getOrThrow()

            val project = File(result)
            assertTrue(File(project, "gradlew").isFile)
            assertTrue(File(project, "gradlew.bat").isFile)
            assertTrue(File(project, "gradle/wrapper/gradle-wrapper.jar").isFile)
            val buildScript = File(project, "build.gradle.kts").readText()
            assertContains(buildScript, "kotlin(\"jvm\") version \"${ProjectTemplateVersions.KOTLIN}\"")
            assertContains(buildScript, "jvmToolchain(${ProjectTemplateVersions.JVM_TOOLCHAIN})")
            assertEquals(ProjectTemplateVersions.gradleWrapper, wrapperGenerator.requestedSpec)
            assertFalse(File(project, ".git").exists())
            coVerify(exactly = 0) { processExecutor.execute(any(), any(), any(), any()) }
        }

    @Test
    fun `refuses to overwrite existing project directory`() =
        runTest {
            val project = File(projectsDir, PROJECT_NAME).apply { mkdirs() }
            val marker = File(project, "keep.txt").apply { writeText("keep") }
            val wrapperGenerator = RecordingWrapperGenerator()

            val result =
                ProjectCreator(JvmFileSystem(), processExecutor, wrapperGenerator)
                    .createProject(kotlinProjectConfig().copy(initGit = true))

            assertTrue(result.isFailure)
            assertEquals("keep", marker.readText())
            assertEquals(0, wrapperGenerator.invocationCount)
            coVerify(exactly = 0) { processExecutor.execute(any(), any(), any(), any()) }
        }

    @Test
    fun `removes partial project when wrapper generation fails`() =
        runTest {
            val creator =
                ProjectCreator(
                    JvmFileSystem(),
                    processExecutor,
                    GradleWrapperGenerator { _, _ -> Result.failure(IllegalStateException("wrapper failed")) },
                )

            val result = creator.createProject(kotlinProjectConfig())

            assertTrue(result.isFailure)
            assertFalse(File(projectsDir, PROJECT_NAME).exists())
        }

    @Test
    fun `initializes Git in the new project when requested`() =
        runTest {
            val config = kotlinProjectConfig().copy(initGit = true)
            coEvery { processExecutor.execute(any(), any(), any(), any()) } coAnswers {
                assertTrue(File(config.projectPath, "build.gradle.kts").isFile)
                assertTrue(File(config.projectPath, ".gitignore").isFile)
                Result.success(ProcessResult(0, "Initialized empty Git repository", ""))
            }

            val result =
                ProjectCreator(JvmFileSystem(), processExecutor, RecordingWrapperGenerator())
                    .createProject(config)

            assertEquals(config.projectPath, result.getOrThrow())
            coVerify(exactly = 1) {
                processExecutor.execute(listOf("git", "init"), config.projectPath, emptyMap(), 30_000)
            }
        }

    @Test
    fun `reports Git exit errors and removes the partial project`() =
        runTest {
            val creator = ProjectCreator(JvmFileSystem(), processExecutor, RecordingWrapperGenerator())
            val outputs =
                listOf(
                    ProcessResult(128, "", "fatal: cannot create repository"),
                    ProcessResult(128, "cannot create repository", ""),
                    ProcessResult(128, "", ""),
                )
            for (output in outputs) {
                coEvery { processExecutor.execute(any(), any(), any(), any()) } returns Result.success(output)

                val result = creator.createProject(kotlinProjectConfig().copy(initGit = true))

                val message = result.exceptionOrNull()?.message.orEmpty()
                assertContains(message, "Failed to initialize Git repository")
                assertContains(message, "exit code 128")
                assertContains(message, output.stderr.ifBlank { output.stdout })
                assertFalse(File(projectsDir, PROJECT_NAME).exists())
            }
        }

    @Test
    fun `reports missing Git with installation guidance`() =
        runTest {
            val failure = IOException("Cannot run program git")
            coEvery { processExecutor.execute(any(), any(), any(), any()) } returns Result.failure(failure)
            val creator = ProjectCreator(JvmFileSystem(), processExecutor, RecordingWrapperGenerator())

            val result = creator.createProject(kotlinProjectConfig().copy(initGit = true))

            val error = result.exceptionOrNull()
            assertContains(error?.message.orEmpty(), "Ensure Git is installed and available on PATH")
            assertSame(failure, error?.cause)
            assertFalse(File(projectsDir, PROJECT_NAME).exists())
        }

    @Test
    fun `cancelling Git initialization rolls back and propagates cancellation`() =
        runTest {
            coEvery { processExecutor.execute(any(), any(), any(), any()) } coAnswers {
                currentCoroutineContext().cancel()
                awaitCancellation()
            }
            val creator = ProjectCreator(JvmFileSystem(), processExecutor, RecordingWrapperGenerator())

            val creation = async { creator.createProject(kotlinProjectConfig().copy(initGit = true)) }

            assertFailsWith<CancellationException> { creation.await() }
            assertFalse(File(projectsDir, PROJECT_NAME).exists())
        }

    private fun kotlinProjectConfig(): ProjectConfig =
        ProjectConfig(
            name = PROJECT_NAME,
            location = projectsDir.absolutePath,
            template = KotlinGradleTemplate,
            packageName = "com.example.sample",
            initGit = false,
            createReadme = false,
            license = License.NONE,
        )

    private class RecordingWrapperGenerator : GradleWrapperGenerator {
        var invocationCount: Int = 0
            private set
        var requestedSpec: GradleWrapperSpec? = null
            private set

        override suspend fun generate(
            projectRoot: String,
            spec: GradleWrapperSpec,
        ): Result<Unit> =
            runCatching {
                invocationCount++
                requestedSpec = spec
                File(projectRoot, "gradle/wrapper").mkdirs()
                File(projectRoot, "gradlew").writeText("#!/bin/sh")
                File(projectRoot, "gradlew.bat").writeText("@echo off")
                File(projectRoot, "gradle/wrapper/gradle-wrapper.jar").writeBytes(byteArrayOf(0))
                File(projectRoot, "gradle/wrapper/gradle-wrapper.properties").writeText("distributionUrl=fake")
            }
    }

    private companion object {
        const val PROJECT_NAME = "sample"
    }
}
