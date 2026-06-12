plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlin.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
