plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:common"))
                api(project(":dap:protocol"))
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
    }
}
