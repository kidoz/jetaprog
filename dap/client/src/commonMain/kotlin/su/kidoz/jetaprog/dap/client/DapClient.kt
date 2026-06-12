package su.kidoz.jetaprog.dap.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.dap.protocol.AttachRequestArguments
import su.kidoz.jetaprog.dap.protocol.ContinueArguments
import su.kidoz.jetaprog.dap.protocol.ContinueResponseBody
import su.kidoz.jetaprog.dap.protocol.DapBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapCapabilities
import su.kidoz.jetaprog.dap.protocol.DapEvent
import su.kidoz.jetaprog.dap.protocol.DapRequest
import su.kidoz.jetaprog.dap.protocol.DapResponse
import su.kidoz.jetaprog.dap.protocol.DapScope
import su.kidoz.jetaprog.dap.protocol.DapSource
import su.kidoz.jetaprog.dap.protocol.DapSourceBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapStackFrame
import su.kidoz.jetaprog.dap.protocol.DapThread
import su.kidoz.jetaprog.dap.protocol.DapVariable
import su.kidoz.jetaprog.dap.protocol.DisconnectArguments
import su.kidoz.jetaprog.dap.protocol.EvaluateArguments
import su.kidoz.jetaprog.dap.protocol.EvaluateResponseBody
import su.kidoz.jetaprog.dap.protocol.InitializeRequestArguments
import su.kidoz.jetaprog.dap.protocol.LaunchRequestArguments
import su.kidoz.jetaprog.dap.protocol.NextArguments
import su.kidoz.jetaprog.dap.protocol.PauseArguments
import su.kidoz.jetaprog.dap.protocol.ScopesArguments
import su.kidoz.jetaprog.dap.protocol.ScopesResponseBody
import su.kidoz.jetaprog.dap.protocol.SetBreakpointsArguments
import su.kidoz.jetaprog.dap.protocol.SetBreakpointsResponseBody
import su.kidoz.jetaprog.dap.protocol.StackTraceArguments
import su.kidoz.jetaprog.dap.protocol.StackTraceResponseBody
import su.kidoz.jetaprog.dap.protocol.StepInArguments
import su.kidoz.jetaprog.dap.protocol.StepOutArguments
import su.kidoz.jetaprog.dap.protocol.ThreadsResponseBody
import su.kidoz.jetaprog.dap.protocol.VariablesArguments
import su.kidoz.jetaprog.dap.protocol.VariablesResponseBody
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for communicating with a Debug Adapter Protocol (DAP) server.
 *
 * This client handles the JSON-RPC style communication with debug adapters,
 * providing a high-level API for debugging operations.
 */
