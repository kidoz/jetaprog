package su.kidoz.jetaprog.app.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.ui.debug.DebugUiState
import su.kidoz.jetaprog.app.viewmodel.DebugViewModel
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.app.viewmodel.GradleViewModel
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.build.gradle.state.GradleState
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IdeMcpSensitiveToolTest {
    @Test
    fun deniesWritesBuildsAndDebugControlUntilTheUserAllowsThem() =
        runTest {
            val root = File(System.getProperty("java.io.tmpdir"), "mcp-gate-${System.nanoTime()}").apply { mkdirs() }
            try {
                val fileSystem = mockk<FileSystem>()
                coEvery { fileSystem.writeText(any(), any(), any()) } returns Result.success(Unit)
                val gradle = mockk<GradleViewModel>(relaxed = true)
                every { gradle.state } returns MutableStateFlow(GradleState())
                val debug = mockk<DebugViewModel>(relaxed = true)
                every { debug.state } returns MutableStateFlow(DebugUiState(hasSession = true))
                var decisions = 0
                val tools =
                    server(session(root, gradle, debug), fileSystem) { _, _ ->
                        decisions += 1
                        false
                    }.tools

                val write =
                    tools.execute(
                        "write_file",
                        buildJsonObject {
                            put("path", "A.kt")
                            put("content", "fun main() {}")
                        },
                    )
                assertEquals(ToolResult.Error("The user denied: Write A.kt"), write)
                coVerify(exactly = 0) { fileSystem.writeText(any(), any(), any()) }

                val option =
                    tools.execute(
                        "run_gradle_task",
                        buildJsonObject { put("task", "--init-script=evil.gradle") },
                    )
                assertEquals(ToolResult.Error("Not a Gradle task path: --init-script=evil.gradle"), option)

                val task = tools.execute("run_gradle_task", buildJsonObject { put("task", "clean") })
                assertEquals(ToolResult.Error("The user denied: Run Gradle task clean"), task)
                verify(exactly = 0) { gradle.dispatch(any()) }

                val step = tools.execute("debug", buildJsonObject { put("action", "step_over") })
                assertEquals(ToolResult.Error("The user denied: Debug step_over"), step)
                coVerify(exactly = 0) { debug.perform(any()) }
                assertEquals(3, decisions)
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun startsATaskPathAfterTheUserAllowsIt() =
        runTest {
            val root = File(System.getProperty("java.io.tmpdir"), "mcp-allow-${System.nanoTime()}").apply { mkdirs() }
            try {
                val gradle = mockk<GradleViewModel>(relaxed = true)
                every { gradle.state } returns MutableStateFlow(GradleState())
                val tools =
                    server(
                        session(root, gradle, mockk(relaxed = true)),
                        mockk(relaxed = true),
                    ) { _, _ ->
                        true
                    }.tools

                val result = tools.execute("run_gradle_task", buildJsonObject { put("task", ":app:test") })

                assertIs<ToolResult.Success>(result)
                verify { gradle.dispatch(GradleIntent.RunTask(":app:test")) }
            } finally {
                root.deleteRecursively()
            }
        }

    private fun session(
        root: File,
        gradle: GradleViewModel,
        debug: DebugViewModel,
    ): ProjectSession {
        val editor = mockk<EditorViewModel>()
        every { editor.state } returns MutableStateFlow(EditorState())
        val project = mockk<ProjectSession>()
        every { project.projectPath } returns root.canonicalPath
        every { project.gradleViewModel } returns gradle
        every { project.debugViewModel } returns debug
        every { project.editorViewModel } returns editor
        return project
    }

    private fun server(
        session: ProjectSession,
        fileSystem: FileSystem,
        approve: suspend (McpSensitiveAction, String) -> Boolean,
    ): EmbeddedMcpServer {
        val mcp = EmbeddedMcpServer(McpServerConfig(enabled = false))
        registerIdeTools(
            server = mcp,
            fileSystem = fileSystem,
            currentSession = { session },
            approve = approve,
        )
        return mcp
    }
}
