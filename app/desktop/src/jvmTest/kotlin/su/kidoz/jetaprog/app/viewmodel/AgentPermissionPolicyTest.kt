package su.kidoz.jetaprog.app.viewmodel

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.ToolKind
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests for classifying permission requests that omit the optional tool kind. */
class AgentPermissionPolicyTest {
    @Test
    fun commandInputIsRunEvenWhenKindIsMissing() {
        val call =
            PermissionToolCall(
                toolCallId = "call-1",
                title = "Run command",
                rawInput = buildJsonObject { put("command", "rm -rf build") },
            )

        assertEquals(PermissionKind.RUN, AgentPermissionPolicy.kindFor(call))
        assertNull(call.kind)
    }

    @Test
    fun editInputIsEditEvenWhenKindIsMissing() {
        val call =
            PermissionToolCall(
                toolCallId = "call-2",
                title = "Edit file",
                rawInput =
                    buildJsonObject {
                        put("file_path", "/project/src/Main.kt")
                        put("old_string", "old")
                        put("new_string", "new")
                    },
            )

        assertEquals(PermissionKind.EDIT, AgentPermissionPolicy.kindFor(call))
    }

    @Test
    fun writeInputIsEdit() {
        val call =
            PermissionToolCall(
                toolCallId = "call-3",
                title = "Write file",
                rawInput =
                    buildJsonObject {
                        put("file_path", "/project/src/Main.kt")
                        put("content", "contents")
                    },
            )

        assertEquals(PermissionKind.EDIT, AgentPermissionPolicy.kindFor(call))
    }

    @Test
    fun pathOnlyInputIsRead() {
        val call =
            PermissionToolCall(
                toolCallId = "call-4",
                title = "Read file",
                rawInput = buildJsonObject { put("file_path", "/project/src/Main.kt") },
            )

        assertEquals(PermissionKind.READ, AgentPermissionPolicy.kindFor(call))
    }

    @Test
    fun declaredKindIsUsedWhenInputIsNotRecognizable() {
        val call =
            PermissionToolCall(
                toolCallId = "call-5",
                title = "Run",
                kind = ToolKind.EXECUTE,
            )

        assertEquals(PermissionKind.RUN, AgentPermissionPolicy.kindFor(call))
    }

    @Test
    fun unknownToolCallsRequireConfirmation() {
        val call =
            PermissionToolCall(
                toolCallId = "call-6",
                title = "Mystery",
                kind = ToolKind.OTHER,
                rawInput = buildJsonObject { put("unrelated", "value") },
            )

        assertNull(AgentPermissionPolicy.kindFor(call))
    }
}
