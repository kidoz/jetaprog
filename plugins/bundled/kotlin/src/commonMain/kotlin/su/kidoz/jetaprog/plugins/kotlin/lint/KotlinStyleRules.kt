package su.kidoz.jetaprog.plugins.kotlin.lint

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
 * Lint provider for Kotlin-specific style rules.
 */
public class KotlinStyleProvider :
    AbstractLintProvider(
        id = "kotlin-style",
        name = "Kotlin Style",
        languages = listOf("kotlin"),
    ) {
    init {
        registerRules(
            AvoidBangBangRule(),
            UseValRule(),
            NamingConventionRule(),
            UseWhenRule(),
        )
    }
}

/**
 * Warns against using the !! (not-null assertion) operator.
 */
public class AvoidBangBangRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("kotlin", "avoid-bang-bang"),
            name = "Avoid !! Operator",
            description = "The !! operator can throw NullPointerException; consider using safer alternatives",
            category = LintCategory.CORRECTNESS,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = false,
            languages = listOf("kotlin"),
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()
        val pattern = Regex("!!")

        for (match in context.findAll(pattern)) {
            val position = context.offsetToPosition(match.range.first)

            if (!context.isInComment(position) && !context.isInString(position)) {
                results.add(
                    createResult(
                        message = "Avoid using !! operator; consider using ?., ?:, or let/run",
                        range = context.rangeFromOffsets(match.range.first, match.range.last + 1),
                    ),
                )
            }
        }

        return results
    }
}

/**
 * Suggests using val instead of var when possible.
 */
public class UseValRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("kotlin", "use-val"),
            name = "Prefer val over var",
            description = "Use val for variables that are never reassigned",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.HINT,
            hasFix = true,
            languages = listOf("kotlin"),
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()
        val varPattern = Regex("""\bvar\s+([a-zA-Z_][a-zA-Z0-9_]*)""")

        for (match in context.findAll(varPattern)) {
            val position = context.offsetToPosition(match.range.first)
            if (context.isInComment(position) || context.isInString(position)) {
                continue
            }

            val varName = match.groupValues[1]

            val reassignmentPattern = Regex("""\b${Regex.escape(varName)}\s*=""")
            val assignmentCount =
                context.findAll(reassignmentPattern).count { matchResult ->
                    val pos = context.offsetToPosition(matchResult.range.first)
                    !context.isInComment(pos) && !context.isInString(pos)
                }

            if (assignmentCount <= 1) {
                results.add(
                    createResult(
                        message =
                            "Consider using 'val' instead of 'var' for '$varName' " +
                                "(appears to be assigned only once)",
                        range = context.rangeFromOffsets(match.range.first, match.range.first + 3),
                        severity = LintSeverity.HINT,
                        data = mapOf("varName" to varName),
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
        LintFix.Replace(
            description = "Change 'var' to 'val'",
            uri = context.uri,
            range = result.range,
            newText = "val",
        )
}

/**
 * Checks Kotlin naming conventions.
 */
public class NamingConventionRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("kotlin", "naming-convention"),
            name = "Naming Convention",
            description = "Follow Kotlin naming conventions (camelCase, PascalCase)",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.WARNING,
            hasFix = false,
            languages = listOf("kotlin"),
        ),
    ) {
    private val classPattern = Regex("""\b(?:class|interface|object|enum)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
    private val funPattern = Regex("""\bfun\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
    private val valVarPattern = Regex("""\b(?:val|var)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
    private val constPattern = Regex("""\bconst\s+val\s+([a-zA-Z_][a-zA-Z0-9_]*)""")

    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()

        for (match in context.findAll(classPattern)) {
            val position = context.offsetToPosition(match.range.first)
            if (context.isInComment(position) || context.isInString(position)) continue

            val name = match.groupValues[1]
            if (!isPascalCase(name)) {
                val nameStart = match.range.first + match.value.indexOf(name)
                results.add(
                    createResult(
                        message = "Class/interface names should be PascalCase: '$name'",
                        range = context.rangeFromOffsets(nameStart, nameStart + name.length),
                    ),
                )
            }
        }

        for (match in context.findAll(funPattern)) {
            val position = context.offsetToPosition(match.range.first)
            if (context.isInComment(position) || context.isInString(position)) continue

            val name = match.groupValues[1]
            if (!isCamelCase(name)) {
                val nameStart = match.range.first + match.value.indexOf(name)
                results.add(
                    createResult(
                        message = "Function names should be camelCase: '$name'",
                        range = context.rangeFromOffsets(nameStart, nameStart + name.length),
                    ),
                )
            }
        }

        for (match in context.findAll(constPattern)) {
            val position = context.offsetToPosition(match.range.first)
            if (context.isInComment(position) || context.isInString(position)) continue

            val name = match.groupValues[1]
            if (!isScreamingSnakeCase(name)) {
                val nameStart = match.range.first + match.value.indexOf(name)
                results.add(
                    createResult(
                        message = "Constant names should be SCREAMING_SNAKE_CASE: '$name'",
                        range = context.rangeFromOffsets(nameStart, nameStart + name.length),
                    ),
                )
            }
        }

        return results
    }

    private fun isPascalCase(name: String): Boolean = name.first().isUpperCase() && !name.contains('_')

    private fun isCamelCase(name: String): Boolean = name.first().isLowerCase() && !name.contains('_')

    private fun isScreamingSnakeCase(name: String): Boolean = name.all { it.isUpperCase() || it == '_' || it.isDigit() }
}

/**
 * Suggests using when expression instead of long if-else chains.
 */
public class UseWhenRule :
    AbstractLintRule(
        LintRuleDescriptor(
            id = LintRuleId.of("kotlin", "use-when"),
            name = "Use when Expression",
            description = "Consider using 'when' expression instead of long if-else chains",
            category = LintCategory.STYLE,
            defaultSeverity = LintSeverity.HINT,
            hasFix = false,
            languages = listOf("kotlin"),
        ),
    ) {
    override suspend fun check(context: LintContext): List<LintResult> {
        val results = mutableListOf<LintResult>()
        val ifElsePattern = Regex("""if\s*\([^)]+\)\s*\{[^}]*\}\s*else\s+if""")

        for (match in context.findAll(ifElsePattern)) {
            val position = context.offsetToPosition(match.range.first)
            if (context.isInComment(position) || context.isInString(position)) continue

            val elseIfCount = countElseIfChain(context.content, match.range.first)
            if (elseIfCount >= 3) {
                results.add(
                    createResult(
                        message =
                            "Consider using 'when' expression instead of if-else chain " +
                                "($elseIfCount branches)",
                        range = context.rangeFromOffsets(match.range.first, match.range.first + 2),
                        severity = LintSeverity.HINT,
                    ),
                )
            }
        }

        return results
    }

    private fun countElseIfChain(
        content: String,
        startOffset: Int,
    ): Int {
        var count = 1
        var offset = startOffset
        val pattern = Regex("""\}\s*else\s+if\s*\(""")

        while (true) {
            val match = pattern.find(content, offset + 1)
            if (match != null && match.range.first < startOffset + 500) {
                count++
                offset = match.range.last
            } else {
                break
            }
        }

        return count
    }
}
