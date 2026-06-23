plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":acp:protocol"))
                implementation(project(":core:common"))
                implementation(libs.serialization.json)
                implementation(libs.coroutines.core)
                implementation(libs.kotlin.logging)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.logback)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":acp:client"))
                implementation(libs.bundles.testing)
            }
        }
    }
}
