plugins {
    id("jetaprog.multiplatform")
}

group = "su.kidoz.jetaprog.lsp"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.lsp.protocol)
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
