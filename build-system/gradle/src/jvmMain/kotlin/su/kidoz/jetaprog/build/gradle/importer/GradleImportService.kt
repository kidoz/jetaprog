package su.kidoz.jetaprog.build.gradle.importer

/**
 * High-level entry point for Gradle project import.
 *
 * Combines the Tooling-API-backed [GradleProjectImporter] with the
 * [GradleModelReconciler] so callers can both derive the project structure from
 * Gradle and detect drift against recorded `.jetaprog` metadata.
 */
public class GradleImportService(
    private val importer: GradleProjectImporter = GradleProjectImporter(),
) {
    /**
     * Imports the Gradle model for the build rooted at [projectRoot].
     */
    public suspend fun importModel(projectRoot: String): Result<GradleImportModel> = importer.import(projectRoot)

    /**
     * Imports the Gradle model and reconciles it against [existing] metadata.
     *
     * @param ignoredPaths module paths excluded from drift reporting.
     */
    public suspend fun importAndReconcile(
        projectRoot: String,
        existing: List<ExistingModuleEntry>,
        ignoredPaths: Set<String> = setOf("buildSrc"),
    ): Result<GradleImportReport> =
        importModel(projectRoot).map { model ->
            GradleImportReport(
                model = model,
                diff = GradleModelReconciler.reconcile(model, existing, ignoredPaths),
            )
        }
}
