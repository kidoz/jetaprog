package su.kidoz.jetaprog.app.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.ui.debug.BreakpointView
import su.kidoz.jetaprog.app.ui.debug.DebugIntent
import su.kidoz.jetaprog.app.ui.debug.DebugStatus
import su.kidoz.jetaprog.app.ui.debug.DebugUiState
import su.kidoz.jetaprog.app.ui.debug.FrameView
import su.kidoz.jetaprog.app.ui.debug.SourceLocation
import su.kidoz.jetaprog.app.viewmodel.DebugViewModel
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The debug tool reports the existing debugger and toggles breakpoints. Stepping
 * waits for a session that is already running.
 */
class IdeMcpDebugToolTest {
    @Test
    fun reportsTheSessionBreakpointsAndStop() {
        val report =
            formatDebug(
                "/proj",
                DebugUiState(
                    status = DebugStatus.PAUSED,
                    hasSession = true,
                    stoppedAt = SourceLocation("/proj/src/A.kt", 4),
                    frames = listOf(FrameView(1, "main", "A.kt:4", isCurrent = true)),
                    variableValues = mapOf("name" to "\"Ada\""),
                    breakpoints = listOf(breakpoint("/proj/src/A.kt", 4)),
                ),
            )

        assertEquals(
            listOf(
                "status: PAUSED",
                "stopped: src/A.kt:4",
                "frames:",
                "* main A.kt:4",
                "variables:",
                "  name = \"Ada\"",
                "breakpoints:",
                "  src/A.kt:4 enabled",
            ).joinToString("\n"),
            report,
        )
    }

    @Test
    fun statusListsBreakpointsWhenNothingIsRunning() =
        runTest {
            val debug = debugVm(DebugUiState(breakpoints = listOf(breakpoint("/proj/src/A.kt", 4))))
            val result = server(session(debug)).tools.execute("debug", buildJsonObject {})

            assertEquals(
                text("status: TERMINATED\nNo debug session is running.\nbreakpoints:\n  src/A.kt:4 enabled"),
                result,
            )
        }

    @Test
    fun togglesABreakpointOnAProjectFile() =
        runTest {
            val root = tempRoot()
            try {
                val source = File(root, "src/A.kt")
                source.parentFile.mkdirs()
                source.writeText("fun main() {}\n")
                val added =
                    DebugUiState(
                        breakpoints = listOf(breakpoint(source.canonicalPath, 4)),
                    )
                val debug = debugVm(DebugUiState())
                coEvery { debug.perform(DebugIntent.ToggleBreakpoint(source.canonicalPath, 4)) } returns added
                val session = session(debug, root.canonicalPath)

                val result =
                    server(session).tools.execute(
                        "debug",
                        buildJsonObject {
                            put("action", "breakpoint")
                            put("path", "src/A.kt")
                            put("line", "4")
                        },
                    )

                assertEquals(
                    text(
                        "requested: breakpoint\nstatus: TERMINATED\nNo debug session is running.\nbreakpoints:\n  src/A.kt:4 enabled",
                    ),
                    result,
                )
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun refusesToStepWithoutASession() =
        runTest {
            val debug = debugVm(DebugUiState())
            val result =
                server(session(debug)).tools.execute(
                    "debug",
                    buildJsonObject { put("action", "step_over") },
                )

            assertEquals(
                text("No debug session is running.\nstatus: TERMINATED\nNo debug session is running."),
                result,
            )
            coVerify(exactly = 0) { debug.perform(any()) }
        }

    @Test
    fun stepsARunningSession() =
        runTest {
            val paused =
                DebugUiState(
                    status = DebugStatus.PAUSED,
                    hasSession = true,
                    stoppedAt = SourceLocation("/proj/src/A.kt", 4),
                )
            val debug = debugVm(paused)
            coEvery { debug.perform(DebugIntent.StepOver) } returns paused

            val result =
                server(session(debug)).tools.execute(
                    "debug",
                    buildJsonObject { put("action", "step_over") },
                )

            assertEquals(text("requested: step_over\nstatus: PAUSED\nstopped: src/A.kt:4"), result)
        }

    @Test
    fun rejectsAnUnknownActionAndABadLine() =
        runTest {
            val debug = debugVm(DebugUiState())
            val session = session(debug)
            val tools = server(session).tools

            val unknown = tools.execute("debug", buildJsonObject { put("action", "launch") })
            assertEquals(ToolResult.Error("Unknown debug action: launch"), unknown)

            val line =
                tools.execute(
                    "debug",
                    buildJsonObject {
                        put("action", "breakpoint")
                        put("line", "0")
                    },
                )
            assertEquals(ToolResult.Error("Pass a 1-based line."), line)
            coVerify(exactly = 0) { debug.perform(any()) }
        }

    @Test
    fun needsAnOpenProject() =
        runTest {
            val result = server(null).tools.execute("debug", buildJsonObject {})
            assertEquals(text("No project is open."), result)
        }

    private fun breakpoint(
        file: String,
        line: Int,
    ): BreakpointView = BreakpointView(file, line, enabled = true, verified = false, condition = null)

    private fun debugVm(state: DebugUiState): DebugViewModel {
        val debug = mockk<DebugViewModel>()
        every { debug.state } returns MutableStateFlow(state)
        return debug
    }

    private fun session(
        debug: DebugViewModel,
        projectPath: String = "/proj",
    ): ProjectSession {
        val editor = mockk<EditorViewModel>()
        every { editor.state } returns MutableStateFlow(EditorState())
        val project = mockk<ProjectSession>()
        every { project.projectPath } returns projectPath
        every { project.debugViewModel } returns debug
        every { project.editorViewModel } returns editor
        return project
    }

    private fun server(session: ProjectSession?): EmbeddedMcpServer {
        val mcp = EmbeddedMcpServer(McpServerConfig(enabled = false))
        registerIdeTools(mcp, mockk<FileSystem>(relaxed = true), currentSession = { session })
        return mcp
    }

    private fun text(value: String): ToolResult = ToolResult.Success(listOf(ToolContent.Text(value)))

    private fun tempRoot(): File =
        File(System.getProperty("java.io.tmpdir"), "debug-${System.nanoTime()}").apply { mkdirs() }
}
