package su.kidoz.jetaprog.build.go

/**
 * One invocation of the `go` command.
 *
 * Package patterns use Go's own syntax, such as `.`, `./...`, or a full module path.
 */
public sealed interface GoOperation {
    /** Compile packages. `go build`. */
    public data class Build(
        val packages: List<String> = listOf("./..."),
        val output: String? = null,
        val race: Boolean = false,
        val tags: List<String> = emptyList(),
        val flags: List<String> = emptyList(),
    ) : GoOperation

    /** Compile and run a main package. `go run`. */
    public data class Run(
        val packagePattern: String = ".",
        val race: Boolean = false,
        val tags: List<String> = emptyList(),
        val buildFlags: List<String> = emptyList(),
        val programArguments: List<String> = emptyList(),
    ) : GoOperation

    /** Run tests. `go test`. */
    public data class Test(
        val packages: List<String> = listOf("./..."),
        val json: Boolean = false,
        val count: Int? = null,
        val run: String? = null,
        val race: Boolean = false,
        val tags: List<String> = emptyList(),
        val flags: List<String> = emptyList(),
    ) : GoOperation

    /** Report suspicious constructs. `go vet`. */
    public data class Vet(
        val packages: List<String> = listOf("./..."),
        val flags: List<String> = emptyList(),
    ) : GoOperation

    /** Format Go source with `gofmt` through `go fmt`. */
    public data class Fmt(
        val paths: List<String> = listOf("./..."),
    ) : GoOperation

    /** Run `go:generate` directives. `go generate`. */
    public data class Generate(
        val packages: List<String> = listOf("./..."),
        val run: String? = null,
    ) : GoOperation

    /** Add missing and remove unused requirements. `go mod tidy`. */
    public data object ModTidy : GoOperation

    /** Download modules to the cache. `go mod download`. */
    public data object ModDownload : GoOperation

    /** Copy module sources into `vendor`. `go mod vendor`. */
    public data object ModVendor : GoOperation

    /** Sync workspace build lists. `go work sync`. */
    public data object WorkSync : GoOperation

    /**
     * Remove build outputs.
     *
     * Cache flags match `go clean -cache`, `-testcache`, and `-modcache`. Package
     * arguments clean package build artifacts instead.
     */
    public data class Clean(
        val cache: Boolean = false,
        val testCache: Boolean = false,
        val modCache: Boolean = false,
        val packages: List<String> = emptyList(),
    ) : GoOperation

    /** List packages. `go list`. */
    public data class ListPackages(
        val pattern: String = "./...",
        val json: Boolean = true,
    ) : GoOperation

    /** Print Go environment values. `go env`. */
    public data class Env(
        val json: Boolean = true,
    ) : GoOperation
}

/**
 * Builds the argument list for a [GoOperation], not including the `go` executable.
 */
public object GoToolchain {
    /**
     * Arguments that follow the `go` executable for [operation].
     */
    public fun arguments(operation: GoOperation): List<String> =
        when (operation) {
            is GoOperation.Build -> buildArguments(operation)
            is GoOperation.Run -> runArguments(operation)
            is GoOperation.Test -> testArguments(operation)
            is GoOperation.Vet -> command("vet", operation.flags, operation.packages)
            is GoOperation.Fmt -> listOf("fmt") + operation.paths
            is GoOperation.Generate -> generateArguments(operation)
            GoOperation.ModTidy -> listOf("mod", "tidy")
            GoOperation.ModDownload -> listOf("mod", "download")
            GoOperation.ModVendor -> listOf("mod", "vendor")
            GoOperation.WorkSync -> listOf("work", "sync")
            is GoOperation.Clean -> cleanArguments(operation)
            is GoOperation.ListPackages -> listArguments(operation)
            is GoOperation.Env -> if (operation.json) listOf("env", "-json") else listOf("env")
        }

    /**
     * Full command line, starting with [goCommand].
     */
    public fun command(
        operation: GoOperation,
        goCommand: String = "go",
    ): List<String> = listOf(goCommand) + arguments(operation)

    private fun buildArguments(operation: GoOperation.Build): List<String> =
        buildList {
            add("build")
            operation.output?.let {
                add("-o")
                add(it)
            }
            addCompileFlags(operation.race, operation.tags, operation.flags)
            addAll(operation.packages)
        }

    private fun runArguments(operation: GoOperation.Run): List<String> =
        buildList {
            add("run")
            addCompileFlags(operation.race, operation.tags, operation.buildFlags)
            add(operation.packagePattern)
            addAll(operation.programArguments)
        }

    private fun testArguments(operation: GoOperation.Test): List<String> =
        buildList {
            add("test")
            if (operation.json) add("-json")
            operation.count?.let { add("-count=$it") }
            operation.run?.let { add("-run=$it") }
            addCompileFlags(operation.race, operation.tags, operation.flags)
            addAll(operation.packages)
        }

    private fun generateArguments(operation: GoOperation.Generate): List<String> =
        buildList {
            add("generate")
            operation.run?.let { add("-run=$it") }
            addAll(operation.packages)
        }

    private fun cleanArguments(operation: GoOperation.Clean): List<String> =
        buildList {
            add("clean")
            if (operation.cache) add("-cache")
            if (operation.testCache) add("-testcache")
            if (operation.modCache) add("-modcache")
            addAll(operation.packages)
        }

    private fun listArguments(operation: GoOperation.ListPackages): List<String> =
        buildList {
            add("list")
            if (operation.json) add("-json")
            add(operation.pattern)
        }

    private fun command(
        name: String,
        flags: List<String>,
        packages: List<String>,
    ): List<String> = listOf(name) + flags + packages
}

private fun MutableList<String>.addCompileFlags(
    race: Boolean,
    tags: List<String>,
    flags: List<String>,
) {
    if (race) add("-race")
    if (tags.isNotEmpty()) add("-tags=${tags.joinToString(",")}")
    addAll(flags)
}
