package su.kidoz.jetaprog.build.gradle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmGradleTaskRunnerTest {
    @Test
    fun `task discovery skips section underlines and prose lines`() {
        val tasksOutput =
            """
            Type-safe project accessors is an incubating feature.

            ------------------------------------------------------------
            Tasks runnable from root project 'jetaprog'
            ------------------------------------------------------------

            Build tasks
            -----------
            assemble - Assembles the outputs of this project.
            build - Assembles and tests this project.
            :app:desktop:run - Runs this project as a JVM application

            Verification tasks
            ------------------
            detekt - Runs detekt analysis.
            """.trimIndent()

        val runner = JvmGradleTaskRunner(FakeProcessExecutor(stdout = tasksOutput))
        val project = GradleProject(rootPath = "/tmp/fake-project")

        val discovered = runBlocking { runner.discoverTasks(project) }.getOrThrow()

        assertEquals(
            listOf("assemble", "build", ":app:desktop:run", "detekt"),
            discovered.tasks.map { it.path },
        )
        assertEquals("Build", discovered.tasks.first().group)
    }

    private class FakeProcessExecutor(
        private val stdout: String,
    ) : ProcessExecutor {
        override suspend fun execute(
            command: List<String>,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.success(ProcessResult(exitCode = 0, stdout = stdout, stderr = ""))

        override suspend fun executeShell(
            command: String,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.success(ProcessResult(exitCode = 0, stdout = stdout, stderr = ""))

        override suspend fun start(config: ProcessConfig): Result<RunningProcess> =
            Result.success(
                object : RunningProcess {
                    override suspend fun writeStdin(text: String) = Unit

                    override suspend fun closeStdin() = Unit

                    override fun kill() = Unit

                    override suspend fun waitFor(): Int = 0

                    override val isAlive: Boolean = false

                    override val output: Flow<ProcessOutput> = emptyFlow()
                },
            )
    }
}
