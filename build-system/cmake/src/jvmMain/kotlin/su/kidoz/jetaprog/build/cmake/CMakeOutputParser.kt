package su.kidoz.jetaprog.build.cmake

/**
 * Turns raw `cmake` and `ctest` output lines into [CMakeOutput] events.
 */
internal object CMakeOutputParser {
    /** `[ 50%] Building CXX object CMakeFiles/app.dir/main.cpp.o` */
    private val BUILD_STEP_PATTERN =
        Regex("""^\[\s*(\d+)%]\s+(\w+)(?:\s+(C|CXX|OBJC|OBJCXX|ASM))?\s+(?:object|executable|library)?\s*(.+)$""")

    /** `[ 50%] Some other progress line` */
    private val PROGRESS_PATTERN = Regex("""^\[\s*(\d+)%]\s+(.+)$""")

    /** `Start 3: my_test` */
    private val CTEST_START_PATTERN = Regex("""^Start\s+\d+:\s+(\S+)$""")

    /** `1/3 Test #1: my_test .......   Passed    0.01 sec` */
    private val CTEST_RESULT_PATTERN =
        Regex(
            """^\d+/\d+\s+Test\s+#\d+:\s+(\S+)\s*\.*\s*""" +
                """(\**(?:Passed|Failed|Timeout|Skipped|Not Run))""" +
                """\s*(?:([\d.]+)\s+sec)?.*$""",
        )

    /** `100% tests passed, 0 tests failed out of 3` */
    private val CTEST_SUMMARY_PATTERN = Regex("""^\d+% tests passed,\s+(\d+) tests? failed out of\s+(\d+)$""")

    /** `src/main.cpp:12:5: error: expected ';'` */
    private val DIAGNOSTIC_PATTERN = Regex("""^(.+?):(\d+):(\d+):\s+(error|warning|note|fatal error):\s+(.+)$""")

    /**
     * Classifies a standard output line.
     */
    fun parseStdout(rawLine: String): CMakeOutput {
        val line = rawLine.trim()

        parseBuildStep(line)?.let { return it }
        parseTestEvent(line)?.let { return it }

        if (line.startsWith("-- ")) {
            return CMakeOutput.ConfigureProgress(line.removePrefix("-- "))
        }

        return parseDiagnostic(rawLine) ?: CMakeOutput.Stdout(rawLine)
    }

    /**
     * Classifies a standard error line. CMake and the compilers report diagnostics here.
     */
    fun parseStderr(rawLine: String): CMakeOutput = parseDiagnostic(rawLine) ?: CMakeOutput.Stderr(rawLine)

    private fun parseBuildStep(line: String): CMakeOutput? {
        BUILD_STEP_PATTERN.matchEntire(line)?.let { match ->
            val percent = match.groupValues[1].toInt()
            val action = match.groupValues[2]
            val language = match.groupValues[3].ifEmpty { null }
            val target = match.groupValues[4]
            return when (action) {
                "Linking" -> CMakeOutput.Linking(target, language)
                "Building" -> CMakeOutput.Compiling(target, language)
                else -> CMakeOutput.BuildProgress(percent, "$action $target")
            }
        }

        PROGRESS_PATTERN.matchEntire(line)?.let { match ->
            return CMakeOutput.BuildProgress(match.groupValues[1].toInt(), match.groupValues[2])
        }

        return null
    }

    private fun parseTestEvent(line: String): CMakeOutput? {
        CTEST_START_PATTERN.matchEntire(line)?.let { match ->
            return CMakeOutput.TestStarted(match.groupValues[1])
        }

        CTEST_RESULT_PATTERN.matchEntire(line)?.let { match ->
            // CTest prefixes failures with asterisks, e.g. "***Failed" or "***Timeout".
            val outcome =
                when (match.groupValues[2].trimStart('*')) {
                    "Passed" -> CTestOutcome.PASSED
                    "Failed" -> CTestOutcome.FAILED
                    "Timeout" -> CTestOutcome.TIMEOUT
                    "Skipped" -> CTestOutcome.SKIPPED
                    else -> CTestOutcome.NOT_RUN
                }
            return CMakeOutput.TestCompleted(
                testName = match.groupValues[1],
                outcome = outcome,
                durationSeconds = match.groupValues[3].toDoubleOrNull(),
            )
        }

        CTEST_SUMMARY_PATTERN.matchEntire(line)?.let { match ->
            val failed = match.groupValues[1].toInt()
            val total = match.groupValues[2].toInt()
            return CMakeOutput.TestSummary(passed = total - failed, failed = failed, total = total)
        }

        return null
    }

    /**
     * Parses a GCC/Clang style diagnostic: `path/to/file.cpp:12:5: error: message`.
     */
    private fun parseDiagnostic(rawLine: String): CMakeOutput.Diagnostic? {
        val match = DIAGNOSTIC_PATTERN.matchEntire(rawLine.trim()) ?: return null
        val severity =
            when {
                match.groupValues[4].endsWith("error") -> DiagnosticSeverity.ERROR
                match.groupValues[4] == "warning" -> DiagnosticSeverity.WARNING
                else -> DiagnosticSeverity.NOTE
            }
        return CMakeOutput.Diagnostic(
            severity = severity,
            message = match.groupValues[5],
            file = match.groupValues[1],
            line = match.groupValues[2].toIntOrNull(),
            column = match.groupValues[3].toIntOrNull(),
        )
    }
}
