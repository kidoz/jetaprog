package su.kidoz.jetaprog.app.command

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.plugins.api.PluginManifest

private val logger = KotlinLogging.logger {}

/**
 * Drives the command palette.
 *
 * The catalog is rebuilt every time the palette opens rather than cached, because
 * plugins register and unregister commands as they activate.
 *
 * @param listCommandIds Ids currently registered with the command service.
 * @param listManifests Manifests of the installed plugins, used for titles and categories.
 * @param executeCommand Runs a command by id and returns whatever it produced.
 */
public class CommandPaletteViewModel(
    private val listCommandIds: () -> Collection<String>,
    private val listManifests: () -> Collection<PluginManifest>,
    private val executeCommand: suspend (String) -> Any?,
) : MviViewModel<CommandPaletteIntent, CommandPaletteState, CommandPaletteEffect>(CommandPaletteState()) {
    private var catalog: List<PaletteCommand> = emptyList()

    override suspend fun handleIntent(intent: CommandPaletteIntent) {
        when (intent) {
            is CommandPaletteIntent.Show -> show()
            is CommandPaletteIntent.Hide -> updateState { CommandPaletteState() }
            is CommandPaletteIntent.QueryChanged -> applyQuery(intent.query)
            is CommandPaletteIntent.Execute -> execute(intent.command)
        }
    }

    private fun show() {
        catalog = CommandCatalog.build(listCommandIds(), listManifests())
        logger.debug { "Command palette opened with ${catalog.size} commands" }
        updateState {
            CommandPaletteState(isVisible = true, query = "", results = catalog)
        }
    }

    private fun applyQuery(query: String) {
        updateState {
            copy(query = query, results = CommandCatalog.filter(catalog, query))
        }
    }

    private suspend fun execute(command: PaletteCommand) {
        // Close immediately: a build command can run for minutes and the palette
        // should not sit on top of the editor while it does.
        updateState { CommandPaletteState(runningCommandId = command.id) }

        val result =
            runCatching { executeCommand(command.id) }
                .onFailure { error ->
                    logger.warn(error) { "Command '${command.id}' failed" }
                }

        updateState { copy(runningCommandId = null) }

        result.fold(
            onSuccess = { value ->
                val output = value?.toString().orEmpty()
                logger.info { "Command '${command.id}' finished:\n$output" }
                emitEffect(CommandPaletteEffect.CommandSucceeded(command, output))
            },
            onFailure = { error ->
                emitEffect(
                    CommandPaletteEffect.CommandFailed(
                        command,
                        error.message ?: error::class.simpleName ?: "Unknown error",
                    ),
                )
            },
        )
    }
}
