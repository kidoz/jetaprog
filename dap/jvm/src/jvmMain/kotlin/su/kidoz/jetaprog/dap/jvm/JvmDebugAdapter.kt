package su.kidoz.jetaprog.dap.jvm

import com.sun.jdi.request.StepRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.dap.protocol.BreakpointEventBody
import su.kidoz.jetaprog.dap.protocol.BreakpointEventReason
import su.kidoz.jetaprog.dap.protocol.ContinueArguments
import su.kidoz.jetaprog.dap.protocol.ContinueResponseBody
import su.kidoz.jetaprog.dap.protocol.ContinuedEventBody
import su.kidoz.jetaprog.dap.protocol.DapBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapCapabilities
import su.kidoz.jetaprog.dap.protocol.DapRequest
import su.kidoz.jetaprog.dap.protocol.DisconnectArguments
import su.kidoz.jetaprog.dap.protocol.EvaluateArguments
import su.kidoz.jetaprog.dap.protocol.EvaluateResponseBody
import su.kidoz.jetaprog.dap.protocol.ExitedEventBody
import su.kidoz.jetaprog.dap.protocol.LaunchRequestArguments
import su.kidoz.jetaprog.dap.protocol.NextArguments
import su.kidoz.jetaprog.dap.protocol.OutputEventBody
import su.kidoz.jetaprog.dap.protocol.PauseArguments
import su.kidoz.jetaprog.dap.protocol.ScopesArguments
import su.kidoz.jetaprog.dap.protocol.ScopesResponseBody
import su.kidoz.jetaprog.dap.protocol.SetBreakpointsArguments
import su.kidoz.jetaprog.dap.protocol.SetBreakpointsResponseBody
import su.kidoz.jetaprog.dap.protocol.StackTraceArguments
import su.kidoz.jetaprog.dap.protocol.StackTraceResponseBody
import su.kidoz.jetaprog.dap.protocol.StepInArguments
import su.kidoz.jetaprog.dap.protocol.StepOutArguments
import su.kidoz.jetaprog.dap.protocol.StoppedEventBody
import su.kidoz.jetaprog.dap.protocol.ThreadEventBody
import su.kidoz.jetaprog.dap.protocol.ThreadsResponseBody
import su.kidoz.jetaprog.dap.protocol.VariablesArguments
import su.kidoz.jetaprog.dap.protocol.VariablesResponseBody

/**
 * A Debug Adapter Protocol server for JVM debuggees, backed by JDI.
 *
 * Reads DAP requests from a [DapServerConnection], drives a [JdiDebugTarget]
 * and forwards its events back over the connection.
 */
