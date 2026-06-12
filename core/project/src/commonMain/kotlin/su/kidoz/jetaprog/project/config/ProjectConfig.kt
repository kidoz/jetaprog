package su.kidoz.jetaprog.project.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Project configuration stored in `.jetaprog/project.json`.
 * This file should be committed to version control.
 */
@Serializable
public data class ProjectConfig(
    /**
     * Project name.
     */
    val name: String,
    /**
     * Project version.
     */
    val version: String = "1.0.0",
    /**
     * SDK identifier (e.g., "kotlin-2.1.0", "java-21").
     */
    val sdk: String? = null,
    /**
     * Source root directories relative to project root.
     */
    val sourceRoots: List<String> = listOf("src/main/kotlin", "src/main/java"),
    /**
     * Test root directories relative to project root.
     */
    val testRoots: List<String> = listOf("src/test/kotlin", "src/test/java"),
    /**
     * Resource directories relative to project root.
     */
    val resourceRoots: List<String> = listOf("src/main/resources"),
    /**
     * Directories to exclude from indexing.
     */
    val excludes: List<String> = listOf("build", "out", ".gradle", "node_modules", ".git"),
    /**
     * Language version for the primary language.
     */
    val languageVersion: String? = null,
    /**
     * Build system type.
     */
    val buildSystem: BuildSystemType = BuildSystemType.GRADLE,
    /**
     * Additional project properties.
     */
    val properties: Map<String, String> = emptyMap(),
) {
    public companion object {
        /**
         * Default configuration for a new project.
         */
        public fun default(name: String): ProjectConfig = ProjectConfig(name = name)
    }
}

/**
 * Supported build systems.
 */
@Serializable
public enum class BuildSystemType {
    @SerialName("gradle")
    GRADLE,

    @SerialName("maven")
    MAVEN,

    @SerialName("cargo")
    CARGO,

    @SerialName("meson")
    MESON,

    @SerialName("cmake")
    CMAKE,

    @SerialName("none")
    NONE,
}
