package su.kidoz.jetaprog.build.go

import kotlinx.coroutines.flow.Flow

/**
 * Runs the Go toolchain in a module or workspace.
 *
 * The runner shells out to the `go` command installed on the machine. A missing
 * binary fails the returned [Result] and does not throw out of the caller.
 */
public interface GoRunner {
    /**
     * Runs [operation] with its working directory set to [project]'s root.
     *
     * @return A flow of output, or a failure when the process cannot be started.
     */
    public suspend fun execute(
        project: GoProject,
        operation: GoOperation,
    ): Result<Flow<GoOutput>>

    /** Stops the command started by the most recent [execute] call. */
    public fun cancel()

    /** True while a command started by this runner is still alive. */
    public val isRunning: Boolean
}