public class JvmDebugAdapter(
    private val connection: DapServerConnection,
) : JdiDebugTarget.Listener {
    private val json = Json { ignoreUnknownKeys = true }
    private val target = JdiDebugTarget(this)

    /**
     * Serves the connection until the client disconnects or the stream closes.
     */
    public fun run() {
        while (true) {
            val request = connection.readRequest() ?: return
            val keepServing =
                try {
                    handle(request)
                } catch (exception: Exception) {
                    connection.sendErrorResponse(request, exception.message ?: exception.toString())
                    true
                }
            if (!keepServing) return
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handle(request: DapRequest): Boolean {
        when (request.command) {
            "initialize" -> {
                connection.sendResponse(
                    request,
                    DapCapabilities.serializer(),
                    DapCapabilities(
                        supportsConfigurationDoneRequest = true,
                        supportsEvaluateForHovers = true,
                        supportTerminateDebuggee = true,
                        supportsTerminateRequest = true,
                    ),
                )
            }

            "launch" -> {
                val args = decode(request, LaunchRequestArguments.serializer())
                val command = listOfNotNull(args.program) + args.args.orEmpty()
                target.launch(
                    command = command,
                    workingDirectory = args.cwd,
                    environment = args.env.orEmpty(),
                    attachHost = args.attachHost ?: "127.0.0.1",
                    attachPort = args.attachPort ?: DEFAULT_JDWP_PORT,
                    attachTimeoutMs = args.attachTimeoutMs ?: DEFAULT_ATTACH_TIMEOUT_MS,
                    sourceRoots = args.sourceRoots.orEmpty(),
                )
                connection.sendResponse(request)
            }

            "setBreakpoints" -> {
                val args = decode(request, SetBreakpointsArguments.serializer())
                val path = args.source.path ?: error("setBreakpoints requires a source path")
                val breakpoints = target.setBreakpoints(path, args.breakpoints.orEmpty())
                connection.sendResponse(
                    request,
                    SetBreakpointsResponseBody.serializer(),
                    SetBreakpointsResponseBody(breakpoints),
                )
            }

            "configurationDone" -> {
                target.configurationDone()
                connection.sendResponse(request)
            }

            "threads" -> {
                connection.sendResponse(
                    request,
                    ThreadsResponseBody.serializer(),
                    ThreadsResponseBody(target.threads()),
                )
            }

            "stackTrace" -> {
                val args = decode(request, StackTraceArguments.serializer())
                val frames = target.stackTrace(args.threadId, args.startFrame ?: 0, args.levels)
                connection.sendResponse(
                    request,
                    StackTraceResponseBody.serializer(),
                    StackTraceResponseBody(stackFrames = frames, totalFrames = frames.size),
                )
            }

            "scopes" -> {
                val args = decode(request, ScopesArguments.serializer())
                connection.sendResponse(
                    request,
                    ScopesResponseBody.serializer(),
                    ScopesResponseBody(target.scopes(args.frameId)),
                )
            }

            "variables" -> {
                val args = decode(request, VariablesArguments.serializer())
                connection.sendResponse(
                    request,
                    VariablesResponseBody.serializer(),
                    VariablesResponseBody(target.variables(args.variablesReference)),
                )
            }

            "continue" -> {
                val args = decode(request, ContinueArguments.serializer())
                connection.sendResponse(
                    request,
                    ContinueResponseBody.serializer(),
                    ContinueResponseBody(allThreadsContinued = true),
                )
                target.resume(args.threadId)
            }

            "next" -> {
                val args = decode(request, NextArguments.serializer())
                connection.sendResponse(request)
                target.step(args.threadId, StepRequest.STEP_OVER)
            }

            "stepIn" -> {
                val args = decode(request, StepInArguments.serializer())
                connection.sendResponse(request)
                target.step(args.threadId, StepRequest.STEP_INTO)
            }

            "stepOut" -> {
                val args = decode(request, StepOutArguments.serializer())
                connection.sendResponse(request)
                target.step(args.threadId, StepRequest.STEP_OUT)
            }

            "pause" -> {
                val args = decode(request, PauseArguments.serializer())
                target.pause(args.threadId)
                connection.sendResponse(request)
            }

            "evaluate" -> {
                val args = decode(request, EvaluateArguments.serializer())
                val variable = target.evaluate(args.expression, args.frameId)
                connection.sendResponse(
                    request,
                    EvaluateResponseBody.serializer(),
                    EvaluateResponseBody(
                        result = variable.value,
                        type = variable.type,
                        variablesReference = variable.variablesReference,
                    ),
                )
            }

            "disconnect" -> {
                val args =
                    request.arguments?.let { decode(request, DisconnectArguments.serializer()) }
                        ?: DisconnectArguments()
                target.disconnect(terminateDebuggee = args.terminateDebuggee)
                connection.sendResponse(request)
                return false
            }

            "terminate" -> {
                target.disconnect(terminateDebuggee = true)
                connection.sendResponse(request)
            }

            else -> {
                connection.sendErrorResponse(request, "Unsupported command: ${request.command}")
            }
        }
        return true
    }

    private fun <T> decode(
        request: DapRequest,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val arguments = request.arguments ?: error("Missing arguments for ${request.command}")
        return json.decodeFromJsonElement(deserializer, arguments)
    }

    // ========================================================================
    // JdiDebugTarget.Listener
    // ========================================================================

    override fun onInitialized() {
        connection.sendEvent("initialized")
    }

    override fun onStopped(body: StoppedEventBody) {
        connection.sendEvent("stopped", StoppedEventBody.serializer(), body)
    }

    override fun onContinued(body: ContinuedEventBody) {
        connection.sendEvent("continued", ContinuedEventBody.serializer(), body)
    }

    override fun onThread(body: ThreadEventBody) {
        connection.sendEvent("thread", ThreadEventBody.serializer(), body)
    }

    override fun onOutput(body: OutputEventBody) {
        connection.sendEvent("output", OutputEventBody.serializer(), body)
    }

    override fun onBreakpointChanged(breakpoint: DapBreakpoint) {
        connection.sendEvent(
            "breakpoint",
            BreakpointEventBody.serializer(),
            BreakpointEventBody(reason = BreakpointEventReason.CHANGED, breakpoint = breakpoint),
        )
    }

    override fun onExited(body: ExitedEventBody) {
        connection.sendEvent("exited", ExitedEventBody.serializer(), body)
    }

    override fun onTerminated() {
        connection.sendEvent("terminated")
    }

    private companion object {
        private const val DEFAULT_JDWP_PORT = 5005
        private const val DEFAULT_ATTACH_TIMEOUT_MS = 120_000L
    }
}
