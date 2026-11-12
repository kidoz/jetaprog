package su.kidoz.jetaprog.build.go

/**
 * A parsed `go.mod` file.
 *
 * @property modulePath Module path from the `module` directive.
 * @property goVersion Language version from the `go` directive.
 * @property toolchain Toolchain name from the `toolchain` directive, such as `go1.25.1`.
 * @property requirements Required modules, including indirect ones.
 * @property excludes Modules excluded from the build list.
 * @property replacements `replace` directives, including local path replacements.
 * @property retractions Versions retracted by this module.
 */
public data class GoModuleFile(
    val modulePath: String? = null,
    val goVersion: String? = null,
    val toolchain: String? = null,
    val requirements: List<GoRequirement> = emptyList(),
    val excludes: List<GoModuleVersion> = emptyList(),
    val replacements: List<GoReplacement> = emptyList(),
    val retractions: List<GoRetraction> = emptyList(),
)

/**
 * One `require` entry.
 *
 * @property path Module path.
 * @property version Module version, including the leading `v` when Go requires it.
 * @property indirect True when the requirement is marked `// indirect`.
 */
public data class GoRequirement(
    val path: String,
    val version: String,
    val indirect: Boolean = false,
)

/**
 * A module path paired with a version.
 *
 * @property path Module path.
 * @property version Module version.
 */
public data class GoModuleVersion(
    val path: String,
    val version: String,
)

/**
 * A `replace` directive.
 *
 * The new side is a versioned module or a local directory when [newVersion] is null.
 *
 * @property oldPath Module path being replaced.
 * @property oldVersion Optional version constraint on the old side.
 * @property newPath Replacement module path or local directory.
 * @property newVersion Replacement version, absent for a local path.
 */
public data class GoReplacement(
    val oldPath: String,
    val oldVersion: String? = null,
    val newPath: String,
    val newVersion: String? = null,
)

/**
 * A retracted version or version interval.
 *
 * @property low The only version, or the low end of an interval.
 * @property high The high end of an interval, when the retraction is a range.
 * @property rationale Comment recorded next to the retraction.
 */
public data class GoRetraction(
    val low: String,
    val high: String? = null,
    val rationale: String? = null,
)

/**
 * Parses `go.mod` text into a [GoModuleFile].
 *
 * Unknown directives are ignored so a newer `go.mod` still yields the directives this
 * toolchain understands.
 */
public object GoModuleParser {
    /**
     * Parses [text]. A blank or unreadable file yields an empty module file.
     */
    public fun parse(text: String): GoModuleFile {
        val requirements = mutableListOf<GoRequirement>()
        val excludes = mutableListOf<GoModuleVersion>()
        val replacements = mutableListOf<GoReplacement>()
        val retractions = mutableListOf<GoRetraction>()
        var modulePath: String? = null
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
                "module" -> modulePath = args.firstOrNull()
                "go" -> goVersion = args.firstOrNull()
                "toolchain" -> toolchain = args.firstOrNull()
                "require" -> parseRequirement(args, line.comment)?.let(requirements::add)
                "exclude" -> parseModuleVersion(args)?.let(excludes::add)
                "replace" -> parseReplacement(args)?.let(replacements::add)
                "retract" -> parseRetraction(args, line.comment)?.let(retractions::add)
            }
        }

        return GoModuleFile(
            modulePath = modulePath,
            goVersion = goVersion,
            toolchain = toolchain,
            requirements = requirements,
            excludes = excludes,
            replacements = replacements,
            retractions = retractions,
        )
    }

    private fun parseRequirement(
        args: List<String>,
        comment: String?,
    ): GoRequirement? {
        val versioned = parseModuleVersion(args) ?: return null
        return GoRequirement(
            path = versioned.path,
            version = versioned.version,
            indirect = comment?.split(Regex("\\s+"))?.any { it == "indirect" } == true,
        )
    }
}
