package su.kidoz.jetaprog.app.gradle

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionService
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleProjectDetectionTest {
    @Test
    fun plainFoldersDoNotRequestAutomaticGradleImport() =
        runTest {
            val fileSystem = mockk<FileSystem>()
            coEvery { fileSystem.exists(any()) } returns false
            val coordinator = GradleImportCoordinator("/plain", fileSystem, mockk<GradleExecutionService>())
            assertFalse(coordinator.isGradleProject())
        }

    @Test
    fun settingsOnlyAndBothBuildScriptDialectsAreRecognized() =
        runTest {
            listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle").forEach { file ->
                val fileSystem = mockk<FileSystem>()
                coEvery { fileSystem.exists(any()) } answers { firstArg<String>() == "/project/$file" }
                val coordinator = GradleImportCoordinator("/project", fileSystem, mockk<GradleExecutionService>())
                assertTrue(coordinator.isGradleProject(), file)
            }
        }
}
