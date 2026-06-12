package su.kidoz.jetaprog.plugins.runtime.services

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.plugins.api.services.InputBoxOptions
import su.kidoz.jetaprog.plugins.api.services.NotificationService
import su.kidoz.jetaprog.plugins.api.services.Progress
import su.kidoz.jetaprog.plugins.api.services.QuickPickItem
import su.kidoz.jetaprog.plugins.api.services.QuickPickOptions

private val logger = KotlinLogging.logger {}

/**
 * Implementation of NotificationService for showing notifications.
 *
 * This is a placeholder implementation that logs messages.
 * In a full implementation, this would integrate with the UI.
 */
public class NotificationServiceImpl : NotificationService {
    override suspend fun showInformationMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.info { "[INFO] $message" }
        // In a full implementation, this would show a dialog and return the selected item
        return null
    }

    override suspend fun showWarningMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.warn { "[WARNING] $message" }
        return null
    }

    override suspend fun showErrorMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.error { "[ERROR] $message" }
        return null
    }

    override suspend fun <T> withProgress(
        title: String,
        cancellable: Boolean,
        task: suspend (Progress) -> T,
    ): T {
        logger.info { "[PROGRESS] Starting: $title" }

        val progress =
            object : Progress {
                override fun report(
                    message: String?,
                    increment: Int?,
                ) {
                    if (message != null) {
                        logger.info { "[PROGRESS] $title: $message" }
                    }
                }
            }

        return try {
            task(progress)
        } finally {
            logger.info { "[PROGRESS] Completed: $title" }
        }
    }

    override suspend fun showInputBox(options: InputBoxOptions): String? {
        logger.info { "[INPUT] ${options.prompt ?: options.title}" }
        // In a full implementation, this would show an input dialog
        return null
    }

    override suspend fun <T : QuickPickItem> showQuickPick(
        items: List<T>,
        options: QuickPickOptions,
    ): T? {
        logger.info { "[QUICKPICK] ${options.title ?: "Select item"}: ${items.map { it.label }}" }
        // In a full implementation, this would show a quick pick dialog
        return null
    }

    override suspend fun <T : QuickPickItem> showQuickPickMulti(
        items: List<T>,
        options: QuickPickOptions,
    ): List<T>? {
        logger.info { "[QUICKPICK_MULTI] ${options.title ?: "Select items"}: ${items.map { it.label }}" }
        // In a full implementation, this would show a multi-select dialog
        return null
    }

    override fun setStatusBarMessage(
        message: String,
        hideAfterMs: Long,
    ) {
        logger.info { "[STATUS] $message" }
        // In a full implementation, this would update the status bar
    }
}