public class DapClient(
    inputStream: InputStream,
    outputStream: OutputStream,
    private val scope: CoroutineScope,
) : Disposable {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val transport = DapTransport(inputStream, outputStream, scope)
    private val sequenceNumber = AtomicInteger(0)
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<DapResponse>>()
    private val requestMutex = Mutex()

    private var capabilities: DapCapabilities? = null
    private var disposed = false

    /**
     * Flow of events from the debug adapter.
     */
    public val events: Flow<DapEvent> = transport.events

    /**
     * The capabilities of the debug adapter (available after initialize).
     */
    public val adapterCapabilities: DapCapabilities?
        get() = capabilities

    /**
     * Start the client and begin listening for messages.
     */
    public fun start() {
        check(!disposed) { "DapClient has been disposed" }
        transport.start()

        // Start response handler
        scope.launch {
            transport.responses.collect { response ->
                val deferred =
                    requestMutex.withLock {
                        pendingRequests.remove(response.requestSeq)
                    }
                deferred?.complete(response)
            }
        }
    }

    /**
     * Initialize the debug adapter.
     *
     * @param params Initialization parameters.
     * @return The capabilities of the debug adapter.
     */
    public suspend fun initialize(params: InitializeRequestArguments): Result<DapCapabilities> =
        sendRequest<DapCapabilities>("initialize", params).also {
            it.onSuccess { caps -> capabilities = caps }
        }

    /**
     * Launch the debuggee.
     *
     * @param params Launch parameters.
     */
    public suspend fun launch(params: LaunchRequestArguments): Result<Unit> = sendRequest<Unit>("launch", params)

    /**
     * Attach to a running process.
     *
     * @param params Attach parameters.
     */
    public suspend fun attach(params: AttachRequestArguments): Result<Unit> = sendRequest<Unit>("attach", params)

    /**
     * Indicate that configuration is done.
     */
    public suspend fun configurationDone(): Result<Unit> = sendRequest<Unit>("configurationDone", null)

    /**
     * Set breakpoints in a source file.
     *
     * @param source The source file.
     * @param breakpoints The breakpoints to set.
     * @return Information about the actual breakpoints.
     */
    public suspend fun setBreakpoints(
        source: DapSource,
        breakpoints: List<DapSourceBreakpoint>,
    ): Result<List<DapBreakpoint>> {
        val args =
            SetBreakpointsArguments(
                source = source,
                breakpoints = breakpoints,
            )
        return sendRequest<SetBreakpointsResponseBody>("setBreakpoints", args)
            .map { it.breakpoints }
    }

    /**
     * Continue execution.
     *
     * @param threadId The thread to continue.
     * @return Whether all threads continued.
     */
    public suspend fun continueExecution(threadId: Int): Result<Boolean> {
        val args = ContinueArguments(threadId = threadId)
        return sendRequest<ContinueResponseBody>("continue", args)
            .map { it.allThreadsContinued }
    }

    /**
     * Step to the next statement (step over).
     *
     * @param threadId The thread to step.
     */
    public suspend fun next(threadId: Int): Result<Unit> {
        val args = NextArguments(threadId = threadId)
        return sendRequest<Unit>("next", args)
    }

    /**
     * Step into the next function call.
     *
     * @param threadId The thread to step.
     */
    public suspend fun stepIn(threadId: Int): Result<Unit> {
        val args = StepInArguments(threadId = threadId)
        return sendRequest<Unit>("stepIn", args)
    }

    /**
     * Step out of the current function.
     *
     * @param threadId The thread to step.
     */
    public suspend fun stepOut(threadId: Int): Result<Unit> {
        val args = StepOutArguments(threadId = threadId)
        return sendRequest<Unit>("stepOut", args)
    }

    /**
     * Pause execution.
     *
     * @param threadId The thread to pause.
     */
    public suspend fun pause(threadId: Int): Result<Unit> {
        val args = PauseArguments(threadId = threadId)
        return sendRequest<Unit>("pause", args)
    }

    /**
     * Get all threads.
     *
     * @return List of threads in the debuggee.
     */
    public suspend fun threads(): Result<List<DapThread>> =
        sendRequest<ThreadsResponseBody>("threads", null)
            .map { it.threads }

    /**
     * Get the stack trace for a thread.
     *
     * @param threadId The thread to get the stack trace for.
     * @param startFrame Optional starting frame index.
     * @param levels Optional number of frames to return.
     * @return List of stack frames.
     */
    public suspend fun stackTrace(
        threadId: Int,
        startFrame: Int? = null,
        levels: Int? = null,
    ): Result<List<DapStackFrame>> {
        val args =
            StackTraceArguments(
                threadId = threadId,
                startFrame = startFrame,
                levels = levels,
            )
        return sendRequest<StackTraceResponseBody>("stackTrace", args)
            .map { it.stackFrames }
    }

    /**
     * Get scopes for a stack frame.
     *
     * @param frameId The stack frame ID.
     * @return List of scopes.
     */
    public suspend fun scopes(frameId: Int): Result<List<DapScope>> {
        val args = ScopesArguments(frameId = frameId)
        return sendRequest<ScopesResponseBody>("scopes", args)
            .map { it.scopes }
    }

    /**
     * Get variables for a variables reference.
     *
     * @param variablesReference The variables reference.
     * @return List of variables.
     */
    public suspend fun variables(variablesReference: Int): Result<List<DapVariable>> {
        val args = VariablesArguments(variablesReference = variablesReference)
        return sendRequest<VariablesResponseBody>("variables", args)
            .map { it.variables }
    }

    /**
     * Evaluate an expression.
     *
     * @param expression The expression to evaluate.
     * @param frameId Optional stack frame context.
     * @return The evaluation result.
     */
    public suspend fun evaluate(
        expression: String,
        frameId: Int? = null,
    ): Result<EvaluateResponseBody> {
        val args =
            EvaluateArguments(
                expression = expression,
                frameId = frameId,
            )
        return sendRequest<EvaluateResponseBody>("evaluate", args)
    }

    /**
     * Disconnect from the debuggee.
     *
     * @param terminateDebuggee Whether to terminate the debuggee.
     * @param restart Whether to restart the session.
     */
    public suspend fun disconnect(
        terminateDebuggee: Boolean = false,
        restart: Boolean = false,
    ): Result<Unit> {
        val args =
            DisconnectArguments(
                terminateDebuggee = terminateDebuggee,
                restart = restart,
            )
        return sendRequest<Unit>("disconnect", args)
    }

    /**
     * Terminate the debuggee.
     */
    public suspend fun terminate(): Result<Unit> = sendRequest<Unit>("terminate", null)

    private suspend inline fun <reified T> sendRequest(
        command: String,
        arguments: Any?,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<T> {
        check(!disposed) { "DapClient has been disposed" }

        val seq = sequenceNumber.incrementAndGet()
        val argumentsJson: JsonElement? =
            arguments?.let {
                json.encodeToJsonElement(
                    kotlinx.serialization.serializer(it::class.java),
                    it,
                )
            }

        val request =
            DapRequest(
                seq = seq,
                command = command,
                arguments = argumentsJson,
            )

        val deferred = CompletableDeferred<DapResponse>()
        requestMutex.withLock {
            pendingRequests[seq] = deferred
        }

        return try {
            transport.sendRequest(request)

            val response =
                withTimeout(timeoutMs) {
                    deferred.await()
                }

            if (response.success) {
                val body = response.body
                if (body != null && T::class != Unit::class) {
                    Result.success(json.decodeFromJsonElement<T>(body))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    Result.success(Unit as T)
                }
            } else {
                Result.failure(DapException(response.message ?: "Request failed: $command"))
            }
        } catch (e: TimeoutCancellationException) {
            requestMutex.withLock { pendingRequests.remove(seq) }
            Result.failure(DapException("Request timed out: $command"))
        } catch (e: Exception) {
            requestMutex.withLock { pendingRequests.remove(seq) }
            Result.failure(e)
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        // Cancel all pending requests
        scope.launch {
            requestMutex.withLock {
                pendingRequests.values.forEach {
                    it.completeExceptionally(DapException("Client disposed"))
                }
                pendingRequests.clear()
            }
        }

        transport.dispose()
    }

    public companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

/**
 * Exception from DAP operations.
 */
public class DapException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
