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
