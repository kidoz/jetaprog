package su.kidoz.jetaprog.dap.jvm

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ArrayReference
import com.sun.jdi.Bootstrap
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.event.ThreadDeathEvent
import com.sun.jdi.event.ThreadStartEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import su.kidoz.jetaprog.dap.protocol.ContinuedEventBody
import su.kidoz.jetaprog.dap.protocol.DapBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapScope
import su.kidoz.jetaprog.dap.protocol.DapSource
import su.kidoz.jetaprog.dap.protocol.DapSourceBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapStackFrame
import su.kidoz.jetaprog.dap.protocol.DapThread
import su.kidoz.jetaprog.dap.protocol.DapVariable
import su.kidoz.jetaprog.dap.protocol.ExitedEventBody
import su.kidoz.jetaprog.dap.protocol.OutputCategory
import su.kidoz.jetaprog.dap.protocol.OutputEventBody
import su.kidoz.jetaprog.dap.protocol.StoppedEventBody
import su.kidoz.jetaprog.dap.protocol.StoppedReason
import su.kidoz.jetaprog.dap.protocol.ThreadEventBody
import su.kidoz.jetaprog.dap.protocol.ThreadEventReason
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * JDI-backed debug target: spawns the debuggee command, attaches to its JDWP
 * agent and translates between JDI and DAP concepts.
 *
 * The debuggee JVM is expected to be started with a JDWP agent in
 * `server=y,suspend=y` mode (for Gradle tasks, `--debug-jvm` does this); the
 * target polls the JDWP port until the agent accepts the connection.
 */
