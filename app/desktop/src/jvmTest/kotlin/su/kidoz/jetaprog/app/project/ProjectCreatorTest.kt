package su.kidoz.jetaprog.app.project

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.build.gradle.wrapper.GradleWrapperGenerator
import su.kidoz.jetaprog.build.gradle.wrapper.GradleWrapperSpec
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectCreatorTest {
    private lateinit var projectsDir: File

    @BeforeTest
    fun setUp() {
        projectsDir = createTempDirectory("project-creator").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectsDir.deleteRecursively()
    }

    @Test
    fun `creates Kotlin project with complete wrapper and current versions`() =
        runTest {
            val wrapperGenerator = RecordingWrapperGenerator()
            val creator = ProjectCreator(JvmFileSystem(), wrapperGenerator)

            val result = creator.createProject(kotlinProjectConfig()).getOrThrow()

            val project = File(result)
            assertTrue(File(project, "gradlew").isFile)
            assertTrue(File(project, "gradlew.bat").isFile)
            assertTrue(File(project, "gradle/wrapper/gradle-wrapper.jar").isFile)
            val buildScript = File(project, "build.gradle.kts").readText()
            assertContains(buildScript, "kotlin(\"jvm\") version \"${ProjectTemplateVersions.KOTLIN}\"")
            assertContains(buildScript, "jvmToolchain(${ProjectTemplateVersions.JVM_TOOLCHAIN})")
            assertEquals(ProjectTemplateVersions.gradleWrapper, wrapperGenerator.requestedSpec)
        }

    @Test
    fun `refuses to overwrite existing project directory`() =
        runTest {
            val project = File(projectsDir, PROJECT_NAME).apply { mkdirs() }
            val marker = File(project, "keep.txt").apply { writeText("keep") }
            val wrapperGenerator = RecordingWrapperGenerator()

            val result = ProjectCreator(JvmFileSystem(), wrapperGenerator).createProject(kotlinProjectConfig())

            assertTrue(result.isFailure)
            assertEquals("keep", marker.readText())
            assertEquals(0, wrapperGenerator.invocationCount)
        }

    @Test
    fun `removes partial project when wrapper generation fails`() =
        runTest {
            val creator =
                ProjectCreator(
                    JvmFileSystem(),
                    GradleWrapperGenerator { _, _ -> Result.failure(IllegalStateException("wrapper failed")) },
                )

            val result = creator.createProject(kotlinProjectConfig())

            assertTrue(result.isFailure)
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
