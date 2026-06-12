plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                implementation(libs.coroutines.core)
                implementation(libs.kotlin.logging)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.bundles.jna)
                implementation(libs.logback)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
