package su.kidoz.jetaprog.build.gradle.wrapper

/** Configuration used when generating a Gradle wrapper for a project. */
public data class GradleWrapperSpec(
    /** Gradle version written to the generated wrapper. */
    val gradleVersion: String,
    /** Optional SHA-256 checksum used to validate the downloaded distribution. */
    val distributionSha256Sum: String? = null,
)

/** Generates the complete Gradle wrapper files for a project. */
public fun interface GradleWrapperGenerator {
    /**
     * Generates wrapper scripts, properties, and the wrapper JAR in [projectRoot].
     */
    public suspend fun generate(
        projectRoot: String,
        spec: GradleWrapperSpec,
    ): Result<Unit>
}
