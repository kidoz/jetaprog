package su.kidoz.jetaprog.build.cargo

import kotlinx.serialization.Serializable

/**
 * Represents a Cargo/Rust project.
 *
 * @property rootPath The root directory containing Cargo.toml.
 * @property manifest The parsed Cargo.toml manifest.
 * @property workspaceMembers List of workspace member paths (for workspace projects).
 */
@Serializable
public data class CargoProject(
    val rootPath: String,
    val manifest: CargoManifest? = null,
    val workspaceMembers: List<String> = emptyList(),
) {
    /**
     * Whether this is a workspace project.
     */
    val isWorkspace: Boolean get() = workspaceMembers.isNotEmpty()

    /**
     * Path to the Cargo.toml file.
     */
    val manifestPath: String get() = "$rootPath/Cargo.toml"

    /**
     * Path to the Cargo.lock file.
     */
    val lockPath: String get() = "$rootPath/Cargo.lock"

    /**
     * Path to the target directory.
     */
    val targetPath: String get() = "$rootPath/target"
}

/**
 * Parsed Cargo.toml manifest.
 */
@Serializable
public data class CargoManifest(
    val package_: CargoPackage? = null,
    val dependencies: Map<String, CargoDependency> = emptyMap(),
    val devDependencies: Map<String, CargoDependency> = emptyMap(),
    val buildDependencies: Map<String, CargoDependency> = emptyMap(),
    val features: Map<String, List<String>> = emptyMap(),
    val bin: List<CargoBinary> = emptyList(),
    val lib: CargoLibrary? = null,
)

/**
 * Package metadata from Cargo.toml [package] section.
 */
@Serializable
public data class CargoPackage(
    val name: String,
    val version: String,
    val edition: String = "2021",
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val license: String? = null,
    val repository: String? = null,
    val keywords: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val rust_version: String? = null,
)

/**
 * Dependency specification.
 */
@Serializable
public data class CargoDependency(
    val version: String? = null,
    val path: String? = null,
    val git: String? = null,
    val branch: String? = null,
    val tag: String? = null,
    val rev: String? = null,
    val features: List<String> = emptyList(),
    val optional: Boolean = false,
    val default_features: Boolean = true,
)

/**
 * Binary target definition.
 */
@Serializable
public data class CargoBinary(
    val name: String,
    val path: String? = null,
)

/**
 * Library target definition.
 */
@Serializable
public data class CargoLibrary(
    val name: String? = null,
    val path: String? = null,
    val crate_type: List<String> = listOf("lib"),
)

/**
 * Build profile (debug, release, etc.).
 */
public enum class CargoProfile(
    public val flag: String?,
) {
    /** Debug build (default). */
    DEBUG(null),

    /** Release build with optimizations. */
    RELEASE("--release"),

    ;

    public companion object {
        /**
         * Creates a custom profile.
         */
        public fun custom(name: String): String = "--profile=$name"
    }
}

/**
 * Target triple for cross-compilation.
 */
@Serializable
@JvmInline
public value class CargoTarget(
    public val triple: String,
) {
    public companion object {
        // Common targets
        public val X86_64_UNKNOWN_LINUX_GNU: CargoTarget = CargoTarget("x86_64-unknown-linux-gnu")
        public val X86_64_UNKNOWN_LINUX_MUSL: CargoTarget = CargoTarget("x86_64-unknown-linux-musl")
        public val X86_64_APPLE_DARWIN: CargoTarget = CargoTarget("x86_64-apple-darwin")
        public val AARCH64_APPLE_DARWIN: CargoTarget = CargoTarget("aarch64-apple-darwin")
        public val X86_64_PC_WINDOWS_MSVC: CargoTarget = CargoTarget("x86_64-pc-windows-msvc")
        public val X86_64_PC_WINDOWS_GNU: CargoTarget = CargoTarget("x86_64-pc-windows-gnu")
        public val WASM32_UNKNOWN_UNKNOWN: CargoTarget = CargoTarget("wasm32-unknown-unknown")
        public val WASM32_WASI: CargoTarget = CargoTarget("wasm32-wasi")
    }
}
