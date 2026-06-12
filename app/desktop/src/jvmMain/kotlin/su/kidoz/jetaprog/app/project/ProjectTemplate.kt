package su.kidoz.jetaprog.app.project

/**
 * Represents a project template for creating new projects.
 */
public sealed interface ProjectTemplate {
    public val name: String
    public val description: String
    public val language: ProjectLanguage
    public val buildTool: BuildTool
}

/**
 * Supported project languages.
 */
public enum class ProjectLanguage(
    public val displayName: String,
) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    RUST("Rust"),
    CPP("C++"),
    VALA("Vala"),
}

/**
 * Supported build tools.
 */
public enum class BuildTool(
    public val displayName: String,
) {
    GRADLE("Gradle"),
    MAVEN("Maven"),
    CARGO("Cargo"),
    MESON("Meson"),
}

/**
 * Returns the available build tools for a given language.
 */
public fun ProjectLanguage.availableBuildTools(): List<BuildTool> =
    when (this) {
        ProjectLanguage.KOTLIN -> listOf(BuildTool.GRADLE, BuildTool.MAVEN)
        ProjectLanguage.JAVA -> listOf(BuildTool.GRADLE, BuildTool.MAVEN)
        ProjectLanguage.RUST -> listOf(BuildTool.CARGO)
        ProjectLanguage.CPP -> listOf(BuildTool.MESON)
        ProjectLanguage.VALA -> listOf(BuildTool.MESON)
    }

/**
 * Supported license types.
 */
public enum class License(
    public val displayName: String,
    public val spdxId: String?,
) {
    NONE("No License", null),
    MIT("MIT License", "MIT"),
    APACHE_2_0("Apache License 2.0", "Apache-2.0"),
    GPL_3_0("GNU GPL v3.0", "GPL-3.0"),
}

/**
 * Kotlin project with Gradle build system.
 */
public data object KotlinGradleTemplate : ProjectTemplate {
    override val name: String = "Kotlin/Gradle"
    override val description: String = "Kotlin project with Gradle build system"
    override val language: ProjectLanguage = ProjectLanguage.KOTLIN
    override val buildTool: BuildTool = BuildTool.GRADLE
}

/**
 * Kotlin project with Maven build system.
 */
public data object KotlinMavenTemplate : ProjectTemplate {
    override val name: String = "Kotlin/Maven"
    override val description: String = "Kotlin project with Maven build system"
    override val language: ProjectLanguage = ProjectLanguage.KOTLIN
    override val buildTool: BuildTool = BuildTool.MAVEN
}

/**
 * Java project with Gradle build system.
 */
public data object JavaGradleTemplate : ProjectTemplate {
    override val name: String = "Java/Gradle"
    override val description: String = "Java project with Gradle build system"
    override val language: ProjectLanguage = ProjectLanguage.JAVA
    override val buildTool: BuildTool = BuildTool.GRADLE
}

/**
 * Java project with Maven build system.
 */
public data object JavaMavenTemplate : ProjectTemplate {
    override val name: String = "Java/Maven"
    override val description: String = "Java project with Maven build system"
    override val language: ProjectLanguage = ProjectLanguage.JAVA
    override val buildTool: BuildTool = BuildTool.MAVEN
}

/**
 * Rust project with Cargo build system.
 */
public data object RustCargoTemplate : ProjectTemplate {
    override val name: String = "Rust/Cargo"
    override val description: String = "Rust project with Cargo build system"
    override val language: ProjectLanguage = ProjectLanguage.RUST
    override val buildTool: BuildTool = BuildTool.CARGO
}

/**
 * C++ project with Meson build system.
 */
public data object CppMesonTemplate : ProjectTemplate {
    override val name: String = "C++/Meson"
    override val description: String = "C++ project with Meson build system"
    override val language: ProjectLanguage = ProjectLanguage.CPP
    override val buildTool: BuildTool = BuildTool.MESON
}

/**
 * Vala project with Meson build system.
 */
public data object ValaMesonTemplate : ProjectTemplate {
    override val name: String = "Vala/Meson"
    override val description: String = "Vala project with Meson build system"
    override val language: ProjectLanguage = ProjectLanguage.VALA
    override val buildTool: BuildTool = BuildTool.MESON
}

/**
 * Gets the template for a given language and build tool combination.
 */
public fun getTemplate(
    language: ProjectLanguage,
    buildTool: BuildTool,
): ProjectTemplate =
    when (language) {
        ProjectLanguage.KOTLIN -> {
            when (buildTool) {
                BuildTool.GRADLE -> KotlinGradleTemplate
                BuildTool.MAVEN -> KotlinMavenTemplate
                else -> KotlinGradleTemplate
            }
        }

        ProjectLanguage.JAVA -> {
            when (buildTool) {
                BuildTool.GRADLE -> JavaGradleTemplate
                BuildTool.MAVEN -> JavaMavenTemplate
                else -> JavaGradleTemplate
            }
        }

        ProjectLanguage.RUST -> {
            RustCargoTemplate
        }

        ProjectLanguage.CPP -> {
            CppMesonTemplate
        }

        ProjectLanguage.VALA -> {
            ValaMesonTemplate
        }
    }

/**
 * Configuration for creating a new project.
 */
public data class ProjectConfig(
    val name: String,
    val location: String,
    val template: ProjectTemplate,
    val packageName: String,
    val initGit: Boolean = true,
    val createReadme: Boolean = true,
    val license: License = License.MIT,
    val sdkVersion: String = "",
) {
    /**
     * Full path to the project directory.
     */
    val projectPath: String
        get() = "$location/$name"
}
