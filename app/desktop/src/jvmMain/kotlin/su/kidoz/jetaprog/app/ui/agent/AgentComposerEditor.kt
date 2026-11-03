package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.concurrent.atomic.AtomicLong

/** Keeps native edits immediate while the MVI state catches up. Owned by one task's composer. */
internal class AgentComposerEditor(
    text: String,
    selection: TextRange?,
    editId: Long?,
) {
    var value: TextFieldValue by mutableStateOf(TextFieldValue(text, selection ?: TextRange(text.length)))
        private set

    private var observedText = text
    private var observedSelection = selection
    private var observedEditId = editId
    private val pendingEdits = mutableListOf<Pair<Long, TextFieldValue>>()

    /** Applies external replacements, but never rolls back typing to an older edit's echo. */
    fun synchronize(
        text: String,
        selection: TextRange?,
        editId: Long?,
    ) {
        if (text == observedText && selection == observedSelection && editId == observedEditId) return
        observedText = text
        observedSelection = selection
        observedEditId = editId
        val acknowledged =
            pendingEdits.indexOfLast {
                it.first == editId && it.second.text == text &&
                    (selection == null || it.second.selection == selection)
            }
        if (acknowledged >= 0) {
            pendingEdits.subList(0, acknowledged + 1).clear()
        } else {
            pendingEdits.clear()
            value =
                if (value.text == text) {
                    value.copy(selection = selection ?: value.selection)
                } else {
                    TextFieldValue(text, selection ?: TextRange(text.length))
                }
        }
    }

    /** Retains selection and IME composition locally before dispatching text to the ViewModel. */
    fun edit(next: TextFieldValue): Long {
        val editId = editIds.incrementAndGet()
        pendingEdits += editId to next.copy(composition = null)
        value = next
        return editId
    }

    private companion object {
        val editIds = AtomicLong()
    }
}
