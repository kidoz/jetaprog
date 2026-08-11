package su.kidoz.jetaprog.plugins.kotlin.mpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinMultiplatformBuildScriptTest {
    @Test
    fun detectsTheKotlinDslShorthandPlugin() {
        val script =
            """
            plugins {
                kotlin("multiplatform") version "2.4.10"
            }
            """.trimIndent()
        assertTrue(KotlinMultiplatformBuildScript.appliesMultiplatformPlugin(script))
    }

    @Test
    fun detectsTheExplicitPluginId() {
        val script =
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
            }
            """.trimIndent()
        assertTrue(KotlinMultiplatformBuildScript.appliesMultiplatformPlugin(script))
    }

    @Test
    fun detectsTheLegacyGroovyApply() {
        val script = "apply plugin: 'kotlin-multiplatform'"
        assertTrue(KotlinMultiplatformBuildScript.appliesMultiplatformPlugin(script))
    }

    @Test
    fun ignoresPlainJvmBuildScripts() {
        val script =
            """
            plugins {
                kotlin("jvm") version "2.4.10"
            }
            """.trimIndent()
        assertFalse(KotlinMultiplatformBuildScript.appliesMultiplatformPlugin(script))
    }

    @Test
    fun parsesDeclaredTargets() {
        val script =
            """
            kotlin {
                jvm {
                    withJava()
                }
                js(IR) { browser() }
                iosArm64()
                iosSimulatorArm64()

                sourceSets {
                    commonMain { }
                    jvmMain { }
                    jvmTest { }
                }
            }
            """.trimIndent()
        assertEquals(
            setOf("iosArm64", "iosSimulatorArm64", "js", "jvm"),
            KotlinMultiplatformBuildScript.declaredTargets(script),
        )
    }

    @Test
    fun usesCustomTargetNames() {
        val script = """kotlin { jvm("desktop") }"""
        assertEquals(setOf("desktop"), KotlinMultiplatformBuildScript.declaredTargets(script))
    }

    @Test
    fun mapsAndroidTargetToItsDefaultTargetName() {
        val script = "kotlin { androidTarget() }"
        assertEquals(setOf("android"), KotlinMultiplatformBuildScript.declaredTargets(script))
    }

    @Test
    fun doesNotMistakeSourceSetBlocksOrToolchainCallsForTargets() {
        val script =
            """
            kotlin {
                jvmToolchain(25)
                sourceSets {
                    jvmMain { }
                    jsMain { }
                }
            }
            """.trimIndent()
        assertEquals(emptySet(), KotlinMultiplatformBuildScript.declaredTargets(script))
    }

    @Test
    fun derivesTargetsFromSourceSetDirectories() {
        val directories = listOf("commonMain", "commonTest", "jvmMain", "jvmTest", "iosArm64Main")
        assertEquals(
            setOf("iosArm64", "jvm"),
            KotlinMultiplatformBuildScript.targetsFromSourceSetDirectories(directories),
        )
    }

    @Test
    fun buildsCompileAndTestTaskNames() {
        assertEquals("compileKotlinJvm", KotlinMultiplatformBuildScript.compileTaskFor("jvm"))
        assertEquals("compileKotlinIosArm64", KotlinMultiplatformBuildScript.compileTaskFor("iosArm64"))
        assertEquals("jvmTest", KotlinMultiplatformBuildScript.testTaskFor("jvm"))
        assertEquals("iosArm64Test", KotlinMultiplatformBuildScript.testTaskFor("iosArm64"))
    }
}
