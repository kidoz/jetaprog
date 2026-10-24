package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.CancellationException
import su.kidoz.jetaprog.app.ui.agent.AGENTS_FILE
import su.kidoz.jetaprog.app.ui.agent.AGENTS_MAX_CHARS
import su.kidoz.jetaprog.app.ui.agent.ProjectInstructionPreview
import su.kidoz.jetaprog.app.ui.agent.instructionText
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/** Reads the same checkout-local instructions for inspection and sending. */
internal suspend fun loadProjectInstructions(
    fileSystem: FileSystem,
    projectPath: String,
): ProjectInstructionPreview {
    val root = projectPath.trimEnd('/', '\\')
    if (root.isEmpty()) return ProjectInstructionPreview(notice = "Workspace unavailable.")
    return try {
        val path = "$root/$AGENTS_FILE"
        if (!fileSystem.exists(path)) return ProjectInstructionPreview(notice = "No root AGENTS.md found.")
        val raw = fileSystem.readText(path).getOrThrow()
        ProjectInstructionPreview(
            text = instructionText(raw),
            notice = if (raw.isBlank()) "Root AGENTS.md is empty." else null,
            truncated = raw.trim().length > AGENTS_MAX_CHARS,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ProjectInstructionPreview(
            notice = "Could not read root AGENTS.md. JetaProg will omit it if it remains unreadable.",
        )
    }
}
