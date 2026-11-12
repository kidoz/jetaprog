plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.lint)
                api(projects.editor.core)
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.buildSystem.go)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        jvmMain {
            dependencies {
                implementation(projects.core.platform)
                implementation(libs.kotlin.logging)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
