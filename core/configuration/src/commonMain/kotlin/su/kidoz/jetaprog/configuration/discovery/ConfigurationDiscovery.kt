package su.kidoz.jetaprog.configuration.discovery

import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.PoetryCommand
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.configuration.UvCommand

/**
 * Creates run configurations based on detected projects.
 */
public class ConfigurationDiscovery(
    private val projectDetector: ProjectDetector,
) {
    /**
     * Discover and create configurations for a project.
     *
     * @param projectPath The root path of the project.
     * @param existingNames Names of existing configurations to avoid duplicates.
     * @return List of recommended configurations to create.
     */
    public suspend fun discoverConfigurations(
        projectPath: String,
        existingNames: Set<String> = emptySet(),
    ): List<RunConfiguration> {
        val detectedProjects = projectDetector.detectProjects(projectPath)
        val configurations = mutableListOf<RunConfiguration>()

        for (project in detectedProjects) {
            val projectConfigs = createConfigurationsForProject(project, existingNames)
            configurations.addAll(projectConfigs)
        }

        return configurations
    }

    /**
     * Create configurations for a specific detected project.
     */
    public fun createConfigurationsForProject(
        project: DetectedProject,
        existingNames: Set<String> = emptySet(),
    ): List<RunConfiguration> =
        when (project.type) {
            ProjectType.GRADLE -> createGradleConfigurations(project, existingNames)

            ProjectType.CARGO -> createCargoConfigurations(project, existingNames)

            ProjectType.MESON -> createMesonConfigurations(project, existingNames)

            ProjectType.POETRY -> createPoetryConfigurations(project, existingNames)

            ProjectType.UV -> createUvConfigurations(project, existingNames)

            ProjectType.PYTHON_PYPROJECT,
            ProjectType.PYTHON_SETUP,
            -> createPythonConfigurations(project, existingNames)

            ProjectType.CMAKE -> emptyList()

            // CMake support TODO
            ProjectType.NODEJS -> emptyList()

            // Node.js support TODO
            ProjectType.GO -> emptyList()

            // Go support TODO
            ProjectType.UNKNOWN -> emptyList()
        }

    private fun createGradleConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Gradle"

        // Create build configuration
        val buildName = "$baseName Build"
        if (buildName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = buildName,
                    type = ConfigurationType.GRADLE,
                    isTemporary = false,
                    settings = ConfigurationSettings.Gradle(taskPath = "build"),
                ),
            )
        }

        // Create run configuration if application plugin is likely used
        val runName = "$baseName Run"
        if (runName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = runName,
                    type = ConfigurationType.GRADLE,
                    isTemporary = true,
                    settings = ConfigurationSettings.Gradle(taskPath = "run"),
                ),
            )
        }

        // Create test configuration
        val testName = "$baseName Test"
        if (testName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = testName,
                    type = ConfigurationType.GRADLE,
                    isTemporary = true,
                    settings = ConfigurationSettings.Gradle(taskPath = "test"),
                ),
            )
        }

        return configs
    }

    private fun createCargoConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Cargo"

        // Create cargo build configuration
        val buildName = "$baseName Build"
        if (buildName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = buildName,
                    type = ConfigurationType.CARGO_BUILD,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.CargoBuild(
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        // Create cargo run configuration if there's a binary target
        if (project.mainEntry != null) {
            val runName = "$baseName Run"
            if (runName !in existingNames) {
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = runName,
                        type = ConfigurationType.CARGO_RUN,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.CargoRun(
                                workingDirectory = project.rootPath,
                            ),
                    ),
                )
            }
        }

        // Create cargo test configuration
        val testName = "$baseName Test"
        if (testName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = testName,
                    type = ConfigurationType.CARGO_TEST,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.CargoTest(
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        // Create cargo clippy configuration
        val clippyName = "$baseName Clippy"
        if (clippyName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = clippyName,
                    type = ConfigurationType.CARGO_CLIPPY,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.CargoClippy(
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        return configs
    }

    private fun createMesonConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Meson"

        // Create meson build configuration
        val buildName = "$baseName Build"
        if (buildName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = buildName,
                    type = ConfigurationType.MESON_BUILD,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.MesonBuild(
                            buildDirectory = "builddir",
                        ),
                ),
            )
        }

        return configs
    }

    private fun createPoetryConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Poetry"

        // Create poetry run configuration
        val runName = "$baseName Run"
        if (runName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = runName,
                    type = ConfigurationType.POETRY,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.Poetry(
                            command = PoetryCommand.RUN,
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        // Create poetry install configuration
        val installName = "$baseName Install"
        if (installName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = installName,
                    type = ConfigurationType.POETRY,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.Poetry(
                            command = PoetryCommand.INSTALL,
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        return configs
    }

    private fun createUvConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "UV"

        // Create uv run configuration
        val runName = "$baseName Run"
        if (runName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = runName,
                    type = ConfigurationType.UV,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.Uv(
                            command = UvCommand.RUN,
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        // Create uv sync configuration
        val syncName = "$baseName Sync"
        if (syncName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = syncName,
                    type = ConfigurationType.UV,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.Uv(
                            command = UvCommand.SYNC,
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        return configs
    }

    private fun createPythonConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Python"

        // Create run configuration if main entry found
        if (project.mainEntry != null) {
            val runName = "$baseName Run"
            if (runName !in existingNames) {
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = runName,
                        type = ConfigurationType.PYTHON,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.Python(
                                scriptPath = project.mainEntry,
                                workingDirectory = project.rootPath,
                            ),
                    ),
                )
            }
        }

        return configs
    }
}
