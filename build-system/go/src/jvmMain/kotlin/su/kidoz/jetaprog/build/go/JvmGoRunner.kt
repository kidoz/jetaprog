package su.kidoz.jetaprog.build.go

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput

private val logger = KotlinLogging.logger {}

/**
 * [GoRunner] that starts `go` through a [ProcessExecutor].
 *
 * @property processExecutor Process launcher.
 * @property goCommand Executable name or path. Tests and settings can override the default `go`.
 */
public class JvmGoRunner(
    private val processExecutor: ProcessExecutor,
    private val goCommand: String = "go",
) : GoRunner {
    private var runningProcess: su.kidoz.jetaprog.platform.process.RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun execute(
        project: GoProject,
        operation: GoOperation,
    ): Result<Flow<GoOutput>> =
        runCatching {
            val args = GoToolchain.arguments(operation)
            val command = listOf(goCommand) + args
            logger.debug { "Running: ${command.joinToString(" ")}" }
            val process =
                processExecutor
                    .start(
                        ProcessConfig(
                            command = command,
                            workingDirectory = project.rootPath,
                        ),
                    ).getOrThrow()
            runningProcess = process
            flow {
                emit(GoOutput.CommandStarted(goCommand, args))
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            emit(GoOutputParser.parse(output.line, fromStderr = false))
                        }

                        is ProcessOutput.Stderr -> {
                            emit(GoOutputParser.parse(output.line, fromStderr = true))
                        }

                        is ProcessOutput.Exited -> {
                            runningProcess = null
                            emit(
                                GoOutput.CommandCompleted(
                                    exitCode = output.exitCode,
                                    success = output.exitCode == 0,
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun cancel() {
        runningProcess?.kill()
        runningProcess = null
    }
}
