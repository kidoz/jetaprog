package su.kidoz.jetaprog.editor.language

import su.kidoz.jetaprog.editor.document.LanguageId

/**
 * Built-in language definitions seeded into [LanguageDefinitionRegistry].
 *
 * These cover every language the IDE recognizes out of the box, including ones without a
 * dedicated plugin (Markdown, TOML, JSON, ...). Plugins may extend them at activation time.
 */
internal object BuiltinLanguageDefinitions {
    val all: List<LanguageDefinition> =
        listOf(
            LanguageDefinition(
                id = LanguageId.KOTLIN,
                extensions = listOf("kt", "kts"),
                aliases = listOf("Kotlin"),
                lexerId = "kotlin",
            ),
            LanguageDefinition(
                id = LanguageId.JAVA,
                extensions = listOf("java"),
                aliases = listOf("Java"),
                lexerId = "java",
            ),
            LanguageDefinition(
                id = LanguageId.JAVASCRIPT,
                extensions = listOf("js", "mjs", "cjs", "jsx"),
                aliases = listOf("JavaScript"),
                lexerId = "javascript",
            ),
            LanguageDefinition(
                id = LanguageId.TYPESCRIPT,
                extensions = listOf("ts", "mts", "cts", "tsx"),
                aliases = listOf("TypeScript"),
                lexerId = "typescript",
            ),
            LanguageDefinition(
                id = LanguageId.PYTHON,
                extensions = listOf("py", "pyi"),
                aliases = listOf("Python"),
                lexerId = "python",
            ),
            LanguageDefinition(
                id = LanguageId.CSHARP,
                extensions = listOf("cs", "csx"),
                aliases = listOf("C#"),
            ),
            LanguageDefinition(
                id = LanguageId.MSBUILD,
                extensions = listOf("csproj", "fsproj", "vbproj", "props", "targets", "sln", "slnx"),
                aliases = listOf("MSBuild"),
            ),
            LanguageDefinition(
                id = LanguageId.RUST,
                extensions = listOf("rs"),
                aliases = listOf("Rust"),
                lexerId = "rust",
            ),
            LanguageDefinition(
                id = LanguageId.GO,
                extensions = listOf("go"),
                aliases = listOf("Go", "Golang"),
                lexerId = "go",
            ),
            // ".h" is shared between C and C++; clangd resolves the real dialect from the
            // compilation database, so map it to C like most editors do.
            LanguageDefinition(
                id = LanguageId.C,
                extensions = listOf("c", "h"),
                aliases = listOf("C"),
                lexerId = "c",
            ),
            LanguageDefinition(
                id = LanguageId.CPP,
                extensions =
                    listOf(
                        "cpp",
                        "cc",
                        "cxx",
                        "c++",
                        "cppm",
                        "ixx",
                        "ccm",
                        "cxxm",
                        "c++m",
                        "hpp",
                        "hh",
                        "hxx",
                        "h++",
                        "inl",
                        "ipp",
                        "tpp",
                    ),
                aliases = listOf("C++"),
                lexerId = "cpp",
            ),
            LanguageDefinition(
                id = LanguageId.JSON,
                extensions = listOf("json"),
                aliases = listOf("JSON"),
            ),
            LanguageDefinition(
                id = LanguageId.YAML,
                extensions = listOf("yaml", "yml"),
                aliases = listOf("YAML"),
            ),
            LanguageDefinition(
                id = LanguageId.TOML,
                extensions = listOf("toml"),
                filenames = listOf("cargo.lock"),
                aliases = listOf("TOML"),
                lexerId = "toml",
            ),
            LanguageDefinition(
                id = LanguageId.XML,
                extensions = listOf("xml", "pom", "xsd", "xsl", "xslt", "svg"),
                filenames = listOf("pom.xml"),
                aliases = listOf("XML"),
                lexerId = "xml",
            ),
            LanguageDefinition(
                id = LanguageId.HTML,
                extensions = listOf("html", "htm"),
                aliases = listOf("HTML"),
            ),
            LanguageDefinition(
                id = LanguageId.CSS,
                extensions = listOf("css"),
                aliases = listOf("CSS"),
            ),
            LanguageDefinition(
                id = LanguageId.CMAKE,
                extensions = listOf("cmake"),
                filenames = listOf("cmakelists.txt", "cmakecache.txt"),
                aliases = listOf("CMake"),
                lexerId = "cmake",
            ),
            LanguageDefinition(
                id = LanguageId.MARKDOWN,
                extensions = listOf("md", "markdown"),
                aliases = listOf("Markdown"),
                lexerId = "markdown",
            ),
            LanguageDefinition(
                id = LanguageId.VALA,
                extensions = listOf("vala", "vapi"),
                aliases = listOf("Vala"),
                lexerId = "vala",
            ),
            LanguageDefinition(
                id = LanguageId.MESON,
                filenames = listOf("meson.build", "meson_options.txt"),
                aliases = listOf("Meson"),
                lexerId = "meson",
            ),
            // Covers ".gitignore" itself plus the "<name>.gitignore" templates some tools keep.
            LanguageDefinition(
                id = LanguageId.GITIGNORE,
                extensions = listOf("gitignore"),
                aliases = listOf("Git Ignore"),
                lexerId = "gitignore",
            ),
        )
}
