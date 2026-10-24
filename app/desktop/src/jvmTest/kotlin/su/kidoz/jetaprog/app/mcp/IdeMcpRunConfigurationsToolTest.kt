package su.kidoz.jetaprog.app.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.viewmodel.ConfigurationViewModel
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DeploymentSource
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.configuration.RunOutputLine
import su.kidoz.jetaprog.configuration.RunOutputType
import su.kidoz.jetaprog.configuration.TomcatRemoteSettings
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The run configuration tool lists saved configurations and selects the active one.
 * It does not start a program.
 */
class IdeMcpRunConfigurationsToolTest {
    @Test
    fun listsTargetsAndHidesSecrets() {
        val report =
            formatRunConfigurations(
                ConfigurationState(
                    configurations =
                        listOf(
                            RunConfiguration(
                                id = ConfigurationId("desktop"),
                                name = "Desktop",
                                type = ConfigurationType.GRADLE,
                                settings =
                                    ConfigurationSettings.Gradle(
                                        taskPath = ":app:desktop:run",
                                        arguments = listOf("--password", "arg-secret"),
                                        environment = mapOf("API_KEY" to "env-secret"),
                                    ),
                            ),
                            RunConfiguration(
                                id = ConfigurationId("shell"),
                                name = "Script",
                                type = ConfigurationType.SHELL_SCRIPT,
                                isTemporary = true,
                                settings =
                                    ConfigurationSettings.ShellScript(
                                        script = "echo inline-secret",
                                        isFile = false,
                                    ),
                            ),
                            RunConfiguration(
                                id = ConfigurationId("tomcat"),
                                name = "Server",
                                type = ConfigurationType.TOMCAT_REMOTE,
                                settings =
                                    TomcatRemoteSettings(
                                        host = "example.test",
                                        username = "deploy-user",
                                        password = "tomcat-secret",
                                        deploymentSource = DeploymentSource.WarFile("app.war"),
                                        contextPath = "/app",
                                    ),
                            ),
                            RunConfiguration(
                                id = ConfigurationId("image"),
                                name = "Image",
                                type = ConfigurationType.DOCKER_BUILD,
                                settings =
                                    DockerBuildSettings(
                                        imageName = "app",
                                        imageTag = "1.2",
                                        buildArgs = mapOf("TOKEN" to "build-secret"),
                                    ),
                            ),
                        ),
                    activeConfigurationId = ConfigurationId("desktop"),
                    runningConfigurationId = ConfigurationId("shell"),
                    executionOutput = listOf(RunOutputLine("streamed-secret", RunOutputType.INFO)),
                ),
            )

        assertEquals(
            listOf(
                "active: desktop",
                "running: shell",
                "desktop\tDesktop\tGradle\t:app:desktop:run\tactive",
                "shell\tScript\tShell Script\tinline script\trunning temporary",
                "tomcat\tServer\tTomcat Remote\texample.test:8080/app",
                "image\tImage\tDocker Build\tapp:1.2",
            ).joinToString("\n"),
            report,
        )
        listOf(
            "arg-secret",
            "env-secret",
            "inline-secret",
            "tomcat-secret",
            "build-secret",
            "streamed-secret",
            "deploy-user",
        ).forEach { secret -> assertFalse(report.contains(secret), secret) }
    }

    @Test
    fun includesTheStoredError() {
        val report =
            formatRunConfigurations(
                ConfigurationState(
                    configurations =
                        listOf(
                            RunConfiguration(
                                id = ConfigurationId("app"),
                                name = "App",
                                type = ConfigurationType.APPLICATION,
                                settings = ConfigurationSettings.Application("bin/app"),
                            ),
                        ),
                    error = "disk\tfull",
                ),
            )

        assertEquals(
            "active: none\nrunning: none\nerror: disk full\napp\tApp\tApplication\tbin/app",
            report,
        )
    }

    @Test
    fun capsTheList() {
        val configurations = (1..201).map { index -> application("c$index") }
        val lines = formatRunConfigurations(ConfigurationState(configurations = configurations)).lines()

        assertEquals(202, lines.size)
        assertTrue(lines.any { it.startsWith("c200\t") })
        assertTrue(lines.none { it.startsWith("c201\t") })
    }

    @Test
    fun listsTheOpenConfigurations() =
        runTest {
            val state =
                ConfigurationState(
                    configurations = listOf(gradle("desktop", "Desktop", ":app:desktop:run")),
                    activeConfigurationId = ConfigurationId("desktop"),
                )
            val result = server(session(configurations(state))).tools.execute("run_configurations", buildJsonObject {})

            assertEquals(
                text("active: desktop\nrunning: none\ndesktop\tDesktop\tGradle\t:app:desktop:run\tactive"),
                result,
            )
        }

