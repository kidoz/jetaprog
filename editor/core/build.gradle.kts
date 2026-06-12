plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.platform)
                api(projects.editor.syntax)
                api(projects.lsp.protocol)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
