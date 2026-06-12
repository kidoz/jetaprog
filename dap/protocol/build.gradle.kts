plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:common"))
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
    }
}
