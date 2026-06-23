package su.kidoz.jetaprog.app.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.build.gradle.importer.ExistingModuleEntry
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.build.gradle.importer.GradleImportReport
import su.kidoz.jetaprog.build.gradle.importer.GradleImportService
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Bridges the Gradle Tooling-API importer to the editor's `.jetaprog` metadata.
 *
 * Imports the live Gradle model and reconciles it against `.jetaprog/modules.json`
 * so stale or missing module metadata can be surfaced to the developer — the
 * editor-neutral replacement for hand-maintained module lists.
 *
 * @param projectPath the workspace root.
 * @param fileSystem used to read `.jetaprog/modules.json`.
 * @param service the underlying import + reconcile service.
 */
public class GradleImportCoordinator(
    private val projectPath: String,
    private val fileSystem: FileSystem,
    private val service: GradleImportService = GradleImportService(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Imports the Gradle model only, without reconciliation.
     */
    public suspend fun importModel(): Result<GradleImportModel> = service.importModel(projectPath)

    /**
     * Imports the Gradle model and reconciles it against recorded metadata.
     *
     * When `.jetaprog/modules.json` is absent, reconciliation runs against an
     * empty baseline so every discovered module is reported as missing.
     */
    public suspend fun reconcile(): Result<GradleImportReport> {
        val existing = readExistingModules()
        return service.importAndReconcile(projectPath, existing)
    }

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
