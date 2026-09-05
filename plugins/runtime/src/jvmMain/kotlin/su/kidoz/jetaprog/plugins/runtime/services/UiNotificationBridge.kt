package su.kidoz.jetaprog.plugins.runtime.services

import su.kidoz.jetaprog.plugins.api.services.QuickPickItem

/**
 * Severity of a plugin message, mirrored here so the runtime does not depend
 * on the host application's notification model.
 */
public enum class UiMessageSeverity {
    INFO,
    WARNING,
    ERROR,
}

/**
 * Handle for a long-lived progress notification.
 */
public interface UiProgressHandle {
    /**
     * Updates the progress detail line.
     *
     * @param message The current step, or null to keep the previous one.
     * @param increment Progress increment (0-100), currently informational.
     */
    public fun report(
        message: String?,
        increment: Int? = null,
    )

    /** Closes the progress notification. */
    public fun close()
}

/**
 * Port the host application implements to surface plugin requests in the UI.
 *
 * The plugin runtime stays UI-free: [NotificationServiceImpl] delegates to
 * this bridge, and the application bridges it onto its notification center
 * and modal dialogs. All methods must be safe to call from any thread.
 */
public interface UiNotificationBridge {
    /**
     * Shows a message to the user.
     *
     * With [actions], the message stays until the user picks an action or
     * dismisses it, and the chosen label is returned. Without actions it is a
     * fire-and-forget toast and returns null immediately.
     */
    public suspend fun showMessage(
        severity: UiMessageSeverity,
        message: String,
        actions: List<String> = emptyList(),
    ): String?

    /**
     * Shows a transient status message.
     *
     * @param hideAfterMs Auto-hide delay; zero keeps it until replaced.
     */
    public fun showStatus(
        message: String,
        hideAfterMs: Long = 0,
    )

    /**
     * Opens a progress indicator that stays visible until [UiProgressHandle.close].
     */
    public fun beginProgress(title: String): UiProgressHandle

    /**
     * Asks the user for text input in a modal dialog.
     *
     * @return The entered value, or null when cancelled.
     */
    public suspend fun requestInput(
        title: String?,
        prompt: String?,
        value: String?,
        placeholder: String?,
        password: Boolean,
    ): String?

    /**
     * Asks the user to pick from [items] in a modal dialog.
     *
     * @param multiSelect When true, several items can be picked.
     * @return The picked items, or null when cancelled.
     */
    public suspend fun <T : QuickPickItem> requestQuickPick(
        items: List<T>,
        title: String?,
        placeholder: String?,
        multiSelect: Boolean,
    ): List<T>?
}
