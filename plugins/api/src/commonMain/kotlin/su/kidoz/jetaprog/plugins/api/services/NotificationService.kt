package su.kidoz.jetaprog.plugins.api.services

/**
 * Service for showing notifications to the user.
 */
public interface NotificationService {
    /**
     * Shows an information message.
     * @param message The message to show
     * @param items Optional action items
     * @return The selected item, or null if dismissed
     */
    public suspend fun showInformationMessage(
        message: String,
        vararg items: String,
    ): String?

    /**
     * Shows a warning message.
     * @param message The message to show
     * @param items Optional action items
     * @return The selected item, or null if dismissed
     */
    public suspend fun showWarningMessage(
        message: String,
        vararg items: String,
    ): String?

    /**
     * Shows an error message.
     * @param message The message to show
     * @param items Optional action items
     * @return The selected item, or null if dismissed
     */
    public suspend fun showErrorMessage(
        message: String,
        vararg items: String,
    ): String?

    /**
     * Shows a progress notification.
     * @param title The progress title
     * @param cancellable Whether the operation can be cancelled
     * @param task The task to execute with progress reporting
     * @return The result of the task
     */
    public suspend fun <T> withProgress(
        title: String,
        cancellable: Boolean = false,
        task: suspend (Progress) -> T,
    ): T

    /**
     * Shows an input box.
     * @param options Input box options
     * @return The entered value, or null if cancelled
     */
    public suspend fun showInputBox(options: InputBoxOptions = InputBoxOptions()): String?

    /**
     * Shows a quick pick selection.
     * @param items Items to choose from
     * @param options Quick pick options
     * @return The selected item, or null if cancelled
     */
    public suspend fun <T : QuickPickItem> showQuickPick(
        items: List<T>,
        options: QuickPickOptions = QuickPickOptions(),
    ): T?

    /**
     * Shows a quick pick with multiple selection.
     * @param items Items to choose from
     * @param options Quick pick options
     * @return The selected items, or null if cancelled
     */
    public suspend fun <T : QuickPickItem> showQuickPickMulti(
        items: List<T>,
        options: QuickPickOptions = QuickPickOptions(),
    ): List<T>?

    /**
     * Sets the status bar message.
     * @param message The message to show
     * @param hideAfterMs Time in milliseconds to show the message (0 for permanent)
     */
    public fun setStatusBarMessage(
        message: String,
        hideAfterMs: Long = 0,
    )
}

/**
 * Progress reporter.
 */
public interface Progress {
    /**
     * Reports progress.
     * @param message Optional message to display
     * @param increment Progress increment (0-100)
     */
    public fun report(
        message: String? = null,
        increment: Int? = null,
    )
}

/**
 * Options for input box.
 */
public data class InputBoxOptions(
    val title: String? = null,
    val prompt: String? = null,
    val value: String? = null,
    val placeHolder: String? = null,
    val password: Boolean = false,
    val validateInput: ((String) -> String?)? = null,
)

/**
 * An item in a quick pick.
 */
public interface QuickPickItem {
    public val label: String
    public val description: String?
    public val detail: String?
    public val picked: Boolean
}

/**
 * Simple quick pick item implementation.
 */
public data class SimpleQuickPickItem(
    override val label: String,
    override val description: String? = null,
    override val detail: String? = null,
    override val picked: Boolean = false,
) : QuickPickItem

/**
 * Options for quick pick.
 */
public data class QuickPickOptions(
    val title: String? = null,
    val placeHolder: String? = null,
    val matchOnDescription: Boolean = false,
    val matchOnDetail: Boolean = false,
    val canPickMany: Boolean = false,
)
