package su.kidoz.jetaprog.app.inspection

import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.plugins.api.services.LintService

/**
 * The outcome of inspecting one file.
 */
public sealed interface FileInspection {
    /** The project's inspection configuration is turned off. */
    public data object Disabled : FileInspection

    /** The file matches an exclusion pattern. */
    public data object Excluded : FileInspection

    /**
     * Rules ran.
     *
     * @property results Findings, in the order the engine reported them.
     */
    public data class Findings(
        val results: List<LintResult>,
    ) : FileInspection
}

/**
 * Runs the registered inspection rules for one file.
 *
 * Uses the session's lint configuration. A disabled configuration or an excluded
 * path does not run the rules. This does not apply fixes.
 *
 * @param lint The session lint service.
 */
public class FileInspectionRunner(
    private val lint: LintService,
) {
    /**
     * Inspects [content] of [absolutePath] as [languageId].
     */
    public suspend fun inspect(
        absolutePath: String,
        content: String,
        languageId: String,
    ): FileInspection {
        val configuration = lint.getConfiguration()
        if (!configuration.enabled) return FileInspection.Disabled
        if (configuration.isExcluded(absolutePath)) return FileInspection.Excluded
        val results = lint.lintFile(DocumentUri.file(absolutePath).value, languageId, content)
        return FileInspection.Findings(results)
    }
}
