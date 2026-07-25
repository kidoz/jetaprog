package su.kidoz.jetaprog.plugins.cfamily

import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.CommandContribution
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest

/**
 * C language support plugin for JetaProg, targeting C23.
 *
 * Provides:
 * - clangd language server integration (completion, navigation, diagnostics, code actions)
 * - clang-tidy diagnostics and clang-format formatting
 * - CMake and Meson build integration, including the compilation database clangd needs
 * - C23 syntax highlighting via [su.kidoz.jetaprog.editor.syntax.c.CLexer]
 *
 * @param cStandard The C standard to configure builds and generated clangd config with.
 */
public class CPlugin(
    cStandard: CStandard = CStandard.LATEST,
) : CFamilyPlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "C Language Support",
                version = "1.0.0",
                description = "C23 language support with clangd, clang-format and CMake/Meson integration",
                activationEvents =
                    listOf(
                        "onLanguage:c",
                        "workspaceContains:CMakeLists.txt",
                        "workspaceContains:meson.build",
                        "workspaceContains:compile_commands.json",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "c",
                                    extensions = CFamilyFiles.C_EXTENSIONS + CFamilyFiles.C_HEADER_EXTENSIONS,
                                    aliases = listOf("C", "c"),
                                ),
                            ),
                        commands =
                            listOf(
                                CommandContribution("c.configure", "Configure", CATEGORY),
                                CommandContribution("c.build", "Build", CATEGORY),
                                CommandContribution("c.test", "Run Tests", CATEGORY),
                                CommandContribution("c.clean", "Clean", CATEGORY),
                                CommandContribution("c.install", "Install", CATEGORY),
                                CommandContribution("c.writeClangdConfig", "Write .clangd Config", CATEGORY),
                            ),
                    ),
            ),
        languageId = LanguageId.C,
        commandPrefix = "c",
        extensions = CFamilyFiles.C_EXTENSIONS + CFamilyFiles.C_HEADER_EXTENSIONS,
        aliases = listOf("C"),
        cStandard = cStandard,
    ) {
    public companion object {
        /** The plugin identifier used by the plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.c"

        private const val CATEGORY = "C"
    }
}
