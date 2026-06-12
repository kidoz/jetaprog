package su.kidoz.jetaprog.build.gradle

/**
 * Represents a Gradle project.
 */
public data class GradleProject(
    /** Root path of the Gradle project. */
    val rootPath: String,
    /** Name of the project (from settings.gradle). */
    val name: String = "",
    /** Available Gradle tasks. */
    val tasks: List<GradleTask> = emptyList(),
    /** Sub-projects in a multi-project build. */
    val subprojects: List<String> = emptyList(),
)

/**
 * Represents a Gradle task.
 */
public data class GradleTask(
    /** Full task path (e.g., ":app:desktop:build"). */
    val path: String,
    /** Task name without project path (e.g., "build"). */
    val name: String,
    /** Task group (e.g., "build", "verification"). */
    val group: String? = null,
    /** Task description. */
    val description: String? = null,
)

/**
 * Common Gradle task paths.
 */
public object GradleTasks {
    public const val BUILD: String = "build"
    public const val CLEAN: String = "clean"
    public const val TEST: String = "test"
    public const val ASSEMBLE: String = "assemble"
    public const val CHECK: String = "check"
    public const val CLASSES: String = "classes"
    public const val JAR: String = "jar"

    // Kotlin/JVM specific
    public const val COMPILE_KOTLIN: String = "compileKotlin"
    public const val COMPILE_KOTLIN_JVM: String = "compileKotlinJvm"

    // Ktlint
    public const val KTLINT_CHECK: String = "ktlintCheck"
    public const val KTLINT_FORMAT: String = "ktlintFormat"

    // Detekt
    public const val DETEKT: String = "detekt"

    // Run
    public const val RUN: String = "run"
}
