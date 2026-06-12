plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.editor.core)
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
