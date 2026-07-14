plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jetaprog"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // Gradle Tooling API is published here, not to Maven Central.
        maven("https://repo.gradle.org/gradle/libs-releases") {
            content { includeGroup("org.gradle") }
        }
    }
}

// Core
include(":core:common")
include(":core:configuration")
include(":core:lint")
include(":core:platform")
include(":core:project")
include(":core:settings")
include(":core:vcs")

// Editor
include(":editor:core")
include(":editor:syntax")
include(":editor:treesitter")

// App
include(":app:desktop")

// Plugins
include(":plugins:api")
include(":plugins:runtime")
include(":plugins:support")
include(":plugins:bundled:dotnet")
include(":plugins:bundled:kotlin")
include(":plugins:bundled:python")
include(":plugins:bundled:rust")
include(":plugins:bundled:vala")

// MCP
include(":mcp:bridge")
include(":mcp:server")

// ACP (Agent Client Protocol)
include(":acp:protocol")
include(":acp:client")
include(":acp:agent")

// LSP
include(":lsp:client")
include(":lsp:protocol")
include(":lsp:server")

// DAP
include(":dap:client")
include(":dap:jvm")
include(":dap:protocol")
include(":dap:service")

// Build System
include(":build-system:cargo")
include(":build-system:dotnet")
include(":build-system:gradle")
include(":build-system:meson")
include(":build-system:python")
include(":build-system:server")
