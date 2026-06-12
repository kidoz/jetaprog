package su.kidoz.jetaprog.platform.process

import kotlinx.coroutines.flow.Flow

/**
 * Represents the result of a process execution.
 */
public data class ProcessResult(
    /**
     * The exit code of the process.
     */
    val exitCode: Int,
    /**
     * The standard output of the process.
     */
    val stdout: String,
    /**
     * The standard error output of the process.
     */
    val stderr: String,
) {
    /**
     * Returns true if the process completed successfully (exit code 0).
     */
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Represents output from a running process.
 */
public sealed interface ProcessOutput {
    /**
     * Output from stdout.
     */
    public data class Stdout(
        val line: String,
    ) : ProcessOutput

    /**
     * Output from stderr.
     */
    public data class Stderr(
        val line: String,
    ) : ProcessOutput

    /**
     * Process has exited.
     */
    public data class Exited(
        val exitCode: Int,
    ) : ProcessOutput
}

/**
 * Configuration for process execution.
 */
public data class ProcessConfig(
    /**
     * The command to execute.
     */
    val command: List<String>,
    /**
     * The working directory for the process.
     */
    val workingDirectory: String? = null,
    /**
     * Environment variables to set.
     */
    val environment: Map<String, String> = emptyMap(),
    /**
     * Timeout in milliseconds (0 for no timeout).
     */
    val timeoutMillis: Long = 0,
    /**
     * Whether to redirect stderr to stdout.
     */
    val redirectErrorStream: Boolean = false,
)

/**
 * A running process that can be interacted with.
 */
public interface RunningProcess {
    /**
     * Writes to the process's stdin.
     * @param text The text to write
     */
    public suspend fun writeStdin(text: String)

    /**
     * Closes the process's stdin.
     */
    public suspend fun closeStdin()

    /**
     * Kills the process.
     */
    public fun kill()

    /**
     * Waits for the process to complete and returns the exit code.
     */
    public suspend fun waitFor(): Int

    /**
     * Returns true if the process is still running.
     */
    public val isAlive: Boolean

    /**
     * A flow of process output.
     */
    public val output: Flow<ProcessOutput>
}

/**
 * Interface for executing external processes.
 */
public interface ProcessExecutor {
    /**
     * Executes a command and waits for it to complete.
     * @param command The command and arguments to execute
     * @param workingDirectory The working directory (null for current directory)
     * @param environment Additional environment variables
     * @param timeoutMillis Timeout in milliseconds (0 for no timeout)
     * @return Result containing the process result or an error
     */
    public suspend fun execute(
        command: List<String>,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMillis: Long = 0,
    ): Result<ProcessResult>

    /**
     * Executes a shell command and waits for it to complete.
     * @param command The shell command to execute
     * @param workingDirectory The working directory (null for current directory)
     * @param environment Additional environment variables
     * @param timeoutMillis Timeout in milliseconds (0 for no timeout)
     * @return Result containing the process result or an error
     */
    public suspend fun executeShell(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMillis: Long = 0,
    ): Result<ProcessResult>

    /**
     * Starts a process and returns a handle to interact with it.
     * @param config The process configuration
     * @return Result containing the running process or an error
     */
    public suspend fun start(config: ProcessConfig): Result<RunningProcess>
}
