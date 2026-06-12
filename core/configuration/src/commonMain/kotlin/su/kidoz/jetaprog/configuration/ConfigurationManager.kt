package su.kidoz.jetaprog.configuration

/**
 * Manager for run configuration operations.
 *
 * Provides high-level operations for managing configurations including
 * CRUD operations, validation, and auto-discovery.
 */
public class ConfigurationManager(
    private val storage: ConfigurationStorage,
) {
    private var projectPath: String = ""
    private var state: ConfigurationState = ConfigurationState()

    /**
     * Initialize the manager with a project path.
     */
    public suspend fun initialize(projectPath: String): Result<ConfigurationState> {
        this.projectPath = projectPath

        return storage
            .load(projectPath)
            .map { data ->
                state =
                    ConfigurationState(
                        configurations = data.configurations,
                        activeConfigurationId = data.activeConfigurationId,
                        recentConfigurationIds = data.recentConfigurationIds,
                    )
                state
            }.recoverCatching {
                // If no file exists, start with empty state
                state = ConfigurationState()
                state
            }
    }

    /**
     * Get current state.
     */
    public fun getState(): ConfigurationState = state

    /**
     * Add a new configuration.
     */
    public suspend fun addConfiguration(configuration: RunConfiguration): Result<ConfigurationState> {
        // Check for duplicate name
        if (state.configurations.any { it.name == configuration.name && it.id != configuration.id }) {
            return Result.failure(
                IllegalArgumentException("Configuration with name '${configuration.name}' already exists"),
            )
        }

        // If temporary, enforce limit
        val newConfigurations =
            if (configuration.isTemporary) {
                val temps = state.temporaryConfigurations
                if (temps.size >= ConfigurationState.MAX_TEMPORARY_CONFIGURATIONS) {
                    // Remove oldest temporary
                    val oldest = temps.last()
                    state.configurations.filter { it.id != oldest.id } + configuration
                } else {
                    state.configurations + configuration
                }
            } else {
                state.configurations + configuration
            }

        state =
            state.copy(
                configurations = newConfigurations,
                activeConfigurationId = configuration.id,
                recentConfigurationIds = updateRecent(configuration.id),
            )

        return saveState()
    }

    /**
     * Update an existing configuration.
     */
    public suspend fun updateConfiguration(configuration: RunConfiguration): Result<ConfigurationState> {
        val index = state.configurations.indexOfFirst { it.id == configuration.id }
        if (index < 0) {
            return Result.failure(IllegalArgumentException("Configuration not found: ${configuration.id}"))
        }

        // Check for duplicate name
        if (state.configurations.any { it.name == configuration.name && it.id != configuration.id }) {
            return Result.failure(
                IllegalArgumentException("Configuration with name '${configuration.name}' already exists"),
            )
        }

        val newConfigurations =
            state.configurations.toMutableList().apply {
                set(index, configuration)
            }

        state = state.copy(configurations = newConfigurations)
        return saveState()
    }

    /**
     * Delete a configuration.
     */
    public suspend fun deleteConfiguration(id: ConfigurationId): Result<ConfigurationState> {
        val newConfigurations = state.configurations.filter { it.id != id }

        val newActive =
            if (state.activeConfigurationId == id) {
                newConfigurations.firstOrNull()?.id
            } else {
                state.activeConfigurationId
            }

        state =
            state.copy(
                configurations = newConfigurations,
                activeConfigurationId = newActive,
                recentConfigurationIds = state.recentConfigurationIds.filter { it != id },
            )

        return saveState()
    }

    /**
     * Duplicate a configuration.
     */
    public suspend fun duplicateConfiguration(id: ConfigurationId): Result<RunConfiguration> {
        val original =
            state.configurations.find { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Configuration not found: $id"))

        val duplicate =
            original.copy(
                id = ConfigurationId.generate(),
                name = generateUniqueName(original.name),
                isTemporary = false,
            )

        return addConfiguration(duplicate).map { duplicate }
    }

    /**
     * Select a configuration as active.
     */
    public suspend fun selectConfiguration(id: ConfigurationId): Result<ConfigurationState> {
        if (state.configurations.none { it.id == id }) {
            return Result.failure(IllegalArgumentException("Configuration not found: $id"))
        }

        state =
            state.copy(
                activeConfigurationId = id,
                recentConfigurationIds = updateRecent(id),
            )

        return saveState()
    }

    /**
     * Make a temporary configuration permanent.
     */
    public suspend fun makePermanent(id: ConfigurationId): Result<ConfigurationState> {
        val config =
            state.configurations.find { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Configuration not found: $id"))

        if (!config.isTemporary) {
            return Result.success(state)
        }

        return updateConfiguration(config.copy(isTemporary = false))
    }

    /**
     * Move configuration to a folder.
     */
    public suspend fun moveToFolder(
        id: ConfigurationId,
        folderName: String?,
    ): Result<ConfigurationState> {
        val config =
            state.configurations.find { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Configuration not found: $id"))

        return updateConfiguration(config.copy(folderName = folderName))
    }

    /**
     * Get configuration by ID.
     */
    public fun getConfiguration(id: ConfigurationId): RunConfiguration? = state.configurations.find { it.id == id }

    /**
     * Get configurations by type.
     */
    public fun getConfigurationsByType(type: ConfigurationType): List<RunConfiguration> =
        state.configurations.filter { it.type == type }

    /**
     * Create a default Gradle configuration.
     */
    public fun createGradleConfiguration(
        name: String,
        taskPath: String,
        isTemporary: Boolean = false,
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId.generate(),
            name = name,
            type = ConfigurationType.GRADLE,
            isTemporary = isTemporary,
            settings = ConfigurationSettings.Gradle(taskPath = taskPath),
        )

    /**
     * Create a default Meson build configuration.
     */
    public fun createMesonBuildConfiguration(
        name: String,
        buildDirectory: String = "builddir",
        target: String? = null,
        isTemporary: Boolean = false,
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId.generate(),
            name = name,
            type = ConfigurationType.MESON_BUILD,
            isTemporary = isTemporary,
            settings =
                ConfigurationSettings.MesonBuild(
                    buildDirectory = buildDirectory,
                    target = target,
                ),
        )

    /**
     * Create a default Meson run configuration.
     */
    public fun createMesonRunConfiguration(
        name: String,
        executable: String,
        buildDirectory: String = "builddir",
        isTemporary: Boolean = false,
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId.generate(),
            name = name,
            type = ConfigurationType.MESON_RUN,
            isTemporary = isTemporary,
            settings =
                ConfigurationSettings.MesonRun(
                    buildDirectory = buildDirectory,
                    executable = executable,
                ),
        )

    /**
     * Create a default application configuration.
     */
    public fun createApplicationConfiguration(
        name: String,
        executablePath: String,
        isTemporary: Boolean = false,
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId.generate(),
            name = name,
            type = ConfigurationType.APPLICATION,
            isTemporary = isTemporary,
            settings = ConfigurationSettings.Application(executablePath = executablePath),
        )

    private suspend fun saveState(): Result<ConfigurationState> {
        val data =
            ConfigurationStorageData(
                configurations = state.configurations.filter { !it.isTemporary || it.storeAsProjectFile },
                activeConfigurationId = state.activeConfigurationId,
                recentConfigurationIds = state.recentConfigurationIds,
            )

        return storage.save(projectPath, data).map { state }
    }

    private fun updateRecent(id: ConfigurationId): List<ConfigurationId> {
        val recent = state.recentConfigurationIds.toMutableList()
        recent.remove(id)
        recent.add(0, id)
        return recent.take(ConfigurationState.MAX_RECENT_CONFIGURATIONS)
    }

    private fun generateUniqueName(baseName: String): String {
        val existingNames = state.configurations.map { it.name }.toSet()

        // Try "Name (1)", "Name (2)", etc.
        var counter = 1
        var newName = "$baseName ($counter)"
        while (newName in existingNames) {
            counter++
            newName = "$baseName ($counter)"
        }
        return newName
    }
}
