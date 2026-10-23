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
import su.kidoz.jetaprog.app.inspection.FileInspection
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * run_inspection inspects one file from disk or from the open editor buffer.
 */
class IdeMcpInspectionToolTest {
    @Test
    fun reportsFindingsForAProjectFile() =
        runTest {
            val root = tempRoot()
            try {
                val source = File(root, "src/A.kt")
                source.parentFile.mkdirs()
                source.writeText("placeholder\n")
                val session = session(root.canonicalFile)
                val fileSystem = mockk<FileSystem>(relaxed = true)
                coEvery { fileSystem.readText(any()) } returns Result.success("val x = 1 \n")
                coEvery { session.inspectFile(any(), "val x = 1 \n", "kotlin") } returns
                    FileInspection.Findings(listOf(trailingWhitespace()))

                val result =
                    server(session, fileSystem).tools.execute(
                        "run_inspection",
                        buildJsonObject { put("path", "src/A.kt") },
                    )

                assertEquals(
                    ToolResult.Success(
                        listOf(
                            ToolContent.Text(
                                "[WARNING] style/trailing-whitespace src/A.kt:1: Line has trailing whitespace",
                            ),
                        ),
                    ),
                    result,
                )
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun usesTheOpenEditorBufferWhenNoPathIsGiven() =
        runTest {
            val root = tempRoot()
            try {
                val source = File(root, "src/A.kt")
                source.parentFile.mkdirs()
                source.writeText("on disk\n")
                val session =
                    session(
                        root.canonicalFile,
                        EditorState(
                            activeDocumentUri = DocumentUri.file(source.canonicalPath),
                            content = "var dirty = 1 \n",
                        ),
                    )
                coEvery { session.inspectFile(any(), "var dirty = 1 \n", "kotlin") } returns
                    FileInspection.Findings(emptyList())
                val fileSystem = mockk<FileSystem>(relaxed = true)

                val result = server(session, fileSystem).tools.execute("run_inspection", buildJsonObject {})

                assertEquals(ToolResult.Success(listOf(ToolContent.Text("No problems."))), result)
                coVerify(exactly = 0) { fileSystem.readText(any()) }
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun rejectsADirectory() =
        runTest {
            val root = tempRoot()
            try {
                File(root, "src").mkdirs()
                val session = session(root.canonicalFile)
                val result =
                    server(session).tools.execute(
                        "run_inspection",
                        buildJsonObject { put("path", "src") },
                    )

                val error = assertIs<ToolResult.Error>(result)
                assertEquals("Pass a file to inspect.", error.message)
                coVerify(exactly = 0) { session.inspectFile(any(), any(), any()) }
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun needsAnOpenProject() =
        runTest {
            val result =
                server(session = null).tools.execute(
                    "run_inspection",
                    buildJsonObject { put("path", "src/A.kt") },
                )

            assertEquals(ToolResult.Success(listOf(ToolContent.Text("No project is open."))), result)
        }

    @Test
    fun needsAFileWhenNothingIsOpen() =
        runTest {
            val root = tempRoot()
            try {
                val result = server(session(root)).tools.execute("run_inspection", buildJsonObject {})

                assertEquals(
                    ToolResult.Success(listOf(ToolContent.Text("No file is open. Pass a path to inspect."))),
                    result,
                )
            } finally {
                root.deleteRecursively()
            }
        }

    private fun session(
        root: File,
        editorState: EditorState = EditorState(),
    ): ProjectSession {
        val editor = mockk<EditorViewModel>()
        every { editor.state } returns MutableStateFlow(editorState)
        val project = mockk<ProjectSession>()
        every { project.projectPath } returns root.canonicalPath
        every { project.editorViewModel } returns editor
        coEvery { project.inspectFile(any(), any(), any()) } returns FileInspection.Findings(emptyList())
        return project
    }

    private fun server(
        session: ProjectSession?,
        fileSystem: FileSystem = mockk(relaxed = true),
    ): EmbeddedMcpServer {
        val mcp = EmbeddedMcpServer(McpServerConfig(enabled = false))
        registerIdeTools(mcp, fileSystem, currentSession = { session })
        return mcp
    }

    private fun trailingWhitespace(): LintResult =
        LintResult(
            ruleId = LintRuleId.of("style", "trailing-whitespace"),
            message = "Line has trailing whitespace",
            range = TextRange(TextPosition(0, 8), TextPosition(0, 9)),
            severity = LintSeverity.WARNING,
            category = LintCategory.STYLE,
        )

    private fun tempRoot(): File =
        File(System.getProperty("java.io.tmpdir"), "inspect-${System.nanoTime()}").apply { mkdirs() }
}
