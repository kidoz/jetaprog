plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.serialization.json)
                implementation(libs.coroutines.core)
            }
        }
    }
}
