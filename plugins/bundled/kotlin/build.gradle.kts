plugins {
    id("jetaprog.multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.common)
                api(projects.core.lint)
                api(projects.core.settings)
                api(projects.plugins.api)
                api(projects.plugins.support)
                api(projects.buildSystem.gradle)
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
