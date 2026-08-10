package su.kidoz.jetaprog.configuration.discovery

import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.GoCommand
import su.kidoz.jetaprog.configuration.JavaBuildTool
import su.kidoz.jetaprog.configuration.JavaCommand
import su.kidoz.jetaprog.configuration.NodePackageManager
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
            ProjectType.GRADLE -> {
                if (project.metadata[JAVA_PROJECT_METADATA_KEY] == "true") {
                    createJavaConfigurations(project, existingNames, JavaBuildTool.GRADLE)
                } else {
                    createGradleConfigurations(project, existingNames)
                }
            }

            ProjectType.MAVEN -> {
                createJavaConfigurations(project, existingNames, JavaBuildTool.MAVEN)
            }

            ProjectType.CARGO -> {
                createCargoConfigurations(project, existingNames)
            }

            ProjectType.MESON -> {
                createMesonConfigurations(project, existingNames)
            }

            ProjectType.POETRY -> {
                createPoetryConfigurations(project, existingNames)
            }

            ProjectType.UV -> {
                createUvConfigurations(project, existingNames)
            }

            ProjectType.DOTNET -> {
                createDotNetConfigurations(project, existingNames)
            }

            ProjectType.PYTHON_PYPROJECT,
            ProjectType.PYTHON_SETUP,
            -> {
                createPythonConfigurations(project, existingNames)
            }

            ProjectType.CMAKE -> {
                emptyList()
            }

            // CMake support TODO
            ProjectType.NODEJS -> {
                createNodeConfigurations(project, existingNames)
            }

            ProjectType.GO -> {
                createGoConfigurations(project, existingNames)
            }

            ProjectType.UNKNOWN -> {
                emptyList()
            }
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

    private fun createJavaConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
        buildTool: JavaBuildTool,
    ): List<RunConfiguration> {
        if (project.metadata[JAVA_PROJECT_METADATA_KEY] != "true") return emptyList()

        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Java"
        val executable = project.metadata[JAVA_EXECUTABLE_METADATA_KEY]
        val mainClass = project.metadata[JAVA_MAIN_CLASS_METADATA_KEY]
        val isRunnable = project.metadata[JAVA_RUNNABLE_METADATA_KEY] == "true"

        if (isRunnable) {
            addJavaConfiguration(
                target = configs,
                existingNames = existingNames,
                name = "$baseName Run",
                type = ConfigurationType.JAVA_RUN,
                command = JavaCommand.RUN,
                buildTool = buildTool,
                task = buildTool.runTask,
                executable = executable,
                mainClass = mainClass,
                workingDirectory = project.rootPath,
                isTemporary = false,
            )
            addJavaConfiguration(
                target = configs,
                existingNames = existingNames,
                name = "$baseName Debug",
                type = ConfigurationType.JAVA_DEBUG,
                command = JavaCommand.DEBUG,
                buildTool = buildTool,
                task = buildTool.runTask,
                executable = executable,
                mainClass = mainClass,
                workingDirectory = project.rootPath,
                isTemporary = true,
            )
        }

        addJavaConfiguration(
            target = configs,
            existingNames = existingNames,
            name = "$baseName Test",
            type = ConfigurationType.JAVA_TEST,
            command = JavaCommand.TEST,
            buildTool = buildTool,
            task = "test",
            executable = executable,
            mainClass = mainClass,
            workingDirectory = project.rootPath,
            isTemporary = true,
        )

        return configs
    }

    private fun addJavaConfiguration(
        target: MutableList<RunConfiguration>,
        existingNames: Set<String>,
        name: String,
        type: ConfigurationType,
        command: JavaCommand,
        buildTool: JavaBuildTool,
        task: String,
        executable: String?,
        mainClass: String?,
        workingDirectory: String,
        isTemporary: Boolean,
    ) {
        if (name in existingNames) return
        target.add(
            RunConfiguration(
                id = ConfigurationId.generate(),
                name = name,
                type = type,
                isTemporary = isTemporary,
                settings =
                    ConfigurationSettings.Java(
                        command = command,
                        buildTool = buildTool,
                        task = task,
                        executable = executable,
                        mainClass = mainClass,
                        workingDirectory = workingDirectory,
                    ),
            ),
        )
    }

    private val JavaBuildTool.runTask: String
        get() = if (this == JavaBuildTool.GRADLE) "run" else "compile exec:java"

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

    private fun createGoConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Go"

        val buildName = "$baseName Build"
        if (buildName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = buildName,
                    type = ConfigurationType.GO_BUILD,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.Go(
                            command = GoCommand.BUILD,
                            packagePattern = "./...",
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        if (project.mainEntry != null) {
            val runName = "$baseName Run"
            if (runName !in existingNames) {
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = runName,
                        type = ConfigurationType.GO_RUN,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.Go(
                                command = GoCommand.RUN,
                                workingDirectory = project.rootPath,
                            ),
                    ),
                )
            }
        }

        val testName = "$baseName Test"
        if (testName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = testName,
                    type = ConfigurationType.GO_TEST,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.Go(
                            command = GoCommand.TEST,
                            packagePattern = "./...",
                            arguments = listOf("-json"),
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        return configs
    }

    private fun createNodeConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: "Node.js"
        val packageManager =
            project.metadata[NODE_PACKAGE_MANAGER_METADATA_KEY]
                ?.let { executable -> NodePackageManager.entries.firstOrNull { it.executable == executable } }
                ?: NodePackageManager.NPM
        val scripts =
            project.metadata.keys
                .filter { it.startsWith(NODE_SCRIPT_METADATA_PREFIX) }
                .mapTo(mutableSetOf()) { it.removePrefix(NODE_SCRIPT_METADATA_PREFIX) }

        val runScript = listOf("start", "dev", "serve").firstOrNull { it in scripts }
        if (runScript != null) {
            addNodeConfiguration(
                target = configs,
                existingNames = existingNames,
                name = "$baseName Run",
                type = ConfigurationType.NODE_RUN,
                packageManager = packageManager,
                script = runScript,
                workingDirectory = project.rootPath,
                isTemporary = false,
            )
        }

        if ("build" in scripts) {
            addNodeConfiguration(
                target = configs,
                existingNames = existingNames,
                name = "$baseName Build",
                type = ConfigurationType.NODE_BUILD,
                packageManager = packageManager,
                script = "build",
                workingDirectory = project.rootPath,
                isTemporary = false,
            )
        }

        if ("test" in scripts) {
            addNodeConfiguration(
                target = configs,
                existingNames = existingNames,
                name = "$baseName Test",
                type = ConfigurationType.NODE_TEST,
                packageManager = packageManager,
                script = "test",
                workingDirectory = project.rootPath,
                isTemporary = true,
            )
        }

        return configs
    }

    private fun addNodeConfiguration(
        target: MutableList<RunConfiguration>,
        existingNames: Set<String>,
        name: String,
        type: ConfigurationType,
        packageManager: NodePackageManager,
        script: String,
        workingDirectory: String,
        isTemporary: Boolean,
    ) {
        if (name in existingNames) return
        target.add(
            RunConfiguration(
                id = ConfigurationId.generate(),
                name = name,
                type = type,
                isTemporary = isTemporary,
                settings =
                    ConfigurationSettings.Node(
                        packageManager = packageManager,
                        script = script,
                        workingDirectory = workingDirectory,
                    ),
            ),
        )
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

    private fun createDotNetConfigurations(
        project: DetectedProject,
        existingNames: Set<String>,
    ): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val baseName = project.projectName ?: ".NET"
        val targetPath = project.metadata[DOTNET_TARGET_PATH_METADATA_KEY] ?: project.detectionFile
        val testTargetPath = project.metadata[DOTNET_TEST_TARGET_PATH_METADATA_KEY] ?: targetPath
        val projectPath = project.metadata[DOTNET_PROJECT_PATH_METADATA_KEY] ?: project.mainEntry
        val isRunnable = project.metadata[DOTNET_RUNNABLE_METADATA_KEY] == "true"
        val targetFramework = project.metadata[DOTNET_TARGET_FRAMEWORK_METADATA_KEY]
        val assemblyName = project.metadata[DOTNET_ASSEMBLY_NAME_METADATA_KEY]

        val buildName = "$baseName Build"
        if (buildName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = buildName,
                    type = ConfigurationType.DOTNET_BUILD,
                    isTemporary = false,
                    settings =
                        ConfigurationSettings.DotNetBuild(
                            targetPath = targetPath,
                            workingDirectory = project.rootPath,
                        ),
                ),
            )
        }

        if (projectPath != null && isRunnable) {
            val runName = "$baseName Run"
            if (runName !in existingNames) {
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = runName,
                        type = ConfigurationType.DOTNET_RUN,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.DotNetRun(
                                projectPath = projectPath,
                                workingDirectory = project.rootPath,
                            ),
                    ),
                )
            }

            // One configuration per launchSettings.json profile (ASP.NET Core projects)
            val launchProfiles =
                project.metadata[DOTNET_LAUNCH_PROFILES_METADATA_KEY]
                    ?.split('\n')
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            for (profile in launchProfiles) {
                val profileName = "$baseName Run ($profile)"
                if (profileName in existingNames) continue
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = profileName,
                        type = ConfigurationType.DOTNET_RUN,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.DotNetRun(
                                projectPath = projectPath,
                                workingDirectory = project.rootPath,
                                launchProfile = profile,
                            ),
                    ),
                )
            }

            val debugName = "$baseName Debug"
            if (debugName !in existingNames) {
                configs.add(
                    RunConfiguration(
                        id = ConfigurationId.generate(),
                        name = debugName,
                        type = ConfigurationType.DOTNET_DEBUG,
                        isTemporary = false,
                        settings =
                            ConfigurationSettings.DotNetDebug(
                                projectPath = projectPath,
                                targetFramework = targetFramework,
                                assemblyName = assemblyName,
                                workingDirectory = project.rootPath,
                            ),
                    ),
                )
            }
        }

        val testName = "$baseName Test"
        if (testName !in existingNames) {
            configs.add(
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = testName,
                    type = ConfigurationType.DOTNET_TEST,
                    isTemporary = true,
                    settings =
                        ConfigurationSettings.DotNetTest(
                            targetPath = testTargetPath,
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
