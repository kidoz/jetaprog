package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentComposerEditorTest {
    @Test
    fun repeatedTextAndOlderSelectionsCannotAcknowledgeNewerEdits() {
        val editor = AgentComposerEditor("", TextRange.Zero, null)
        val first = editor.edit(TextFieldValue("a", TextRange(1)))
        val second = editor.edit(TextFieldValue("ab", TextRange(2)))
        val third = editor.edit(TextFieldValue("a", TextRange(1)))
        val latest = TextFieldValue("a", TextRange.Zero, composition = TextRange(0, 1))
        val fourth = editor.edit(latest)

        editor.synchronize("a", TextRange(1), first)
        assertEquals(latest, editor.value)
        editor.synchronize("ab", TextRange(2), second)
        assertEquals(latest, editor.value)
        editor.synchronize("a", TextRange(1), third)
        assertEquals(latest, editor.value)
        editor.synchronize("a", TextRange.Zero, fourth)
        assertEquals(latest, editor.value)
    }

    @Test
    fun submissionCanClearBeforeTheTypedStateWasRendered() {
        val editor = AgentComposerEditor("", TextRange.Zero, null)
        val editId = editor.edit(TextFieldValue("send this", TextRange(9)))
        editor.synchronize("", TextRange.Zero, editId)
        assertEquals(TextFieldValue(""), editor.value)
    }

    @Test
    fun externalReplacementWinsOverPendingTyping() {
        val editor = AgentComposerEditor("", TextRange.Zero, null)
        editor.edit(TextFieldValue("draft", TextRange(5)))
        editor.synchronize("/review ", null, null)
        assertEquals(TextFieldValue("/review ", TextRange(8)), editor.value)
    }
}
