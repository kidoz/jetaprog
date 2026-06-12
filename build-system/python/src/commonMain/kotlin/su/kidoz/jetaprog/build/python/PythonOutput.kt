package su.kidoz.jetaprog.build.python

/**
 * Output from Python build tool execution.
 */
public sealed interface PythonOutput {
    /** Standard output line. */
    public data class Stdout(
        val line: String,
    ) : PythonOutput

    /** Standard error line. */
    public data class Stderr(
        val line: String,
    ) : PythonOutput

    /** Command started executing. */
    public data class CommandStarted(
        val command: String,
        val args: List<String>,
    ) : PythonOutput

    /** Package installation started. */
    public data class PackageInstalling(
        val packageName: String,
        val version: String? = null,
    ) : PythonOutput

    /** Package installation completed. */
    public data class PackageInstalled(
        val packageName: String,
        val version: String,
    ) : PythonOutput

    /** Package removal completed. */
    public data class PackageRemoved(
        val packageName: String,
    ) : PythonOutput

    /** Virtual environment created. */
    public data class VenvCreated(
        val path: String,
        val pythonVersion: String? = null,
    ) : PythonOutput

    /** Lock file updated. */
    public data class LockFileUpdated(
        val path: String,
    ) : PythonOutput

    /** Dependency resolution progress. */
    public data class ResolvingDependencies(
        val message: String,
    ) : PythonOutput

    /** Script execution started. */
    public data class ScriptStarted(
        val scriptName: String,
    ) : PythonOutput

    /** Command completed. */
    public data class CommandCompleted(
        val success: Boolean,
        val exitCode: Int,
        val duration: Long = 0,
    ) : PythonOutput
}

/**
 * Outcome of a Python build tool operation.
 */
public enum class PythonOperationOutcome {
    /** Operation completed successfully. */
    SUCCESS,

    /** Operation failed. */
    FAILED,

    /** Operation was cancelled. */
    CANCELLED,

    /** No changes were needed. */
    NO_CHANGES,
}