    @Test
    fun selectsAnExistingConfiguration() =
        runTest {
            val initial =
                ConfigurationState(
                    configurations =
                        listOf(
                            gradle("desktop", "Desktop", ":app:desktop:run"),
                            gradle("tests", "Tests", ":app:desktop:jvmTest"),
                        ),
                    activeConfigurationId = ConfigurationId("desktop"),
                )
            val selected = initial.copy(activeConfigurationId = ConfigurationId("tests"))
            val configurations = configurations(initial)
            coEvery { configurations.perform(any()) } returns selected

            val result =
                server(session(configurations)).tools.execute(
                    "run_configurations",
                    buildJsonObject {
                        put("action", "select")
                        put("id", "tests")
                    },
                )

            assertEquals(
                text(
                    "requested: select\nactive: tests\nrunning: none\n" +
                        "desktop\tDesktop\tGradle\t:app:desktop:run\n" +
                        "tests\tTests\tGradle\t:app:desktop:jvmTest\tactive",
                ),
                result,
            )
            coVerify(exactly = 1) {
                configurations.perform(ConfigurationIntent.SelectConfiguration(ConfigurationId("tests")))
            }
        }

    @Test
    fun rejectsAnUnknownIdWithoutSelecting() =
        runTest {
            val configurations =
                configurations(ConfigurationState(configurations = listOf(gradle("desktop", "Desktop", "run"))))
            val result =
                server(session(configurations)).tools.execute(
                    "run_configurations",
                    buildJsonObject {
                        put("action", "select")
                        put("id", "missing")
                    },
                )

            assertEquals(ToolResult.Error("No run configuration has id missing."), result)
            coVerify(exactly = 0) { configurations.perform(any()) }
        }

    @Test
    fun rejectsRunAndLeavesTheSelection() =
        runTest {
            val configurations =
                configurations(ConfigurationState(configurations = listOf(gradle("desktop", "Desktop", "run"))))
            val tools = server(session(configurations)).tools

            listOf("run", "debug", "stop").forEach { action ->
                val result = tools.execute("run_configurations", buildJsonObject { put("action", action) })
                assertEquals(ToolResult.Error("Unknown run configuration action: $action"), result)
            }
            coVerify(exactly = 0) { configurations.perform(any()) }
        }

    @Test
    fun needsAConfigurationId() =
        runTest {
            val configurations =
                configurations(ConfigurationState(configurations = listOf(gradle("desktop", "Desktop", "run"))))
            val result =
                server(session(configurations)).tools.execute(
                    "run_configurations",
                    buildJsonObject {
                        put("action", "select")
                        put("id", "  ")
                    },
                )

            assertEquals(ToolResult.Error("Pass a configuration id."), result)
            coVerify(exactly = 0) { configurations.perform(any()) }
        }

    @Test
    fun reportsWhenNoneExist() =
        runTest {
            val result =
                server(session(configurations(ConfigurationState()))).tools.execute(
                    "run_configurations",
                    buildJsonObject { put("action", "list") },
                )

            assertEquals(text("No run configurations."), result)
        }

    @Test
    fun needsAnOpenProject() =
        runTest {
            val result = server(null).tools.execute("run_configurations", buildJsonObject {})
            assertEquals(text("No project is open."), result)
        }

    private fun gradle(
        id: String,
        name: String,
        task: String,
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId(id),
            name = name,
            type = ConfigurationType.GRADLE,
            settings = ConfigurationSettings.Gradle(taskPath = task),
        )

    private fun application(id: String): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId(id),
            name = id,
            type = ConfigurationType.APPLICATION,
            settings = ConfigurationSettings.Application(id),
        )

    private fun configurations(state: ConfigurationState): ConfigurationViewModel {
        val configurations = mockk<ConfigurationViewModel>()
        every { configurations.state } returns MutableStateFlow(state)
        return configurations
    }

    private fun session(configurations: ConfigurationViewModel): ProjectSession {
        val project = mockk<ProjectSession>()
        every { project.configurationViewModel } returns configurations
        return project
    }

    private fun server(session: ProjectSession?): EmbeddedMcpServer {
        val mcp = EmbeddedMcpServer(McpServerConfig(enabled = false))
        registerIdeTools(mcp, mockk<FileSystem>(relaxed = true), currentSession = { session })
        return mcp
    }

    private fun text(value: String): ToolResult = ToolResult.Success(listOf(ToolContent.Text(value)))
}
