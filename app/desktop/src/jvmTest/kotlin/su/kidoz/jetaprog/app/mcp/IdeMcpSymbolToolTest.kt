package su.kidoz.jetaprog.app.mcp

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.app.ProjectSession
import su.kidoz.jetaprog.app.navigation.IndexedSymbolLookup
import su.kidoz.jetaprog.mcp.server.EmbeddedMcpServer
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The search_symbol tool reports index hits for the open project and leaves the
 * call unanswered when no project is open.
 */
class IdeMcpSymbolToolTest {
    @Test
    fun reportsTheOpenProjectsIndexedSymbols() =
        runTest {
            val session = mockk<ProjectSession>()
            every { session.projectPath } returns "/proj"
            coEvery { session.searchIndexedSymbols("Greeter", any()) } returns
                listOf(
                    IndexedSymbolLookup(
                        name = "Greeter",
                        kind = "CLASS",
                        filePath = "/proj/src/Greeter.java",
                        line = 2,
                    ),
                )
            val server = server(session)

            val result = server.tools.execute("search_symbol", buildJsonObject { put("query", "Greeter") })

            assertEquals(
                ToolResult.Success(listOf(ToolContent.Text("Greeter CLASS src/Greeter.java:3"))),
                result,
            )
        }

    @Test
    fun needsAnOpenProject() =
        runTest {
            val server = server(session = null)

            val result = server.tools.execute("search_symbol", buildJsonObject { put("query", "Greeter") })

            assertEquals(ToolResult.Success(listOf(ToolContent.Text("No project is open."))), result)
        }

    @Test
    fun needsAQuery() =
        runTest {
            val server = server(session = null)

            val result = server.tools.execute("search_symbol", buildJsonObject {})

            assertEquals(ToolResult.Error("Missing required parameter: query"), result)
        }

    private fun server(session: ProjectSession?): EmbeddedMcpServer {
        val mcp = EmbeddedMcpServer(McpServerConfig(enabled = false))
        registerIdeTools(
            server = mcp,
            fileSystem = mockk<FileSystem>(relaxed = true),
            currentSession = { session },
        )
        return mcp
    }
}
