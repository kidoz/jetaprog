plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:common"))
                api(project(":core:platform"))
                api(project(":core:configuration"))
                api(project(":dap:protocol"))
                api(project(":dap:client"))
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
    }
}
