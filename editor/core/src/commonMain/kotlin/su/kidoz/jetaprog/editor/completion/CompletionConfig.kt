package su.kidoz.jetaprog.editor.completion

/**
 * Configuration for code completion behavior.
 */
public data class CompletionConfig(
    /**
     * Characters that automatically trigger completion.
     */
    val triggerCharacters: Set<Char> = DEFAULT_TRIGGER_CHARACTERS,
    /**
     * Minimum characters typed before auto-triggering completion on identifier.
     */
    val minTriggerLength: Int = 1,
    /**
     * Debounce delay in milliseconds for auto-trigger.
     */
    val debounceDelayMs: Long = 150,
    /**
     * Maximum number of items to display in the popup.
     */
    val maxDisplayItems: Int = 50,
    /**
     * Whether to show documentation in the completion popup.
     */
    val showDocumentation: Boolean = true,
    /**
     * Whether to auto-insert when there's only one matching item.
     */
    val autoInsertSingleItem: Boolean = false,
    /**
     * Whether completion is enabled.
     */
    val enabled: Boolean = true,
) {
    public companion object {
        /**
         * Default trigger characters for Kotlin.
         */
        public val DEFAULT_TRIGGER_CHARACTERS: Set<Char> =
            setOf(
                '.', // Member access
                ':', // Type annotation
                '@', // Annotations
            )
    }
}
