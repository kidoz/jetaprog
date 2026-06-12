plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.platform)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.bundles.ktor.server)
                implementation(libs.kotlin.logging)
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
