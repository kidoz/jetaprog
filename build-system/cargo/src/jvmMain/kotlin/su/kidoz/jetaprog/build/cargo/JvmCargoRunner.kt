package su.kidoz.jetaprog.build.cargo

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

private val logger = KotlinLogging.logger {}

/**
 * JVM implementation of CargoRunner using ProcessExecutor.
 */
public class JvmCargoRunner(
    private val processExecutor: ProcessExecutor,
    private val cargoCommand: String = "cargo",
) : CargoRunner {
    private var runningProcess: RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun build(
        project: CargoProject,
        profile: CargoProfile,
        target: CargoTarget?,
        features: List<String>,
        allFeatures: Boolean,
        noDefaultFeatures: Boolean,
        package_: String?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("build")
            profile.flag?.let { add(it) }
            target?.let { add("--target=${it.triple}") }
            if (features.isNotEmpty()) add("--features=${features.joinToString(",")}")
            if (allFeatures) add("--all-features")
            if (noDefaultFeatures) add("--no-default-features")
            package_?.let { add("--package=$it") }
        }

    override suspend fun run(
        project: CargoProject,
        profile: CargoProfile,
        bin: String?,
        example: String?,
        args: List<String>,
        features: List<String>,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("run")
            profile.flag?.let { add(it) }
            bin?.let { add("--bin=$it") }
            example?.let { add("--example=$it") }
            if (features.isNotEmpty()) add("--features=${features.joinToString(",")}")
            if (args.isNotEmpty()) {
                add("--")
                addAll(args)
            }
        }

    override suspend fun test(
        project: CargoProject,
        testName: String?,
        profile: CargoProfile,
        package_: String?,
        lib: Boolean,
        bins: Boolean,
        tests: Boolean,
        doc: Boolean,
        nocapture: Boolean,
        testArgs: List<String>,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("test")
            profile.flag?.let { add(it) }
            package_?.let { add("--package=$it") }
            if (lib) add("--lib")
            if (bins) add("--bins")
            if (tests) add("--tests")
            if (doc) add("--doc")
            testName?.let { add(it) }
            if (nocapture || testArgs.isNotEmpty()) {
                add("--")
                if (nocapture) add("--nocapture")
                addAll(testArgs)
            }
        }

    override suspend fun check(
        project: CargoProject,
        package_: String?,
        allTargets: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("check")
            package_?.let { add("--package=$it") }
            if (allTargets) add("--all-targets")
        }

    override suspend fun clippy(
        project: CargoProject,
        fix: Boolean,
        package_: String?,
        allTargets: Boolean,
        denyWarnings: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("clippy")
            if (fix) add("--fix")
            package_?.let { add("--package=$it") }
            if (allTargets) add("--all-targets")
            if (denyWarnings) {
                add("--")
                add("-D")
                add("warnings")
            }
        }

    override suspend fun fmt(
        project: CargoProject,
        check: Boolean,
        package_: String?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("fmt")
            if (check) add("--check")
            package_?.let { add("--package=$it") }
        }

    override suspend fun doc(
        project: CargoProject,
        open: Boolean,
        noDeps: Boolean,
        package_: String?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("doc")
            if (open) add("--open")
            if (noDeps) add("--no-deps")
            package_?.let { add("--package=$it") }
        }

    override suspend fun clean(
        project: CargoProject,
        package_: String?,
        profile: CargoProfile?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("clean")
            package_?.let { add("--package=$it") }
            profile?.flag?.let { add(it) }
        }

    override suspend fun add(
        project: CargoProject,
        crate: String,
        version: String?,
        features: List<String>,
        dev: Boolean,
        build: Boolean,
        optional: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("add")
            add(crate)
            version?.let { add("--vers=$it") }
            if (features.isNotEmpty()) add("--features=${features.joinToString(",")}")
            if (dev) add("--dev")
            if (build) add("--build")
            if (optional) add("--optional")
        }

    override suspend fun remove(
        project: CargoProject,
        crate: String,
        dev: Boolean,
        build: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("remove")
            add(crate)
            if (dev) add("--dev")
            if (build) add("--build")
        }

    override suspend fun update(
        project: CargoProject,
        crate: String?,
        precise: String?,
        dryRun: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("update")
            crate?.let { add(it) }
            precise?.let { add("--precise=$it") }
            if (dryRun) add("--dry-run")
        }

    override suspend fun new(
        path: String,
        name: String?,
        lib: Boolean,
        edition: String,
        vcs: String,
    ): Result<Flow<CargoOutput>> =
        runCommandWithPath(path) {
            add("new")
            add(path)
            name?.let { add("--name=$it") }
            if (lib) add("--lib")
            add("--edition=$edition")
            add("--vcs=$vcs")
        }

    override suspend fun init(
        path: String,
        name: String?,
        lib: Boolean,
        edition: String,
    ): Result<Flow<CargoOutput>> =
        runCommandWithPath(path) {
            add("init")
            name?.let { add("--name=$it") }
            if (lib) add("--lib")
            add("--edition=$edition")
        }

    override suspend fun fetch(
        project: CargoProject,
        target: CargoTarget?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("fetch")
            target?.let { add("--target=${it.triple}") }
        }

    override suspend fun tree(
        project: CargoProject,
        package_: String?,
        invert: Boolean,
        duplicates: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("tree")
            package_?.let { add("--package=$it") }
            if (invert) add("--invert")
            if (duplicates) add("--duplicates")
        }

    override suspend fun bench(
        project: CargoProject,
        benchName: String?,
        package_: String?,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("bench")
            package_?.let { add("--package=$it") }
            benchName?.let { add(it) }
        }

    override suspend fun publish(
        project: CargoProject,
        dryRun: Boolean,
        allowDirty: Boolean,
    ): Result<Flow<CargoOutput>> =
        runCommand(project) {
            add("publish")
            if (dryRun) add("--dry-run")
            if (allowDirty) add("--allow-dirty")
        }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }

    private suspend fun runCommand(
        project: CargoProject,
        buildArgs: MutableList<String>.() -> Unit,
    ): Result<Flow<CargoOutput>> = runCommandWithPath(project.rootPath, buildArgs)

    private suspend fun runCommandWithPath(
        workingDir: String,
        buildArgs: MutableList<String>.() -> Unit,
    ): Result<Flow<CargoOutput>> =
        runCatching {
            val args = mutableListOf(cargoCommand).apply(buildArgs)
            logger.debug { "Running: ${args.joinToString(" ")}" }

            val config =
                ProcessConfig(
                    command = args,
                    workingDirectory = workingDir,
                    environment = mapOf("CARGO_TERM_COLOR" to "never"),
                )

            val process = processExecutor.start(config).getOrThrow()
            runningProcess = process

            flow {
                emit(CargoOutput.CommandStarted(cargoCommand, args.drop(1)))

                val startTime = System.currentTimeMillis()

                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            val parsed = parseOutputLine(output.line)
                            emit(parsed)
                        }

                        is ProcessOutput.Stderr -> {
                            val parsed = parseStderrLine(output.line)
                            emit(parsed)
                        }

                        is ProcessOutput.Exited -> {
                            runningProcess = null
                            val duration = System.currentTimeMillis() - startTime
                            emit(
                                CargoOutput.CommandCompleted(
                                    success = output.exitCode == 0,
                                    exitCode = output.exitCode,
                                    duration = duration,
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun parseOutputLine(line: String): CargoOutput {
        // Parse Cargo's structured output
        val compilingPattern = Regex("""^\s*Compiling\s+(\S+)\s+v([^\s]+)(?:\s+\((.+)\))?""")
        val checkingPattern = Regex("""^\s*Checking\s+(\S+)\s+v([^\s]+)""")
        val downloadingPattern = Regex("""^\s*Downloading\s+(\S+)\s+v([^\s]+)""")
        val downloadedPattern = Regex("""^\s*Downloaded\s+(\S+)\s+v([^\s]+)""")
        val runningPattern = Regex("""^\s*Running\s+(.+)""")
        val documentingPattern = Regex("""^\s*Documenting\s+(\S+)""")
        val freshPattern = Regex("""^\s*Fresh\s+(\S+)\s+v([^\s]+)""")
        val finishedPattern = Regex("""^\s*Finished\s+`?(\S+)`?\s+.*""")
        val testResultPattern = Regex("""^test\s+(.+)\s+\.\.\.\s+(ok|FAILED|ignored)""")
        val testSummaryPattern =
            Regex("""^test result: \w+\. (\d+) passed; (\d+) failed; (\d+) ignored;.*""")
        val addingPattern = Regex("""^\s*Adding\s+(\S+)\s+v([^\s]+)""")
        val removingPattern = Regex("""^\s*Removing\s+(\S+)""")

        compilingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Compiling(
                crateName = match.groupValues[1],
                version = match.groupValues[2],
                path = match.groupValues.getOrNull(3),
            )
        }

        checkingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Checking(
                crateName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        downloadingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Downloading(
                crateName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        downloadedPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Downloaded(
                crateName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        runningPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Running(target = match.groupValues[1])
        }

        documentingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Documenting(crateName = match.groupValues[1])
        }

        freshPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Fresh(
                crateName = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        finishedPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Finished(profile = match.groupValues[1])
        }

        testResultPattern.matchEntire(line)?.let { match ->
            val outcome =
                when (match.groupValues[2]) {
                    "ok" -> TestOutcome.PASSED
                    "FAILED" -> TestOutcome.FAILED
                    "ignored" -> TestOutcome.IGNORED
                    else -> TestOutcome.PASSED
                }
            return CargoOutput.TestResult(name = match.groupValues[1], outcome = outcome)
        }

        testSummaryPattern.matchEntire(line)?.let { match ->
            return CargoOutput.TestSummary(
                passed = match.groupValues[1].toIntOrNull() ?: 0,
                failed = match.groupValues[2].toIntOrNull() ?: 0,
                ignored = match.groupValues[3].toIntOrNull() ?: 0,
                measured = 0,
                filteredOut = 0,
                duration = 0,
            )
        }

        addingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.DependencyAdded(
                name = match.groupValues[1],
                version = match.groupValues[2],
            )
        }

        removingPattern.matchEntire(line)?.let { match ->
            return CargoOutput.DependencyRemoved(name = match.groupValues[1])
        }

        return CargoOutput.Stdout(line)
    }

    private fun parseStderrLine(line: String): CargoOutput {
        // Parse error/warning output
        val errorPattern = Regex("""^error(?:\[([^\]]+)\])?: (.+)""")
        val warningPattern = Regex("""^warning(?:\[([^\]]+)\])?: (.+)""")
        val locationPattern = Regex("""^\s*-->\s*([^:]+):(\d+):(\d+)""")

        errorPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Error(
                message = match.groupValues[2],
                code = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() },
            )
        }

        warningPattern.matchEntire(line)?.let { match ->
            return CargoOutput.Warning(
                message = match.groupValues[2],
                code = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() },
            )
        }

        locationPattern.matchEntire(line)?.let { match ->
            val lineNum = match.groupValues[2].toIntOrNull() ?: 0
            val col = match.groupValues[3].toIntOrNull() ?: 0
            return CargoOutput.Stderr(line)
        }

        return CargoOutput.Stderr(line)
    }

    @Suppress("UnusedPrivateMember")
    private fun parseLocation(
        file: String,
        lineNum: Int,
        col: Int,
    ): Pair<String, TextRange> {
        val position = TextPosition(lineNum - 1, col - 1)
        return file to TextRange(position, position)
    }
}
