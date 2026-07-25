package su.kidoz.jetaprog.plugins.cfamily

/**
 * C language standards understood by current Clang and GCC releases.
 *
 * @property flag The `-std=` value passed to the compiler and to clangd's fallback flags.
 * @property displayName Human-readable name shown in the UI.
 * @property cmakeValue The value for `CMAKE_C_STANDARD`.
 */
public enum class CStandard(
    public val flag: String,
    public val displayName: String,
    public val cmakeValue: String,
) {
    C11("c11", "C11", "11"),
    C17("c17", "C17", "17"),
    C23("c23", "C23", "23"),

    /** The in-progress standard that follows C23. */
    C2Y("c2y", "C2y (draft)", "23"),
    ;

    public companion object {
        /** The most recent published C standard, ISO/IEC 9899:2024. */
        public val LATEST: CStandard = C23
    }
}

/**
 * C++ language standards understood by current Clang and GCC releases.
 *
 * @property flag The `-std=` value passed to the compiler and to clangd's fallback flags.
 * @property displayName Human-readable name shown in the UI.
 * @property cmakeValue The value for `CMAKE_CXX_STANDARD`.
 */
public enum class CppStandard(
    public val flag: String,
    public val displayName: String,
    public val cmakeValue: String,
) {
    CPP17("c++17", "C++17", "17"),
    CPP20("c++20", "C++20", "20"),
    CPP23("c++23", "C++23", "23"),
    CPP26("c++26", "C++26", "26"),
    ;

    public companion object {
        /** The most recent C++ standard Clang and GCC accept a `-std=` flag for. */
        public val LATEST: CppStandard = CPP26
    }
}

/**
 * File extensions and well-known filenames for the C family languages.
 */
public object CFamilyFiles {
    /** Extensions that map to the `c` language. */
    public val C_EXTENSIONS: List<String> = listOf(".c")

    /**
     * Header extensions treated as C. `.h` is shared with C++, and is mapped to C
     * because clangd resolves the real dialect from the compilation database.
     */
    public val C_HEADER_EXTENSIONS: List<String> = listOf(".h")

    /** Extensions that map to the `cpp` language, including C++20 module units. */
    public val CPP_EXTENSIONS: List<String> =
        listOf(".cpp", ".cc", ".cxx", ".c++", ".cppm", ".ixx", ".ccm", ".cxxm", ".c++m")

    /** Header and inline-implementation extensions that map to the `cpp` language. */
    public val CPP_HEADER_EXTENSIONS: List<String> =
        listOf(".hpp", ".hh", ".hxx", ".h++", ".inl", ".ipp", ".tpp")

    /** Files whose presence marks the workspace as a CMake project. */
    public const val CMAKE_LISTS: String = "CMakeLists.txt"

    /** Files whose presence marks the workspace as a Meson project. */
    public const val MESON_BUILD: String = "meson.build"

    /** The compilation database clangd consumes. */
    public const val COMPILE_COMMANDS: String = "compile_commands.json"
}
