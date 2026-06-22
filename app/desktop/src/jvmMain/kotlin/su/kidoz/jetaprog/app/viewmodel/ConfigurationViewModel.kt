package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.configuration.CargoProfileType
import su.kidoz.jetaprog.configuration.ConfigurationEffect
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationManager
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.DockerComposeSettings
import su.kidoz.jetaprog.configuration.DockerRunSettings
import su.kidoz.jetaprog.configuration.DotNetConfigurationType
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.configuration.SpringBootDevServerSettings
import su.kidoz.jetaprog.configuration.SpringBootSettings
import su.kidoz.jetaprog.configuration.TomcatLocalSettings
import su.kidoz.jetaprog.configuration.TomcatRemoteSettings
import su.kidoz.jetaprog.configuration.discovery.ConfigurationDiscovery
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import java.io.File

/**
 * ViewModel for run configuration management.
 */
public class ConfigurationViewModel(
    private val configurationManager: ConfigurationManager,
    private val processExecutor: ProcessExecutor,
    private val gradleTaskRunner: GradleTaskRunner,
    private val configurationDiscovery: ConfigurationDiscovery,
) : MviViewModel<ConfigurationIntent, ConfigurationState, ConfigurationEffect>(ConfigurationState()) {
    private var projectPath: String = ""

    override suspend fun handleIntent(intent: ConfigurationIntent) {
        when (intent) {
            is ConfigurationIntent.Initialize -> initialize(intent.projectPath)
            is ConfigurationIntent.SelectConfiguration -> selectConfiguration(intent.id)
            is ConfigurationIntent.RunActive -> runActiveConfiguration()
            is ConfigurationIntent.Run -> runConfiguration(intent.id)
            is ConfigurationIntent.Stop -> stopConfiguration()
            is ConfigurationIntent.Create -> createConfiguration(intent.configuration)
            is ConfigurationIntent.Update -> updateConfiguration(intent.configuration)
            is ConfigurationIntent.Delete -> deleteConfiguration(intent.id)
            is ConfigurationIntent.Duplicate -> duplicateConfiguration(intent.id)
            is ConfigurationIntent.MakePermanent -> makePermanent(intent.id)
            is ConfigurationIntent.MoveToFolder -> moveToFolder(intent.id, intent.folderName)
            is ConfigurationIntent.OpenDialog -> openDialog()
            is ConfigurationIntent.EditConfiguration -> editConfiguration(intent.id)
            is ConfigurationIntent.CreateNew -> createNewConfiguration(intent.type)
            is ConfigurationIntent.CreateRecommended -> createRecommendedConfiguration()
            is ConfigurationIntent.CloseDialog -> closeDialog()
            is ConfigurationIntent.SaveFromDialog -> saveFromDialog(intent.configuration)
            is ConfigurationIntent.ClearError -> clearError()
            is ConfigurationIntent.DiscoverConfigurations -> discoverConfigurations(intent.projectPath)
        }
    }

    private suspend fun initialize(projectPath: String) {
        this.projectPath = projectPath
        // Clear existing state immediately so old project configs don't persist
        updateState { ConfigurationState() }
        configurationManager
            .initialize(projectPath)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }

                // Auto-discover configurations if none exist
                if (state.configurations.isEmpty()) {
                    discoverConfigurations(projectPath)
                }

                emitEffect(ConfigurationEffect.ConfigurationsLoaded(state.configurations.size))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to load configurations: ${error.message}"))
            }
    }

    private suspend fun selectConfiguration(id: ConfigurationId) {
        configurationManager
            .selectConfiguration(id)
            .onSuccess { state ->
                updateState {
                    copy(
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private suspend fun runActiveConfiguration() {
        val activeConfig = currentState.activeConfiguration ?: return
        runConfiguration(activeConfig.id)
    }

    private suspend fun runConfiguration(id: ConfigurationId) {
        val config = currentState.configurations.find { it.id == id } ?: return

        // Update to recent
        selectConfiguration(id)

        updateState {
            copy(
                runningConfigurationId = id,
                isRunning = true,
            )
        }

        emitEffect(ConfigurationEffect.ConfigurationStarted(config))

        // Execute based on configuration type
        val result = executeConfiguration(config)

        updateState {
            copy(
                runningConfigurationId = null,
                isRunning = false,
            )
        }

        emitEffect(
            ConfigurationEffect.ConfigurationFinished(
                configuration = config,
                success = result.isSuccess,
                exitCode = result.getOrNull() ?: -1,
            ),
        )
    }

    private suspend fun executeConfiguration(config: RunConfiguration): Result<Int> =
        when (val settings = config.settings) {
            is ConfigurationSettings.Gradle -> {
                executeGradle(settings)
            }

            is ConfigurationSettings.MesonBuild -> {
                executeMesonBuild(settings)
            }

            is ConfigurationSettings.MesonRun -> {
                executeMesonRun(settings)
            }

            is ConfigurationSettings.Python -> {
                executePython(settings)
            }

            is ConfigurationSettings.Poetry -> {
                executePoetry(settings)
            }

            is ConfigurationSettings.Uv -> {
                executeUv(settings)
            }

            is ConfigurationSettings.CargoBuild -> {
                executeCargoBuild(settings)
            }

            is ConfigurationSettings.CargoRun -> {
                executeCargoRun(settings)
            }

            is ConfigurationSettings.CargoTest -> {
                executeCargoTest(settings)
            }

            is ConfigurationSettings.CargoClippy -> {
                executeCargoClippy(settings)
            }

            is ConfigurationSettings.DotNetBuild -> {
                executeDotNetBuild(settings)
            }

            is ConfigurationSettings.DotNetRun -> {
                executeDotNetRun(settings)
            }

            is ConfigurationSettings.DotNetTest -> {
                executeDotNetTest(settings)
            }

            is ConfigurationSettings.Application -> {
                executeApplication(settings)
            }

            is ConfigurationSettings.ShellScript -> {
                executeShellScript(settings)
            }

            is ConfigurationSettings.Compound -> {
                executeCompound(settings)
            }

            // Server configurations - not yet implemented in this ViewModel
            is TomcatLocalSettings -> {
                Result.failure(UnsupportedOperationException("Tomcat execution not implemented"))
            }

            is TomcatRemoteSettings -> {
                Result.failure(UnsupportedOperationException("Tomcat execution not implemented"))
            }

            is SpringBootSettings -> {
                Result.failure(UnsupportedOperationException("Spring Boot execution not implemented"))
            }

            is SpringBootDevServerSettings -> {
                Result.failure(UnsupportedOperationException("Spring Boot execution not implemented"))
            }

            is DockerBuildSettings -> {
                Result.failure(UnsupportedOperationException("Docker execution not implemented"))
            }

            is DockerRunSettings -> {
                Result.failure(UnsupportedOperationException("Docker execution not implemented"))
            }

            is DockerComposeSettings -> {
                Result.failure(UnsupportedOperationException("Docker Compose execution not implemented"))
            }
        }

    private suspend fun executeGradle(settings: ConfigurationSettings.Gradle): Result<Int> {
        val args = settings.arguments + settings.jvmArguments.map { "-D$it" }
        val project = GradleProject(rootPath = projectPath)
        val result = gradleTaskRunner.runTask(project, settings.taskPath, args)

        return result.map { outputFlow ->
            var exitCode = 0
            outputFlow.collect { output ->
                when (output) {
                    is su.kidoz.jetaprog.build.gradle.GradleOutput.BuildFinished -> {
                        exitCode = output.exitCode
                    }

                    else -> { /* Ignore other outputs */ }
                }
            }
            exitCode
        }
    }

    private suspend fun executeMesonBuild(settings: ConfigurationSettings.MesonBuild): Result<Int> {
        val command =
            buildList {
                add("meson")
                add("compile")
                add("-C")
                add(settings.buildDirectory)
                settings.target?.let { add(it) }
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = projectPath,
            ).map { it.exitCode }
    }

    private suspend fun executeMesonRun(settings: ConfigurationSettings.MesonRun): Result<Int> {
        val executablePath = "$projectPath/${settings.buildDirectory}/${settings.executable}"

        val command =
            buildList {
                add(executablePath)
                addAll(settings.programArguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeApplication(settings: ConfigurationSettings.Application): Result<Int> {
        val command =
            buildList {
                add(settings.executablePath)
                addAll(settings.programArguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeShellScript(settings: ConfigurationSettings.ShellScript): Result<Int> {
        val command =
            if (settings.isFile) {
                buildList {
                    settings.interpreter?.let { add(it) } ?: add("/bin/sh")
                    add(settings.script)
                    addAll(settings.arguments)
                }
            } else {
                buildList {
                    settings.interpreter?.let { add(it) } ?: add("/bin/sh")
                    add("-c")
                    add(settings.script)
                }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCompound(settings: ConfigurationSettings.Compound): Result<Int> {
        if (settings.parallel) {
            // Run all configurations in parallel
            val results =
                settings.configurationIds.map { id ->
                    viewModelScope.launch { runConfiguration(id) }
                }
            results.forEach { it.join() }
        } else {
            // Run sequentially
            for (id in settings.configurationIds) {
                runConfiguration(id)
            }
        }
        return Result.success(0)
    }

    private suspend fun executePython(settings: ConfigurationSettings.Python): Result<Int> {
        val command =
            buildList {
                add(settings.pythonInterpreter)
                addAll(settings.interpreterArguments)
                if (settings.module != null) {
                    add("-m")
                    add(settings.module!!)
                } else {
                    add(settings.scriptPath)
                }
                addAll(settings.scriptArguments)
            }

        val environment = settings.environment.toMutableMap()
        if (settings.pythonPath.isNotEmpty()) {
            val pythonPath = settings.pythonPath.joinToString(System.getProperty("path.separator"))
            val existingPythonPath = environment["PYTHONPATH"]
            environment["PYTHONPATH"] =
                if (existingPythonPath != null) {
                    "$pythonPath${System.getProperty("path.separator")}$existingPythonPath"
                } else {
                    pythonPath
                }
        }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = environment,
            ).map { it.exitCode }
    }

    private suspend fun executePoetry(settings: ConfigurationSettings.Poetry): Result<Int> {
        val command =
            buildList {
                add("poetry")
                add(settings.command.value)
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeUv(settings: ConfigurationSettings.Uv): Result<Int> {
        val command =
            buildList {
                add("uv")
                // Handle compound commands like "pip install"
                settings.command.value
                    .split(" ")
                    .forEach { add(it) }
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoBuild(settings: ConfigurationSettings.CargoBuild): Result<Int> {
        val command =
            buildList {
                add("cargo")
                add("build")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.target?.let { add("--target=$it") }
                if (settings.features.isNotEmpty()) add("--features=${settings.features.joinToString(",")}")
                if (settings.allFeatures) add("--all-features")
                if (settings.noDefaultFeatures) add("--no-default-features")
                settings.package_?.let { add("--package=$it") }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoRun(settings: ConfigurationSettings.CargoRun): Result<Int> {
        val command =
            buildList {
                add("cargo")
                add("run")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.bin?.let { add("--bin=$it") }
                settings.example?.let { add("--example=$it") }
                if (settings.features.isNotEmpty()) add("--features=${settings.features.joinToString(",")}")
                if (settings.programArguments.isNotEmpty()) {
                    add("--")
                    addAll(settings.programArguments)
                }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoTest(settings: ConfigurationSettings.CargoTest): Result<Int> {
        val command =
            buildList {
                add("cargo")
                add("test")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.package_?.let { add("--package=$it") }
                if (settings.lib) add("--lib")
                if (settings.doc) add("--doc")
                settings.testName?.let { add(it) }
                if (settings.nocapture || settings.testArguments.isNotEmpty()) {
                    add("--")
                    if (settings.nocapture) add("--nocapture")
                    addAll(settings.testArguments)
                }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoClippy(settings: ConfigurationSettings.CargoClippy): Result<Int> {
        val command =
            buildList {
                add("cargo")
                add("clippy")
                if (settings.fix) add("--fix")
                settings.package_?.let { add("--package=$it") }
                if (settings.allTargets) add("--all-targets")
                if (settings.denyWarnings) {
                    add("--")
                    add("-D")
                    add("warnings")
                }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeDotNetBuild(settings: ConfigurationSettings.DotNetBuild): Result<Int> {
        val command =
            buildList {
                add("dotnet")
                add("build")
                settings.targetPath?.let { add(it) }
                add("--configuration")
                add(settings.configuration.value)
                if (settings.noRestore) add("--no-restore")
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeDotNetRun(settings: ConfigurationSettings.DotNetRun): Result<Int> {
        val command =
            buildList {
                add("dotnet")
                add("run")
                settings.projectPath?.let {
                    add("--project")
                    add(it)
                }
                add("--configuration")
                add(settings.configuration.value)
                if (settings.noRestore) add("--no-restore")
                if (settings.programArguments.isNotEmpty()) {
                    add("--")
                    addAll(settings.programArguments)
                }
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeDotNetTest(settings: ConfigurationSettings.DotNetTest): Result<Int> {
        val command =
            buildList {
                add("dotnet")
                add("test")
                settings.targetPath?.let { add(it) }
                add("--configuration")
                add(settings.configuration.value)
                settings.filter?.let {
                    add("--filter")
                    add(it)
                }
                if (settings.noBuild) add("--no-build")
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private fun stopConfiguration() {
        gradleTaskRunner.cancelTask()
        updateState {
            copy(
                runningConfigurationId = null,
                isRunning = false,
            )
        }
    }

    private suspend fun createConfiguration(configuration: RunConfiguration) {
        configurationManager
            .addConfiguration(configuration)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
                emitEffect(ConfigurationEffect.ConfigurationSaved(configuration))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to create configuration: ${error.message}"))
            }
    }

    private suspend fun updateConfiguration(configuration: RunConfiguration) {
        configurationManager
            .updateConfiguration(configuration)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
                emitEffect(ConfigurationEffect.ConfigurationSaved(configuration))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to update configuration: ${error.message}"))
            }
    }

    private suspend fun deleteConfiguration(id: ConfigurationId) {
        configurationManager
            .deleteConfiguration(id)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private suspend fun duplicateConfiguration(id: ConfigurationId) {
        configurationManager
            .duplicateConfiguration(id)
            .onSuccess { duplicated ->
                updateState {
                    copy(configurations = configurationManager.getState().configurations)
                }
                emitEffect(ConfigurationEffect.ConfigurationSaved(duplicated))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private suspend fun makePermanent(id: ConfigurationId) {
        configurationManager
            .makePermanent(id)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
            }
    }

    private suspend fun moveToFolder(
        id: ConfigurationId,
        folderName: String?,
    ) {
        configurationManager
            .moveToFolder(id, folderName)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
            }
    }

    private fun openDialog() {
        updateState { copy(isDialogOpen = true, editingConfiguration = null) }
    }

    private fun editConfiguration(id: ConfigurationId) {
        val config = currentState.configurations.find { it.id == id }
        updateState { copy(isDialogOpen = true, editingConfiguration = config) }
    }

    private fun createNewConfiguration(type: ConfigurationType) {
        val newConfig = createConfigurationForType(type, defaultNameForType(type))
        updateState { copy(isDialogOpen = true, editingConfiguration = newConfig) }
    }

    private fun createRecommendedConfiguration() {
        val recommendedType = detectRecommendedType() ?: ConfigurationType.APPLICATION
        val recommendedConfig = createConfigurationForType(recommendedType, recommendedNameForType(recommendedType))
        updateState { copy(isDialogOpen = true, editingConfiguration = recommendedConfig) }
    }

    private fun detectRecommendedType(): ConfigurationType? {
        if (projectPath.isBlank()) return null
        val root = File(projectPath)

        fun exists(name: String): Boolean = File(root, name).exists()

        val hasGradleSettings = exists("settings.gradle.kts") || exists("settings.gradle")
        val hasGradleBuild = exists("build.gradle.kts") || exists("build.gradle")
        if (hasGradleSettings || hasGradleBuild) {
            return ConfigurationType.GRADLE
        }
        if (exists("Cargo.toml")) return ConfigurationType.CARGO_RUN
        if (hasDotNetProject(root)) return ConfigurationType.DOTNET_RUN
        if (exists("meson.build")) return ConfigurationType.MESON_BUILD
        if (exists("uv.lock") || hasPyprojectSection(root, "tool.uv")) return ConfigurationType.UV
        if (exists("poetry.lock") || hasPyprojectSection(root, "tool.poetry")) return ConfigurationType.POETRY

        return null
    }

    private fun hasDotNetProject(root: File): Boolean =
        root
            .listFiles()
            ?.any { file ->
                file.isFile &&
                    (
                        file.name.endsWith(".sln") ||
                            file.name.endsWith(".slnx") ||
                            file.name.endsWith(".csproj") ||
                            file.name.endsWith(".fsproj") ||
                            file.name.endsWith(".vbproj")
                    )
            } ?: false

    private fun hasPyprojectSection(
        root: File,
        section: String,
    ): Boolean {
        val pyproject = File(root, "pyproject.toml")
        if (!pyproject.exists()) return false
        return runCatching { pyproject.readText().contains("[$section]") }.getOrDefault(false)
    }

    private fun createConfigurationForType(
        type: ConfigurationType,
        name: String,
    ): RunConfiguration =
        when (type) {
            ConfigurationType.GRADLE -> {
                configurationManager.createGradleConfiguration(
                    name = name,
                    taskPath = "build",
                )
            }

            ConfigurationType.MESON_BUILD -> {
                configurationManager.createMesonBuildConfiguration(
                    name = name,
                )
            }

            ConfigurationType.MESON_RUN -> {
                configurationManager.createMesonRunConfiguration(
                    name = name,
                    executable = "",
                )
            }

            ConfigurationType.APPLICATION -> {
                configurationManager.createApplicationConfiguration(
                    name = name,
                    executablePath = "",
                )
            }

            ConfigurationType.SHELL_SCRIPT -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.SHELL_SCRIPT,
                    settings = ConfigurationSettings.ShellScript(script = ""),
                )
            }

            ConfigurationType.COMPOUND -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.COMPOUND,
                    settings = ConfigurationSettings.Compound(configurationIds = emptyList()),
                )
            }

            ConfigurationType.PYTHON -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.PYTHON,
                    settings = ConfigurationSettings.Python(scriptPath = ""),
                )
            }

            ConfigurationType.POETRY -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.POETRY,
                    settings =
                        ConfigurationSettings.Poetry(
                            command = su.kidoz.jetaprog.configuration.PoetryCommand.RUN,
                        ),
                )
            }

            ConfigurationType.UV -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.UV,
                    settings = ConfigurationSettings.Uv(command = su.kidoz.jetaprog.configuration.UvCommand.RUN),
                )
            }

            ConfigurationType.CARGO_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_BUILD,
                    settings = ConfigurationSettings.CargoBuild(),
                )
            }

            ConfigurationType.CARGO_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_RUN,
                    settings = ConfigurationSettings.CargoRun(),
                )
            }

            ConfigurationType.CARGO_TEST -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_TEST,
                    settings = ConfigurationSettings.CargoTest(),
                )
            }

            ConfigurationType.CARGO_CLIPPY -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_CLIPPY,
                    settings = ConfigurationSettings.CargoClippy(),
                )
            }

            ConfigurationType.DOTNET_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_BUILD,
                    settings =
                        ConfigurationSettings.DotNetBuild(
                            targetPath = findDotNetTargetPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_RUN,
                    settings =
                        ConfigurationSettings.DotNetRun(
                            projectPath = findDotNetProjectPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_TEST -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_TEST,
                    settings =
                        ConfigurationSettings.DotNetTest(
                            targetPath = findDotNetTargetPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.TOMCAT_LOCAL -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.TOMCAT_LOCAL,
                    settings =
                        TomcatLocalSettings(
                            tomcatHome = "",
                            deploymentSource =
                                su.kidoz.jetaprog.configuration.DeploymentSource
                                    .WarFile(""),
                        ),
                )
            }

            ConfigurationType.TOMCAT_REMOTE -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.TOMCAT_REMOTE,
                    settings =
                        TomcatRemoteSettings(
                            host = "",
                            username = "",
                            password = "",
                            deploymentSource =
                                su.kidoz.jetaprog.configuration.DeploymentSource
                                    .WarFile(""),
                        ),
                )
            }

            ConfigurationType.SPRING_BOOT -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.SPRING_BOOT,
                    settings = SpringBootSettings(mainClass = ""),
                )
            }

            ConfigurationType.DOCKER_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_BUILD,
                    settings = DockerBuildSettings(imageName = ""),
                )
            }

            ConfigurationType.DOCKER_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_RUN,
                    settings = DockerRunSettings(imageName = ""),
                )
            }

            ConfigurationType.DOCKER_COMPOSE -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_COMPOSE,
                    settings = DockerComposeSettings(),
                )
            }
        }

    private fun defaultNameForType(type: ConfigurationType): String =
        when (type) {
            ConfigurationType.GRADLE -> "New Gradle Configuration"
            ConfigurationType.MESON_BUILD -> "New Meson Build"
            ConfigurationType.MESON_RUN -> "New Meson Run"
            ConfigurationType.APPLICATION -> "New Application"
            ConfigurationType.SHELL_SCRIPT -> "New Shell Script"
            ConfigurationType.COMPOUND -> "New Compound"
            ConfigurationType.PYTHON -> "New Python Script"
            ConfigurationType.POETRY -> "New Poetry Command"
            ConfigurationType.UV -> "New uv Command"
            ConfigurationType.CARGO_BUILD -> "New Cargo Build"
            ConfigurationType.CARGO_RUN -> "New Cargo Run"
            ConfigurationType.CARGO_TEST -> "New Cargo Test"
            ConfigurationType.CARGO_CLIPPY -> "New Cargo Clippy"
            ConfigurationType.DOTNET_BUILD -> "New .NET Build"
            ConfigurationType.DOTNET_RUN -> "New .NET Run"
            ConfigurationType.DOTNET_TEST -> "New .NET Test"
            ConfigurationType.TOMCAT_LOCAL -> "New Tomcat Local"
            ConfigurationType.TOMCAT_REMOTE -> "New Tomcat Remote"
            ConfigurationType.SPRING_BOOT -> "New Spring Boot"
            ConfigurationType.DOCKER_BUILD -> "New Docker Build"
            ConfigurationType.DOCKER_RUN -> "New Docker Run"
            ConfigurationType.DOCKER_COMPOSE -> "New Docker Compose"
        }

    private fun recommendedNameForType(type: ConfigurationType): String =
        when (type) {
            ConfigurationType.GRADLE -> "Gradle Build"
            ConfigurationType.MESON_BUILD -> "Meson Build"
            ConfigurationType.MESON_RUN -> "Meson Run"
            ConfigurationType.APPLICATION -> "Application"
            ConfigurationType.SHELL_SCRIPT -> "Shell Script"
            ConfigurationType.COMPOUND -> "Compound"
            ConfigurationType.PYTHON -> "Python"
            ConfigurationType.POETRY -> "Poetry Run"
            ConfigurationType.UV -> "uv Run"
            ConfigurationType.CARGO_BUILD -> "Cargo Build"
            ConfigurationType.CARGO_RUN -> "Cargo Run"
            ConfigurationType.CARGO_TEST -> "Cargo Test"
            ConfigurationType.CARGO_CLIPPY -> "Cargo Clippy"
            ConfigurationType.DOTNET_BUILD -> ".NET Build"
            ConfigurationType.DOTNET_RUN -> ".NET Run"
            ConfigurationType.DOTNET_TEST -> ".NET Test"
            ConfigurationType.TOMCAT_LOCAL -> "Tomcat Local"
            ConfigurationType.TOMCAT_REMOTE -> "Tomcat Remote"
            ConfigurationType.SPRING_BOOT -> "Spring Boot"
            ConfigurationType.DOCKER_BUILD -> "Docker Build"
            ConfigurationType.DOCKER_RUN -> "Docker Run"
            ConfigurationType.DOCKER_COMPOSE -> "Docker Compose"
        }

    private fun findDotNetTargetPath(): String? {
        if (projectPath.isBlank()) return null
        val root = File(projectPath)
        return root.findChildPath(".sln", ".slnx")
            ?: root.findChildPath(".csproj", ".fsproj", ".vbproj")
    }

    private fun findDotNetProjectPath(): String? {
        if (projectPath.isBlank()) return null
        return File(projectPath).findChildPath(".csproj", ".fsproj", ".vbproj")
    }

    private fun File.findChildPath(vararg extensions: String): String? =
        listFiles()
            ?.firstOrNull { file ->
                file.isFile && extensions.any { file.name.endsWith(it) }
            }?.path

    private fun closeDialog() {
        updateState { copy(isDialogOpen = false, editingConfiguration = null) }
    }

    private suspend fun saveFromDialog(configuration: RunConfiguration) {
        val isNew = currentState.configurations.none { it.id == configuration.id }
        if (isNew) {
            createConfiguration(configuration)
        } else {
            updateConfiguration(configuration)
        }
        closeDialog()
    }

    private fun clearError() {
        updateState { copy(error = null) }
    }

    private suspend fun discoverConfigurations(projectPath: String) {
        val existingNames = currentState.configurations.map { it.name }.toSet()

        // Use the configuration discovery service to detect project types and create configs
        val discoveredConfigs =
            configurationDiscovery.discoverConfigurations(
                projectPath = projectPath,
                existingNames = existingNames,
            )

        // Add all discovered configurations
        for (config in discoveredConfigs) {
            configurationManager.addConfiguration(config)
        }

        // Update state with new configurations
        val newState = configurationManager.getState()
        updateState {
            copy(
                configurations = newState.configurations,
                activeConfigurationId = newState.activeConfigurationId,
            )
        }

        if (discoveredConfigs.isNotEmpty()) {
            emitEffect(
                ConfigurationEffect.ShowSuccess(
                    "Discovered ${discoveredConfigs.size} configuration(s)",
                ),
            )
        }
    }

    /**
     * Get the output flow for a running configuration.
     * Returns null if no configuration is running.
     */
    public fun getRunningOutput(): Flow<su.kidoz.jetaprog.build.gradle.GradleOutput>? {
        // For now, only Gradle output is supported
        return null // Would need to track the flow from execution
    }
}
