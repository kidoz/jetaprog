package su.kidoz.jetaprog.plugins.python.lint

import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleDescriptor
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.AbstractLintProvider
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult

/**
 * Lint provider that integrates with the ruff Python linter.
 *
 * Runs `ruff check --output-format json` on Python files and
 * converts the output to LintResult objects.
 */
public class RuffLintProvider(
    processExecutor: ProcessExecutor = JvmProcessExecutor(),
    ruffPath: String = "ruff",
    extraArgs: List<String> = emptyList(),
) : AbstractLintProvider(
        id = "python-ruff",
        name = "Ruff",
        languages = listOf("python"),
    ) {
    init {
        registerRules(
            RuffExternalRule(processExecutor, ruffPath, extraArgs),
        )
    }
}

/**
 * External lint rule that delegates to ruff.
 */
public class RuffExternalRule(
    private val processExecutor: ProcessExecutor,
    private val ruffPath: String,
    private val extraArgs: List<String>,
) : AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("ruff", "check"),
            name = "Ruff Linter",
            description = "Run ruff check for Python linting (Pyflakes, pycodestyle, and more)",
            category = LintCategory.CORRECTNESS,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = true,
            languages = listOf("python"),
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        // Build ruff command
        val command =
            buildList {
                add(ruffPath)
                add("check")
                add("--output-format")
                add("json")
                add("--stdin-filename")
                add(context.uri)
                add("-") // Read from stdin
                addAll(extraArgs)
            }

        // Execute ruff
        val result =
            processExecutor.execute(
                command = command,
                timeoutMillis = 30000,
            )

        return result.fold(
            onSuccess = { processResult ->
                parseRuffOutput(processResult, context.uri)
            },
            onFailure = {
                emptyList() // Ruff not available or failed
            },
        )
    }

    private fun parseRuffOutput(
        result: ProcessResult,
        uri: String,
    ): List<LintResult> {
        // Ruff outputs to stdout even on exit code 1 (when issues found)
        val output = result.stdout.ifBlank { result.stderr }
        return RuffOutputParser.parse(output, uri)
    }

    override suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix? {
        // Check if this result has fix information
        val hasFix = result.data["hasFix"] == "true"
        if (!hasFix) return null

        // Run ruff with --fix-only to get the fixed content
        val command =
            buildList {
                add(ruffPath)
                add("check")
                add("--fix-only")
                add("--stdin-filename")
                add(context.uri)
                add("-") // Read from stdin
                addAll(extraArgs)
            }

        val fixResult =
            processExecutor.execute(
                command = command,
                timeoutMillis = 30000,
            )

        return fixResult.fold(
            onSuccess = { processResult ->
                if (processResult.isSuccess && processResult.stdout.isNotBlank()) {
                    LintFix.Replace(
                        description = "Apply ruff fix",
                        uri = context.uri,
                        range = result.range,
                        newText = processResult.stdout,
                    )
                } else {
                    null
                }
            },
            onFailure = { null },
        )
    }
}
