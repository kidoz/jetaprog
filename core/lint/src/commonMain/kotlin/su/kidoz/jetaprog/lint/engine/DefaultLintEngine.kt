package su.kidoz.jetaprog.lint.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRule
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import kotlin.time.TimeSource

/**
 * Default implementation of [LintEngine].
 */
public class DefaultLintEngine : LintEngine {
    private val rules = mutableMapOf<LintRuleId, LintRule>()

    override fun registerRule(rule: LintRule) {
        rules[rule.descriptor.id] = rule
    }

    override fun unregisterRule(ruleId: LintRuleId) {
        rules.remove(ruleId)
    }

    override fun getRules(): List<LintRule> = rules.values.toList()

    override fun getRule(ruleId: LintRuleId): LintRule? = rules[ruleId]

    override fun getRulesForLanguage(languageId: String): List<LintRule> =
        rules.values.filter { rule ->
            val languages = rule.descriptor.languages
            languages.isEmpty() || languageId in languages
        }

    override suspend fun lint(
        input: LintInput,
        configuration: LintConfiguration,
    ): LintOutput {
        if (!configuration.enabled) {
            return LintOutput(uri = input.uri, results = emptyList(), durationMs = 0)
        }

        val timeSource = TimeSource.Monotonic
        val start = timeSource.markNow()

        val context =
            DefaultLintContext(
                uri = input.uri,
                languageId = input.languageId,
                content = input.content,
                configuration = configuration,
                isOnSave = input.isOnSave,
            )

        val applicableRules =
            getRulesForLanguage(input.languageId)
                .filter { isRuleEnabled(it, configuration) }

        val results = mutableListOf<LintResult>()

        for (rule in applicableRules) {
            try {
                currentCoroutineContext().ensureActive()

                val ruleResults = rule.check(context)

                // Apply severity overrides and filter disabled
                val processedResults =
                    ruleResults.mapNotNull { result ->
                        val effectiveSeverity = getEffectiveSeverity(rule, result, configuration)
                        if (effectiveSeverity.isEnabled) {
                            result.copy(severity = effectiveSeverity)
                        } else {
                            null
                        }
                    }

                results.addAll(processedResults)

                // Check max issues limit
                if (results.size >= configuration.maxIssuesPerFile) {
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Log error but continue with other rules
                // In a real implementation, this would use a logger
            }
        }

        val duration = start.elapsedNow()
        return LintOutput(
            uri = input.uri,
            results = results.take(configuration.maxIssuesPerFile),
            durationMs = duration.inWholeMilliseconds,
        )
    }

    override fun lintWithProgress(
        inputs: List<LintInput>,
        configuration: LintConfiguration,
    ): Flow<LintProgress> =
        flow {
            if (!configuration.enabled || inputs.isEmpty()) {
                emit(LintProgress.Completed(0, 0, 0))
                return@flow
            }

            val timeSource = TimeSource.Monotonic
            val start = timeSource.markNow()

            val applicableRules = rules.values.filter { isRuleEnabled(it, configuration) }

            emit(LintProgress.Started(inputs.size, applicableRules.size))

            var totalResults = 0

            for ((index, input) in inputs.withIndex()) {
                try {
                    currentCoroutineContext().ensureActive()

                    emit(LintProgress.FileStarted(input.uri, index, inputs.size))

                    val output = lint(input, configuration)
                    totalResults += output.results.size

                    emit(LintProgress.FileFinished(input.uri, output.results))
                } catch (e: CancellationException) {
                    emit(LintProgress.Cancelled)
                    throw e
                }
            }

            val duration = start.elapsedNow()
            emit(LintProgress.Completed(totalResults, inputs.size, duration.inWholeMilliseconds))
        }

    override suspend fun getFix(
        input: LintInput,
        result: LintResult,
        configuration: LintConfiguration,
    ): LintFix? {
        val rule = rules[result.ruleId] ?: return null

        val context =
            DefaultLintContext(
                uri = input.uri,
                languageId = input.languageId,
                content = input.content,
                configuration = configuration,
            )

        return try {
            rule.fix(context, result)
        } catch (e: Exception) {
            null
        }
    }

    override fun applyFix(
        content: String,
        fix: LintFix,
    ): String =
        when (fix) {
            is LintFix.Replace -> applyReplaceFix(content, fix)

            is LintFix.Delete -> applyDeleteFix(content, fix)

            is LintFix.Insert -> applyInsertFix(content, fix)

            is LintFix.Composite -> applyCompositeFix(content, fix)

            is LintFix.AddImport -> content

            // Requires language-specific handling
            is LintFix.RemoveImport -> content

            // Requires language-specific handling
            is LintFix.Rename -> content

            // Requires full project analysis
            is LintFix.Command -> content // Requires IDE integration
        }

    private fun applyReplaceFix(
        content: String,
        fix: LintFix.Replace,
    ): String {
        val lines = content.lines().toMutableList()
        val startLine = fix.range.start.line
        val endLine = fix.range.end.line

        if (startLine !in lines.indices) return content

        if (startLine == endLine) {
            // Single line replacement
            val line = lines[startLine]
            val startCol =
                fix.range.start.column
                    .coerceIn(0, line.length)
            val endCol =
                fix.range.end.column
                    .coerceIn(0, line.length)
            lines[startLine] = line.substring(0, startCol) + fix.newText + line.substring(endCol)
        } else {
            // Multi-line replacement
            val firstLine = lines[startLine]
            val lastLine = lines.getOrNull(endLine) ?: ""

            val startCol =
                fix.range.start.column
                    .coerceIn(0, firstLine.length)
            val endCol =
                fix.range.end.column
                    .coerceIn(0, lastLine.length)

            val newContent = firstLine.substring(0, startCol) + fix.newText + lastLine.substring(endCol)

            // Remove lines in range and insert new content
            for (i in startLine..endLine.coerceAtMost(lines.lastIndex)) {
                lines.removeAt(startLine)
            }

            newContent.lines().forEachIndexed { index, line ->
                lines.add(startLine + index, line)
            }
        }

        return lines.joinToString("\n")
    }

    private fun applyDeleteFix(
        content: String,
        fix: LintFix.Delete,
    ): String =
        applyReplaceFix(
            content,
            LintFix.Replace(
                description = fix.description,
                uri = fix.uri,
                range = fix.range,
                newText = "",
                isSafe = fix.isSafe,
            ),
        )

    private fun applyInsertFix(
        content: String,
        fix: LintFix.Insert,
    ): String {
        val lines = content.lines().toMutableList()
        val line = fix.position.line

        if (line !in lines.indices) return content

        val lineContent = lines[line]
        val col = fix.position.column.coerceIn(0, lineContent.length)
        lines[line] = lineContent.substring(0, col) + fix.text + lineContent.substring(col)

        return lines.joinToString("\n")
    }

    private fun applyCompositeFix(
        content: String,
        fix: LintFix.Composite,
    ): String {
        var result = content
        // Apply fixes in reverse order to maintain positions
        for (subFix in fix.fixes.reversed()) {
            result = applyFix(result, subFix)
        }
        return result
    }

    private fun isRuleEnabled(
        rule: LintRule,
        configuration: LintConfiguration,
    ): Boolean {
        // Check explicit rule disable
        val ruleOverride = configuration.ruleOverrides.find { it.ruleId == rule.descriptor.id }
        if (ruleOverride?.severity == LintSeverity.OFF) {
            return false
        }

        // Check category disable
        val categoryOverride = configuration.categoryOverrides[rule.descriptor.category]
        if (categoryOverride == LintSeverity.OFF) {
            return ruleOverride?.severity?.isEnabled ?: false
        }

        return true
    }

    private fun getEffectiveSeverity(
        rule: LintRule,
        result: LintResult,
        configuration: LintConfiguration,
    ): LintSeverity {
        // Priority: rule override > category override > default
        val ruleOverride = configuration.ruleOverrides.find { it.ruleId == rule.descriptor.id }
        if (ruleOverride != null) {
            return ruleOverride.severity
        }

        val categoryOverride = configuration.categoryOverrides[rule.descriptor.category]
        if (categoryOverride != null) {
            return categoryOverride
        }

        return result.severity
    }
}
