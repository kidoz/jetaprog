package su.kidoz.jetaprog.lint.config

/**
 * Storage interface for lint configuration.
 */
public interface LintConfigurationStorage {
    /**
     * Load the lint configuration for a project.
     *
     * @param projectPath The project root path.
     * @return The loaded configuration, or default if not found.
     */
    public suspend fun load(projectPath: String): Result<LintConfiguration>

    /**
     * Save the lint configuration for a project.
     *
     * @param projectPath The project root path.
     * @param configuration The configuration to save.
     */
    public suspend fun save(
        projectPath: String,
        configuration: LintConfiguration,
    ): Result<Unit>

    /**
     * Check if a configuration file exists.
     *
     * @param projectPath The project root path.
     * @return True if configuration exists.
     */
    public suspend fun exists(projectPath: String): Boolean

    /**
     * Delete the lint configuration file.
     *
     * @param projectPath The project root path.
     */
    public suspend fun delete(projectPath: String): Result<Unit>

    public companion object {
        /**
         * The configuration file name.
         */
        public const val CONFIG_FILE_NAME: String = "lint.json"

        /**
         * The configuration directory name within project.
         */
        public const val CONFIG_DIR_NAME: String = ".jetaprog"
    }
}
