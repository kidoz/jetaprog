package su.kidoz.jetaprog.dap.service

import kotlinx.coroutines.CoroutineScope
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.DotNetConfigurationType
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DotNetDebugLaunchTest {
    private val service = DebugService(UnusedProcessExecutor, CoroutineScope(EmptyCoroutineContext))

    @Test
    fun `infers configured assembly output and forwards debug options`() {
        val settings =
            ConfigurationSettings.DotNetDebug(
                projectPath = "/workspace/src/App/App.csproj",
                targetFramework = "net10.0",
                assemblyName = "Demo.Cli",
                configuration = DotNetConfigurationType.RELEASE,
                programArguments = listOf("--seed", "42"),
                environment = mapOf("DOTNET_ENVIRONMENT" to "Development"),
                workingDirectory = "/workspace",
                stopAtEntry = true,
            )

        val launch = service.buildDotNetLaunchArgs(settings, "/fallback").getOrThrow()

        assertEquals("/workspace/src/App/bin/Release/net10.0/Demo.Cli.dll", launch.program)
        assertEquals(listOf("--seed", "42"), launch.args)
        assertEquals("/workspace", launch.cwd)
        assertEquals("Development", launch.env?.get("DOTNET_ENVIRONMENT"))
        assertEquals(true, launch.stopAtEntry)
    }

    private object UnusedProcessExecutor : ProcessExecutor {
        override suspend fun execute(
            command: List<String>,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.failure(UnsupportedOperationException())

        override suspend fun executeShell(
            command: String,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.failure(UnsupportedOperationException())

        override suspend fun start(config: ProcessConfig): Result<RunningProcess> =
            Result.failure(UnsupportedOperationException())
    }
}
