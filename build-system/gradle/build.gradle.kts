plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.platform)
                implementation(libs.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                // JVM-specific dependencies if needed
            }
        }
    }
}
