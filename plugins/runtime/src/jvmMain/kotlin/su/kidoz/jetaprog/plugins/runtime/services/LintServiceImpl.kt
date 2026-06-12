package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.config.RuleOverride
import su.kidoz.jetaprog.lint.engine.LintEngine
import su.kidoz.jetaprog.lint.engine.LintInput
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.model.LintSummary
import su.kidoz.jetaprog.lint.provider.LintProvider
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.plugins.api.services.LintService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of LintService that delegates to LintEngine and LintProviderRegistry.
 */
public class LintServiceImpl(
    private val lintEngine: LintEngine,
    private val providerRegistry: LintProviderRegistry,
) : LintService {
    private val resultsCache = ConcurrentHashMap<String, List<LintResult>>()
    private val resultsFlowInternal = MutableStateFlow<Map<String, List<LintResult>>>(emptyMap())
    private val summaryFlowInternal = MutableStateFlow(LintSummary.EMPTY)
    private var configuration = LintConfiguration()
    private val isLinting = AtomicBoolean(false)

    override fun registerProvider(provider: LintProvider): Disposable {
        providerRegistry.register(provider)

        // Register all rules from the provider with the engine
        for (rule in provider.getRules()) {
            lintEngine.registerRule(rule)
        }

        return Disposable {
            providerRegistry.unregister(provider.id)
            for (rule in provider.getRules()) {
                lintEngine.unregisterRule(rule.descriptor.id)
            }
        }
    }

    override suspend fun lintFile(
        uri: String,
        languageId: String,
        content: String,
    ): List<LintResult> {
        isLinting.set(true)
        try {
            val input =
                LintInput(
                    uri = uri,
                    languageId = languageId,
                    content = content,
                )

            val output = lintEngine.lint(input, configuration)
            resultsCache[uri] = output.results
            updateFlows()
            return output.results
        } finally {
            isLinting.set(false)
        }
    }

    override fun getResults(uri: String): List<LintResult> = resultsCache[uri] ?: emptyList()

    override fun getAllResults(): Map<String, List<LintResult>> = resultsCache.toMap()

    override fun getSummary(): LintSummary {
        val allResults = resultsCache.values.flatten()
        return LintSummary.from(allResults)
    }

    override fun observeResults(uri: String): Flow<List<LintResult>> =
        resultsFlowInternal.map {
            it[uri] ?: emptyList()
        }

    override fun observeSummary(): Flow<LintSummary> = summaryFlowInternal.asStateFlow()

    override suspend fun getFix(
        uri: String,
        result: LintResult,
    ): LintFix? {
        val content = "" // Would need to get from editor
        val input =
            LintInput(
                uri = uri,
                languageId = "unknown",
                content = content,
            )
        return lintEngine.getFix(input, result, configuration)
    }

    override suspend fun applyFix(
        uri: String,
        fix: LintFix,
    ): String {
        // Would need to get content from editor
        val content = ""
        return lintEngine.applyFix(content, fix)
    }

    override fun getConfiguration(): LintConfiguration = configuration

    override fun setConfiguration(configuration: LintConfiguration) {
        this.configuration = configuration
    }

    override fun setCategorySeverity(
        category: LintCategory,
        severity: LintSeverity,
    ) {
        val newOverrides = configuration.categoryOverrides.toMutableMap()
        newOverrides[category] = severity
        configuration = configuration.copy(categoryOverrides = newOverrides)
    }

    override fun setRuleSeverity(
        ruleId: LintRuleId,
        severity: LintSeverity,
    ) {
        val newOverrides = configuration.ruleOverrides.toMutableList()
        newOverrides.removeIf { it.ruleId == ruleId }
        newOverrides.add(RuleOverride(ruleId, severity))
        configuration = configuration.copy(ruleOverrides = newOverrides)
    }

    override fun isLinting(): Boolean = isLinting.get()

    override fun cancelLint() {
        // TODO: Implement cancellation support
        isLinting.set(false)
    }

    private fun updateFlows() {
        resultsFlowInternal.value = resultsCache.toMap()
        summaryFlowInternal.value = getSummary()
    }
}
