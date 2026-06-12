plugins {
    id("jetaprog.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":lsp:protocol"))
                implementation(project(":core:common"))
                implementation(project(":core:platform"))
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
    }
}
