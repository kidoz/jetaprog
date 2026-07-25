package su.kidoz.jetaprog.build.cmake

/**
 * Represents a CMake project.
 */
public data class CMakeProject(
    /** Root path of the CMake project (the directory holding CMakeLists.txt). */
    val rootPath: String,
    /** Build directory, relative to [rootPath] unless absolute. */
    val buildDir: String = "build",
    /** Name of the project, as reported by the CMake file API. */
    val name: String = "",
    /** Generator to use, or null to let CMake pick its platform default. */
    val generator: CMakeGenerator? = null,
    /** Build configuration to configure and build. */
    val buildType: CMakeBuildType = CMakeBuildType.DEBUG,
    /** Targets discovered through the CMake file API. */
    val targets: List<CMakeTarget> = emptyList(),
    /** Additional `-D` cache entries applied on configure. */
    val cacheEntries: Map<String, String> = emptyMap(),
)

/**
 * A build target within a CMake project.
 */
public data class CMakeTarget(
    /** Target name as written in CMakeLists.txt. */
    val name: String,
    /** Kind of target. */
    val type: CMakeTargetType,
    /** Path of the primary build artifact, relative to the build directory. */
    val artifactPath: String? = null,
)

/**
 * Kind of a CMake build target.
 */
public enum class CMakeTargetType {
    EXECUTABLE,
    STATIC_LIBRARY,
    SHARED_LIBRARY,
    MODULE_LIBRARY,
    OBJECT_LIBRARY,
    INTERFACE_LIBRARY,
    UTILITY,
}

/**
 * CMake build configuration, matching the values accepted by `CMAKE_BUILD_TYPE`.
 */
public enum class CMakeBuildType(
    /** The spelling CMake expects on the command line. */
    public val cmakeName: String,
) {
    DEBUG("Debug"),
    RELEASE("Release"),
    REL_WITH_DEB_INFO("RelWithDebInfo"),
    MIN_SIZE_REL("MinSizeRel"),
}

/**
 * Generators JetaProg can request explicitly.
 */
public enum class CMakeGenerator(
    /** The generator name passed to `cmake -G`. */
    public val generatorName: String,
) {
    NINJA("Ninja"),
    NINJA_MULTI_CONFIG("Ninja Multi-Config"),
    UNIX_MAKEFILES("Unix Makefiles"),
    XCODE("Xcode"),
    VISUAL_STUDIO_17("Visual Studio 17 2022"),
}

/**
 * Well-known CMake cache entries used by JetaProg.
 */
public object CMakeCacheKeys {
    /** Makes CMake emit `compile_commands.json`, which clangd consumes. */
    public const val EXPORT_COMPILE_COMMANDS: String = "CMAKE_EXPORT_COMPILE_COMMANDS"

    /** The C language standard, e.g. `23`. */
    public const val C_STANDARD: String = "CMAKE_C_STANDARD"

    /** The C++ language standard, e.g. `26`. */
    public const val CXX_STANDARD: String = "CMAKE_CXX_STANDARD"

    /** The build configuration for single-config generators. */
    public const val BUILD_TYPE: String = "CMAKE_BUILD_TYPE"
}
