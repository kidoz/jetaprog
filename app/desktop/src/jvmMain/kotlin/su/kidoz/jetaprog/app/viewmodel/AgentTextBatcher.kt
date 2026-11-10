package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coalesces adjacent text chunks; control events and turn completion explicitly flush. */
internal class AgentTextBatcher(
    private val scope: CoroutineScope,
    private val append: (String, Boolean) -> Unit,
) {
    private val pending = StringBuilder()
    private var thought = false
    private var job: Job? = null

    fun offer(
        text: String,
        isThought: Boolean,
    ) {
        if (text.isEmpty()) return
        if (thought != isThought) flush()
        thought = isThought
        if (job == null) {
            // First content is visible immediately; subsequent chunks share one UI publication.
            append(text, thought)
            scheduleFlush()
        } else {
            pending.append(text)
            if (pending.length >= MAX_BATCH_CHARS) flush()
        }
    }

    fun flush() {
        job?.cancel()
        job = null
        if (pending.isNotEmpty()) {
            val text = pending.toString()
            pending.clear()
            append(text, thought)
        }
    }

    private fun scheduleFlush() {
        job =
            scope.launch {
                delay(BATCH_DELAY_MS)
                job = null
                flush()
            }
    }
}

private const val BATCH_DELAY_MS = 40L
private const val MAX_BATCH_CHARS = 32_768
