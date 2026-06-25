package su.kidoz.jetaprog.app.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.tools.Tool
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.io.File

/**
 * Registers the IDE's tools on the embedded MCP [server], exposing live workspace
 * context to external agents (a terminal `claude`/`codex`). Handlers read the
 * **current** [ProjectSession] each call via [currentSession], so they always
 * reflect the open project.
 */
public fun registerIdeTools(
    server: EmbeddedMcpServer,
    fileSystem: FileSystem,
    currentSession: () -> ProjectSession?,
) {
    fun resolve(path: String): String {
        val base = currentSession()?.projectPath
        return if (base != null && !File(path).isAbsolute) File(base, path).path else path
    }

    server.tools.register(
        Tool(
            name = "read_file",
            description = "Read a text file from the open project. Paths may be relative to the project root.",
            inputSchema = objectSchema("path" to "File path to read", required = listOf("path")),
        ) { args ->
            val path = args.string("path") ?: return@Tool missing("path")
            fileSystem.readText(resolve(path)).fold(
                onSuccess = { text(it) },
                onFailure = { ToolResult.Error("Failed to read $path: ${it.message}") },
            )
        },
    )

    server.tools.register(
        Tool(
            name = "write_file",
            description = "Write (create or overwrite) a text file in the open project.",
            inputSchema =
                objectSchema(
                    "path" to "File path to write",
                    "content" to "Full new file contents",
                    required = listOf("path", "content"),
                ),
        ) { args ->
            val path = args.string("path") ?: return@Tool missing("path")
            val content = args.string("content") ?: return@Tool missing("content")
            fileSystem.writeText(resolve(path), content).fold(
                onSuccess = { text("Wrote ${content.length} chars to $path") },
                onFailure = { ToolResult.Error("Failed to write $path: ${it.message}") },
            )
        },
    )

    server.tools.register(
        Tool(
            name = "list_directory",
            description = "List the entries of a directory in the open project.",
            inputSchema = objectSchema("path" to "Directory path to list", required = listOf("path")),
        ) { args ->
            val path = args.string("path") ?: return@Tool missing("path")
            fileSystem.listDirectory(resolve(path)).fold(
                onSuccess = { entries ->
                    text(entries.joinToString("\n") { (if (it.isDirectory) "[dir]  " else "[file] ") + it.name })
                },
                onFailure = { ToolResult.Error("Failed to list $path: ${it.message}") },
            )
        },
    )

    server.tools.register(
        Tool(
            name = "project_info",
            description = "Get information about the currently open project (path, name, branch).",
            inputSchema = objectSchema(required = emptyList()),
        ) {
            val session = currentSession() ?: return@Tool text("No project is open.")
            val branch = session.gitViewModel.state.value.branch ?: "(no branch)"
            text("name: ${File(session.projectPath).name}\npath: ${session.projectPath}\nbranch: $branch")
        },
    )

    server.tools.register(
        Tool(
            name = "git_status",
            description = "Get the Git status of the open project: branch, staged and unstaged changes.",
            inputSchema = objectSchema(required = emptyList()),
        ) {
            val session = currentSession() ?: return@Tool text("No project is open.")
            val git = session.gitViewModel.state.value
            val staged = git.staged.joinToString("\n") { "  + ${it.type} ${it.path}" }.ifEmpty { "  (none)" }
            val unstaged = git.unstaged.joinToString("\n") { "  + ${it.type} ${it.path}" }.ifEmpty { "  (none)" }
            text(
                "branch: ${git.branch ?: "(detached)"}  ↑${git.ahead} ↓${git.behind}\nStaged:\n$staged\nUnstaged:\n$unstaged",
            )
        },
    )

    server.tools.register(
        Tool(
            name = "get_diagnostics",
            description = "Get the current errors and warnings for the active editor document.",
            inputSchema = objectSchema(required = emptyList()),
        ) {
            val session = currentSession() ?: return@Tool text("No project is open.")
            val diagnostics = session.editorViewModel.state.value.diagnostics
            if (diagnostics.isEmpty()) {
                text("No problems in the active document.")
            } else {
                text(
                    diagnostics.joinToString("\n") { diagnostic ->
                        val tag = if (diagnostic.severity == DiagnosticSeverity.ERROR) "ERROR" else "WARN"
                        "[$tag] ${diagnostic.message}"
                    },
                )
            }
        },
    )
}

private fun objectSchema(
    vararg properties: Pair<String, String>,
    required: List<String>,
): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (name, description) ->
                putJsonObject(name) {
                    put("type", "string")
                    put("description", description)
                }
            }
        }
        putJsonArray("required") { required.forEach { add(it) } }
    }

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun text(value: String): ToolResult = ToolResult.Success(listOf(ToolContent.Text(value)))

private fun missing(parameter: String): ToolResult = ToolResult.Error("Missing required parameter: $parameter")
