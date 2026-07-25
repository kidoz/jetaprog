package su.kidoz.jetaprog.app.command

import androidx.compose.runtime.Immutable
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State

/**
 * User actions on the command palette.
 */
public sealed interface CommandPaletteIntent : Intent {
    /** Opens the palette and refreshes the command list. */
    public data object Show : CommandPaletteIntent

    /** Closes the palette. */
    public data object Hide : CommandPaletteIntent

    /** The search query changed. */
    public data class QueryChanged(
        val query: String,
    ) : CommandPaletteIntent

    /** Runs a command and closes the palette. */
    public data class Execute(
        val command: PaletteCommand,
    ) : CommandPaletteIntent
}

/**
 * Command palette state.
 *
 * @property isVisible Whether the palette is on screen.
 * @property query The current search query.
 * @property results Commands matching [query], ranked best first.
 * @property runningCommandId Id of the command currently executing, if any.
 */
@Immutable
public data class CommandPaletteState(
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<PaletteCommand> = emptyList(),
    val runningCommandId: String? = null,
) : State

/**
 * One-off command palette results.
 */
public sealed interface CommandPaletteEffect : Effect {
    /** A command finished successfully. */
    public data class CommandSucceeded(
        val command: PaletteCommand,
        val output: String,
    ) : CommandPaletteEffect

    /** A command failed or threw. */
    public data class CommandFailed(
        val command: PaletteCommand,
        val message: String,
    ) : CommandPaletteEffect
}
