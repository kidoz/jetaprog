package su.kidoz.jetaprog.project.config

import kotlinx.serialization.Serializable

/**
 * Module configuration stored in `.jetaprog/modules.json`.
 * This file should be committed to version control.
 */
@Serializable
public data class ModulesConfig(
    /**
     * List of modules in the project.
     */
    val modules: List<ModuleConfig> = emptyList(),
)

/**
 * Configuration for a single module.
 */
@Serializable
public data class ModuleConfig(
    /**
     * Module name/identifier.
     */
    val name: String,
    /**
     * Module path relative to project root.
     */
    val path: String,
    /**
     * Module type.
     */
    val type: ModuleType = ModuleType.SOURCE,
    /**
     * Source directories relative to module path.
     */
    val sourceRoots: List<String> = emptyList(),
    /**
     * Test directories relative to module path.
     */
    val testRoots: List<String> = emptyList(),
    /**
     * Resource directories relative to module path.
     */
    val resourceRoots: List<String> = emptyList(),
    /**
     * Dependencies on other modules (by name).
     */
    val dependencies: List<String> = emptyList(),
    /**
     * Primary language for this module.
     */
    val language: String? = null,
)

/**
 * Module types.
 */
@Serializable
public enum class ModuleType {
    /**
     * Source module containing production code.
     */
    SOURCE,

    /**
     * Test module containing test code.
     */
    TEST,

    /**
     * Library module (external dependency).
     */
    LIBRARY,

    /**
     * Build configuration module (e.g., buildSrc).
     */
    BUILD,
}
