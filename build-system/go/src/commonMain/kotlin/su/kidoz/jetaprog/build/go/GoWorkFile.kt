package su.kidoz.jetaprog.build.go

/**
 * A parsed `go.work` file.
 *
 * @property goVersion Language version from the `go` directive.
 * @property toolchain Toolchain name from the `toolchain` directive.
 * @property use Directory paths listed by `use` directives, relative to the workspace.
 * @property replacements `replace` directives that apply to every module in the workspace.
 */
public data class GoWorkFile(
    val goVersion: String? = null,
    val toolchain: String? = null,
    val use: List<String> = emptyList(),
    val replacements: List<GoReplacement> = emptyList(),
)

/**
 * Parses `go.work` text into a [GoWorkFile].
 */
public object GoWorkParser {
    /**
     * Parses [text]. A blank file yields an empty workspace file.
     */
    public fun parse(text: String): GoWorkFile {
        val use = mutableListOf<String>()
        val replacements = mutableListOf<GoReplacement>()
        var goVersion: String? = null
        var toolchain: String? = null
        var block: String? = null

        for (line in readGoManifestLines(text)) {
            val tokens = line.tokens
            if (tokens.isEmpty()) continue
            if (tokens.singleOrNull() == ")") {
                block = null
                continue
            }
            if (tokens.last() == "(" && tokens.size >= 2) {
                block = tokens.first()
                continue
            }
            val directive = block ?: tokens.first()
            val args = if (block == null) tokens.drop(1) else tokens
            when (directive) {
                "go" -> goVersion = args.firstOrNull()
                "toolchain" -> toolchain = args.firstOrNull()
                "use" -> args.firstOrNull()?.let(use::add)
                "replace" -> parseReplacement(args)?.let(replacements::add)
            }
        }

        return GoWorkFile(
            goVersion = goVersion,
            toolchain = toolchain,
            use = use,
            replacements = replacements,
        )
    }
}
