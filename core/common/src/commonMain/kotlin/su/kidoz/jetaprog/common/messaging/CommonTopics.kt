package su.kidoz.jetaprog.common.messaging

/**
 * Listener for settings change events.
 */
public fun interface SettingsChangeListener {
    /**
     * Called when settings have been changed and applied.
     *
     * @param category The category of settings that changed, or null for bulk changes
     */
    public fun onSettingsChanged(category: String?)
}

/**
 * Listener for file-related events.
 */
public interface FileEventListener {
    /**
     * Called when a file is opened.
     */
    public fun onFileOpened(path: String) {}

    /**
     * Called when a file is closed.
     */
    public fun onFileClosed(path: String) {}

    /**
     * Called when a file is saved.
     */
    public fun onFileSaved(path: String) {}

    /**
     * Called when a file's content changes.
     */
    public fun onFileContentChanged(path: String) {}
}

/**
 * Listener for completion events.
 */
public interface CompletionEventListener {
    /**
     * Called when completion is triggered.
     */
    public fun onCompletionStarted(
        filePath: String,
        line: Int,
        column: Int,
    ) {}

    /**
     * Called when a completion item is selected and applied.
     */
    public fun onCompletionItemApplied(
        label: String,
        source: String,
    ) {}

    /**
     * Called when completion is dismissed without selection.
     */
    public fun onCompletionDismissed() {}
}

/**
 * Common topics used across the IDE.
 */
public object CommonTopics {
    /**
     * Topic for settings change notifications.
     * Broadcast TO_CHILDREN so project-level components receive app-level settings changes.
     */
    public val SETTINGS_CHANGED: Topic<SettingsChangeListener> =
        Topic.create("SettingsChanged", BroadcastDirection.TO_CHILDREN)

    /**
     * Topic for file events.
     * Broadcast TO_CHILDREN so project-level components receive file events.
     */
    public val FILE_EVENTS: Topic<FileEventListener> =
        Topic.create("FileEvents", BroadcastDirection.TO_CHILDREN)

    /**
     * Topic for completion events (statistics, learning).
     * No broadcast -- local to the bus where published.
     */
    public val COMPLETION_EVENTS: Topic<CompletionEventListener> =
        Topic.create("CompletionEvents", BroadcastDirection.NONE)
}
