package su.kidoz.jetaprog.app.plugin

/**
 * Metadata for one bundled plugin, mirroring its [su.kidoz.jetaprog.plugins.api.PluginManifest].
 */
public data class BundledPluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
)

/**
 * The bundled plugins shipped with the IDE, in registration order.
 *
 * `ProjectSession` registers the live plugin instances from this same set; the
 * settings Plugins panel and the Welcome Hub's Plugins page render this catalog.
 * The ids here match the plugins' manifest ids, so the `disabledPlugins`
 * setting (keyed by manifest id) applies to both the UI and activation gating.
 */
public object BundledPluginCatalog {
    /** All bundled plugins in the order `ProjectSession` registers them. */
    public val plugins: List<BundledPluginInfo> =
        listOf(
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.kotlin",
                name = "Kotlin Language Support",
                description = "Kotlin language support including code completion, navigation, and formatting",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.kotlin.multiplatform",
                name = "Kotlin Multiplatform Support",
                description = "Kotlin Multiplatform detection, target discovery, and Gradle task commands",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.c",
                name = "C Language Support",
                description = "C23 language support with clangd, clang-format and CMake/Meson integration",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.cpp",
                name = "C++ Language Support",
                description = "C++26 language support with clangd, clang-format and CMake/Meson integration",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.dotnet",
                name = ".NET Language Support",
                description = ".NET support with Roslyn LSP and dotnet CLI integration",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.dotnet.ef",
                name = "Entity Framework Core Support",
                description = "Entity Framework Core detection and dotnet-ef commands",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.dotnet.prolog",
                name = "DotProlog Support",
                description = "DotProlog project detection, Prolog language registration, and dotnet CLI commands",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.go",
                name = "Go Language Support",
                description = "Go support with gopls navigation, diagnostics, completion, and gofmt formatting",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.java",
                name = "Java Language Support",
                description = "Java support with Eclipse JDT LS, navigation, diagnostics, and formatting",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.spring",
                name = "Spring Boot Support",
                description = "Spring Boot detection and configuration-key completion",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.javascript-typescript",
                name = "JavaScript and TypeScript Language Support",
                description = "JavaScript and TypeScript support with LSP diagnostics, navigation, and Prettier",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.python",
                name = "Python Language Support",
                description = "Python language support with LSP, Ruff linting, and formatting",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.rust",
                name = "Rust Language Support",
                description = "Rust language support with rust-analyzer and Cargo integration",
                version = "1.0.0",
            ),
            BundledPluginInfo(
                id = "su.kidoz.jetaprog.vala",
                name = "Vala Language Support",
                description = "Vala language support with Language Server integration",
                version = "1.0.0",
            ),
        )
}
