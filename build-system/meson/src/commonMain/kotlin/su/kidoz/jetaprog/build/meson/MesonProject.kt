package su.kidoz.jetaprog.build.meson

/**
 * Represents a Meson project.
 */
public data class MesonProject(
    /** Root path of the Meson project. */
    val rootPath: String,
    /** Build directory (default: "builddir"). */
    val buildDir: String = "builddir",
    /** Name of the project (from meson.build). */
    val name: String = "",
    /** Version of the project. */
    val version: String = "",
    /** Available build targets. */
    val targets: List<MesonTarget> = emptyList(),
    /** Build type (e.g., "debug", "release"). */
    val buildType: MesonBuildType = MesonBuildType.DEBUG,
    /**
     * Project options passed as `-Dkey=value` on `meson setup`, such as
     * `cpp_std` or `warning_level`.
     */
    val options: Map<String, String> = emptyMap(),
)

/**
 * Represents a Meson build target.
 */
public data class MesonTarget(
    /** Target name. */
    val name: String,
    /** Target type (executable, library, etc.). */
    val type: MesonTargetType,
    /** Target output file path. */
    val outputPath: String? = null,
)

/**
 * Type of Meson target.
 */
public enum class MesonTargetType {
    EXECUTABLE,
    STATIC_LIBRARY,
    SHARED_LIBRARY,
    BOTH_LIBRARIES,
    CUSTOM,
}

/**
 * Meson build type.
 */
public enum class MesonBuildType {
    DEBUG,
    DEBUGOPTIMIZED,
    RELEASE,
    MINSIZE,
    CUSTOM,
}

/**
 * Well-known Meson built-in option names.
 */
public object MesonOptions {
    /** The C language standard, e.g. `c23`. */
    public const val C_STD: String = "c_std"

    /** The C++ language standard, e.g. `c++26`. */
    public const val CPP_STD: String = "cpp_std"

    /** Compiler warning level, `0` to `3` (`everything` on recent Meson). */
    public const val WARNING_LEVEL: String = "warning_level"
}

/**
 * Common Meson commands.
 */
public object MesonCommands {
    public const val SETUP: String = "setup"
    public const val COMPILE: String = "compile"
    public const val TEST: String = "test"
    public const val INSTALL: String = "install"
    public const val CLEAN: String = "clean"
    public const val DIST: String = "dist"
    public const val INTROSPECT: String = "introspect"
}
