plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.core.common)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
                implementation(libs.koin.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(projects.core.settings)
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
