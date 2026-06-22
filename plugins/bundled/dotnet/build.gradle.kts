plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.editor.core)
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.buildSystem.dotnet)
                implementation(libs.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlin.logging)
            }
        }
    }
}
