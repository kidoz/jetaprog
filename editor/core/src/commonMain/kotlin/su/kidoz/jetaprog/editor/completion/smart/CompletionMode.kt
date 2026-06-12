package su.kidoz.jetaprog.editor.completion.smart

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.completion.CompletionContext
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.index.IndexRange

/**
 * Completion mode following IntelliJ IDEA's completion types.
 *
 * Different modes provide different levels of filtering and intelligence:
 * - Basic: All accessible symbols
 * - Smart: Type-filtered symbols
 * - Statement: Auto-complete the current statement
 */
@Serializable
public enum class CompletionMode {
    /**
     * Basic completion (Ctrl+Space).
     *
     * Shows all accessible symbols:
     * - Local variables in scope
     * - Class members
     * - Available functions
     * - Keywords
     * - Templates
     */
    Basic,

    /**
     * Smart/Type-aware completion (Ctrl+Shift+Space).
     *
     * Filters suggestions by expected type:
     * - Only shows items matching the expected type
     * - Ranks exact type matches higher
     * - Considers type compatibility (subtypes, nullable)
     */
    Smart,

    /**
     * Statement completion (Ctrl+Shift+Enter).
     *
     * Completes the current statement:
     * - Adds missing parentheses, braces, semicolons
     * - Formats the completed code
     * - Positions cursor for next input
     */
    Statement,
}

/**
 * Extended completion request with smart completion context.
 */
@Serializable
public data class SmartCompletionRequest(
    /**
     * File path being edited.
     */
    val filePath: String,
    /**
     * Cursor position in the file.
     */
    val position: TextPosition,
    /**
     * Completion mode (Basic, Smart, Statement).
     */
    val mode: CompletionMode = CompletionMode.Basic,
    /**
     * Basic completion context (trigger info).
     */
    val context: CompletionContext,
    /**
     * Expected type context for smart completion.
     */
    val typeContext: ExpectedTypeContext = ExpectedTypeContext.None,
    /**
     * The typed prefix at cursor (for filtering).
     */
    val prefix: String = "",
    /**
     * Maximum number of items to return.
     */
    val limit: Int = 50,
    /**
     * Whether to include deprecated items.
     */
    val includeDeprecated: Boolean = true,
    /**
     * Whether to include items from other files.
     */
    val includeExternalSymbols: Boolean = true,
)

/**
 * Result of smart completion with additional metadata.
 */
@Serializable
public data class SmartCompletionResult(
    /**
     * The completion items, sorted by relevance.
     */
    val items: List<ScoredCompletionItem>,
    /**
     * Whether this list is incomplete (more items available).
     */
    val isIncomplete: Boolean = false,
    /**
     * The completion mode used.
     */
    val mode: CompletionMode,
    /**
     * The expected type context used for filtering.
     */
    val typeContext: ExpectedTypeContext,
    /**
     * Total number of items before filtering (for stats).
     */
    val totalBeforeFilter: Int = 0,
)

/**
 * A completion item with computed relevance score.
 */
@Serializable
public data class ScoredCompletionItem(
    /**
     * The completion item.
     */
    val item: su.kidoz.jetaprog.common.completion.CompletionItem,
    /**
     * Overall relevance score (higher is better).
     */
    val score: Int,
    /**
     * Type compatibility level.
     */
    val typeCompatibility: TypeCompatibility = TypeCompatibility.Incompatible,
    /**
     * Match ranges in the label for highlighting.
     */
    val matchRanges: List<IndexRange> = emptyList(),
)

/**
 * Relevance factors used for scoring completion items.
 *
 * Each factor contributes to the final score with its weight.
 */
public object RelevanceFactors {
    /** Exact type match. */
    public const val TYPE_EXACT: Int = 1000

    /** Type is subtype of expected. */
    public const val TYPE_SUBTYPE: Int = 500

    /** Type matches an alternative. */
    public const val TYPE_ALTERNATIVE: Int = 400

    /** Exact prefix match. */
    public const val PREFIX_EXACT: Int = 300

    /** Prefix match (case-insensitive). */
    public const val PREFIX_MATCH: Int = 200

    /** CamelCase pattern match. */
    public const val CAMEL_CASE_MATCH: Int = 150

    /** Contains match. */
    public const val CONTAINS_MATCH: Int = 100

    /** Item is in local scope. */
    public const val LOCAL_SCOPE: Int = 80

    /** Item was recently used. */
    public const val RECENTLY_USED: Int = 50

    /** Item is frequently used. */
    public const val FREQUENTLY_USED: Int = 40

    /** Item has same container as current context. */
    public const val SAME_CONTAINER: Int = 30

    /** Penalty for deprecated items. */
    public const val DEPRECATED_PENALTY: Int = -200

    /** Bonus for preselected items. */
    public const val PRESELECT_BONUS: Int = 100

    /** Length penalty factor (shorter is better). */
    public const val LENGTH_PENALTY_FACTOR: Int = 2
}

/**
 * Configuration for smart completion behavior.
 */
@Serializable
public data class SmartCompletionConfig(
    /**
     * Whether smart completion is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Minimum type compatibility score to include in smart mode.
     */
    val minTypeScore: Int = 0,
    /**
     * Whether to fall back to basic completion if smart yields no results.
     */
    val fallbackToBasic: Boolean = true,
    /**
     * Whether to boost exact type matches significantly.
     */
    val boostExactTypes: Boolean = true,
    /**
     * Whether to show type compatibility indicators.
     */
    val showTypeIndicators: Boolean = true,
    /**
     * Maximum items to show in smart mode.
     */
    val smartModeLimit: Int = 20,
)
