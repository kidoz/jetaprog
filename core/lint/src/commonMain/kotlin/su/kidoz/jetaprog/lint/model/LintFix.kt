package su.kidoz.jetaprog.lint.model

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextRange

/**
 * A fix that can be applied to resolve a lint issue.
 *
 * Fixes can be simple text replacements or complex multi-step operations.
 */
@Serializable
public sealed interface LintFix {
    /**
     * Human-readable description of what this fix does.
     */
    public val description: String

    /**
     * Whether this fix is considered safe to apply automatically.
     * Unsafe fixes may change behavior and require user review.
     */
    public val isSafe: Boolean

    /**
     * Replace text in a range with new text.
     */
    @Serializable
    public data class Replace(
        override val description: String,
        /**
         * The file URI where the fix applies.
         */
        val uri: String,
        /**
         * The range to replace.
         */
        val range: TextRange,
        /**
         * The new text to insert.
         */
        val newText: String,
        override val isSafe: Boolean = true,
    ) : LintFix

    /**
     * Delete text in a range.
     */
    @Serializable
    public data class Delete(
        override val description: String,
        /**
         * The file URI where the fix applies.
         */
        val uri: String,
        /**
         * The range to delete.
         */
        val range: TextRange,
        override val isSafe: Boolean = true,
    ) : LintFix

    /**
     * Insert text at a position.
     */
    @Serializable
    public data class Insert(
        override val description: String,
        /**
         * The file URI where the fix applies.
         */
        val uri: String,
        /**
         * The position to insert at (uses start of range).
         */
        val position: su.kidoz.jetaprog.common.text.TextPosition,
        /**
         * The text to insert.
         */
        val text: String,
        override val isSafe: Boolean = true,
    ) : LintFix

    /**
     * Multiple fixes to apply together as a single operation.
     */
    @Serializable
    public data class Composite(
        override val description: String,
        /**
         * The fixes to apply in order.
         */
        val fixes: List<LintFix>,
        override val isSafe: Boolean = fixes.all { it.isSafe },
    ) : LintFix

    /**
     * Add an import statement.
     */
    @Serializable
    public data class AddImport(
        override val description: String,
        /**
         * The file URI where the import should be added.
         */
        val uri: String,
        /**
         * The import to add (e.g., "kotlin.collections.List").
         */
        val import: String,
        override val isSafe: Boolean = true,
    ) : LintFix

    /**
     * Remove an import statement.
     */
    @Serializable
    public data class RemoveImport(
        override val description: String,
        /**
         * The file URI where the import should be removed.
         */
        val uri: String,
        /**
         * The import to remove.
         */
        val import: String,
        override val isSafe: Boolean = true,
    ) : LintFix

    /**
     * Rename a symbol across the codebase.
     */
    @Serializable
    public data class Rename(
        override val description: String,
        /**
         * The file URI containing the symbol.
         */
        val uri: String,
        /**
         * The range of the symbol to rename.
         */
        val range: TextRange,
        /**
         * The new name for the symbol.
         */
        val newName: String,
        override val isSafe: Boolean = false,
    ) : LintFix

    /**
     * A command to execute (for complex fixes that need IDE support).
     */
    @Serializable
    public data class Command(
        override val description: String,
        /**
         * The command identifier.
         */
        val commandId: String,
        /**
         * Arguments for the command.
         */
        val arguments: List<String> = emptyList(),
        override val isSafe: Boolean = false,
    ) : LintFix
}
