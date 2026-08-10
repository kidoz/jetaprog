package su.kidoz.jetaprog.plugins.api.services

/**
 * A resolved external dependency of the workspace.
 *
 * For JVM builds [group]/[name] are Maven coordinates; for NuGet packages, which have no
 * group concept, both fields carry the package id.
 */
public data class BuildDependency(
    /** Group id (Maven) or package id (NuGet). */
    val group: String,
    /** Artifact name (Maven) or package id (NuGet). */
    val name: String,
    /** Resolved or requested version, when known. */
    val version: String? = null,
)

/**
 * Read-only access to the workspace build model for plugins.
 *
 * This is the seam framework plugins use for detection ("does this project depend on
 * `org.springframework.boot`?") and for locating dependency artifacts (e.g. reading
 * configuration metadata out of jars on the classpath).
 */
public interface BuildModelService {
    /**
     * All known external dependencies across workspace modules.
     *
     * Sourced from the imported build model when available (Gradle) and from project
     * files (`.csproj` package references). May be empty before a build import finishes.
     */
    public suspend fun dependencies(): List<BuildDependency>

    /**
     * Whether the workspace depends on anything matching [prefix].
     *
     * Matches a dependency whose group equals the prefix, starts with `"$prefix."`, or
     * whose name starts with the prefix. When no structured build model is available yet,
     * falls back to scanning build files (`build.gradle`, `build.gradle.kts`, `pom.xml`)
     * for the literal prefix, so framework detection works before an import completes.
     */
    public suspend fun hasDependency(prefix: String): Boolean

    /**
     * Absolute paths of resolved dependency jars on the workspace classpath, when the
     * build model provides them (Gradle). Empty for non-JVM builds.
     */
    public suspend fun classpathJars(): List<String>
}
