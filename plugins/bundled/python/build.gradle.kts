plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.lint)
                api(projects.editor.core)
                api(projects.plugins.api)
                api(projects.plugins.support)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        jvmMain {
            dependencies {
                // Platform-specific process execution
                implementation(projects.core.platform)
                implementation(libs.kotlin.logging)
            }
        }
    }
}
