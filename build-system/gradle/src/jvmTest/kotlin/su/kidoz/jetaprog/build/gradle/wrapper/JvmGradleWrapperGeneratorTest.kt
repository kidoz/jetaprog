package su.kidoz.jetaprog.build.gradle.wrapper

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class JvmGradleWrapperGeneratorTest {
    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("gradle-wrapper-generator").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `generates complete wrapper with checksum`() =
        runBlocking {
            val spec =
                GradleWrapperSpec(
                    gradleVersion = "9.7.0",
                    distributionSha256Sum = GRADLE_9_7_CHECKSUM,
                )

            JvmGradleWrapperGenerator().generate(projectDir.absolutePath, spec).getOrThrow()

            assertTrue(File(projectDir, "gradlew").isFile)
            assertTrue(File(projectDir, "gradlew.bat").isFile)
            assertTrue(File(projectDir, "gradle/wrapper/gradle-wrapper.jar").isFile)
            val properties = File(projectDir, "gradle/wrapper/gradle-wrapper.properties").readText()
            assertContains(properties, "gradle-9.7.0-bin.zip")
            assertContains(properties, "distributionSha256Sum=$GRADLE_9_7_CHECKSUM")
        }

    private companion object {
        const val GRADLE_9_7_CHECKSUM = "84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae"
    }
}
