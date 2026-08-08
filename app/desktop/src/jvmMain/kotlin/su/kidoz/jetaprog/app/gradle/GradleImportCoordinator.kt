package su.kidoz.jetaprog.app.gradle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionService
import su.kidoz.jetaprog.build.gradle.importer.ExistingModuleEntry
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.build.gradle.importer.GradleImportReport
import su.kidoz.jetaprog.build.gradle.importer.GradleModelReconciler
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/** Current state of the workspace Gradle model synchronization. */
public sealed interface GradleSyncState {
    /** No synchronization has started yet. */
    public data object Idle : GradleSyncState

    /** The Gradle model is currently being imported. */
    public data object Syncing : GradleSyncState

    /** The latest Gradle model was imported successfully. */
    public data class Synchronized(
        public val moduleCount: Int,
    ) : GradleSyncState

    /** The latest Gradle model import failed. */
    public data class Failed(
        public val message: String,
    ) : GradleSyncState
}

/**
 * Bridges the Gradle Tooling-API importer to the editor's `.jetaprog` metadata.
 *
 * Imports the live Gradle model and reconciles it against `.jetaprog/modules.json`
 * so stale or missing module metadata can be surfaced to the developer — the
 * editor-neutral replacement for hand-maintained module lists.
 *
 * @param projectPath the workspace root.
 * @param fileSystem used to read `.jetaprog/modules.json`.
 * @param executionService the project-scoped Gradle execution service.
 */
public class GradleImportCoordinator(
    private val projectPath: String,
    private val fileSystem: FileSystem,
    private val executionService: GradleExecutionService,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow<GradleSyncState>(GradleSyncState.Idle)

    /** Observable synchronization state for status and error reporting. */
    public val state: StateFlow<GradleSyncState> = mutableState.asStateFlow()

    /**
     * Imports the Gradle model only, without reconciliation.
     */
    public suspend fun importModel(): Result<GradleImportModel> {
        mutableState.value = GradleSyncState.Syncing
        return try {
            executionService.importModel(projectPath).also { result ->
                mutableState.value = result.toSyncState()
            }
        } catch (error: CancellationException) {
            mutableState.value = GradleSyncState.Idle
            throw error
        }
    }

    /**
     * Imports the Gradle model and reconciles it against recorded metadata.
     *
     * When `.jetaprog/modules.json` is absent, reconciliation runs against an
     * empty baseline so every discovered module is reported as missing.
     */
    public suspend fun reconcile(): Result<GradleImportReport> {
        val existing = readExistingModules()
        return importModel().map { model ->
            GradleImportReport(
                model = model,
                diff = GradleModelReconciler.reconcile(model, existing, setOf("buildSrc")),
            )
        }
    }

    private fun Result<GradleImportModel>.toSyncState(): GradleSyncState =
        fold(
            onSuccess = { model -> GradleSyncState.Synchronized(model.modules.size) },
            onFailure = { error -> GradleSyncState.Failed(error.message ?: "Gradle model import failed") },
        )

    private suspend fun readExistingModules(): List<ExistingModuleEntry> {
        val path = "$projectPath/.jetaprog/modules.json"
        if (!fileSystem.exists(path)) return emptyList()
        val content = fileSystem.readText(path).getOrNull() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ModulesFile.serializer(), content).modules.map { module ->
                ExistingModuleEntry(
                    path = module.path,
                    sourceRoots = module.sourceRoots,
                    testRoots = module.testRoots,
                )
            }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class ModulesFile(
        val modules: List<ModuleEntry> = emptyList(),
    )

    @Serializable
    private data class ModuleEntry(
        val path: String,
        @SerialName("sourceRoots") val sourceRoots: List<String> = emptyList(),
        @SerialName("testRoots") val testRoots: List<String> = emptyList(),
    )
}
