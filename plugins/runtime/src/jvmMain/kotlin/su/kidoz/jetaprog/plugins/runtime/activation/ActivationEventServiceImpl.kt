package su.kidoz.jetaprog.plugins.runtime.activation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService

private val logger = KotlinLogging.logger {}

/**
 * JVM implementation of [ActivationEventService].
 *
 * This service emits activation triggers that are observed by [LazyPluginActivator]
 * to activate plugins when their activation events match.
 */
public class ActivationEventServiceImpl(
    private val workspaceService: WorkspaceService,
) : ActivationEventService {
    private val _triggers = MutableSharedFlow<ActivationTrigger>(extraBufferCapacity = 64)

    override val triggers: Flow<ActivationTrigger> = _triggers.asSharedFlow()

    override suspend fun fireLanguageOpened(languageId: String) {
        logger.debug { "Firing activation trigger: LanguageOpened($languageId)" }
        _triggers.emit(ActivationTrigger.LanguageOpened(languageId))
    }

    override suspend fun fireCommandInvoked(commandId: String) {
        logger.debug { "Firing activation trigger: CommandInvoked($commandId)" }
        _triggers.emit(ActivationTrigger.CommandInvoked(commandId))
    }

    override suspend fun fireStartupFinished() {
        logger.debug { "Firing activation trigger: StartupFinished" }
        _triggers.emit(ActivationTrigger.StartupFinished)
    }

    override suspend fun checkWorkspaceContains(glob: String): Boolean {
        val rootPath = workspaceService.rootPath
        if (rootPath == null) {
            logger.debug { "No workspace root, workspaceContains check returns false for: $glob" }
            return false
        }

        val matchingFiles = workspaceService.findFiles(glob, maxResults = 1)
        val matches = matchingFiles.isNotEmpty()
        logger.debug { "workspaceContains check for '$glob': $matches" }
        return matches
    }
}
