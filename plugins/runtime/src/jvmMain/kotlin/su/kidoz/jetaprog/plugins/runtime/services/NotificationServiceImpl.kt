package su.kidoz.jetaprog.plugins.runtime.services

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.plugins.api.services.InputBoxOptions
import su.kidoz.jetaprog.plugins.api.services.NotificationService
import su.kidoz.jetaprog.plugins.api.services.Progress
import su.kidoz.jetaprog.plugins.api.services.QuickPickItem
import su.kidoz.jetaprog.plugins.api.services.QuickPickOptions

private val logger = KotlinLogging.logger {}

/**
 * Implementation of NotificationService that surfaces plugin messages in the
 * host UI through [bridge].
 *
 * Messages, progress, input boxes, quick picks, and status-bar messages all
 * render in the application (toasts and modal dialogs). Without a bridge —
 * headless or test setups — everything falls back to console logging and
 * dialog-style calls return null.
 *
 * @param bridge The host UI port, or null to log only.
 */
public class NotificationServiceImpl(
    private val bridge: UiNotificationBridge? = null,
) : NotificationService {
    override suspend fun showInformationMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.info { "[INFO] $message" }
        return bridge?.showMessage(UiMessageSeverity.INFO, message, items.toList())
    }

    override suspend fun showWarningMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.warn { "[WARNING] $message" }
        return bridge?.showMessage(UiMessageSeverity.WARNING, message, items.toList())
    }

    override suspend fun showErrorMessage(
        message: String,
        vararg items: String,
    ): String? {
        logger.error { "[ERROR] $message" }
        return bridge?.showMessage(UiMessageSeverity.ERROR, message, items.toList())
    }

    override suspend fun <T> withProgress(
        title: String,
        cancellable: Boolean,
        task: suspend (Progress) -> T,
    ): T {
        logger.info { "[PROGRESS] Starting: $title" }

        val handle = bridge?.beginProgress(title)
        val progress =
            object : Progress {
                override fun report(
                    message: String?,
                    increment: Int?,
                ) {
                    if (message != null) {
                        logger.info { "[PROGRESS] $title: $message" }
                    }
                    handle?.report(message, increment)
                }
            }

        return try {
            task(progress)
        } finally {
            handle?.close()
            logger.info { "[PROGRESS] Completed: $title" }
        }
    }

    override suspend fun showInputBox(options: InputBoxOptions): String? {
        logger.info { "[INPUT] ${options.prompt ?: options.title}" }
        return bridge?.requestInput(
            title = options.title,
            prompt = options.prompt,
            value = options.value,
            placeholder = options.placeHolder,
            password = options.password,
        )
    }

    override suspend fun <T : QuickPickItem> showQuickPick(
        items: List<T>,
        options: QuickPickOptions,
    ): T? {
        logger.info { "[QUICKPICK] ${options.title ?: "Select item"}: ${items.map { it.label }}" }
        val picked =
            bridge?.requestQuickPick(
                items = items,
                title = options.title,
                placeholder = options.placeHolder,
                multiSelect = false,
            )
        return picked?.firstOrNull()
    }

    override suspend fun <T : QuickPickItem> showQuickPickMulti(
        items: List<T>,
        options: QuickPickOptions,
    ): List<T>? {
        logger.info { "[QUICKPICK_MULTI] ${options.title ?: "Select items"}: ${items.map { it.label }}" }
        return bridge?.requestQuickPick(
            items = items,
            title = options.title,
            placeholder = options.placeHolder,
            multiSelect = true,
        )
    }

    override fun setStatusBarMessage(
        message: String,
        hideAfterMs: Long,
    ) {
        logger.info { "[STATUS] $message" }
        bridge?.showStatus(message, hideAfterMs)
    }
}
