plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(projects.core.common)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
                runtimeOnly(libs.postgresql)
                runtimeOnly(libs.starrocks.connector)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
