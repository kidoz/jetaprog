package su.kidoz.jetaprog.build.dotnet

/**
 * Output from .NET CLI command execution.
 */
public sealed interface DotNetOutput {
    /** Standard output line. */
    public data class Stdout(
        val line: String,
    ) : DotNetOutput

    /** Standard error line. */
    public data class Stderr(
        val line: String,
    ) : DotNetOutput

    /** Command started executing. */
    public data class CommandStarted(
        val command: String,
        val args: List<String>,
    ) : DotNetOutput

    /** Restore operation started. */
    public data object Restoring : DotNetOutput

    /** Build operation started for a project or solution. */
    public data class Building(
        val target: String? = null,
    ) : DotNetOutput

    /** Test execution started for a project or solution. */
    public data class Testing(
        val target: String? = null,
    ) : DotNetOutput

    /** Publish operation started for a project or solution. */
    public data class Publishing(
        val target: String? = null,
    ) : DotNetOutput

    /** NuGet package creation started. */
    public data class Packing(
        val target: String? = null,
    ) : DotNetOutput

    /** Command completed. */
    public data class CommandCompleted(
        val success: Boolean,
        val exitCode: Int,
        val duration: Long = 0,
    ) : DotNetOutput
}
