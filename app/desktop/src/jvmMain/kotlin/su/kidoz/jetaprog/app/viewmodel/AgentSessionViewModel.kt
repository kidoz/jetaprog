package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.client.StdioAcpTransport
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.FileSystemCapability
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.PermissionOptionKind
import su.kidoz.jetaprog.acp.protocol.PermissionOutcome
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.ToolCallContent
import su.kidoz.jetaprog.acp.protocol.ToolCallStatus
import su.kidoz.jetaprog.acp.protocol.ToolKind
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.app.agent.AgentPrefs
import su.kidoz.jetaprog.app.agent.AgentPrefsStore
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentUiState
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.DiffDecision
import su.kidoz.jetaprog.app.ui.agent.DiffLine
import su.kidoz.jetaprog.app.ui.agent.DiffLineKind
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.app.ui.agent.Presence
import su.kidoz.jetaprog.app.ui.agent.ToolStatus
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Drives the AI agent surface from a single Agent Client Protocol session.
 *
 * Connects to an external coding agent on demand, maps its streamed updates into
 * the surface's [Block] model (text, tool-call, inline-diff and approval cards),
 * services the agent's file-system and permission callbacks via [AcpClientHandler],
 * and persists per-project preferences.
 *
 * @param projectPath the workspace root used as the session working directory.
 * @param fileSystem the file system used to satisfy the agent's file requests.
 * @param defaultAgentCommand the command used to launch the agent process.
 */
