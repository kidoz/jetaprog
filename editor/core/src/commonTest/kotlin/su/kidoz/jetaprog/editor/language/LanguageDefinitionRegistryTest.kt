package su.kidoz.jetaprog.editor.language

import su.kidoz.jetaprog.editor.document.LanguageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageDefinitionRegistryTest {
    @Test
    fun detectsLanguageByExtension() {
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("Main.kt"))
        assertEquals(LanguageId.GO, LanguageDefinitionRegistry.detect("main.go"))
        assertEquals(LanguageId.TYPESCRIPT, LanguageDefinitionRegistry.detect("app.tsx"))
        assertEquals(LanguageId.SQL, LanguageDefinitionRegistry.detect("schema.sql"))
        assertEquals(LanguageId.POSTGRESQL, LanguageDefinitionRegistry.detect("functions.pgsql"))
    }

    @Test
    fun detectsLanguageByFilename() {
        assertEquals(LanguageId.MESON, LanguageDefinitionRegistry.detect("meson.build"))
        assertEquals(LanguageId.CMAKE, LanguageDefinitionRegistry.detect("CMakeLists.txt"))
        assertEquals(LanguageId.TOML, LanguageDefinitionRegistry.detect("Cargo.lock"))
        assertEquals(LanguageId.XML, LanguageDefinitionRegistry.detect("pom.xml"))
    }

    @Test
    fun filenameTakesPrecedenceOverExtension() {
        // meson_options.txt would otherwise fall through to no language at all
        assertEquals(LanguageId.MESON, LanguageDefinitionRegistry.detect("meson_options.txt"))
    }

    @Test
    fun detectsGitignoreTemplates() {
        assertEquals(LanguageId.GITIGNORE, LanguageDefinitionRegistry.detect(".gitignore"))
        assertEquals(LanguageId.GITIGNORE, LanguageDefinitionRegistry.detect("Node.gitignore"))
    }

    @Test
    fun stripsDirectoriesFromPaths() {
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("/src/main/kotlin/App.kt"))
        assertEquals(LanguageId.MESON, LanguageDefinitionRegistry.detect("/project/meson.build"))
    }

    @Test
    fun detectsDotPrologFiles() {
        assertEquals(LanguageId.DOTPROLOG, LanguageDefinitionRegistry.detect("pricing.pl"))
        assertEquals(LanguageId.DOTPROLOG, LanguageDefinitionRegistry.detect("pricing.dpli"))
        assertEquals(LanguageId.MSBUILD, LanguageDefinitionRegistry.detect("Pricing.dplproj"))
    }

    @Test
    fun sharedHeaderExtensionMapsToC() {
        assertEquals(LanguageId.C, LanguageDefinitionRegistry.detect("util.h"))
        assertEquals(LanguageId.CPP, LanguageDefinitionRegistry.detect("util.hpp"))
    }

    @Test
    fun longestCompoundExtensionSelectsSqlDialect() {
        assertEquals(LanguageId.POSTGRESQL, LanguageDefinitionRegistry.detect("schema.postgresql.sql"))
        assertEquals(LanguageId.STARROCKS, LanguageDefinitionRegistry.detect("warehouse.starrocks.sql"))
        assertEquals(LanguageId.STARROCKS, LanguageDefinitionRegistry.detect("warehouse.sr.sql"))
        assertEquals(LanguageId.SQL, LanguageDefinitionRegistry.detect("warehouse.sql"))
    }

    @Test
    fun unknownFilesReturnNull() {
        assertNull(LanguageDefinitionRegistry.detect("archive.zip"))
        assertNull(LanguageDefinitionRegistry.detect("README"))
    }

    @Test
    fun lexerIdResolvesForBuiltinLanguages() {
        assertEquals("kotlin", LanguageDefinitionRegistry.lexerIdFor(LanguageId.KOTLIN))
        assertEquals("typescript", LanguageDefinitionRegistry.lexerIdFor(LanguageId.TYPESCRIPT))
        assertEquals("csharp", LanguageDefinitionRegistry.lexerIdFor(LanguageId.CSHARP))
        assertEquals("sql", LanguageDefinitionRegistry.lexerIdFor(LanguageId.SQL))
        assertEquals("postgresql", LanguageDefinitionRegistry.lexerIdFor(LanguageId.POSTGRESQL))
        assertEquals("starrocks", LanguageDefinitionRegistry.lexerIdFor(LanguageId.STARROCKS))
        assertEquals("dotprolog", LanguageDefinitionRegistry.lexerIdFor(LanguageId.DOTPROLOG))
        assertEquals("xml", LanguageDefinitionRegistry.lexerIdFor(LanguageId.MSBUILD))
        assertNull(LanguageDefinitionRegistry.lexerIdFor(LanguageId.JSON))
    }

    @Test
    fun registrationMergesAndDisposeRestores() {
        val custom = LanguageId("mylang")
        val disposable =
            LanguageDefinitionRegistry.register(
                LanguageDefinition(id = custom, extensions = listOf(".mylang")),
            )
        assertEquals(custom, LanguageDefinitionRegistry.detect("test.mylang"))

        // Extending an existing language merges extensions instead of replacing them
        val kotlinExtra =
            LanguageDefinitionRegistry.register(
                LanguageDefinition(id = LanguageId.KOTLIN, extensions = listOf("ktm")),
            )
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("x.ktm"))
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("x.kt"))

        kotlinExtra.dispose()
        assertNull(LanguageDefinitionRegistry.detect("x.ktm"))
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("x.kt"))

        disposable.dispose()
        assertNull(LanguageDefinitionRegistry.detect("test.mylang"))
    }

    @Test
    fun pluginRegistrationCannotStealBuiltinExtension() {
        val impostor = LanguageId("impostor")
        val disposable =
            LanguageDefinitionRegistry.register(
                LanguageDefinition(id = impostor, extensions = listOf("kt")),
            )
        assertEquals(LanguageId.KOTLIN, LanguageDefinitionRegistry.detect("Main.kt"))
        disposable.dispose()
    }
}
