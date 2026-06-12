plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    // Override allWarningsAsErrors for this module due to K1 API deprecation warning
    // The K1 API is required for PSI-based analysis until K2 Analysis API is publicly available
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    allWarningsAsErrors.set(false)
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.lint)
                api(projects.core.settings)
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.editor.syntax)
                implementation(libs.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                // Kotlin compiler for parsing and semantic analysis
                implementation(libs.kotlin.compiler.embeddable)
                implementation(libs.kotlin.scripting.compiler.embeddable)
                // Embedded LSP server
                implementation(projects.lsp.server)
                implementation(libs.kotlin.logging)
            }
        }
    }
}
