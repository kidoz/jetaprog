package su.kidoz.jetaprog.project.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.project.ProjectDirectoryConstants
import su.kidoz.jetaprog.project.config.CodeStyleConfig
import su.kidoz.jetaprog.project.config.InspectionsConfig
import su.kidoz.jetaprog.project.config.ModulesConfig
import su.kidoz.jetaprog.project.config.ProjectConfig
import su.kidoz.jetaprog.project.config.RunConfigsFile
import su.kidoz.jetaprog.project.state.WorkspaceState

/**
 * Service for managing the `.jetaprog` project directory.
 *
 * Handles reading/writing project configuration, workspace state,
 * and managing the directory structure.
 */
public class ProjectDirectoryService(
    /**
     * The project root directory path.
     */
    private val projectRoot: String,
    /**
     * File system operations provider.
     */
    private val fileOps: FileOperations,
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Path to the .jetaprog directory.
     */
    public val projectDirectoryPath: String
        get() = "$projectRoot/${ProjectDirectoryConstants.DIRECTORY_NAME}"

    /**
     * Initialize the project directory structure.
     * Creates all necessary directories and default files if they don't exist.
     */
    public suspend fun initialize(): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                // Create main directory
                fileOps.createDirectory(projectDirectoryPath)

                // Create subdirectories
                listOf(
                    ProjectDirectoryConstants.SETTINGS_DIR,
                    ProjectDirectoryConstants.INDEX_DIR,
                    ProjectDirectoryConstants.STATE_DIR,
                    ProjectDirectoryConstants.CACHE_DIR,
                    ProjectDirectoryConstants.PLUGINS_DIR,
                    "${ProjectDirectoryConstants.CACHE_DIR}/${ProjectDirectoryConstants.SYNTAX_TOKENS_CACHE_DIR}",
                    "${ProjectDirectoryConstants.CACHE_DIR}/${ProjectDirectoryConstants.COMPLETIONS_CACHE_DIR}",
                    "${ProjectDirectoryConstants.CACHE_DIR}/${ProjectDirectoryConstants.DIAGNOSTICS_CACHE_DIR}",
                    "${ProjectDirectoryConstants.INDEX_DIR}/${ProjectDirectoryConstants.AST_CACHE_DIR}",
                    "${ProjectDirectoryConstants.SETTINGS_DIR}/${ProjectDirectoryConstants.DICTIONARIES_DIR}",
                ).forEach { subdir ->
                    fileOps.createDirectory("$projectDirectoryPath/$subdir")
                }

                // Create default project.json if it doesn't exist
                val projectConfigPath = "$projectDirectoryPath/${ProjectDirectoryConstants.PROJECT_CONFIG_FILE}"
                if (!fileOps.exists(projectConfigPath)) {
                    val projectName = projectRoot.substringAfterLast('/')
                    val defaultConfig = ProjectConfig.default(projectName)
                    saveProjectConfig(defaultConfig)
                }
            }
        }

    /**
     * Check if the project directory exists.
     */
    public suspend fun exists(): Boolean =
        withContext(Dispatchers.Default) {
            fileOps.exists(projectDirectoryPath)
        }

    /**
     * Check if the project has been initialized with a project.json.
     */
    public suspend fun isInitialized(): Boolean =
        withContext(Dispatchers.Default) {
            fileOps.exists("$projectDirectoryPath/${ProjectDirectoryConstants.PROJECT_CONFIG_FILE}")
        }

    // ========================================================================
    // Project Configuration
    // ========================================================================

    /**
     * Load the project configuration.
     */
    public suspend fun loadProjectConfig(): Result<ProjectConfig> =
        withContext(Dispatchers.Default) {
            loadJson("$projectDirectoryPath/${ProjectDirectoryConstants.PROJECT_CONFIG_FILE}")
        }

    /**
     * Save the project configuration.
     */
    public suspend fun saveProjectConfig(config: ProjectConfig): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson("$projectDirectoryPath/${ProjectDirectoryConstants.PROJECT_CONFIG_FILE}", config)
        }

    // ========================================================================
    // Modules Configuration
    // ========================================================================

    /**
     * Load the modules configuration.
     */
    public suspend fun loadModulesConfig(): Result<ModulesConfig> =
        withContext(Dispatchers.Default) {
            val path = "$projectDirectoryPath/${ProjectDirectoryConstants.MODULES_CONFIG_FILE}"
            if (!fileOps.exists(path)) {
                return@withContext Result.success(ModulesConfig())
            }
            loadJson(path)
        }

    /**
     * Save the modules configuration.
     */
    public suspend fun saveModulesConfig(config: ModulesConfig): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson("$projectDirectoryPath/${ProjectDirectoryConstants.MODULES_CONFIG_FILE}", config)
        }

    // ========================================================================
    // Workspace State (User-specific)
    // ========================================================================

    /**
     * Load the workspace state.
     */
    public suspend fun loadWorkspaceState(): Result<WorkspaceState> =
        withContext(Dispatchers.Default) {
            val path = "$projectDirectoryPath/${ProjectDirectoryConstants.WORKSPACE_STATE_FILE}"
            if (!fileOps.exists(path)) {
                return@withContext Result.success(WorkspaceState())
            }
            loadJson(path)
        }

    /**
     * Save the workspace state.
     */
    public suspend fun saveWorkspaceState(state: WorkspaceState): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson("$projectDirectoryPath/${ProjectDirectoryConstants.WORKSPACE_STATE_FILE}", state)
        }

    // ========================================================================
    // Settings
    // ========================================================================

    /**
     * Load code style settings.
     */
    public suspend fun loadCodeStyle(): Result<CodeStyleConfig> =
        withContext(Dispatchers.Default) {
            val path =
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.CODE_STYLE_FILE
            if (!fileOps.exists(path)) {
                return@withContext Result.success(CodeStyleConfig())
            }
            loadJson(path)
        }

    /**
     * Save code style settings.
     */
    public suspend fun saveCodeStyle(config: CodeStyleConfig): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson(
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.CODE_STYLE_FILE,
                config,
            )
        }

    /**
     * Load inspections settings.
     */
    public suspend fun loadInspections(): Result<InspectionsConfig> =
        withContext(Dispatchers.Default) {
            val path =
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.INSPECTIONS_FILE
            if (!fileOps.exists(path)) {
                return@withContext Result.success(InspectionsConfig())
            }
            loadJson(path)
        }

    /**
     * Save inspections settings.
     */
    public suspend fun saveInspections(config: InspectionsConfig): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson(
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.INSPECTIONS_FILE,
                config,
            )
        }

    /**
     * Load run configurations.
     */
    public suspend fun loadRunConfigs(): Result<RunConfigsFile> =
        withContext(Dispatchers.Default) {
            val path =
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.RUN_CONFIGS_FILE
            if (!fileOps.exists(path)) {
                return@withContext Result.success(RunConfigsFile())
            }
            loadJson(path)
        }

    /**
     * Save run configurations.
     */
    public suspend fun saveRunConfigs(config: RunConfigsFile): Result<Unit> =
        withContext(Dispatchers.Default) {
            saveJson(
                "$projectDirectoryPath/${ProjectDirectoryConstants.SETTINGS_DIR}/" +
                    ProjectDirectoryConstants.RUN_CONFIGS_FILE,
                config,
            )
        }

    // ========================================================================
    // Cache Management
    // ========================================================================

    /**
     * Clear all caches.
     */
    public suspend fun clearCache(): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val cachePath = "$projectDirectoryPath/${ProjectDirectoryConstants.CACHE_DIR}"
                fileOps.deleteRecursively(cachePath)
                fileOps.createDirectory(cachePath)
            }
        }

    /**
     * Clear the index.
     */
    public suspend fun clearIndex(): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val indexPath = "$projectDirectoryPath/${ProjectDirectoryConstants.INDEX_DIR}"
                fileOps.deleteRecursively(indexPath)
                fileOps.createDirectory(indexPath)
            }
        }

    /**
     * Get the path to the symbols database.
     */
    public fun getSymbolsDbPath(): String =
        "$projectDirectoryPath/${ProjectDirectoryConstants.INDEX_DIR}/${ProjectDirectoryConstants.SYMBOLS_DB_FILE}"

    /**
     * Get the path for plugin-specific data.
     */
    public fun getPluginDataPath(pluginId: String): String =
        "$projectDirectoryPath/${ProjectDirectoryConstants.PLUGINS_DIR}/$pluginId"

    // ========================================================================
    // Gitignore Generation
    // ========================================================================

    /**
     * Generate .gitignore entries for the project directory.
     */
    public fun generateGitignoreEntries(): String =
        ProjectDirectoryConstants.GITIGNORE_ENTRIES.joinToString("\n") { "/$it" }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private suspend inline fun <reified T> loadJson(path: String): Result<T> =
        runCatching {
            val content = fileOps.readText(path)
            json.decodeFromString<T>(content)
        }

    private suspend inline fun <reified T> saveJson(
        path: String,
        data: T,
    ): Result<Unit> =
        runCatching {
            val content = json.encodeToString(data)
            fileOps.writeText(path, content)
        }
}

/**
 * File operations interface for platform abstraction.
 */
public interface FileOperations {
    /**
     * Check if a file or directory exists.
     */
    public suspend fun exists(path: String): Boolean

    /**
     * Create a directory (and parents if needed).
     */
    public suspend fun createDirectory(path: String)

    /**
     * Read file content as text.
     */
    public suspend fun readText(path: String): String

    /**
     * Write text to a file.
     */
    public suspend fun writeText(
        path: String,
        content: String,
    )

    /**
     * Delete a file or directory recursively.
     */
    public suspend fun deleteRecursively(path: String)
}
