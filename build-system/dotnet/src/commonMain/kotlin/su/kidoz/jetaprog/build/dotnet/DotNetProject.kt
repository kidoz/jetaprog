package su.kidoz.jetaprog.build.dotnet

/**
 * A .NET workspace root with optional solution and project entry points.
 */
public data class DotNetProject(
    /** Workspace root path. */
    val rootPath: String,
    /** Optional solution file path. */
    val solutionPath: String? = null,
    /** Optional project file path. */
    val projectPath: String? = null,
) {
    /**
     * Preferred target path for solution-level commands.
     */
    public val targetPath: String?
        get() = solutionPath ?: projectPath
}

/**
 * .NET build configuration.
 */
public enum class DotNetConfiguration {
    /** Debug configuration. */
    DEBUG,

    /** Release configuration. */
    RELEASE,
    ;

    /**
     * Command-line value accepted by the .NET CLI.
     */
    public val cliValue: String
        get() =
            when (this) {
                DEBUG -> "Debug"
                RELEASE -> "Release"
            }
}

/**
 * Supported .NET project templates for `dotnet new`.
 */
public enum class DotNetTemplate {
    /** Console application template. */
    CONSOLE,

    /** Class library template. */
    CLASSLIB,

    /** ASP.NET Core web application template. */
    WEB,

    /** xUnit test project template. */
    XUNIT,
    ;

    /**
     * Template short name accepted by `dotnet new`.
     */
    public val cliValue: String
        get() =
            when (this) {
                CONSOLE -> "console"
                CLASSLIB -> "classlib"
                WEB -> "web"
                XUNIT -> "xunit"
            }
}
