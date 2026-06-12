plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.editor.syntax)
                implementation(libs.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.bundles.testing)
            }
        }
    }
}
