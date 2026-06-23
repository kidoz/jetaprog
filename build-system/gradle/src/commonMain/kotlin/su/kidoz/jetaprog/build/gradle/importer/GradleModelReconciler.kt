package su.kidoz.jetaprog.build.gradle.importer

/**
 * An existing `.jetaprog` module entry, as recorded in `modules.json`.
 */
public data class ExistingModuleEntry(
    /** Repository-relative module directory. */
    val path: String,
    /** Recorded source roots. */
    val sourceRoots: List<String> = emptyList(),
    /** Recorded test roots. */
    val testRoots: List<String> = emptyList(),
)

/**
 * A per-module mismatch between Gradle-derived roots and recorded metadata.
 */
public data class ModuleRootDiff(
    /** The module path the mismatch applies to. */
    val path: String,
    /** Roots Gradle reports that are absent from the metadata. */
    val missingRoots: List<String>,
    /** Roots recorded in metadata that Gradle no longer reports. */
    val staleRoots: List<String>,
)

/**
 * The difference between the live Gradle model and recorded `.jetaprog` metadata.
 */
public data class GradleMetadataDiff(
    /** Module paths present in Gradle but missing from metadata. */
    val missingModules: List<String>,
    /** Module paths present in metadata but no longer in Gradle. */
    val staleModules: List<String>,
    /** Per-module source/test root mismatches for modules present in both. */
    val rootDiffs: List<ModuleRootDiff>,
) {
    /** Whether the metadata is fully consistent with the Gradle model. */
    public val isInSync: Boolean
        get() = missingModules.isEmpty() && staleModules.isEmpty() && rootDiffs.isEmpty()
}

/**
 * Compares a Gradle-derived [GradleImportModel] against recorded `.jetaprog`
 * module metadata, surfacing drift so it can be corrected.
 */
public object GradleModelReconciler {
    /**
     * Reconciles [imported] Gradle modules with the [existing] metadata entries.
     *
     * @param ignoredPaths module paths excluded from drift reporting (for
     *   example `buildSrc`, which is an included build the main model omits).
     */
    public fun reconcile(
        imported: GradleImportModel,
        existing: List<ExistingModuleEntry>,
        ignoredPaths: Set<String> = setOf("buildSrc"),
    ): GradleMetadataDiff {
        val importedByPath = imported.modules.associateBy { it.path }
        val existingByPath = existing.associateBy { it.path }

        val importedPaths = importedByPath.keys - ignoredPaths
        val existingPaths = existingByPath.keys - ignoredPaths

        val missingModules = (importedPaths - existingPaths).sorted()
        val staleModules = (existingPaths - importedPaths).sorted()

        val rootDiffs =
            (importedPaths intersect existingPaths)
                .sorted()
                .mapNotNull { path ->
                    val gradleRoots = importedByPath.getValue(path).allRoots()
                    val metadataRoots = existingByPath.getValue(path).allRoots()
                    val missingRoots = (gradleRoots - metadataRoots).sorted()
                    val staleRoots = (metadataRoots - gradleRoots).sorted()
                    if (missingRoots.isEmpty() && staleRoots.isEmpty()) {
                        null
                    } else {
                        ModuleRootDiff(path, missingRoots, staleRoots)
                    }
                }

        return GradleMetadataDiff(missingModules, staleModules, rootDiffs)
    }

    private fun GradleModuleModel.allRoots(): Set<String> = (sourceRoots + testRoots).toSet()

    private fun ExistingModuleEntry.allRoots(): Set<String> = (sourceRoots + testRoots).toSet()
}
