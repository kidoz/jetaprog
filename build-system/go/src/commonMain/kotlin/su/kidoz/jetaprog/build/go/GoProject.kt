package su.kidoz.jetaprog.build.go

/**
 * A Go module, a Go workspace, or both rooted at the same directory.
 *
 * @property rootPath Directory that contains `go.mod`, `go.work`, or both.
 * @property module Parsed `go.mod`, when that file was read.
 * @property workspace Parsed `go.work`, when that file was read.
 */
public data class GoProject(
    val rootPath: String,
    val module: GoModuleFile? = null,
    val workspace: GoWorkFile? = null,
) {
    /** True when a `go.work` file was loaded. */
    val isWorkspace: Boolean get() = workspace != null

    /** Module path from `go.mod`, when the file declares one. */
    val modulePath: String? get() = module?.modulePath

    /** True when `go.mod` or `go.work` was loaded. */
    val hasToolchainRoot: Boolean get() = module != null || workspace != null
}
