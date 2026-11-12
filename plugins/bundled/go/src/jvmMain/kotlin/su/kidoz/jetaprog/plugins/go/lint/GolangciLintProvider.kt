package su.kidoz.jetaprog.plugins.go.lint

import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleDescriptor
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.AbstractLintProvider
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import java.net.URI

/**
 * Lint provider that runs golangci-lint on a saved Go file.
 *
 * The linter reads the file from disk. A missing binary yields no results and does not
 * fail the lint pass. v2's JSON output flag is tried first, then the v1 flag.
 *
 * @param processExecutor Process launcher.
 * @param lintCommand golangci-lint executable.
 */
public class GolangciLintProvider(
    processExecutor: ProcessExecutor = JvmProcessExecutor(),
    lintCommand: String = "golangci-lint",
) : AbstractLintProvider(
        id = "go-golangci",
        name = "golangci-lint",
        languages = listOf("go"),
    ) {
    init {
        registerRules(GolangciExternalRule(processExecutor, lintCommand))
    }
}

/**
 * External rule that delegates one file to golangci-lint.
 */
public class GolangciExternalRule(
    private val processExecutor: ProcessExecutor,
    private val lintCommand: String,
) : AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("go", "golangci-lint"),
            name = "golangci-lint",
            description = "Runs golangci-lint, including govet, staticcheck, and the configured linter set",
            category = LintCategory.CORRECTNESS,
            defaultSeverity = LintSeverity.WARNING,
            languages = listOf("go"),
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        if (context.languageId != "go") return emptyList()
        val path = golangciTarget(context.uri) ?: return emptyList()
        val result = runLint(path) ?: return emptyList()
        if (result.stdout.isBlank()) return emptyList()
        return GolangciOutputParser.parse(result.stdout)
    }

    private suspend fun runLint(path: String): ProcessResult? {
        val directory = path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { null }
        val attempts =
            listOf(
                listOf(lintCommand, "run", "--output.json.path", "stdout", path),
                listOf(lintCommand, "run", "--out-format", "json", path),
            )
        var fallback: ProcessResult? = null
        for (command in attempts) {
            val result =
                processExecutor
                    .execute(command = command, workingDirectory = directory, timeoutMillis = LINT_TIMEOUT_MILLIS)
                    .getOrNull() ?: return null
            val unknownFlag =
                result.stderr.contains("unknown flag") || result.stderr.contains("flag provided but not defined")
            if (!unknownFlag) return result
            fallback = result
        }
        return fallback
    }

    private companion object {
        const val LINT_TIMEOUT_MILLIS = 60_000L
    }
}

/**
 * Converts a document URI into a filesystem path golangci-lint can open.
 */
internal fun golangciTarget(uri: String): String? {
    val path =
        if (uri.startsWith("file:")) {
            runCatching { URI(uri).path }.getOrNull()
        } else {
            uri
        }
    if (path.isNullOrBlank()) return null
    return if (path.length >= 3 && path[0] == '/' && path[2] == ':') path.drop(1) else path
}
