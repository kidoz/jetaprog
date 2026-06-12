package su.kidoz.jetaprog.editor.state

import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Side effects emitted by the editor.
 */
public sealed interface EditorEffect : Effect {
    /**
     * Show a notification message.
     */
    public data class ShowNotification(
        val message: String,
        val type: NotificationType,
    ) : EditorEffect

    /**
     * Show an error message.
     */
    public data class ShowError(
        val message: String,
    ) : EditorEffect

    /**
     * Navigate to a position in a file.
     */
    public data class NavigateTo(
        val path: String,
        val position: TextPosition,
    ) : EditorEffect

    /**
     * File was saved successfully.
     */
    public data class FileSaved(
        val path: String,
    ) : EditorEffect

    /**
     * File was opened successfully.
     */
    public data class FileOpened(
        val path: String,
    ) : EditorEffect

    /**
     * File was closed.
     */
    public data class FileClosed(
        val path: String,
    ) : EditorEffect

    /**
     * Copy text to clipboard.
     */
    public data class CopyToClipboard(
        val text: String,
    ) : EditorEffect

    /**
     * Request paste from clipboard.
     */
    public data object RequestPaste : EditorEffect

    /**
     * Show a confirmation dialog.
     */
    public data class ShowConfirmation(
        val message: String,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
    ) : EditorEffect

    /**
     * Request focus on the editor.
     */
    public data object RequestFocus : EditorEffect

    /**
     * Completion was applied.
     */
    public data class CompletionApplied(
        val item: su.kidoz.jetaprog.common.completion.CompletionItem,
        val additionalEdits: List<su.kidoz.jetaprog.common.completion.TextEditData>,
    ) : EditorEffect

    /**
     * Formatting was applied.
     */
    public data class FormattingApplied(
        val originalLength: Int,
        val newLength: Int,
    ) : EditorEffect

    /**
     * Formatting failed.
     */
    public data class FormattingFailed(
        val reason: String,
    ) : EditorEffect

    /**
     * Hover information was loaded.
     */
    public data class HoverLoaded(
        val position: TextPosition,
    ) : EditorEffect

    /**
     * Signature help was loaded.
     */
    public data class SignatureHelpLoaded(
        val position: TextPosition,
    ) : EditorEffect

    /**
     * Show usages result.
     */
    public data class ShowUsages(
        val result: su.kidoz.jetaprog.editor.navigation.FindUsagesResult,
    ) : EditorEffect

    /**
     * Show symbol search dialog.
     */
    public data object ShowSymbolSearch : EditorEffect
}

/**
 * Types of notifications.
 */
public enum class NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}
