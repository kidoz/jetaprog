package su.kidoz.jetaprog.build.gradle.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class GradleClasspathResolverTest {
    private val model =
        GradleImportModel(
            rootName = "jetaprog",
            modules =
                listOf(
                    GradleModuleModel(
                        path = "core/common",
                        name = "core-common",
                        classpath = listOf("/libs/common.jar"),
                    ),
                    GradleModuleModel(
                        path = "app/desktop",
                        name = "app-desktop",
                        moduleDependencies = listOf("core-common"),
                        classpath = listOf("/libs/compose.jar"),
                    ),
                    GradleModuleModel(
                        path = "app/desktop/feature",
                        name = "feature",
                        classpath = listOf("/libs/feature.jar"),
                    ),
                ),
        )
    private val resolver = GradleClasspathResolver("/workspace/jetaprog", model)

    @Test
    fun resolvesOwningModuleAndTransitiveProjectClasspath() {
        assertEquals(
            listOf("/libs/compose.jar", "/libs/common.jar"),
            resolver.classpathFor("/workspace/jetaprog/app/desktop/src/jvmMain/kotlin/App.kt"),
        )
    }

    @Test
    fun prefersNestedModule() {
        assertEquals(
            "app/desktop/feature",
            resolver.moduleFor("/workspace/jetaprog/app/desktop/feature/src/main/kotlin/Feature.kt")?.path,
        )
    }

    @Test
    fun fallsBackToWorkspaceClasspathOutsideImportedModules() {
        assertEquals(
            listOf("/libs/common.jar", "/libs/compose.jar", "/libs/feature.jar"),
            resolver.classpathFor("/workspace/jetaprog/build.gradle.kts"),
        )
    }
}
