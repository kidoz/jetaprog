plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
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
