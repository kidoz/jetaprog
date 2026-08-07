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
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.prompts.Prompt
import su.kidoz.jetaprog.mcp.server.prompts.PromptArgument
import su.kidoz.jetaprog.mcp.server.resources.Resource
import su.kidoz.jetaprog.mcp.server.resources.ResourceContent
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
        val base = currentSession()?.projectPath ?: error("No project is open")
        val root = File(base).canonicalFile
        val target = (if (File(path).isAbsolute) File(path) else File(root, path)).canonicalFile
        require(target == root || target.path.startsWith(root.path + File.separator)) {
            "Path is outside the open project"
        }
        return target.path
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
            description = "Get current errors and warnings across the open workspace.",
            inputSchema = objectSchema(required = emptyList()),
        ) {
            val session = currentSession() ?: return@Tool text("No project is open.")
            val diagnostics = session.editorViewModel.state.value.workspaceDiagnostics
            if (diagnostics.isEmpty()) {
                text("No workspace problems.")
            } else {
                text(
                    diagnostics.joinToString("\n") { workspaceDiagnostic ->
                        val diagnostic = workspaceDiagnostic.diagnostic
                        val tag = if (diagnostic.severity == DiagnosticSeverity.ERROR) "ERROR" else "WARN"
                        val path = workspaceDiagnostic.uri.value.removePrefix("file://")
                        "[$tag] $path:${diagnostic.range.start.line + 1}: ${diagnostic.message}"
                    },
                )
            }
        },
    )

    server.tools.register(
        Tool(
            name = "search_text",
            description = "Search project files for literal text and return matching file names and lines.",
            inputSchema =
                objectSchema(
                    "query" to "Literal text to find",
                    "path" to "Optional project-relative directory (defaults to project root)",
                    required = listOf("query"),
                ),
        ) { args ->
            val query = args.string("query") ?: return@Tool missing("query")
            val session = currentSession() ?: return@Tool text("No project is open.")
            val projectRoot = File(session.projectPath)
            val searchRoot = resolve(args.string("path") ?: ".")
            val matches =
                File(searchRoot)
                    .walkTopDown()
                    .onEnter { directory -> directory.name !in SEARCH_EXCLUDED_DIRECTORIES }
                    .filter(File::isFile)
                    .flatMap { file ->
                        runCatching {
                            file.useLines { lines ->
                                lines
                                    .mapIndexedNotNull { index, line ->
                                        if (query in line) {
                                            buildString {
                                                append(file.relativeTo(projectRoot))
                                                append(":${index + 1}:")
                                                append(line)
                                            }
                                        } else {
                                            null
                                        }
                                    }.toList()
                                    .asSequence()
                            }
                        }.getOrDefault(emptySequence())
                    }.take(MAX_SEARCH_RESULTS)
                    .toList()
            text(matches.joinToString("\n").ifEmpty { "No matches." })
        },
    )

    server.tools.register(
        Tool(
            name = "run_gradle_task",
            description = "Start a Gradle task in the open project; inspect build and test state afterward.",
            inputSchema = objectSchema("task" to "Gradle task path", required = listOf("task")),
        ) { args ->
            val task = args.string("task") ?: return@Tool missing("task")
            val session = currentSession() ?: return@Tool text("No project is open.")
            if (session.gradleViewModel.state.value.isRunning) {
                return@Tool ToolResult.Error("A Gradle task is already running")
            }
            session.gradleViewModel
                .dispatch(GradleIntent.RunTask(task))
            text("Started Gradle task: $task")
        },
    )

    server.resources.register(
        Resource(
            uri = "jetaprog://project/context",
            name = "JetaProg project context",
            description = "Live project, editor, diagnostics, build, and test context.",
            mimeType = "text/plain",
        ) {
            val session =
                currentSession()
                    ?: return@Resource ResourceContent.Text("No project is open.")
            val editor = session.editorViewModel.state.value
            val gradle = session.gradleViewModel.state.value
            ResourceContent.Text(
                buildString {
                    appendLine("project: ${session.projectPath}")
                    appendLine("branch: ${session.gitViewModel.state.value.branch ?: "(detached)"}")
                    appendLine("open files: ${editor.tabs.joinToString { it.name }}")
                    appendLine("problems: ${editor.workspaceDiagnostics.size}")
                    appendLine("build running: ${gradle.isRunning}")
                    gradle.lastBuildResult?.let { appendLine("last build succeeded: ${it.success}") }
                    gradle.testRun?.let {
                        appendLine(
                            "tests: ${it.passedCount} passed, ${it.failedCount} failed, " +
                                "${it.skippedCount} skipped",
                        )
                    }
                },
            )
        },
    )

    server.resources.register(
        Resource(
            uri = "jetaprog://editor/active",
            name = "Active editor document",
            description = "Unsaved content of the active editor document.",
            mimeType = "text/plain",
        ) {
            val state = currentSession()?.editorViewModel?.state?.value
            ResourceContent.Text(
                state?.activeDocumentUri?.let { "${it.value}\n\n${state.content}" } ?: "No active document.",
            )
        },
    )

    server.prompts.register(
        Prompt(
            name = "self_host",
            description = "Plan and implement a change in the JetaProg codebase using live IDE context.",
            arguments = listOf(PromptArgument("goal", "The JetaProg change to implement", required = true)),
        ) { args ->
            val goal = args.string("goal") ?: "Improve JetaProg"
            "Work on the open JetaProg project. Goal: $goal. " +
                "Inspect jetaprog://project/context first, preserve existing changes, run focused tests, and report verification."
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

private val SEARCH_EXCLUDED_DIRECTORIES = setOf(".git", ".gradle", ".idea", ".jetaprog", "build", "node_modules")
private const val MAX_SEARCH_RESULTS = 200
