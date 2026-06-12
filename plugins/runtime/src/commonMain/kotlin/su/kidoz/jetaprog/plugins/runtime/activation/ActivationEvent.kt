package su.kidoz.jetaprog.plugins.runtime.activation

/**
 * Represents a typed activation event that triggers plugin activation.
 *
 * Activation events are parsed from string declarations in plugin manifests
 * (e.g., "onLanguage:kotlin", "onCommand:myPlugin.doSomething").
 */
public sealed interface ActivationEvent {
    /**
     * Activates the plugin when a file of the specified language is opened.
     *
     * Example: `onLanguage:kotlin` activates when a Kotlin file is opened.
     */
    public data class OnLanguage(
        /**
         * The language identifier (e.g., "kotlin", "python", "rust").
         */
        val languageId: String,
    ) : ActivationEvent

    /**
     * Activates the plugin when a specific command is invoked.
     *
     * The plugin should register a stub command that triggers activation,
     * then the real command handler takes over.
     *
     * Example: `onCommand:myPlugin.formatCode`
     */
    public data class OnCommand(
        /**
         * The command identifier.
         */
        val commandId: String,
    ) : ActivationEvent

    /**
     * Activates the plugin after IDE startup has finished.
     *
     * This is useful for plugins that need to run background tasks
     * but don't need to be activated immediately.
     */
    public data object OnStartupFinished : ActivationEvent

    /**
     * Activates the plugin when the workspace contains a file matching the glob pattern.
     *
     * Example: `workspaceContains:Cargo.toml` activates for Rust projects.
     */
    public data class WorkspaceContains(
        /**
         * The glob pattern to match against workspace files.
         */
        val glob: String,
    ) : ActivationEvent

    /**
     * Activates the plugin immediately at startup.
     *
     * Corresponds to the "*" activation event string.
     */
    public data object Always : ActivationEvent

    /**
     * Represents an unrecognized activation event string.
     *
     * Unknown events are preserved for forward compatibility but
     * will never trigger activation.
     */
    public data class Unknown(
        /**
         * The raw activation event string that couldn't be parsed.
         */
        val raw: String,
    ) : ActivationEvent

    public companion object {
        private const val PREFIX_ON_LANGUAGE = "onLanguage:"
        private const val PREFIX_ON_COMMAND = "onCommand:"
        private const val PREFIX_WORKSPACE_CONTAINS = "workspaceContains:"
        private const val EVENT_STARTUP_FINISHED = "onStartupFinished"
        private const val EVENT_ALWAYS = "*"

        /**
         * Parses an activation event string into a typed [ActivationEvent].
         *
         * Supported formats:
         * - `*` → [Always]
         * - `onStartupFinished` → [OnStartupFinished]
         * - `onLanguage:<languageId>` → [OnLanguage]
         * - `onCommand:<commandId>` → [OnCommand]
         * - `workspaceContains:<glob>` → [WorkspaceContains]
         * - anything else → [Unknown]
         *
         * @param event The activation event string to parse.
         * @return The parsed [ActivationEvent].
         */
        public fun parse(event: String): ActivationEvent =
            when {
                event == EVENT_ALWAYS -> {
                    Always
                }

                event == EVENT_STARTUP_FINISHED -> {
                    OnStartupFinished
                }

                event.startsWith(PREFIX_ON_LANGUAGE) -> {
                    OnLanguage(event.removePrefix(PREFIX_ON_LANGUAGE))
                }

                event.startsWith(PREFIX_ON_COMMAND) -> {
                    OnCommand(event.removePrefix(PREFIX_ON_COMMAND))
                }

                event.startsWith(PREFIX_WORKSPACE_CONTAINS) -> {
                    WorkspaceContains(
                        event.removePrefix(PREFIX_WORKSPACE_CONTAINS),
                    )
                }

                else -> {
                    Unknown(event)
                }
            }

        /**
         * Parses a list of activation event strings into typed [ActivationEvent]s.
         *
         * @param events The list of activation event strings.
         * @return List of parsed [ActivationEvent]s.
         */
        public fun parseAll(events: List<String>): List<ActivationEvent> = events.map { parse(it) }
    }
}
