package su.kidoz.jetaprog.build.gradle.importer

/**
 * The result of importing the Gradle model and reconciling it against the
 * recorded `.jetaprog` metadata.
 */
public data class GradleImportReport(
    /** The freshly imported Gradle model. */
    val model: GradleImportModel,
    /** Drift between the model and the recorded metadata. */
    val diff: GradleMetadataDiff,
)
