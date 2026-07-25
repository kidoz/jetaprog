package su.kidoz.jetaprog.app.ui.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import su.kidoz.jetaprog.app.command.CommandPaletteEffect
import su.kidoz.jetaprog.app.command.CommandPaletteViewModel
import su.kidoz.jetaprog.app.notification.NotificationCenter

/**
 * Hosts the command palette and reports what a command produced.
 *
 * Build commands can emit hundreds of lines, so only a short tail is shown as a
 * notification; the full output goes to the log from
 * [su.kidoz.jetaprog.app.command.CommandPaletteViewModel].
 */
@Composable
public fun CommandPaletteHost(
    viewModel: CommandPaletteViewModel,
    notificationCenter: NotificationCenter,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CommandPaletteEffect.CommandSucceeded -> {
                    if (looksLikeFailure(effect.output)) {
                        notificationCenter.error(
                            title = effect.command.displayName,
                            message = summarize(effect.output),
                        )
                    } else {
                        notificationCenter.success(
                            title = effect.command.displayName,
                            message = summarize(effect.output),
                        )
                    }
                }

                is CommandPaletteEffect.CommandFailed -> {
                    notificationCenter.error(
                        title = effect.command.displayName,
                        message = effect.message,
                    )
                }
            }
        }
    }

    CommandPalette(
        state = state,
        onIntent = viewModel::dispatch,
    )
}

/**
 * A command that completes normally can still report a failed build, so the output is
 * inspected for the markers the build integrations emit.
 */
internal fun looksLikeFailure(output: String): Boolean =
    output.lineSequence().any { line ->
        line.startsWith("[error]") ||
            line.startsWith("Command FAILED") ||
            line.startsWith("Build FAILED") ||
            line.startsWith("Command failed:")
    }

/**
 * Reduces command output to the last few meaningful lines.
 */
internal fun summarize(output: String): String? {
    val lines =
        output
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
    if (lines.isEmpty()) return null

    val errors = lines.filter { it.startsWith("[error]") }
    val interesting = errors.ifEmpty { lines }

    return interesting.takeLast(SUMMARY_LINES).joinToString("\n")
}

private const val SUMMARY_LINES = 5