public class JdiDebugTarget(
    private val listener: Listener,
) {
    /**
     * Receives DAP events produced by the target.
     */
    public interface Listener {
        /** The target attached to the debuggee and is ready for breakpoints. */
        public fun onInitialized()

        /** The debuggee stopped (breakpoint, step or pause). */
        public fun onStopped(body: StoppedEventBody)

        /** The debuggee resumed execution. */
        public fun onContinued(body: ContinuedEventBody)

        /** A debuggee thread started or exited. */
        public fun onThread(body: ThreadEventBody)

        /** Output captured from the debuggee process. */
        public fun onOutput(body: OutputEventBody)

        /** A breakpoint changed state (e.g. became verified). */
        public fun onBreakpointChanged(breakpoint: DapBreakpoint)

        /** The debuggee process exited. */
        public fun onExited(body: ExitedEventBody)

        /** The debug session ended. */
        public fun onTerminated()
    }

    private class FileBreakpoints(
        val path: String,
        val fileName: String,
        var requested: List<DapSourceBreakpoint>,
        val packageName: String?,
    ) {
        val requests: MutableList<BreakpointRequest> = mutableListOf()
        val verifiedLines: MutableSet<Int> = mutableSetOf()
    }

    private sealed interface VariableContainer {
        class FrameLocals(
            val thread: ThreadReference,
            val frameIndex: Int,
        ) : VariableContainer

        class ObjectFields(
            val value: ObjectReference,
        ) : VariableContainer

        class ArrayElements(
            val value: ArrayReference,
        ) : VariableContainer
    }

    private val lock = Any()

    @Volatile
    private var vm: VirtualMachine? = null

    @Volatile
    private var process: Process? = null

    @Volatile
    private var shuttingDown = false

    private var sourceRoots: List<String> = emptyList()
    private val breakpointsByPath = mutableMapOf<String, FileBreakpoints>()
    private val fileNameToPath = mutableMapOf<String, String>()
    private val frames = mutableMapOf<Int, Pair<ThreadReference, Int>>()
    private val variableContainers = mutableMapOf<Int, VariableContainer>()
    private var nextFrameId = 1
    private var nextVariablesReference = 1

    /**
     * Spawns [command] in [workingDirectory] and starts attaching to the
     * debuggee's JDWP agent in the background.
     */
    public fun launch(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        attachHost: String,
        attachPort: Int,
        attachTimeoutMs: Long,
        sourceRoots: List<String>,
    ) {
        require(command.isNotEmpty()) { "Launch command must not be empty" }
        this.sourceRoots = sourceRoots

        val builder = ProcessBuilder(command)
        workingDirectory?.let { builder.directory(File(it)) }
        environment.forEach { (key, value) -> builder.environment()[key] = value }

        val startedProcess = builder.start()
        process = startedProcess

        pipeOutput(startedProcess.inputStream, OutputCategory.STDOUT)
        pipeOutput(startedProcess.errorStream, OutputCategory.STDERR)

        thread(name = "jdi-process-watcher", isDaemon = true) {
            val exitCode = runCatching { startedProcess.waitFor() }.getOrDefault(-1)
            listener.onExited(ExitedEventBody(exitCode = exitCode))
            listener.onTerminated()
        }

        thread(name = "jdi-attach", isDaemon = true) {
            attachLoop(startedProcess, attachHost, attachPort, attachTimeoutMs)
        }
    }

    /**
     * Registers [breakpoints] for the file at [path] and applies them to all
     * matching loaded classes; unloaded classes are covered by deferred
     * class-prepare requests.
     *
     * @return The breakpoint states, in the order they were requested.
     */
    public fun setBreakpoints(
        path: String,
        breakpoints: List<DapSourceBreakpoint>,
    ): List<DapBreakpoint> =
        synchronized(lock) {
            val fileName = path.substringAfterLast('/')
            val existing = breakpointsByPath[path]
            val fileBreakpoints =
                existing?.apply { requested = breakpoints }
                    ?: FileBreakpoints(
                        path = path,
                        fileName = fileName,
                        requested = breakpoints,
                        packageName = parsePackageName(path),
                    ).also { breakpointsByPath[path] = it }
            fileNameToPath[fileName] = path

            val machine = vm
            if (machine != null) {
                clearBreakpointRequests(machine, fileBreakpoints)
                ensureClassPrepareRequest(machine, fileBreakpoints)
                loadedClassesFor(machine, fileBreakpoints).forEach { type ->
                    applyBreakpointsToClass(type, fileBreakpoints)
                }
            }

            breakpoints.map { requested ->
                DapBreakpoint(
                    verified = requested.line in fileBreakpoints.verifiedLines,
                    line = requested.line,
                    source = DapSource(name = fileName, path = path),
                )
            }
        }

    /**
     * Resumes the debuggee after the configuration phase.
     */
    public fun configurationDone() {
        vm?.resume()
    }

    /**
     * Returns the debuggee threads.
     */
    public fun threads(): List<DapThread> {
        val machine = vm ?: return emptyList()
        return machine.allThreads().map { thread ->
            DapThread(id = thread.uniqueID().toInt(), name = thread.name())
        }
    }

    /**
     * Returns the stack frames of a suspended thread.
     */
    public fun stackTrace(
        threadId: Int,
        startFrame: Int,
        levels: Int?,
    ): List<DapStackFrame> =
        synchronized(lock) {
            val thread = threadById(threadId) ?: error("Unknown thread: $threadId")
            val allFrames = thread.frames()
            val selected =
                allFrames
                    .drop(startFrame)
                    .let { if (levels != null && levels > 0) it.take(levels) else it }
            selected.mapIndexed { index, frame ->
                val location = frame.location()
                val frameId = nextFrameId++
                frames[frameId] = thread to (startFrame + index)
                DapStackFrame(
                    id = frameId,
                    name = "${location.declaringType().name()}.${location.method().name()}",
                    source = sourceFor(location),
                    line = location.lineNumber().coerceAtLeast(1),
                    column = 1,
                )
            }
        }

    /**
     * Returns the variable scopes of a stack frame.
     */
    public fun scopes(frameId: Int): List<DapScope> =
        synchronized(lock) {
            val (thread, frameIndex) = frames[frameId] ?: error("Unknown frame: $frameId")
            val reference = nextVariablesReference++
            variableContainers[reference] = VariableContainer.FrameLocals(thread, frameIndex)
            listOf(DapScope(name = "Locals", variablesReference = reference))
        }

    /**
     * Returns the child variables of a variables reference.
     */
    public fun variables(variablesReference: Int): List<DapVariable> =
        synchronized(lock) {
            when (val container = variableContainers[variablesReference]) {
                is VariableContainer.FrameLocals -> frameVariables(container)
                is VariableContainer.ObjectFields -> objectVariables(container.value)
                is VariableContainer.ArrayElements -> arrayVariables(container.value)
                null -> emptyList()
            }
        }

    /**
     * Evaluates [expression] as a variable name in the context of a frame.
     */
    public fun evaluate(
        expression: String,
        frameId: Int?,
    ): DapVariable =
        synchronized(lock) {
            val frameHandle = frameId?.let { frames[it] } ?: error("No frame context for evaluation")
            val frame = frameHandle.first.frame(frameHandle.second)
            val name = expression.trim()
            val local = runCatching { frame.visibleVariableByName(name) }.getOrNull()
            val value =
                when {
                    local != null -> {
                        frame.getValue(local)
                    }

                    name == "this" -> {
                        frame.thisObject()
                    }

                    else -> {
                        val thisObject = frame.thisObject() ?: error("Cannot resolve '$name'")
                        val field =
                            thisObject.referenceType().fieldByName(name)
                                ?: error("Cannot resolve '$name'")
                        thisObject.getValue(field)
                    }
                }
            toDapVariable(name, value)
        }

    /**
     * Resumes all threads.
     */
    public fun resume(threadId: Int) {
        val machine = vm ?: return
        clearSuspendedState()
        listener.onContinued(ContinuedEventBody(threadId = threadId, allThreadsContinued = true))
        machine.resume()
    }

    /**
     * Performs a step of the given JDI [depth] (see [StepRequest]) on a thread.
     */
    public fun step(
        threadId: Int,
        depth: Int,
    ) {
        val machine = vm ?: return
        val thread = threadById(threadId) ?: error("Unknown thread: $threadId")

        val requestManager = machine.eventRequestManager()
        requestManager
            .stepRequests()
            .filter { it.thread() == thread }
            .forEach(requestManager::deleteEventRequest)

        val request = requestManager.createStepRequest(thread, StepRequest.STEP_LINE, depth)
        request.addCountFilter(1)
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
        request.enable()

        clearSuspendedState()
        listener.onContinued(ContinuedEventBody(threadId = threadId, allThreadsContinued = true))
        machine.resume()
    }

    /**
     * Suspends all threads.
     */
    public fun pause(threadId: Int) {
        val machine = vm ?: return
        machine.suspend()
        listener.onStopped(
            StoppedEventBody(
                reason = StoppedReason.PAUSE,
                threadId = threadId,
                allThreadsStopped = true,
            ),
        )
    }

    /**
     * Ends the session, optionally terminating the debuggee process.
     */
    public fun disconnect(terminateDebuggee: Boolean) {
        shuttingDown = true
        val machine = vm
        if (terminateDebuggee) {
            runCatching { machine?.exit(0) }
            process?.let { debuggee ->
                debuggee.descendants().forEach(ProcessHandle::destroy)
                debuggee.destroy()
            }
        } else {
            runCatching { machine?.dispose() }
        }
    }

    // ========================================================================
    // Attach and event loop
    // ========================================================================

    private fun attachLoop(
        debuggee: Process,
        host: String,
        port: Int,
        timeoutMs: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && debuggee.isAlive && !shuttingDown) {
            val machine =
                try {
                    attachOnce(host, port)
                } catch (exception: IOException) {
                    Thread.sleep(ATTACH_RETRY_DELAY_MS)
                    continue
                } catch (exception: Exception) {
                    System.err.println("JDWP attach failed: $exception")
                    Thread.sleep(ATTACH_RETRY_DELAY_MS)
                    continue
                }

            synchronized(lock) {
                vm = machine
            }
            val requestManager = machine.eventRequestManager()
            requestManager.createThreadStartRequest().apply {
                setSuspendPolicy(EventRequest.SUSPEND_NONE)
                enable()
            }
            requestManager.createThreadDeathRequest().apply {
                setSuspendPolicy(EventRequest.SUSPEND_NONE)
                enable()
            }

            thread(name = "jdi-event-loop", isDaemon = true) { eventLoop(machine) }
            listener.onInitialized()
            return
        }

        if (!shuttingDown) {
            listener.onOutput(
                OutputEventBody(
                    category = OutputCategory.STDERR,
                    output = "Timed out attaching to JDWP agent on $host:$port\n",
                ),
            )
            debuggee.destroy()
        }
    }

    private fun attachOnce(
        host: String,
        port: Int,
    ): VirtualMachine {
        val connector =
            Bootstrap
                .virtualMachineManager()
                .attachingConnectors()
                .firstOrNull { it.transport().name() == "dt_socket" }
                ?: error("No socket attaching connector available in this JVM")
        val arguments = connector.defaultArguments()
        arguments["hostname"]?.setValue(host)
        arguments["port"]?.setValue(port.toString())
        arguments["timeout"]?.setValue(ATTACH_CONNECT_TIMEOUT_MS.toString())
        return connector.attach(arguments)
    }

    private fun eventLoop(machine: VirtualMachine) {
        val queue = machine.eventQueue()
        try {
            while (true) {
                val eventSet = queue.remove()
                var resumeSet = true
                for (event in eventSet) {
                    when (event) {
                        is BreakpointEvent -> {
                            resumeSet = false
                            handleStop(StoppedReason.BREAKPOINT, event.thread())
                        }

                        is StepEvent -> {
                            machine.eventRequestManager().deleteEventRequest(event.request())
                            resumeSet = false
                            handleStop(StoppedReason.STEP, event.thread())
                        }

                        is VMStartEvent -> {
                            // suspend=y holds the VM via this event's suspension;
                            // keep it held until configurationDone resumes the VM
                            resumeSet = false
                        }

                        is ClassPrepareEvent -> {
                            handleClassPrepare(event.referenceType())
                        }

                        is ThreadStartEvent -> {
                            listener.onThread(
                                ThreadEventBody(
                                    reason = ThreadEventReason.STARTED,
                                    threadId = event.thread().uniqueID().toInt(),
                                ),
                            )
                        }

                        is ThreadDeathEvent -> {
                            listener.onThread(
                                ThreadEventBody(
                                    reason = ThreadEventReason.EXITED,
                                    threadId = event.thread().uniqueID().toInt(),
                                ),
                            )
                        }

                        is VMDisconnectEvent -> {
                            return
                        }
                    }
                }
                if (resumeSet) {
                    eventSet.resume()
                }
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exception: Exception) {
            // VMDisconnectedException and transport errors end the session
        }
    }

    private fun handleStop(
        reason: StoppedReason,
        thread: ThreadReference,
    ) {
        clearSuspendedState()
        listener.onStopped(
            StoppedEventBody(
                reason = reason,
                threadId = thread.uniqueID().toInt(),
                allThreadsStopped = true,
            ),
        )
    }

    private fun handleClassPrepare(type: ReferenceType) {
        synchronized(lock) {
            val sourceName = runCatching { type.sourceName() }.getOrNull() ?: return
            breakpointsByPath.values
                .filter { it.fileName == sourceName }
                .forEach { fileBreakpoints ->
                    val newlyVerified = applyBreakpointsToClass(type, fileBreakpoints)
                    newlyVerified.forEach { line ->
                        listener.onBreakpointChanged(
                            DapBreakpoint(
                                verified = true,
                                line = line,
                                source = DapSource(name = fileBreakpoints.fileName, path = fileBreakpoints.path),
                            ),
                        )
                    }
                }
        }
    }

    // ========================================================================
    // Breakpoints
    // ========================================================================

    private fun clearBreakpointRequests(
        machine: VirtualMachine,
        fileBreakpoints: FileBreakpoints,
    ) {
        val requestManager = machine.eventRequestManager()
        fileBreakpoints.requests.forEach { request ->
            runCatching { requestManager.deleteEventRequest(request) }
        }
        fileBreakpoints.requests.clear()
        fileBreakpoints.verifiedLines.clear()
    }

    private fun ensureClassPrepareRequest(
        machine: VirtualMachine,
        fileBreakpoints: FileBreakpoints,
    ) {
        val requestManager = machine.eventRequestManager()
        val alreadyRegistered =
            requestManager.classPrepareRequests().any { request ->
                request.getProperty(SOURCE_NAME_PROPERTY) == fileBreakpoints.fileName
            }
        if (alreadyRegistered) return

        val request = requestManager.createClassPrepareRequest()
        if (machine.canUseSourceNameFilters()) {
            request.addSourceNameFilter(fileBreakpoints.fileName)
        }
        request.putProperty(SOURCE_NAME_PROPERTY, fileBreakpoints.fileName)
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
        request.enable()
    }

    private fun loadedClassesFor(
        machine: VirtualMachine,
        fileBreakpoints: FileBreakpoints,
    ): List<ReferenceType> {
        val packageName = fileBreakpoints.packageName
        return machine
            .allClasses()
            .asSequence()
            .filter { type ->
                when {
                    packageName == null -> true
                    packageName.isEmpty() -> '.' !in type.name()
                    else -> type.name().startsWith("$packageName.")
                }
            }.filter { type ->
                runCatching { type.sourceName() }.getOrNull() == fileBreakpoints.fileName
            }.toList()
    }

    /**
     * Sets breakpoint requests for [fileBreakpoints] in [type].
     *
     * @return Lines that became verified by this application.
     */
    private fun applyBreakpointsToClass(
        type: ReferenceType,
        fileBreakpoints: FileBreakpoints,
    ): List<Int> {
        val machine = vm ?: return emptyList()
        val requestManager = machine.eventRequestManager()
        val newlyVerified = mutableListOf<Int>()

        fileBreakpoints.requested.forEach { requested ->
            val locations =
                try {
                    type.locationsOfLine(requested.line)
                } catch (exception: AbsentInformationException) {
                    emptyList()
                }
            val location = locations.firstOrNull() ?: return@forEach

            val request = requestManager.createBreakpointRequest(location)
            request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
            request.enable()
            fileBreakpoints.requests.add(request)
            if (fileBreakpoints.verifiedLines.add(requested.line)) {
                newlyVerified.add(requested.line)
            }
        }

        return newlyVerified
    }

    private fun parsePackageName(path: String): String? {
        val content = runCatching { File(path).readText() }.getOrNull() ?: return null
        return packageRegex.find(content)?.groupValues?.get(1) ?: ""
    }

    // ========================================================================
    // Threads, frames and variables
    // ========================================================================

    private fun threadById(threadId: Int): ThreadReference? =
        vm?.allThreads()?.firstOrNull { it.uniqueID().toInt() == threadId }

    private fun clearSuspendedState() {
        synchronized(lock) {
            frames.clear()
            variableContainers.clear()
        }
    }

    private fun sourceFor(location: Location): DapSource? {
        val name = runCatching { location.sourceName() }.getOrNull() ?: return null
        val path = fileNameToPath[name] ?: resolveViaSourceRoots(location)
        return DapSource(name = name, path = path)
    }

    private fun resolveViaSourceRoots(location: Location): String? {
        val relativePath = runCatching { location.sourcePath() }.getOrNull() ?: return null
        return sourceRoots
            .asSequence()
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }

    private fun frameVariables(container: VariableContainer.FrameLocals): List<DapVariable> {
        val frame = container.thread.frame(container.frameIndex)
        val variables = mutableListOf<DapVariable>()

        frame.thisObject()?.let { thisObject ->
            variables.add(toDapVariable("this", thisObject))
        }

        val locals =
            try {
                frame.visibleVariables()
            } catch (exception: AbsentInformationException) {
                emptyList()
            }
        // Re-read the frame: fetching values can invalidate the frame proxy
        val values = container.thread.frame(container.frameIndex).getValues(locals)
        locals.forEach { local ->
            variables.add(toDapVariable(local.name(), values[local]))
        }
        return variables
    }

    private fun objectVariables(value: ObjectReference): List<DapVariable> {
        val fields = value.referenceType().allFields().filterNot { it.isStatic }
        val values = value.getValues(fields)
        return fields.map { field -> toDapVariable(field.name(), values[field]) }
    }

    private fun arrayVariables(value: ArrayReference): List<DapVariable> {
        val length = value.length().coerceAtMost(MAX_ARRAY_CHILDREN)
        return (0 until length).map { index ->
            toDapVariable("[$index]", value.getValue(index))
        }
    }

    private fun toDapVariable(
        name: String,
        value: Value?,
    ): DapVariable =
        DapVariable(
            name = name,
            value = renderValue(value),
            type = value?.type()?.name(),
            variablesReference = childReference(value),
        )

    private fun renderValue(value: Value?): String =
        when (value) {
            null -> "null"
            is StringReference -> "\"${value.value()}\""
            is PrimitiveValue -> value.toString()
            is ArrayReference -> "${value.type().name().removeSuffix("[]")}[${value.length()}]"
            is ObjectReference -> "${value.referenceType().name()} (id=${value.uniqueID()})"
            else -> value.toString()
        }

    private fun childReference(value: Value?): Int =
        synchronized(lock) {
            when {
                value is ArrayReference && value.length() > 0 -> {
                    val reference = nextVariablesReference++
                    variableContainers[reference] = VariableContainer.ArrayElements(value)
                    reference
                }

                value is ObjectReference &&
                    value !is StringReference &&
                    value
                        .referenceType()
                        .allFields()
                        .any { !it.isStatic } -> {
                    val reference = nextVariablesReference++
                    variableContainers[reference] = VariableContainer.ObjectFields(value)
                    reference
                }

                else -> {
                    0
                }
            }
        }

    private fun pipeOutput(
        stream: InputStream,
        category: OutputCategory,
    ) {
        thread(name = "jdi-output-${category.name.lowercase()}", isDaemon = true) {
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    listener.onOutput(OutputEventBody(category = category, output = line + "\n"))
                }
            }
        }
    }

    private companion object {
        private const val ATTACH_RETRY_DELAY_MS = 500L
        private const val ATTACH_CONNECT_TIMEOUT_MS = 2_000L
        private const val MAX_ARRAY_CHILDREN = 100
        private const val SOURCE_NAME_PROPERTY = "jetaprog.sourceName"
        private val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    }
}
