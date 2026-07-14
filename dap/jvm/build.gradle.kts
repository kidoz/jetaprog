plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":dap:protocol"))
                implementation(libs.serialization.json)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":dap:client"))
                implementation(libs.bundles.testing)
                implementation(libs.coroutines.core)
            }
        }
    }
}
