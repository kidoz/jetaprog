package su.kidoz.jetaprog.lint.providers

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleDescriptor
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.AbstractLintProvider

/**
 * Lint provider for common code style rules.
 * These rules apply to all languages.
 */
public class StyleLintProvider :
    AbstractLintProvider(
        id = "style",
        name = "Code Style",
        languages = emptyList(),
    ) {
    init {
        registerRules(
            TrailingWhitespaceRule(),
            FinalNewlineRule(),
            MaxLineLengthRule(),
            NoTabsRule(),
            ConsistentIndentRule(),
        )
    }
}

/**
 * Checks for trailing whitespace at the end of lines.
 */
public class TrailingWhitespaceRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("style", "trailing-whitespace"),
            name = "No Trailing Whitespace",
            description = "Lines should not have trailing whitespace",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = true,
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()

        for ((lineIndex, line) in context.lines.withIndex()) {
            val trimmedLength = line.trimEnd().length
            if (trimmedLength < line.length) {
                val range =
                    TextRange(
                        start = TextPosition(lineIndex, trimmedLength),
                        end = TextPosition(lineIndex, line.length),
                    )
                results.add(
                    createResult(
                        message = "Line has trailing whitespace",
                        range = range,
                    ),
                )
            }
        }

        return results
    }

    override suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix =
        LintFix.Delete(
            description = "Remove trailing whitespace",
            uri = context.uri,
            range = result.range,
        )
}

/**
 * Checks that files end with a newline.
 */
public class FinalNewlineRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("style", "final-newline"),
            name = "Final Newline",
            description = "Files should end with a newline character",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.INFO,
            hasFix = true,
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        if (context.content.isEmpty()) return emptyList()

        if (!context.content.endsWith("\n")) {
            val lastLine = context.lines.lastIndex
            val lastColumn = context.lines.lastOrNull()?.length ?: 0
            return listOf(
                createResult(
                    message = "File should end with a newline",
                    range =
                        TextRange(
                            start = TextPosition(lastLine, lastColumn),
                            end = TextPosition(lastLine, lastColumn),
                        ),
                ),
            )
        }

        return emptyList()
    }

    override suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix =
        LintFix.Insert(
            description = "Add final newline",
            uri = context.uri,
            position = result.range.start,
            text = "\n",
        )
}

/**
 * Checks for lines exceeding the maximum length.
 */
public class MaxLineLengthRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("style", "max-line-length"),
            name = "Maximum Line Length",
            description = "Lines should not exceed the configured maximum length",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = false,
        ),
    ) {
    private val maxLength = 120

    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()

        for ((lineIndex, line) in context.lines.withIndex()) {
            if (line.length > maxLength) {
                val range =
                    TextRange(
                        start = TextPosition(lineIndex, maxLength),
                        end = TextPosition(lineIndex, line.length),
                    )
                results.add(
                    createResult(
                        message = "Line exceeds $maxLength characters (${line.length} chars)",
                        range = range,
                    ),
                )
            }
        }

        return results
    }
}

/**
 * Checks for tab characters in code (when spaces are preferred).
 */
public class NoTabsRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("style", "no-tabs"),
            name = "No Tabs",
            description = "Use spaces for indentation instead of tabs",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = true,
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()

        for ((lineIndex, line) in context.lines.withIndex()) {
            var column = 0
            for (char in line) {
                if (char == '\t') {
                    val range =
                        TextRange(
                            start = TextPosition(lineIndex, column),
                            end = TextPosition(lineIndex, column + 1),
                        )
                    results.add(
                        createResult(
                            message = "Tab character found; use spaces instead",
                            range = range,
                            data = mapOf("column" to column.toString()),
                        ),
                    )
                }
                column++
            }
        }

        return results
    }

    override suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix =
        LintFix.Replace(
            description = "Replace tab with spaces",
            uri = context.uri,
            range = result.range,
            newText = "    ",
        )
}

/**
 * Checks for consistent indentation size.
 */
public class ConsistentIndentRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("style", "consistent-indent"),
            name = "Consistent Indentation",
            description = "Use consistent indentation size (default: 4 spaces)",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = false,
        ),
    ) {
    private val indentSize = 4

    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()

        for ((lineIndex, line) in context.lines.withIndex()) {
            val leadingSpaces = line.takeWhile { it == ' ' }

            if (leadingSpaces.isNotEmpty() && leadingSpaces.length % indentSize != 0) {
                val range =
                    TextRange(
                        start = TextPosition(lineIndex, 0),
                        end = TextPosition(lineIndex, leadingSpaces.length),
                    )
                results.add(
                    createResult(
                        message =
                            "Indentation is not a multiple of $indentSize spaces " +
                                "(found ${leadingSpaces.length} spaces)",
                        range = range,
                    ),
                )
            }
        }

        return results
    }
}
