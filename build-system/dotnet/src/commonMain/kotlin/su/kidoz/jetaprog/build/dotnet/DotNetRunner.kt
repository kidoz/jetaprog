package su.kidoz.jetaprog.build.dotnet

import kotlinx.coroutines.flow.Flow

/**
 * Runs .NET CLI commands in a .NET workspace.
 */
public interface DotNetRunner {
    /**
     * Whether a .NET CLI command is currently running.
     */
    public val isRunning: Boolean

    /**
     * Restores NuGet packages.
     *
     * Equivalent to: `dotnet restore [target]`
     */
    public suspend fun restore(
        project: DotNetProject,
        sources: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Builds a project or solution.
     *
     * Equivalent to: `dotnet build [target] --configuration <configuration>`
     */
    public suspend fun build(
        project: DotNetProject,
        configuration: DotNetConfiguration = DotNetConfiguration.DEBUG,
        noRestore: Boolean = false,
        arguments: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Runs an executable project.
     *
     * Equivalent to: `dotnet run --project <project> --configuration <configuration>`
     */
    public suspend fun run(
        project: DotNetProject,
        configuration: DotNetConfiguration = DotNetConfiguration.DEBUG,
        projectPath: String? = null,
        arguments: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Runs tests.
     *
     * Equivalent to: `dotnet test [target] --configuration <configuration>`
     */
    public suspend fun test(
        project: DotNetProject,
        configuration: DotNetConfiguration = DotNetConfiguration.DEBUG,
        filter: String? = null,
        noBuild: Boolean = false,
        arguments: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Publishes a project or solution.
     *
     * Equivalent to: `dotnet publish [target] --configuration <configuration>`
     */
    public suspend fun publish(
        project: DotNetProject,
        configuration: DotNetConfiguration = DotNetConfiguration.RELEASE,
        outputDirectory: String? = null,
        arguments: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Creates NuGet packages.
     *
     * Equivalent to: `dotnet pack [target] --configuration <configuration>`
     */
    public suspend fun pack(
        project: DotNetProject,
        configuration: DotNetConfiguration = DotNetConfiguration.RELEASE,
        outputDirectory: String? = null,
        arguments: List<String> = emptyList(),
    ): Result<Flow<DotNetOutput>>

    /**
     * Creates a new .NET project from a template.
     *
     * Equivalent to: `dotnet new <template>`
     */
    public suspend fun new(
        path: String,
        template: DotNetTemplate = DotNetTemplate.CONSOLE,
        name: String? = null,
        framework: String? = null,
    ): Result<Flow<DotNetOutput>>

    /**
     * Returns `dotnet --info`.
     */
    public suspend fun info(): Result<String>

    /**
     * Returns installed SDK versions from `dotnet --list-sdks`.
     */
    public suspend fun listSdks(): Result<List<String>>

    /**
     * Cancels the currently running command.
     */
    public fun cancel()
}
