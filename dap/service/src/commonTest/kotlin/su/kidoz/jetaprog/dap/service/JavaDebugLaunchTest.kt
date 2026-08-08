package su.kidoz.jetaprog.dap.service

import kotlinx.coroutines.CoroutineScope
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.JavaBuildTool
import su.kidoz.jetaprog.configuration.JavaCommand
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaDebugLaunchTest {
    private val service = DebugService(UnusedProcessExecutor, CoroutineScope(EmptyCoroutineContext))

    @Test
    fun `Gradle debug launch uses debug JVM and test filter`() {
        val settings =
            ConfigurationSettings.Java(
                command = JavaCommand.TEST,
                buildTool = JavaBuildTool.GRADLE,
                task = "test",
                executable = "/workspace/gradlew",
                testFilter = "com.example.MainTest",
                buildArguments = listOf("--info"),
                workingDirectory = "/workspace",
            )

        val launch = service.buildJavaLaunchArgs(settings, "/fallback").getOrThrow()

        assertEquals("/workspace/gradlew", launch.program)
        assertEquals(
            listOf("test", "--debug-jvm", "--info", "--tests", "com.example.MainTest"),
            launch.args,
        )
        assertEquals(5005, launch.attachPort)
        assertEquals("/workspace", launch.cwd)
    }

    @Test
    fun `Maven debug launch injects JDWP and disables test forking`() {
        val settings =
            ConfigurationSettings.Java(
                command = JavaCommand.TEST,
                buildTool = JavaBuildTool.MAVEN,
                task = "test",
                executable = "/workspace/mvnw",
                testFilter = "com.example.MainTest.method",
                jvmArguments = listOf("-Xmx1g"),
                environment = mapOf("MAVEN_OPTS" to "-Dexisting=true"),
                workingDirectory = "/workspace",
            )

        val launch = service.buildJavaLaunchArgs(settings, "/fallback").getOrThrow()

        assertEquals("/workspace/mvnw", launch.program)
        assertEquals(listOf("test", "-Dtest=com.example.MainTest.method", "-DforkCount=0"), launch.args)
        val mavenOptions = launch.env.orEmpty().getValue("MAVEN_OPTS")
        assertTrue(mavenOptions.contains("-Dexisting=true -Xmx1g"))
        assertTrue(mavenOptions.contains("-agentlib:jdwp="))
        assertTrue(mavenOptions.contains("address=127.0.0.1:5005"))
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
