package su.kidoz.jetaprog.build.cargo

import su.kidoz.jetaprog.common.text.TextRange

/**
 * Output from Cargo command execution.
 */
public sealed interface CargoOutput {
    /** Standard output line. */
    public data class Stdout(
        val line: String,
    ) : CargoOutput

    /** Standard error line. */
    public data class Stderr(
        val line: String,
    ) : CargoOutput

    /** Command started executing. */
    public data class CommandStarted(
        val command: String,
        val args: List<String>,
    ) : CargoOutput

    /** Compiling a crate. */
    public data class Compiling(
        val crateName: String,
        val version: String? = null,
        val path: String? = null,
    ) : CargoOutput

    /** Downloading a crate. */
    public data class Downloading(
        val crateName: String,
        val version: String,
    ) : CargoOutput

    /** Downloaded a crate. */
    public data class Downloaded(
        val crateName: String,
        val version: String,
    ) : CargoOutput

    /** Building a crate. */
    public data class Building(
        val crateName: String,
        val progress: String? = null,
    ) : CargoOutput

    /** Checking a crate. */
    public data class Checking(
        val crateName: String,
        val version: String? = null,
    ) : CargoOutput

    /** Running a test or binary. */
    public data class Running(
        val target: String,
    ) : CargoOutput

    /** Documenting a crate. */
    public data class Documenting(
        val crateName: String,
    ) : CargoOutput

    /** Fresh crate (no recompilation needed). */
    public data class Fresh(
        val crateName: String,
        val version: String? = null,
    ) : CargoOutput

    /** Finished build/check. */
    public data class Finished(
        val profile: String,
        val targetDir: String? = null,
        val duration: String? = null,
    ) : CargoOutput

    /** Compiler warning. */
    public data class Warning(
        val message: String,
        val file: String? = null,
        val range: TextRange? = null,
        val code: String? = null,
    ) : CargoOutput

    /** Compiler error. */
    public data class Error(
        val message: String,
        val file: String? = null,
        val range: TextRange? = null,
        val code: String? = null,
    ) : CargoOutput

    /** Clippy lint. */
    public data class ClippyLint(
        val level: String,
        val message: String,
        val file: String? = null,
        val range: TextRange? = null,
        val lintName: String? = null,
        val suggestion: String? = null,
    ) : CargoOutput

    /** Test result. */
    public data class TestResult(
        val name: String,
        val outcome: TestOutcome,
        val duration: Long? = null,
        val message: String? = null,
    ) : CargoOutput

    /** Test summary. */
    public data class TestSummary(
        val passed: Int,
        val failed: Int,
        val ignored: Int,
        val measured: Int,
        val filteredOut: Int,
        val duration: Long,
    ) : CargoOutput

    /** Dependency added. */
    public data class DependencyAdded(
        val name: String,
        val version: String,
        val features: List<String> = emptyList(),
    ) : CargoOutput

    /** Dependency removed. */
    public data class DependencyRemoved(
        val name: String,
    ) : CargoOutput

    /** Lock file updated. */
    public data class LockfileUpdated(
        val path: String,
    ) : CargoOutput

    /** Command completed. */
    public data class CommandCompleted(
        val success: Boolean,
        val exitCode: Int,
        val duration: Long = 0,
    ) : CargoOutput
}

/**
 * Test outcome.
 */
public enum class TestOutcome {
    /** Test passed. */
    PASSED,

    /** Test failed. */
    FAILED,

    /** Test was ignored. */
    IGNORED,

    /** Benchmark measurement. */
    MEASURED,
}

/**
 * Outcome of a Cargo operation.
 */
public enum class CargoOperationOutcome {
    /** Operation completed successfully. */
    SUCCESS,

    /** Compilation/check failed with errors. */
    COMPILE_ERROR,

    /** Tests failed. */
    TEST_FAILURE,

    /** Operation was cancelled. */
    CANCELLED,

    /** Other failure. */
    FAILED,
}
