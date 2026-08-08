package su.kidoz.jetaprog.build.gradle.importer

/** Cancellable source of Gradle project models. */
public interface GradleModelImporter {
    /** Imports the model for the build rooted at [projectRoot]. */
    public suspend fun import(projectRoot: String): Result<GradleImportModel>

    /** Cancels the active import, if any. */
    public fun cancel()
}