public class AgentSessionViewModel(
    private val projectPath: String,
    private val fileSystem: FileSystem,
    defaultAgentCommand: String = DEFAULT_CLAUDE_CODE_COMMAND,
) : Disposable,
    AcpClientHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val agentCommand = defaultAgentCommand
    private val prefsStore = AgentPrefsStore(fileSystem, projectPath)

    private val _state = MutableStateFlow(AgentUiState())

    /** The observable agent surface state. */
    public val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var client: AcpClient? = null
    private var sessionId: String? = null
    private var seq = 0
    private val pendingApprovals = mutableMapOf<String, PendingApproval>()

    init {
        scope.launch {
            val prefs = prefsStore.load()
            _state.update {
                it.copy(
                    permissions = prefs.permissions,
                    model = prefs.model,
                    effort = prefs.effort,
                    docked = prefs.docked,
                )
            }
        }
    }

    /** Dispatches a user [intent]. */
    public fun dispatch(intent: AgentIntent) {
        scope.launch { handle(intent) }
    }

    private suspend fun handle(intent: AgentIntent) {
        when (intent) {
            AgentIntent.EnsureConnected -> {
                ensureConnected()
            }

            is AgentIntent.Send -> {
                handleSend(intent.prompt, intent.context)
            }

            AgentIntent.Stop -> {
                handleStop()
            }

            AgentIntent.NewChat -> {
                _state.update { it.copy(turns = emptyList(), error = null) }
            }

            is AgentIntent.ToggleToolCall -> {
                toggleTool(intent.blockId)
            }

            is AgentIntent.AcceptDiff -> {
                decideDiff(intent.blockId, accept = true)
            }

            is AgentIntent.RejectDiff -> {
                decideDiff(intent.blockId, accept = false)
            }

            AgentIntent.AcceptAll -> {
                decideAll(accept = true)
            }

            AgentIntent.RevertAll -> {
                decideAll(accept = false)
            }

            is AgentIntent.Approve -> {
                resolveApproval(intent.callId, approve = true)
            }

            is AgentIntent.Deny -> {
                resolveApproval(intent.callId, approve = false)
            }

            is AgentIntent.SetPermission -> {
                _state.update { it.copy(permissions = it.permissions + (intent.kind to intent.policy)) }
                persist()
            }

            is AgentIntent.SetModel -> {
                _state.update { it.copy(model = intent.model, effort = intent.effort) }
                persist()
            }

            AgentIntent.ExpandToPerspective -> {
                _state.update { it.copy(docked = false) }
                persist()
            }

            AgentIntent.DockToToolWindow -> {
                _state.update { it.copy(docked = true) }
                persist()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------------

    private suspend fun ensureConnected(): Boolean {
        if (client != null && _state.value.connection == AgentConnection.CONNECTED) return true
        if (agentCommand.isBlank()) {
            _state.update { it.copy(connection = AgentConnection.ERROR, error = "No agent command configured") }
            return false
        }
        _state.update { it.copy(connection = AgentConnection.CONNECTING, error = null) }
        return try {
            openConnection()
            true
        } catch (e: Exception) {
            _state.update {
                it.copy(connection = AgentConnection.ERROR, error = e.message ?: "Failed to connect to agent")
            }
            false
        }
    }

    private suspend fun openConnection() {
        val stdioTransport =
            StdioAcpTransport(
                StdioAgentConfig(
                    command = agentCommand.split(Regex("\\s+")),
                    workingDirectory = projectPath,
                ),
            )
        stdioTransport.start()

        val acpClient =
            AcpClient(
                transport = stdioTransport,
                scope = scope,
                capabilities =
                    ClientCapabilities(
                        fs = FileSystemCapability(readTextFile = true, writeTextFile = true),
                        terminal = false,
                    ),
                clientInfo = Implementation("JetaProg", "1.0"),
                handler = this,
            )
        client = acpClient

        scope.launch {
            acpClient.sessionUpdates.collect { notification -> applyUpdate(notification.update) }
        }

        val initialize = acpClient.initialize()
        val session = acpClient.newSession(cwd = projectPath)
        sessionId = session.sessionId
        _state.update {
            it.copy(
                connection = AgentConnection.CONNECTED,
                agentName = initialize.agentInfo?.name,
                error = null,
            )
        }
    }

    // ------------------------------------------------------------------------
    // Turns
    // ------------------------------------------------------------------------

    private suspend fun handleSend(
        prompt: String,
        context: List<String>,
    ) {
        val text = prompt.trim()
        if (text.isEmpty() || _state.value.isStreaming) return
        if (!ensureConnected()) return
        val acpClient = client ?: return
        val activeSession = sessionId ?: return

        _state.update {
            it.copy(
                turns =
                    it.turns +
                        Turn.User(nextId("usr"), text, "now", context) +
                        Turn.Agent(nextId("agt"), "now", emptyList()),
                isStreaming = true,
                error = null,
                presence = Presence(action = "Thinking", fileName = null, startedAtEpochMillis = now()),
            )
        }

        try {
            acpClient.promptText(activeSession, text)
            endTurn()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "The agent run failed") }
            endTurn()
        }
    }

    private suspend fun handleStop() {
        val acpClient = client ?: return
        val activeSession = sessionId ?: return
        acpClient.cancel(activeSession)
        endTurn()
    }

    private fun endTurn() {
        _state.update { st ->
            val turns =
                st.turns.map { turn ->
                    if (turn is Turn.Agent) {
                        turn.copy(blocks = turn.blocks.map { if (it is Block.Text) it.copy(streaming = false) else it })
                    } else {
                        turn
                    }
                }
            st.copy(isStreaming = false, presence = null, turns = turns)
        }
    }

    // ------------------------------------------------------------------------
    // ACP session updates -> blocks
    // ------------------------------------------------------------------------

    private fun applyUpdate(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                appendText(textOf(update.content), thought = false)
            }

            is SessionUpdate.AgentThoughtChunk -> {
                appendText(textOf(update.content), thought = true)
            }

            is SessionUpdate.ToolCallStart -> {
                applyToolCall(
                    id = update.toolCallId,
                    title = update.title,
                    kind = update.kind,
                    status = update.status,
                    content = update.content,
                    location = update.locations.firstOrNull()?.path,
                    rawInput = update.rawInput,
                )
            }

            is SessionUpdate.ToolCallProgress -> {
                applyToolCall(
                    id = update.toolCallId,
                    title = update.title,
                    kind = null,
                    status = update.status,
                    content = update.content,
                    location = null,
                    rawInput = null,
                )
            }

            is SessionUpdate.UserMessageChunk,
            is SessionUpdate.Plan,
            is SessionUpdate.AvailableCommandsUpdate,
            is SessionUpdate.CurrentModeUpdate,
            -> {
                // These updates are not surfaced on the agent UI.
            }
        }
    }

    private fun appendText(
        text: String,
        thought: Boolean,
    ) {
        if (text.isEmpty()) return
        mutateAgentTurn { blocks ->
            val last = blocks.lastOrNull()
            if (last is Block.Text && last.thought == thought && last.streaming) {
                blocks.dropLast(1) + last.copy(text = last.text + text)
            } else {
                stopStreaming(blocks) + Block.Text(nextId("txt"), text, thought = thought, streaming = true)
            }
        }
    }

    private fun applyToolCall(
        id: String,
        title: String?,
        kind: ToolKind?,
        status: ToolCallStatus?,
        content: List<ToolCallContent>,
        location: String?,
        rawInput: JsonElement?,
    ) {
        val resultText =
            content
                .filter { it.type != "diff" }
                .mapNotNull {
                    it.content?.let(
                        ::textOf,
                    )
                }.joinToString("\n")
        val toolStatus = toolStatusOf(status)
        mutateAgentTurn { blocks ->
            val stopped = stopStreaming(blocks)
            val index = stopped.indexOfFirst { it is Block.Tool && it.id == id }
            val updated =
                if (index >= 0) {
                    val existing = stopped[index] as Block.Tool
                    stopped.toMutableList().also {
                        it[index] =
                            existing.copy(
                                name = title ?: existing.name,
                                status = toolStatus,
                                result = mergeResult(existing.result, resultText),
                            )
                    }
                } else {
                    stopped +
                        Block.Tool(
                            id = id,
                            name = title ?: id,
                            kind = kind?.value ?: ToolKind.OTHER.value,
                            args = summarizeArgs(rawInput),
                            status = toolStatus,
                            result = resultText.ifBlank { null },
                            expanded = false,
                        )
                }
            content.filter { it.type == "diff" }.fold(updated) { acc, diff -> upsertDiff(acc, id, diff) }
        }
        updatePresence(kind, toolStatus, location)
    }

    private fun upsertDiff(
        blocks: List<Block>,
        toolCallId: String,
        content: ToolCallContent,
    ): List<Block> {
        val path = content.path ?: return blocks
        val diffId = "$toolCallId:diff"
        val (lines, added, removed) = computeDiff(content.oldText, content.newText.orEmpty())
        val block =
            Block.Diff(
                id = diffId,
                fileName = path.substringAfterLast('/'),
                path = path.substringBeforeLast('/', missingDelimiterValue = ""),
                added = added,
                removed = removed,
                lines = lines,
                decision = DiffDecision.PROPOSED,
                absolutePath = path,
                oldText = content.oldText,
            )
        val index = blocks.indexOfFirst { it is Block.Diff && it.id == diffId }
        return if (index >= 0) {
            blocks.toMutableList().also { it[index] = block }
        } else {
            blocks + block
        }
    }

    private fun updatePresence(
        kind: ToolKind?,
        status: ToolStatus,
        location: String?,
    ) {
        if (status != ToolStatus.RUNNING) return
        _state.update { st ->
            if (!st.isStreaming) {
                st
            } else {
                st.copy(
                    presence =
                        Presence(
                            action = presenceAction(kind),
                            fileName = location?.substringAfterLast('/') ?: st.presence?.fileName,
                            startedAtEpochMillis = st.presence?.startedAtEpochMillis ?: now(),
                        ),
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // Card actions
    // ------------------------------------------------------------------------

    private fun toggleTool(blockId: String) {
        mutateAgentTurn { blocks ->
            blocks.map { if (it is Block.Tool && it.id == blockId) it.copy(expanded = !it.expanded) else it }
        }
    }

    private suspend fun decideDiff(
        blockId: String,
        accept: Boolean,
    ) {
        val diff = currentDiffs().firstOrNull { it.id == blockId } ?: return
        if (!accept && diff.oldText != null) {
            fileSystem.writeText(diff.absolutePath, diff.oldText)
        }
        setDiffDecision(blockId, if (accept) DiffDecision.ACCEPTED else DiffDecision.REJECTED)
    }

    private suspend fun decideAll(accept: Boolean) {
        currentDiffs().filter { it.decision == DiffDecision.PROPOSED }.forEach { decideDiff(it.id, accept) }
    }

    private fun setDiffDecision(
        blockId: String,
        decision: DiffDecision,
    ) {
        mutateAgentTurn { blocks ->
            blocks.map { if (it is Block.Diff && it.id == blockId) it.copy(decision = decision) else it }
        }
    }

    private fun currentDiffs(): List<Block.Diff> =
        (_state.value.turns.lastOrNull() as? Turn.Agent)?.blocks?.filterIsInstance<Block.Diff>().orEmpty()

    private fun resolveApproval(
        callId: String,
        approve: Boolean,
    ) {
        val pending = pendingApprovals.remove(callId) ?: return
        val optionId = if (approve) pending.allowOptionId else pending.rejectOptionId
        val outcome = if (optionId != null) PermissionOutcome.Selected(optionId) else PermissionOutcome.Cancelled
        pending.deferred.complete(RequestPermissionResponse(outcome))
        mutateAgentTurn { blocks ->
            blocks.map { if (it is Block.Approval && it.id == callId) it.copy(resolved = true) else it }
        }
    }

    // ------------------------------------------------------------------------
    // AcpClientHandler
    // ------------------------------------------------------------------------

    override suspend fun readTextFile(request: ReadTextFileRequest): ReadTextFileResponse {
        val text = fileSystem.readText(request.path).getOrElse { throw it }
        val sliced =
            if (request.line != null || request.limit != null) {
                val lines = text.lines()
                val start = ((request.line ?: 1) - 1).coerceIn(0, lines.size)
                val end = request.limit?.let { (start + it).coerceAtMost(lines.size) } ?: lines.size
                lines.subList(start, end).joinToString("\n")
            } else {
                text
            }
        return ReadTextFileResponse(sliced)
    }

    override suspend fun writeTextFile(request: WriteTextFileRequest) {
        fileSystem.writeText(request.path, request.content).getOrElse { throw it }
    }

    override suspend fun requestPermission(request: RequestPermissionRequest): RequestPermissionResponse {
        val kind = permissionKindFor(request.toolCall.kind)
        val policy = _state.value.permissions[kind] ?: PermissionPolicy.ASK
        val allow =
            request.options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE }?.optionId
                ?: request.options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }?.optionId
        val reject =
            request.options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ONCE }?.optionId
                ?: request.options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ALWAYS }?.optionId

        return when (policy) {
            PermissionPolicy.AUTO -> {
                RequestPermissionResponse(outcomeFor(allow))
            }

            PermissionPolicy.NEVER -> {
                RequestPermissionResponse(outcomeFor(reject))
            }

            PermissionPolicy.ASK -> {
                val deferred = CompletableDeferred<RequestPermissionResponse>()
                val callId = request.toolCall.toolCallId
                pendingApprovals[callId] = PendingApproval(deferred, allow, reject)
                mutateAgentTurn { blocks ->
                    blocks + Block.Approval(callId, request.toolCall.title ?: "Tool call", commandFor(request.toolCall))
                }
                deferred.await()
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private suspend fun persist() {
        val st = _state.value
        prefsStore.save(AgentPrefs(st.permissions, st.model, st.effort, st.docked))
    }

    private fun mutateAgentTurn(transform: (List<Block>) -> List<Block>) {
        _state.update { st ->
            val index = st.turns.indexOfLast { it is Turn.Agent }
            if (index < 0) {
                st
            } else {
                val turn = st.turns[index] as Turn.Agent
                st.copy(
                    turns = st.turns.toMutableList().also { it[index] = turn.copy(blocks = transform(turn.blocks)) },
                )
            }
        }
    }

    private fun nextId(prefix: String): String = "$prefix-${seq++}"

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        private const val MAX_RESULT_CHARS = 4000
        private const val MAX_DIFF_BODY_LINES = 10
        private const val CONTEXT_LINES = 2

        /**
         * Default launch command driving Claude Code over ACP via the Zed adapter.
         * `npx -y` resolves and runs the published adapter without an interactive
         * install prompt, reusing the user's existing Claude Code authentication.
         */
        const val DEFAULT_CLAUDE_CODE_COMMAND: String = "npx -y @zed-industries/claude-code-acp"

        fun textOf(content: ContentBlock): String = (content as? ContentBlock.Text)?.text.orEmpty()

        fun stopStreaming(blocks: List<Block>): List<Block> =
            blocks.map { if (it is Block.Text && it.streaming) it.copy(streaming = false) else it }

        fun mergeResult(
            existing: String?,
            delta: String,
        ): String? {
            if (delta.isBlank()) return existing
            val combined = if (existing.isNullOrBlank()) delta else "$existing\n$delta"
            return combined.take(MAX_RESULT_CHARS)
        }

        fun toolStatusOf(status: ToolCallStatus?): ToolStatus =
            when (status) {
                ToolCallStatus.COMPLETED -> ToolStatus.OK
                ToolCallStatus.FAILED -> ToolStatus.FAILED
                else -> ToolStatus.RUNNING
            }

        fun presenceAction(kind: ToolKind?): String =
            when (kind) {
                ToolKind.READ, ToolKind.SEARCH -> "Reading"
                ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE -> "Editing"
                ToolKind.EXECUTE -> "Running"
                ToolKind.FETCH -> "Fetching"
                else -> "Working"
            }

        fun permissionKindFor(kind: ToolKind?): PermissionKind =
            when (kind) {
                ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE -> PermissionKind.EDIT
                ToolKind.EXECUTE -> PermissionKind.RUN
                else -> PermissionKind.READ
            }

        fun outcomeFor(optionId: String?): PermissionOutcome =
            if (optionId != null) PermissionOutcome.Selected(optionId) else PermissionOutcome.Cancelled

        fun commandFor(toolCall: PermissionToolCall): String? =
            stringField(toolCall.rawInput, "command", "cmd") ?: toolCall.title

        fun summarizeArgs(raw: JsonElement?): String {
            val obj = raw as? JsonObject ?: return ""
            for (key in listOf("path", "file_path", "filePath", "abs_path")) {
                stringValue(obj[key])?.let { return it.substringAfterLast('/') }
            }
            for (key in listOf("command", "cmd", "pattern", "query", "url")) {
                stringValue(obj[key])?.let { return it }
            }
            return ""
        }

        fun stringField(
            raw: JsonElement?,
            vararg keys: String,
        ): String? {
            val obj = raw as? JsonObject ?: return null
            for (key in keys) stringValue(obj[key])?.let { return it }
            return null
        }

        fun stringValue(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

        fun computeDiff(
            oldText: String?,
            newText: String,
        ): Triple<List<DiffLine>, Int, Int> {
            val oldLines = oldText?.split("\n").orEmpty()
            val newLines = newText.split("\n")
            var prefix = 0
            while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
            var suffix = 0
            while (
                suffix < oldLines.size - prefix &&
                suffix < newLines.size - prefix &&
                oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
            ) {
                suffix++
            }
            val removed = oldLines.subList(prefix, oldLines.size - suffix).take(MAX_DIFF_BODY_LINES)
            val added = newLines.subList(prefix, newLines.size - suffix).take(MAX_DIFF_BODY_LINES)
            val before = oldLines.subList(maxOf(0, prefix - CONTEXT_LINES), prefix)

            val lines = mutableListOf<DiffLine>()
            var oldNo = prefix - before.size + 1
            var newNo = prefix - before.size + 1
            before.forEach {
                lines += DiffLine(newNo, ' ', it, DiffLineKind.CONTEXT)
                oldNo++
                newNo++
            }
            removed.forEach { lines += DiffLine(oldNo++, '-', it, DiffLineKind.REMOVED) }
            added.forEach { lines += DiffLine(newNo++, '+', it, DiffLineKind.ADDED) }
            return Triple(lines, added.size, removed.size)
        }
    }

    private data class PendingApproval(
        val deferred: CompletableDeferred<RequestPermissionResponse>,
        val allowOptionId: String?,
        val rejectOptionId: String?,
    )
}
