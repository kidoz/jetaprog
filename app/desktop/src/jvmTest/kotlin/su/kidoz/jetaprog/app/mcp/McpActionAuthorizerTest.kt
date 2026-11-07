package su.kidoz.jetaprog.app.mcp

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpActionAuthorizerTest {
    @Test
    fun autoAllowsAndNeverDeniesWithoutAsking() =
        runTest {
            var asked = false
            val authorizer =
                McpActionAuthorizer(
                    permissions = {
                        mapOf(
                            PermissionKind.EDIT to PermissionPolicy.AUTO,
                            PermissionKind.RUN to PermissionPolicy.NEVER,
                        )
                    },
                    ask = {
                        asked = true
                        true
                    },
                )

            assertTrue(authorizer.allow(McpSensitiveAction.EDIT, "Write Main.kt"))
            assertFalse(authorizer.allow(McpSensitiveAction.RUN, "Run Gradle task test"))
            assertFalse(asked)
        }

    @Test
    fun askUsesTheUsersDecision() =
        runTest {
            val authorizer =
                McpActionAuthorizer(
                    permissions = { emptyMap() },
                    ask = { description -> description == "Write Main.kt" },
                )

            assertTrue(authorizer.allow(McpSensitiveAction.EDIT, "Write Main.kt"))
            assertFalse(authorizer.allow(McpSensitiveAction.RUN, "Debug step_over"))
        }
}
