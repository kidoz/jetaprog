package su.kidoz.jetaprog.build.gradle.importer

/**
 * A single module derived from the Gradle build model.
 *
 * Source/test/resource roots are expressed relative to the module directory
 * (for example `src/jvmMain/kotlin`) so they line up with `.jetaprog` metadata.
 */
public data class GradleModuleModel(
    /** Repository-relative module directory, e.g. `app/desktop`. */
    val path: String,
    /** Gradle project name for the module. */
    val name: String,
    /** Production source roots, relative to the module directory. */
    val sourceRoots: List<String> = emptyList(),
    /** Test source roots, relative to the module directory. */
    val testRoots: List<String> = emptyList(),
    /** Resource roots, relative to the module directory. */
    val resourceRoots: List<String> = emptyList(),
    /** Generated source roots, relative to the module directory. */
    val generatedRoots: List<String> = emptyList(),
    /** Names of other Gradle modules this module depends on. */
    val moduleDependencies: List<String> = emptyList(),
    /** Absolute paths of resolved library dependencies (jars) on the module classpath. */
    val classpath: List<String> = emptyList(),
    /** Maven coordinates of the resolved external dependencies, when known. */
    val externalDependencies: List<ExternalDependency> = emptyList(),
)

/**
 * Maven coordinates of a resolved external library dependency.
 */
public data class ExternalDependency(
    /** Group id, e.g. `org.springframework.boot`. */
    val group: String,
    /** Artifact name, e.g. `spring-boot-starter-web`. */
    val name: String,
    /** Resolved version, if reported by the build tool. */
    val version: String? = null,
)

/**
 * The project structure derived from Gradle via the Tooling API.
 */
public data class GradleImportModel(
    /** The root project name. */
    val rootName: String,
    /** All discovered modules, excluding the root aggregator project. */
    val modules: List<GradleModuleModel> = emptyList(),
    /** The JDK name reported by Gradle, if available. */
    val jdkName: String? = null,
    /** The language level reported by Gradle, if available. */
    val languageLevel: String? = null,
)
