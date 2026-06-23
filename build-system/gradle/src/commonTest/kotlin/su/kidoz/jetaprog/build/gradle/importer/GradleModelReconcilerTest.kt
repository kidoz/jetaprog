package su.kidoz.jetaprog.build.gradle.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleModelReconcilerTest {
    private fun module(
        path: String,
        source: List<String> = listOf("src/commonMain/kotlin"),
        test: List<String> = emptyList(),
    ) = GradleModuleModel(path = path, name = path.replace('/', '-'), sourceRoots = source, testRoots = test)

    private fun existing(
        path: String,
        source: List<String> = listOf("src/commonMain/kotlin"),
        test: List<String> = emptyList(),
    ) = ExistingModuleEntry(path = path, sourceRoots = source, testRoots = test)

    @Test
    fun matchingModelAndMetadataAreInSync() {
        val imported = GradleImportModel("jetaprog", listOf(module("core/common"), module("acp/protocol")))
        val existing = listOf(existing("core/common"), existing("acp/protocol"))

        val diff = GradleModelReconciler.reconcile(imported, existing)

        assertTrue(diff.isInSync)
        assertEquals(emptyList(), diff.missingModules)
        assertEquals(emptyList(), diff.staleModules)
    }

    @Test
    fun reportsModulesMissingFromMetadata() {
        val imported = GradleImportModel("jetaprog", listOf(module("core/common"), module("acp/client")))
        val existing = listOf(existing("core/common"))

        val diff = GradleModelReconciler.reconcile(imported, existing)

        assertEquals(listOf("acp/client"), diff.missingModules)
        assertFalse(diff.isInSync)
    }

    @Test
    fun reportsStaleModulesInMetadata() {
        val imported = GradleImportModel("jetaprog", listOf(module("core/common")))
        val existing = listOf(existing("core/common"), existing("legacy/removed"))

        val diff = GradleModelReconciler.reconcile(imported, existing)

        assertEquals(listOf("legacy/removed"), diff.staleModules)
    }

    @Test
    fun buildSrcIsIgnoredByDefault() {
        val imported = GradleImportModel("jetaprog", listOf(module("core/common")))
        val existing = listOf(existing("core/common"), existing("buildSrc"))

        val diff = GradleModelReconciler.reconcile(imported, existing)

        assertTrue(diff.isInSync)
    }

    @Test
    fun reportsSourceRootDrift() {
        val imported =
            GradleImportModel(
                "jetaprog",
                listOf(
                    module("app/desktop", source = listOf("src/jvmMain/kotlin"), test = listOf("src/jvmTest/kotlin")),
                ),
            )
        val existing = listOf(existing("app/desktop", source = listOf("src/jvmMain/kotlin")))

        val diff = GradleModelReconciler.reconcile(imported, existing)

        assertEquals(1, diff.rootDiffs.size)
        val rootDiff = diff.rootDiffs.single()
        assertEquals("app/desktop", rootDiff.path)
        assertEquals(listOf("src/jvmTest/kotlin"), rootDiff.missingRoots)
        assertEquals(emptyList(), rootDiff.staleRoots)
        assertFalse(diff.isInSync)
    }
}
