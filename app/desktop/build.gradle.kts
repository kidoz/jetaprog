import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("jetaprog.multiplatform")
    id("jetaprog.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.core.platform)
                implementation(projects.core.project)
                implementation(projects.core.configuration)
                implementation(projects.core.settings)
                implementation(projects.core.lint)
                implementation(projects.editor.core)
                implementation(projects.editor.syntax)
                implementation(projects.plugins.api)
                implementation(projects.plugins.runtime)
                implementation(projects.plugins.support)
                implementation(projects.plugins.bundled.kotlin)
                implementation(projects.plugins.bundled.python)
                implementation(projects.plugins.bundled.rust)
                implementation(projects.plugins.bundled.vala)
                implementation(projects.mcp.server)
                implementation(projects.mcp.bridge)
                implementation(projects.buildSystem.cargo)
                implementation(projects.buildSystem.gradle)
                implementation(projects.lsp.client)
                implementation(projects.lsp.protocol)
                implementation(project(":lsp:server"))

                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)

                implementation(libs.coroutines.core)
                implementation(libs.coroutines.swing)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.serialization.json)
                implementation(libs.kotlin.logging)
                implementation(libs.logback)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "su.kidoz.jetaprog.app.MainKt"

        jvmArgs +=
            listOf(
                "-Dskiko.vsync.enabled=false",
                "-Dapple.awt.application.name=JetaProg",
            )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "JetaProg"
            packageVersion = "1.0.0"
            description = "JetaProg IDE - Modern Cross-Platform IDE"
            vendor = "Aleksandr Pavlov"
            licenseFile.set(project.file("LICENSE"))

            macOS {
                bundleID = "su.kidoz.jetaprog"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
                menuGroup = "JetaProg"
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}
