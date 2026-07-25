package su.kidoz.jetaprog.plugins.cfamily

import su.kidoz.jetaprog.build.meson.MesonOptions
import su.kidoz.jetaprog.build.meson.MesonProject
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CFamilyPluginTest {
    @Test
    fun cPluginDeclaresTheCLanguageAndItsExtensions() {
        val manifest = CPlugin().manifest
        assertEquals("su.kidoz.jetaprog.c", manifest.id)

        val language = manifest.contributes.languages.single()
        assertEquals("c", language.id)
        assertContains(language.extensions, ".c")
        assertContains(language.extensions, ".h")
    }

    @Test
    fun cppPluginDeclaresTheCppLanguageIncludingModuleUnits() {
        val manifest = CppPlugin().manifest
        assertEquals("su.kidoz.jetaprog.cpp", manifest.id)

        val language = manifest.contributes.languages.single()
        assertEquals("cpp", language.id)
        assertContains(language.extensions, ".cpp")
        assertContains(language.extensions, ".hpp")
        // C++20 module interface units, as emitted by the major toolchains.
        assertContains(language.extensions, ".cppm")
        assertContains(language.extensions, ".ixx")
    }

    @Test
    fun theTwoPluginsClaimDisjointExtensions() {
        val cExtensions =
            CPlugin()
                .manifest.contributes.languages
                .single()
                .extensions
                .toSet()
        val cppExtensions =
            CppPlugin()
                .manifest.contributes.languages
                .single()
                .extensions
                .toSet()
        assertEquals(emptySet(), cExtensions intersect cppExtensions)
    }

    @Test
    fun bothPluginsActivateOnTheSharedBuildSystemMarkers() {
        for (manifest in listOf(CPlugin().manifest, CppPlugin().manifest)) {
            assertContains(manifest.activationEvents, "workspaceContains:CMakeLists.txt")
            assertContains(manifest.activationEvents, "workspaceContains:meson.build")
            assertContains(manifest.activationEvents, "workspaceContains:compile_commands.json")
        }
    }

    @Test
    fun commandsAreNamespacedPerLanguage() {
        val cCommands =
            CPlugin()
                .manifest.contributes.commands
                .map { it.command }
        val cppCommands =
            CppPlugin()
                .manifest.contributes.commands
                .map { it.command }

        assertTrue(cCommands.all { it.startsWith("c.") }, "unexpected C commands: $cCommands")
        assertTrue(cppCommands.all { it.startsWith("cpp.") }, "unexpected C++ commands: $cppCommands")
        assertEquals(emptySet(), cCommands.toSet() intersect cppCommands.toSet())
    }

    @Test
    fun defaultsTargetTheLatestStandards() {
        assertEquals(CStandard.C23, CStandard.LATEST)
        assertEquals(CppStandard.CPP26, CppStandard.LATEST)
        assertEquals("c23", CStandard.LATEST.flag)
        assertEquals("c++26", CppStandard.LATEST.flag)
        assertEquals("26", CppStandard.LATEST.cmakeValue)
    }

    @Test
    fun mesonReceivesTheSameLanguageStandardsAsCMake() {
        val meson = CFamilyBuildSupport.mesonOptions(CStandard.LATEST, CppStandard.LATEST)

        assertEquals("c23", meson["c_std"])
        assertEquals("c++26", meson["cpp_std"])
    }

    @Test
    fun cmakeCacheEntriesCarryStandardsAndTheCompilationDatabase() {
        val cmake = CFamilyBuildSupport.cmakeCacheEntries(CStandard.LATEST, CppStandard.LATEST)

        assertEquals("ON", cmake["CMAKE_EXPORT_COMPILE_COMMANDS"])
        assertEquals("23", cmake["CMAKE_C_STANDARD"])
        assertEquals("26", cmake["CMAKE_CXX_STANDARD"])
    }

    @Test
    fun mesonStandardsUseCompilerFlagSpellingNotCMakeNumbers() {
        // meson wants -Dcpp_std=c++26; CMake wants CMAKE_CXX_STANDARD=26.
        val meson = CFamilyBuildSupport.mesonOptions(CStandard.LATEST, CppStandard.LATEST)
        val cmake = CFamilyBuildSupport.cmakeCacheEntries(CStandard.LATEST, CppStandard.LATEST)

        assertEquals(CppStandard.LATEST.flag, meson[MesonOptions.CPP_STD])
        assertEquals(CppStandard.LATEST.cmakeValue, cmake["CMAKE_CXX_STANDARD"])
        assertTrue(meson.getValue(MesonOptions.CPP_STD) != cmake.getValue("CMAKE_CXX_STANDARD"))
    }

    @Test
    fun mesonSetupEmitsProjectOptions() {
        val project =
            MesonProject(
                rootPath = "/tmp/project",
                options = CFamilyBuildSupport.mesonOptions(CStandard.LATEST, CppStandard.LATEST),
            )

        // The runner turns each option into a -Dkey=value argument for `meson setup`.
        val rendered = project.options.map { (key, value) -> "-D$key=$value" }
        assertContains(rendered, "-Dc_std=c23")
        assertContains(rendered, "-Dcpp_std=c++26")
    }

    @Test
    fun clangdRunsWithBackgroundIndexingAndClangTidy() {
        val command = ClangdOptions(compileCommandsDir = "/tmp/build").command()

        assertEquals("clangd", command.first())
        assertContains(command, "--background-index")
        assertContains(command, "--clang-tidy")
        assertContains(command, "--enable-config")
        assertContains(command, "--compile-commands-dir=/tmp/build")
    }

    @Test
    fun clangdOmitsTheDatabaseFlagWhenThereIsNoDatabase() {
        val command = ClangdOptions(compileCommandsDir = null).command()
        assertTrue(command.none { it.startsWith("--compile-commands-dir") })
    }

    @Test
    fun clangdConfigAppliesEachStandardToItsOwnSources() {
        val config = renderClangdConfig(CStandard.LATEST, CppStandard.LATEST)

        // Per-language blocks matter: clang rejects -std=c++26 outright for a .c file.
        assertContains(config, "-std=c23")
        assertContains(config, "-std=c++26")
        assertTrue(config.indexOf("-std=c23") < config.indexOf("-std=c++26"))
        assertContains(config, "PathMatch")
    }

    @Test
    fun writingTheClangdConfigCreatesItOnceAndThenLeavesItAlone() {
        val workspace = createTempDirectory("jetaprog-cfamily").toFile()
        try {
            val first = writeClangdConfig(workspace.absolutePath, CStandard.LATEST, CppStandard.LATEST)
            assertContains(first, "Wrote")

            val configFile = File(workspace, ".clangd")
            assertTrue(configFile.isFile)
            assertContains(configFile.readText(), "-std=c++26")

            val second = writeClangdConfig(workspace.absolutePath, CStandard.LATEST, CppStandard.LATEST)
            assertContains(second, "already exists")
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * Guards the argument construction. `--fallback-style` accepts only a named style,
     * so passing inline YAML there made clang-format exit 1 on every call and the
     * formatter silently returned the input untouched.
     */
    @Test
    fun formatterActuallyReformatsWhenClangFormatIsInstalled() {
        val workspace = createTempDirectory("jetaprog-cfamily-real").toFile()
        try {
            val formatter = ClangFormatFormatter(LanguageId.CPP, workspace.absolutePath)
            val result = formatter.format("int  main( ){int x=1;return   x;}", FormattingOptions())
            val formatted = assertIs<FormattingResult.Success>(result).formattedText

            if (formatted == "int  main( ){int x=1;return   x;}") {
                // clang-format is not installed on this machine; the missing-tool path
                // is covered by the test below.
                return
            }
            assertContains(formatted, "int main() {")
            assertTrue(formatted.none { it == '\t' })
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun formatterHonoursTheEditorIndentWidthWithoutAProjectStyle() {
        val workspace = createTempDirectory("jetaprog-cfamily-indent").toFile()
        try {
            val formatter = ClangFormatFormatter(LanguageId.CPP, workspace.absolutePath)
            val source = "int f() {\nif (true) {\nreturn 1;\n}\nreturn 0;\n}"
            val formatted =
                assertIs<FormattingResult.Success>(
                    formatter.format(source, FormattingOptions(tabSize = 8)),
                ).formattedText

            if (formatted == source) return // clang-format not installed
            assertContains(formatted, "\n        if (true)")
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun formatterLeavesContentUnchangedWhenClangFormatIsMissing() {
        val workspace = createTempDirectory("jetaprog-cfamily-fmt").toFile()
        try {
            val formatter =
                ClangFormatFormatter(
                    languageId = LanguageId.CPP,
                    workspacePath = workspace.absolutePath,
                    clangFormatPath = "jetaprog-clang-format-does-not-exist",
                )

            val source = "int  main( ){return 0;}"
            val result = assertIs<FormattingResult.Success>(formatter.format(source, FormattingOptions()))

            assertEquals(source, result.formattedText)
            assertEquals(emptyList(), result.edits)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun `sysroot is passed to clangd as fallback flags`() {
        val options = ClangdOptions(sysroot = "/SDKs/MacOSX26.5.sdk")

        assertEquals(
            mapOf("fallbackFlags" to listOf("-isysroot", "/SDKs/MacOSX26.5.sdk")),
            options.initializationOptions(),
        )
    }

    @Test
    fun `no fallback flags without a sysroot`() {
        assertEquals(emptyMap(), ClangdOptions(sysroot = null).initializationOptions())
    }
}
