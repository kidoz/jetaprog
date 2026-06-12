package su.kidoz.jetaprog.build.cargo

import kotlinx.coroutines.flow.Flow

/**
 * Runs Cargo commands in a Rust project.
 *
 * Cargo is the Rust package manager and build system that provides:
 * - Dependency resolution and installation
 * - Compilation and linking
 * - Testing
 * - Documentation generation
 * - Publishing to crates.io
 *
 * @see <a href="https://doc.rust-lang.org/cargo/">Cargo Book</a>
 */
public interface CargoRunner {
    /**
     * Builds the project.
     *
     * Equivalent to: `cargo build [--release] [--target <target>]`
     *
     * @param project The Cargo project to build.
     * @param profile Build profile (debug or release).
     * @param target Target triple for cross-compilation.
     * @param features Features to enable.
     * @param allFeatures Enable all features.
     * @param noDefaultFeatures Disable default features.
     * @param package_ Specific package to build in a workspace.
     * @return A flow of build output.
     */
    public suspend fun build(
        project: CargoProject,
        profile: CargoProfile = CargoProfile.DEBUG,
        target: CargoTarget? = null,
        features: List<String> = emptyList(),
        allFeatures: Boolean = false,
        noDefaultFeatures: Boolean = false,
        package_: String? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Runs the project.
     *
     * Equivalent to: `cargo run [--release] [-- args]`
     *
     * @param project The Cargo project.
     * @param profile Build profile.
     * @param bin Binary to run (for multi-binary projects).
     * @param example Example to run.
     * @param args Arguments to pass to the binary.
     * @param features Features to enable.
     * @return A flow of output.
     */
    public suspend fun run(
        project: CargoProject,
        profile: CargoProfile = CargoProfile.DEBUG,
        bin: String? = null,
        example: String? = null,
        args: List<String> = emptyList(),
        features: List<String> = emptyList(),
    ): Result<Flow<CargoOutput>>

    /**
     * Runs tests.
     *
     * Equivalent to: `cargo test [test_name] [-- test_args]`
     *
     * @param project The Cargo project.
     * @param testName Specific test to run (supports patterns).
     * @param profile Build profile.
     * @param package_ Specific package to test in a workspace.
     * @param lib Only run library tests.
     * @param bins Only run binary tests.
     * @param tests Only run integration tests.
     * @param doc Only run doc tests.
     * @param nocapture Don't capture stdout/stderr.
     * @param testArgs Additional arguments passed to test harness.
     * @return A flow of test output.
     */
    public suspend fun test(
        project: CargoProject,
        testName: String? = null,
        profile: CargoProfile = CargoProfile.DEBUG,
        package_: String? = null,
        lib: Boolean = false,
        bins: Boolean = false,
        tests: Boolean = false,
        doc: Boolean = false,
        nocapture: Boolean = false,
        testArgs: List<String> = emptyList(),
    ): Result<Flow<CargoOutput>>

    /**
     * Type-checks the project without producing binaries.
     *
     * Equivalent to: `cargo check`
     *
     * @param project The Cargo project.
     * @param package_ Specific package to check in a workspace.
     * @param allTargets Check all targets.
     * @return A flow of check output.
     */
    public suspend fun check(
        project: CargoProject,
        package_: String? = null,
        allTargets: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Runs Clippy linter.
     *
     * Equivalent to: `cargo clippy`
     *
     * @param project The Cargo project.
     * @param fix Automatically apply suggestions.
     * @param package_ Specific package to lint.
     * @param allTargets Check all targets.
     * @param denyWarnings Treat warnings as errors.
     * @return A flow of clippy output.
     */
    public suspend fun clippy(
        project: CargoProject,
        fix: Boolean = false,
        package_: String? = null,
        allTargets: Boolean = false,
        denyWarnings: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Formats the project code.
     *
     * Equivalent to: `cargo fmt`
     *
     * @param project The Cargo project.
     * @param check Only check formatting without modifying files.
     * @param package_ Specific package to format.
     * @return A flow of format output.
     */
    public suspend fun fmt(
        project: CargoProject,
        check: Boolean = false,
        package_: String? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Builds documentation.
     *
     * Equivalent to: `cargo doc`
     *
     * @param project The Cargo project.
     * @param open Open in browser after building.
     * @param noDeps Don't build documentation for dependencies.
     * @param package_ Specific package to document.
     * @return A flow of doc output.
     */
    public suspend fun doc(
        project: CargoProject,
        open: Boolean = false,
        noDeps: Boolean = false,
        package_: String? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Cleans build artifacts.
     *
     * Equivalent to: `cargo clean`
     *
     * @param project The Cargo project.
     * @param package_ Specific package to clean.
     * @param profile Profile to clean (debug, release).
     * @return A flow of clean output.
     */
    public suspend fun clean(
        project: CargoProject,
        package_: String? = null,
        profile: CargoProfile? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Adds a dependency.
     *
     * Equivalent to: `cargo add <crate>`
     *
     * @param project The Cargo project.
     * @param crate Crate name or specification.
     * @param version Version requirement.
     * @param features Features to enable.
     * @param dev Add as dev dependency.
     * @param build Add as build dependency.
     * @param optional Add as optional dependency.
     * @return A flow of output.
     */
    public suspend fun add(
        project: CargoProject,
        crate: String,
        version: String? = null,
        features: List<String> = emptyList(),
        dev: Boolean = false,
        build: Boolean = false,
        optional: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Removes a dependency.
     *
     * Equivalent to: `cargo remove <crate>`
     *
     * @param project The Cargo project.
     * @param crate Crate name to remove.
     * @param dev Remove from dev dependencies.
     * @param build Remove from build dependencies.
     * @return A flow of output.
     */
    public suspend fun remove(
        project: CargoProject,
        crate: String,
        dev: Boolean = false,
        build: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Updates dependencies.
     *
     * Equivalent to: `cargo update [crate]`
     *
     * @param project The Cargo project.
     * @param crate Specific crate to update.
     * @param precise Update to a precise version.
     * @param dryRun Don't actually update, just show what would happen.
     * @return A flow of output.
     */
    public suspend fun update(
        project: CargoProject,
        crate: String? = null,
        precise: String? = null,
        dryRun: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Creates a new Cargo project.
     *
     * Equivalent to: `cargo new <path>`
     *
     * @param path Directory path for the new project.
     * @param name Project name (defaults to directory name).
     * @param lib Create a library instead of binary.
     * @param edition Rust edition (2018, 2021, etc.).
     * @param vcs Initialize with version control (git, none).
     * @return A flow of output.
     */
    public suspend fun new(
        path: String,
        name: String? = null,
        lib: Boolean = false,
        edition: String = "2021",
        vcs: String = "git",
    ): Result<Flow<CargoOutput>>

    /**
     * Initializes a Cargo project in existing directory.
     *
     * Equivalent to: `cargo init`
     *
     * @param path Directory path.
     * @param name Project name.
     * @param lib Create a library instead of binary.
     * @param edition Rust edition.
     * @return A flow of output.
     */
    public suspend fun init(
        path: String,
        name: String? = null,
        lib: Boolean = false,
        edition: String = "2021",
    ): Result<Flow<CargoOutput>>

    /**
     * Fetches dependencies without building.
     *
     * Equivalent to: `cargo fetch`
     *
     * @param project The Cargo project.
     * @param target Target triple to fetch for.
     * @return A flow of output.
     */
    public suspend fun fetch(
        project: CargoProject,
        target: CargoTarget? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Shows the dependency tree.
     *
     * Equivalent to: `cargo tree`
     *
     * @param project The Cargo project.
     * @param package_ Focus on specific package.
     * @param invert Show reverse dependencies.
     * @param duplicates Only show duplicated dependencies.
     * @return A flow of output.
     */
    public suspend fun tree(
        project: CargoProject,
        package_: String? = null,
        invert: Boolean = false,
        duplicates: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Runs benchmarks.
     *
     * Equivalent to: `cargo bench`
     *
     * @param project The Cargo project.
     * @param benchName Specific benchmark to run.
     * @param package_ Specific package.
     * @return A flow of output.
     */
    public suspend fun bench(
        project: CargoProject,
        benchName: String? = null,
        package_: String? = null,
    ): Result<Flow<CargoOutput>>

    /**
     * Publishes a crate to crates.io.
     *
     * Equivalent to: `cargo publish`
     *
     * @param project The Cargo project.
     * @param dryRun Perform checks without publishing.
     * @param allowDirty Allow publishing with uncommitted changes.
     * @return A flow of output.
     */
    public suspend fun publish(
        project: CargoProject,
        dryRun: Boolean = false,
        allowDirty: Boolean = false,
    ): Result<Flow<CargoOutput>>

    /**
     * Cancels the currently running command.
     */
    public fun cancel()

    /**
     * Whether a command is currently running.
     */
    public val isRunning: Boolean
}
