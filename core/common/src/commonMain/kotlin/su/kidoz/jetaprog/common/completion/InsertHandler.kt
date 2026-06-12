package su.kidoz.jetaprog.common.completion

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Handles post-insertion behavior when a completion item is selected.
 *
 * Implementations can perform custom actions like:
 * - Adding import statements
 * - Inserting parentheses and positioning the cursor
 * - Applying additional text edits
 * - Triggering signature help
 *
 * Inspired by IntelliJ's `InsertHandler<LookupElement>`.
 */
public fun interface InsertHandler {
    /**
     * Called after the completion item's text has been inserted into the document.
     *
     * @param context The insertion context with editor/document access
     * @param item The completion item that was selected
     */
    public fun handleInsert(
        context: InsertionContext,
        item: CompletionItem,
    )
}

/**
 * Context provided to [InsertHandler] implementations during completion insertion.
 *
 * Provides access to the editor state and document, allowing handlers
 * to perform additional modifications after the initial text insertion.
 */
public data class InsertionContext(
    /**
     * The file path of the document being edited.
     */
    val filePath: String,
    /**
     * The full document content after the initial insertion.
     */
    val documentContent: String,
    /**
     * The start offset of the inserted text.
     */
    val startOffset: Int,
    /**
     * The tail offset (end of inserted text).
     */
    val tailOffset: Int,
    /**
     * The character used to accept the completion (e.g., Enter, Tab, '.').
     */
    val completionChar: Char?,
    /**
     * The cursor position after insertion.
     */
    val cursorPosition: TextPosition,
    /**
     * The language ID of the document.
     */
    val languageId: String,
    /**
     * Callback to apply additional text edits to the document.
     * Returns the new cursor position after the edits.
     */
    val applyEdits: (List<TextEditData>) -> TextPosition,
    /**
     * Callback to move the cursor to a new position.
     */
    val moveCursor: (TextPosition) -> Unit,
    /**
     * Callback to insert text at a specific position.
     */
    val insertTextAt: (TextPosition, String) -> Unit,
    /**
     * Callback to schedule a post-insertion action (e.g., trigger signature help).
     */
    val scheduleLater: (() -> Unit) -> Unit,
)

/**
 * An [InsertHandler] that adds import statements when a completion is applied.
 *
 * @param importStatement The full import statement to add (e.g., "import kotlin.collections.List")
 */
public class AddImportInsertHandler(
    private val importStatement: String,
) : InsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: CompletionItem,
    ) {
        val content = context.documentContent
        val lines = content.lines()

        // Check if import already exists
        if (lines.any { it.trim() == importStatement }) return

        // Find the position to insert the import
        val insertLine = findImportInsertPosition(lines)
        val importText = "$importStatement\n"
        context.insertTextAt(TextPosition(insertLine, 0), importText)
    }

    private fun findImportInsertPosition(lines: List<String>): Int {
        // Find the last import line, or after the package declaration
        var lastImportLine = -1
        var packageLine = -1

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                packageLine = index
            }
            if (trimmed.startsWith("import ")) {
                lastImportLine = index
            }
        }

        return when {
            lastImportLine >= 0 -> lastImportLine + 1

            packageLine >= 0 -> packageLine + 2

            // blank line after package
            else -> 0
        }
    }
}

/**
 * An [InsertHandler] that adds parentheses after function/method completion
 * and positions the cursor inside them.
 *
 * @param hasParameters Whether the function has parameters (cursor goes inside parens)
 */
public class ParenthesesInsertHandler(
    private val hasParameters: Boolean = true,
) : InsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: CompletionItem,
    ) {
        val pos = context.cursorPosition
        if (hasParameters) {
            context.insertTextAt(pos, "()")
            // Position cursor between parentheses
            context.moveCursor(TextPosition(pos.line, pos.column + 1))
        } else {
            context.insertTextAt(pos, "()")
            // Position cursor after closing paren
            context.moveCursor(TextPosition(pos.line, pos.column + 2))
        }
    }
}

/**
 * An [InsertHandler] that chains multiple handlers in sequence.
 *
 * @param handlers The handlers to chain
 */
public class CompositeInsertHandler(
    private val handlers: List<InsertHandler>,
) : InsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: CompletionItem,
    ) {
        for (handler in handlers) {
            handler.handleInsert(context, item)
        }
    }
}

/**
 * Registry of insert handlers keyed by completion item kind.
 *
 * Allows registering default insert handlers for specific kinds
 * (e.g., always add parentheses after function completion).
 */
public class InsertHandlerRegistry {
    private val handlers = mutableMapOf<CompletionItemKind, InsertHandler>()
    private val customHandlers = mutableMapOf<String, InsertHandler>()

    /**
     * Registers a default insert handler for a completion item kind.
     */
    public fun registerForKind(
        kind: CompletionItemKind,
        handler: InsertHandler,
    ) {
        handlers[kind] = handler
    }

    /**
     * Registers a custom insert handler for a specific item label pattern.
     */
    public fun registerCustom(
        labelPattern: String,
        handler: InsertHandler,
    ) {
        customHandlers[labelPattern] = handler
    }

    /**
     * Finds the appropriate insert handler for a completion item.
     *
     * @return The handler, or null if no handler is registered
     */
    public fun findHandler(item: CompletionItem): InsertHandler? {
        // Check custom handlers first (exact match on label)
        customHandlers[item.label]?.let { return it }

        // Fall back to kind-based handler
        return handlers[item.kind]
    }
}
