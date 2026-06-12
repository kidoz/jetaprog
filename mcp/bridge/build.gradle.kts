plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.mcp.server)
                api(projects.core.platform)
                api(projects.editor.core)
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
