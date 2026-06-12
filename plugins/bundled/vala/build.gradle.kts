plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.editor.core)
                api(projects.editor.syntax)
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.buildSystem.meson)
                implementation(libs.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(projects.core.platform)
                implementation(libs.kotlin.logging)
            }
        }
    }
}
