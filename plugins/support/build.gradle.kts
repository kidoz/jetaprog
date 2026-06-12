plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":plugins:api"))
                api(project(":lsp:protocol"))
                api(project(":lsp:client"))
                implementation(project(":core:common"))
                implementation(project(":core:platform"))
                implementation(project(":core:settings"))
                implementation(project(":editor:core"))
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
