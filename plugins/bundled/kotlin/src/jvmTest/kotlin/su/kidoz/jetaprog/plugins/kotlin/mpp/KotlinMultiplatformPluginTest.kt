package su.kidoz.jetaprog.plugins.kotlin.mpp

import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.TaskOutcome
import su.kidoz.jetaprog.plugins.kotlin.KotlinPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinMultiplatformPluginTest {
    @Test
    fun manifestDependsOnKotlinPlugin() {
        val manifest = KotlinMultiplatformPlugin().manifest
        assertEquals(KotlinMultiplatformPlugin.PLUGIN_ID, manifest.id)
        assertTrue(KotlinPlugin.PLUGIN_ID in manifest.dependencies)
    }

    @Test
    fun manifestActivatesOnGradleWorkspaces() {
        val events = KotlinMultiplatformPlugin().manifest.activationEvents
        assertTrue("workspaceContains:build.gradle" in events)
        assertTrue("workspaceContains:build.gradle.kts" in events)
    }

    @Test
    fun formatsGradleOutputEvents() {
        assertEquals("compiling", formatGradleOutput(GradleOutput.Stdout("compiling")))
        assertEquals("[stderr] warning", formatGradleOutput(GradleOutput.Stderr("warning")))
        assertEquals("> Task :jvmTest", formatGradleOutput(GradleOutput.TaskStarted(":jvmTest")))
        assertEquals(
            "> Task :jvmTest SUCCESS",
            formatGradleOutput(GradleOutput.TaskCompleted(":jvmTest", TaskOutcome.SUCCESS)),
        )
        assertEquals(
            "Build succeeded with exit code 0",
            formatGradleOutput(GradleOutput.BuildFinished(success = true, exitCode = 0)),
        )
        assertEquals(
            "Build failed with exit code 1",
            formatGradleOutput(GradleOutput.BuildFinished(success = false, exitCode = 1)),
        )
    }
}
