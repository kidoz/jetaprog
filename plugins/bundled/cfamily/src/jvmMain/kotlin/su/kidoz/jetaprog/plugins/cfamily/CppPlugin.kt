package su.kidoz.jetaprog.plugins.cfamily

import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.CommandContribution
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest

/**
 * C++ language support plugin for JetaProg, targeting C++26.
 *
 * Provides:
 * - clangd language server integration (completion, navigation, diagnostics, code actions)
 * - clang-tidy diagnostics and clang-format formatting
 * - CMake and Meson build integration, including the compilation database clangd needs
 * - C++26 syntax highlighting via [su.kidoz.jetaprog.editor.syntax.cpp.CppLexer], covering
 *   modules, coroutines, contracts and the reflection operators
 *
 * @param cppStandard The C++ standard to configure builds and generated clangd config with.
 */
public class CppPlugin(
    cppStandard: CppStandard = CppStandard.LATEST,
) : CFamilyPlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "C++ Language Support",
                version = "1.0.0",
                description = "C++26 language support with clangd, clang-format and CMake/Meson integration",
                activationEvents =
                    listOf(
                        "onLanguage:cpp",
                        "workspaceContains:CMakeLists.txt",
                        "workspaceContains:meson.build",
                        "workspaceContains:compile_commands.json",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "cpp",
                                    extensions = CFamilyFiles.CPP_EXTENSIONS + CFamilyFiles.CPP_HEADER_EXTENSIONS,
                                    aliases = listOf("C++", "cpp"),
                                ),
                            ),
                        commands =
                            listOf(
                                CommandContribution("cpp.configure", "Configure", CATEGORY),
                                CommandContribution("cpp.build", "Build", CATEGORY),
                                CommandContribution("cpp.test", "Run Tests", CATEGORY),
                                CommandContribution("cpp.clean", "Clean", CATEGORY),
                                CommandContribution("cpp.install", "Install", CATEGORY),
                                CommandContribution("cpp.writeClangdConfig", "Write .clangd Config", CATEGORY),
                            ),
                    ),
            ),
        languageId = LanguageId.CPP,
        commandPrefix = "cpp",
        extensions = CFamilyFiles.CPP_EXTENSIONS + CFamilyFiles.CPP_HEADER_EXTENSIONS,
        aliases = listOf("C++"),
        cppStandard = cppStandard,
    ) {
    public companion object {
        /** The plugin identifier used by the plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.cpp"

        private const val CATEGORY = "C++"
    }
}
