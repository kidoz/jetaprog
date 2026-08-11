package su.kidoz.jetaprog.plugins.kotlin.mpp

/**
 * Text-level analysis of Gradle build scripts for Kotlin Multiplatform markers.
 *
 * The analysis is deliberately heuristic: it runs before (and without) a Gradle import,
 * so it works on the raw script text. It recognises the multiplatform plugin application
 * and the well-known target DSL functions (`jvm()`, `js { }`, `iosArm64()`, ...), including
 * custom target names such as `jvm("desktop")`. Targets declared indirectly — e.g. through
 * a convention plugin — are picked up from the source-set directory layout instead via
 * [targetsFromSourceSetDirectories].
 */
internal object KotlinMultiplatformBuildScript {
    /** Whether the script applies the Kotlin Multiplatform Gradle plugin. */
    fun appliesMultiplatformPlugin(script: String): Boolean = PLUGIN_MARKERS.any { marker -> script.contains(marker) }

    /**
     * Target names declared through the Kotlin Multiplatform DSL, e.g. `jvm`, `js`,
     * `iosArm64`, or the custom name from `jvm("desktop")`.
     */
    fun declaredTargets(script: String): Set<String> =
        TARGET_CALL_PATTERN
            .findAll(script)
            .map { match ->
                val declaration = match.groupValues[1]
                val customName = match.groupValues[2]
                customName.ifEmpty { CANONICAL_TARGET_NAMES[declaration] ?: declaration }
            }.toSortedSet()

    /**
     * Target names derived from `src/` source-set directories: every `<target>Main`
     * directory except `commonMain` names a target, e.g. `jvmMain` -> `jvm`.
     */
    fun targetsFromSourceSetDirectories(directoryNames: Iterable<String>): Set<String> =
        directoryNames
            .filter { it.endsWith(MAIN_SOURCE_SET_SUFFIX) }
            .map { it.removeSuffix(MAIN_SOURCE_SET_SUFFIX) }
            .filter { it.isNotEmpty() && it != COMMON_SOURCE_SET_NAME }
            .toSortedSet()

    /** Gradle compilation task for a target, e.g. `jvm` -> `compileKotlinJvm`. */
    fun compileTaskFor(target: String): String = "compileKotlin" + target.replaceFirstChar { it.uppercaseChar() }

    /** Gradle test task for a target, e.g. `jvm` -> `jvmTest`. */
    fun testTaskFor(target: String): String = target + "Test"

    private const val MAIN_SOURCE_SET_SUFFIX = "Main"
    private const val COMMON_SOURCE_SET_NAME = "common"

    private val PLUGIN_MARKERS =
        listOf(
            // Kotlin DSL shorthand: kotlin("multiplatform")
            "kotlin(\"multiplatform\")",
            // Explicit plugin id, Kotlin or Groovy DSL
            "org.jetbrains.kotlin.multiplatform",
            // Legacy Groovy apply: 'kotlin-multiplatform'
            "kotlin-multiplatform",
        )

    private val TARGET_DECLARATIONS =
        setOf(
            "jvm",
            "js",
            "wasmJs",
            "wasmWasi",
            "androidTarget",
            "androidNativeArm32",
            "androidNativeArm64",
            "androidNativeX86",
            "androidNativeX64",
            "iosArm64",
            "iosX64",
            "iosSimulatorArm64",
            "watchosArm32",
            "watchosArm64",
            "watchosX64",
            "watchosSimulatorArm64",
            "watchosDeviceArm64",
            "tvosArm64",
            "tvosX64",
            "tvosSimulatorArm64",
            "macosX64",
            "macosArm64",
            "linuxX64",
            "linuxArm64",
            "mingwX64",
        )

    /** DSL functions whose created target has a different default name. */
    private val CANONICAL_TARGET_NAMES = mapOf("androidTarget" to "android")

    /**
     * Matches a target DSL call: the function name (not preceded by a word character or
     * `.`) followed by `(`, `{`, or a quoted custom target name. Longer names first so
     * alternation never stops at a shorter prefix.
     */
    private val TARGET_CALL_PATTERN =
        Regex(
            """(?<![\w.])(${TARGET_DECLARATIONS.sortedByDescending { it.length }.joinToString("|")})""" +
                """\s*(?:\(\s*"([^"]+)"\s*\)|[({])""",
        )
}
